package com.example.msdksample.kami.mission

import android.content.Context
import android.util.Log
import com.example.msdksample.drone.NavPoint
import com.example.msdksample.drone.ObstacleAvoidanceMonitor
import com.example.msdksample.drone.SimulatedFlightEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 应用进程内唯一的本地任务执行器，复用现有模拟引擎，绝不调用 DJI 上传或飞行 API。
 * @param {Context} context 应用上下文。
 */
class LocalMissionRuntime private constructor(context: Context) {
    private val repository = MissionRunRepository(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** 同一互斥锁串行化推进、暂停和取消，保证终态与数据库一致。 */
    private val mutex = Mutex()
    private val ready = CompletableDeferred<Unit>()
    private val mutableState = MutableStateFlow<MissionRun?>(null)
    val state: StateFlow<MissionRun?> = mutableState
    /** 无法提交执行记录时单独报告存储故障，不用内存终态冒充数据库结果。 */
    private val mutableFailure = MutableStateFlow<String?>(null)
    val failure: StateFlow<String?> = mutableFailure
    private var engine: SimulatedFlightEngine? = null
    private var telemetry: AircraftTelemetry? = null

    init {
        scope.launch {
            try {
                repository.recoverInterrupted()
                mutableState.value = repository.history(1).firstOrNull()
                ready.complete(Unit)
            } catch (error: Exception) { ready.completeExceptionally(error) }
        }
    }

    /**
     * 创建并执行指定已保存航线，重复调用由 SQLite 唯一活动任务约束拒绝。
     * @param {String} routeId 已保存航线的稳定标识。
     * @param {MissionScenario} scenario 用户确认的可重复模拟场景。
     * @return {Unit} 启动失败时抛出异常；成功后进度通过 state 发布。
     */
    suspend fun start(routeId: String, scenario: MissionScenario) = withContext(Dispatchers.IO) {
        ready.await()
        val created = mutex.withLock {
            repository.create(routeId, scenario).also {
                mutableState.value = it; mutableFailure.value = null; engine = null; telemetry = null
            }
        }
        scope.launch { execute(created.id) }
        Unit
    }

    /**
     * 暂停或继续当前模拟任务，暂停期间不推进位置。
     * @return {Unit} 当前不处于可暂停或继续状态时明确失败。
     */
    suspend fun togglePause() = withContext(Dispatchers.IO) {
        ready.await()
        mutex.withLock {
            val current = mutableState.value ?: error("没有模拟任务")
            val target = when (current.state) {
                MissionRunState.RUNNING -> MissionRunState.PAUSED
                MissionRunState.PAUSED -> MissionRunState.RUNNING
                else -> error("当前任务状态不能暂停或继续")
            }
            mutableState.value = repository.update(current, target, if (target == MissionRunState.PAUSED) "用户暂停本地模拟" else "用户继续本地模拟")
        }
    }

    /**
     * 将活动模拟任务提交为取消终态，并停止模拟引擎。
     * @return {Unit} 取消事件落库后返回；没有活动任务时明确失败。
     */
    suspend fun cancel() = withContext(Dispatchers.IO) {
        ready.await()
        mutex.withLock {
            val current = mutableState.value ?: error("没有模拟任务")
            mutableState.value = repository.update(current, MissionRunState.CANCELLED, "用户取消本地模拟任务")
            mutableFailure.value = null
            engine?.stop()
            engine = null
        }
    }

    /**
     * 查询已落库的历史，不受页面重建影响。
     * @param {Int} offset 分页起点。
     * @return {List<MissionRun>} 最近一页执行记录。
     */
    suspend fun history(offset: Int = 0): List<MissionRun> = withContext(Dispatchers.IO) {
        ready.await(); repository.history(offset = offset)
    }

    /**
     * 查询某条任务的状态事件。
     * @param {String} runId 任务稳定标识。
     * @return {List<MissionRunEvent>} 按发生顺序排列的事件。
     */
    suspend fun events(runId: String): List<MissionRunEvent> = withContext(Dispatchers.IO) {
        ready.await(); repository.events(runId)
    }

    /**
     * 按固定节拍推进已创建任务，所有发布结果均来自已提交的数据库记录。
     * @param {String} runId 本轮任务标识，防止旧循环推进新任务。
     * @return {Unit} 到达终态或记录错误后结束循环。
     */
    private suspend fun execute(runId: String) {
        try {
            while (true) {
                delay(600)
                val active = mutex.withLock {
                    val current = mutableState.value
                    if (current == null || current.id != runId || !current.state.isActive) return@withLock false
                    val updated = when (current.state) {
                        MissionRunState.PREPARING -> repository.update(current, MissionRunState.UPLOADING, "本地校验通过，模拟上传中；未连接飞机")
                        MissionRunState.UPLOADING -> {
                            if (current.scenario == MissionScenario.UPLOAD_FAILURE) {
                                repository.update(current, MissionRunState.FAILED, "模拟上传失败：场景注入的传输错误，未启动飞行")
                            } else {
                                val points = repository.waypoints(current.id)
                                engine = SimulatedFlightEngine(points.map { NavPoint(it.latitude, it.longitude, it.altitude) },
                                    ObstacleAvoidanceMonitor(), arrivalThresholdMeters = 0.05,
                                    telemetryListener = { telemetry = it }, statusListener = {})
                                    .also { it.configure("brake", emptyList()); it.start() }
                                repository.update(current, MissionRunState.RUNNING, "模拟上传完成，开始本地航线推进")
                            }
                        }
                        MissionRunState.RUNNING -> advance(current)
                        else -> current
                    }
                    mutableState.value = updated
                    updated.state.isActive
                }
                if (!active) { engine?.stop(); engine = null; return }
            }
        } catch (error: Exception) {
            mutex.withLock {
                Log.e("LocalMissionRuntime", "模拟执行或记录提交失败", error)
                val current = mutableState.value
                if (current?.id == runId && current.state.isActive) {
                    // 写入错误不得继续发布成功；能写入时记录失败，不能写入则保留旧记录等待进程恢复。
                    runCatching { repository.update(current, MissionRunState.FAILED, "模拟执行失败，任务已停止，请重新发起") }
                        .onSuccess { mutableState.value = it }
                        .onFailure { mutableFailure.value = "执行记录保存失败，模拟已停止。请检查存储空间后取消任务，或重新打开应用恢复记录。" }
                }
                engine?.stop(); engine = null
            }
        }
    }

    /**
     * 推进复用的模拟引擎并持久化进度，注入断连场景时以失败终止。
     * @param {MissionRun} current 当前已提交且处于执行中的任务。
     * @return {MissionRun} 新的执行状态或终态。
     */
    private fun advance(current: MissionRun): MissionRun {
        if (current.scenario == MissionScenario.DISCONNECT && current.reachedPoints >= 1) {
            return repository.update(current, MissionRunState.FAILED, "模拟执行中断连，任务已停止；需手动重新发起")
        }
        val runningEngine = engine ?: error("模拟引擎不可用")
        runningEngine.tick()
        val reached = if (runningEngine.isFinished) current.totalPoints else runningEngine.targetIndex.coerceIn(0, current.totalPoints)
        val nextState = if (runningEngine.isFinished) MissionRunState.COMPLETED else MissionRunState.RUNNING
        return repository.update(current, nextState,
            if (nextState == MissionRunState.COMPLETED) "本地模拟完成，已到达全部航点；未执行真实拍摄或飞行"
            else "本地模拟已到达 $reached / ${current.totalPoints} 个航点", reached, telemetry)
    }

    companion object {
        @Volatile private var instance: LocalMissionRuntime? = null

        /**
         * 获取应用进程共用的执行器，页面离开和重建不会重复恢复或启动任务。
         * @param {Context} context 任意 Android 上下文。
         * @return {LocalMissionRuntime} 仅持有应用上下文的单例。
         */
        fun get(context: Context): LocalMissionRuntime = instance ?: synchronized(this) {
            instance ?: LocalMissionRuntime(context.applicationContext).also { instance = it }
        }
    }
}
