package com.example.msdksample.ui.local

import android.widget.TextView
import com.example.msdksample.data.remote.*
import kotlinx.coroutines.*

abstract class AccountCloudActivity : LocalToolsActivity() {
    protected val cloudStore by lazy { AccountCloudStore.getInstance(this) }
    protected lateinit var cloudStatus: TextView
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var ownerAtOpen: String? = null
    private var running = false
    private var requestEpoch=0L

    protected fun addCloudStatus() {
        ownerAtOpen = localProfileOwner(this)
        val session = BackendSessions.registry.current()
        cloudStatus = text(if (session == null) "云端未登录；演示登录不能访问账户数据。"
            else "云端账号：${session.username}；账户记录与本机演示分别保存。")
    }
    protected fun <T> cloud(action: suspend (BackendAccountApi, BackendSessionRegistry.Lease) -> T,
                             success: (T, BackendSessionRegistry.Lease) -> Unit) {
        val lease = accountLease() ?: return
        if (running) { toast("正在处理账户请求，请稍候"); return }
        running = true
        val epoch=++requestEpoch
        cloudStatus.text = "正在处理账户请求…"
        scope.launch {
            try {
                val result = action(BackendAccountRuntime.api(applicationContext), lease)
                if (epoch==requestEpoch && BackendSessions.registry.isCurrent(lease)) success(result, lease)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if(epoch!=requestEpoch) return@launch
                cloudStatus.text = when (error) {
                    is AccountApiFailure -> error.message
                    is IllegalStateException -> error.message
                    else -> "网络或响应异常，尚未确认云端保存；待处理内容已保留。"
                }
            } finally { if(epoch==requestEpoch) running = false }
        }
    }
    protected fun samePageIdentity(): Boolean {
        if (ownerAtOpen != localProfileOwner(this)) {
            toast("登录身份已变化，请重新打开页面"); finish(); return false
        }
        return true
    }
    protected fun accountLease(): BackendSessionRegistry.Lease? {
        if (!samePageIdentity()) return null
        return BackendSessions.registry.current().also {
            if (it == null) cloudStatus.text = "请先完成云端登录；当前本机功能仍可使用。"
        }
    }
    override fun onStart() {
        super.onStart()
        if (ownerAtOpen != null && ownerAtOpen != localProfileOwner(this)) {
            toast("登录身份已变化，请重新打开页面"); finish()
        }
    }
    protected fun cancelCloudRequests() { requestEpoch++; scope.coroutineContext.cancelChildren(); running=false }
    override fun onStop() { cancelCloudRequests(); super.onStop() }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
