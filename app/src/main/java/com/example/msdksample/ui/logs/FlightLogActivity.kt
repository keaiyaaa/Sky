package com.example.msdksample.ui.logs

import android.content.pm.ApplicationInfo
import android.view.View
import android.widget.*
import androidx.lifecycle.lifecycleScope
import com.example.msdksample.R
import com.example.msdksample.data.local.*
import com.example.msdksample.data.logs.FlightLogPolicy
import com.example.msdksample.data.remote.*
import com.example.msdksample.kami.mission.MissionRunRepository
import com.example.msdksample.ui.local.AccountCloudActivity
import com.google.gson.Gson
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class FlightLogActivity : AccountCloudActivity() {
    private val store by lazy { LocalDataStore.getInstance(this) }
    private lateinit var entries: LinearLayout
    private lateinit var summary: TextView
    private lateinit var keyword: EditText
    private lateinit var fromDate: EditText
    private lateinit var toDate: EditText
    private var scopeIndex=0
    private var sourceCode: String?=null
    private var categoryCode: String?=null
    private var page=0
    private var more=false
    private var refreshJob: Job?=null
    private data class Position(val local: Int=0,val mission: Int=0)
    private data class LocalPage(val rows: List<FlightLogEntry>,val localBound: Long,val missionBound: Long)
    private val positions=mutableListOf(Position())
    private var nextPosition=Position()
    private var maxLocal=Long.MAX_VALUE
    private var maxMission=Long.MAX_VALUE
    private val json=Gson()

    override fun initViews() {
        setupPage("飞行日志","按当前身份查看业务事件。设备上报是客户端报告，不构成真实飞行证明；旧记录保留为未归属历史。")
        addCloudStatus()
        selector(R.id.log_scope,listOf("当前身份的本机日志","本机未归属历史（只读）","账户日志")) { scopeIndex=it; refresh(true) }
        keyword=field("按操作、结果、任务编号或原因检索","",R.id.log_keyword)
        fromDate=field("开始日期（北京时间，yyyy-MM-dd，选填）","",R.id.log_from)
        toDate=field("结束日期（含当天，选填）","",R.id.log_to)
        selector(R.id.log_source,listOf("全部来源")+FlightLogPolicy.sources.map(FlightLogPolicy::sourceLabel)) {
            sourceCode=if(it==0) null else FlightLogPolicy.sources[it-1]; refresh(true)
        }
        val categories=listOf("CONNECTION","SETTING","COMMAND","MISSION")
        selector(R.id.log_category,listOf("全部分类")+categories.map(FlightLogPolicy::categoryLabel)) {
            categoryCode=if(it==0) null else categories[it-1]; refresh(true)
        }
        actions("查询／刷新" to { refresh(true) },"清空检索" to {
            keyword.setText(""); fromDate.setText(""); toDate.setText(""); refresh(true)
        })
        if(applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE!=0) button("生成演示飞行事件") {
            if(!samePageIdentity()) return@button
            runCatching {
                val taskId="DEMO-${System.currentTimeMillis()}"
                store.record("指令","模拟起飞","成功","本地演示","仅生成演示事件，未执行飞控",taskId)
                store.record("任务","模拟巡检","中断","本地演示","演示：低电量触发中断",taskId)
                store.record("指令","模拟返航","失败","本地演示","演示：连接丢失",taskId)
            }.onSuccess { refresh(true) }.onFailure { toast("保存演示日志失败") }
        }
        summary=text("").apply { id=R.id.log_summary }
        actions("上一页" to {
            if(page>0) { page--; if(scopeIndex!=2) positions.removeAt(positions.lastIndex); refresh(false) }
        },"下一页" to {
            if(more) { page++; if(scopeIndex!=2) positions+=nextPosition; refresh(false) }
        })
        entries=section("事件记录")
        refresh(true)
    }
    private fun selector(viewId: Int,labels: List<String>, selected: (Int)->Unit) {
        Spinner(this).apply {
            id=viewId
            adapter=ArrayAdapter(this@FlightLogActivity,android.R.layout.simple_spinner_dropdown_item,labels)
            binding.content.addView(this,LinearLayout.LayoutParams(-1,dp(48)))
            onItemSelectedListener=object: AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?,view: View?,position: Int,id: Long) { selected(position) }
                override fun onNothingSelected(parent: AdapterView<*>?)=Unit
            }
        }
    }
    override fun onResume() { super.onResume(); if(::entries.isInitialized) refresh(true) }
    override fun onStop() { refreshJob?.cancel(); super.onStop() }

    private fun refresh(reset: Boolean) {
        if(!::entries.isInitialized || !samePageIdentity()) return
        refreshJob?.cancel(); cancelCloudRequests()
        val query=keyword.text.toString().trim()
        val dates=runCatching { FlightLogPolicy.day(fromDate.text.toString().trim()) to FlightLogPolicy.day(toDate.text.toString().trim(),true) }
            .getOrElse { summary.text="日期无效，请使用yyyy-MM-dd格式"; return }
        if(query.codePointCount(0,query.length)>128 || (dates.first!=null && dates.second!=null && dates.first!!>=dates.second!!)) {
            summary.text="检索词最多128字，开始日期不得晚于结束日期"; return
        }
        if(reset) { page=0; positions.clear(); positions+=Position() }
        more=false
        val selectedSource=sourceCode
        val selectedCategory=categoryCode
        val mode=scopeIndex
        val selectedPage=page
        if(mode==2) {
            entries.removeAllViews(); summary.text="账户日志尚未读取，请查看上方请求状态"
            val filter=FlightLogFilter(query,selectedSource,selectedCategory,dates.first?.let(FlightLogPolicy::utc),dates.second?.let(FlightLogPolicy::utc))
            cloud({api,lease -> api.flightLogs(lease,selectedPage,filter)}) { result,_ ->
                summary.text="账户第${result.page+1}页，共${result.total}条；" + if(result.missionEventsAvailable) "含运行事件视图" else "运行模块尚未接入，仅含已上报业务事件"
                more=(result.page+1L)*result.size<result.total
                cloudStatus.text="账户日志已读取"
                entries.removeAllViews()
                if(result.items.isEmpty()) text("暂无符合条件的账户日志",entries)
                result.items.forEach { item ->
                    show(FlightLogPolicy.parseUtc(item.occurredAt),item.category,item.action,item.result,item.source,item.detail,item.taskId,
                        if(item.origin=="MISSION_EVENT") "运行事件视图" else "客户端上报")
                }
            }
            return
        }
        val owner=if(mode==1) FlightLogPolicy.LEGACY_OWNER else AccountIdentity.owner(this)
        val position=positions.last()
        val previousLocalBound=maxLocal
        val previousMissionBound=maxMission
        refreshJob=lifecycleScope.launch {
            try {
                val result=withContext(Dispatchers.IO) {
                    MissionRunRepository(applicationContext).use { missions ->
                        val localBound=if(reset) store.maxLogId() else previousLocalBound
                        val missionBound=if(reset) missions.maxLogId() else previousMissionBound
                        val local=store.logs(query,limit=21,owner=owner,offset=position.local,maxId=localBound,from=dates.first,to=dates.second,
                            category=selectedCategory?.let(FlightLogPolicy::categoryLabel),sourceCode=selectedSource)
                        val runs=if((selectedCategory==null || selectedCategory=="MISSION") && (selectedSource==null || selectedSource=="LOCAL_SIMULATION"))
                            missions.logs(query,limit=21,owner=owner,offset=position.mission,maxId=missionBound,from=dates.first,to=dates.second) else emptyList()
                        LocalPage((local+runs).sortedWith(compareByDescending<FlightLogEntry>{it.time}.thenBy{if(it.id<0) 1 else 0}.thenByDescending{kotlin.math.abs(it.id)}),localBound,missionBound)
                    }
                }
                if(!samePageIdentity()) return@launch
                maxLocal=result.localBound; maxMission=result.missionBound
                more=result.rows.size>20
                val rows=result.rows.take(20)
                nextPosition=Position(position.local+rows.count{it.id>0},position.mission+rows.count{it.id<0})
                summary.text="${if(mode==1) "未归属历史（不会上传）" else "当前身份本机日志"} · 第${page+1}页，${rows.size}条"
                entries.removeAllViews()
                if(rows.isEmpty()) text("暂无符合条件的日志",entries)
                rows.forEach { item ->
                    show(item.time,FlightLogPolicy.category(item.category),item.action,item.result,item.sourceCode,item.detail,item.taskId,
                        if(item.id<0) "运行事件视图" else if(item.uploaded) "已上报" else "本机记录")
                    if(mode==0 && item.owner.startsWith("user:") && !item.uploaded && item.sourceCode !in listOf("LOCAL_SIMULATION","DJI_SIMULATOR") && FlightLogPolicy.category(item.category) in listOf("CONNECTION","SETTING","COMMAND")) {
                        button("上报／重试此事件",entries) { upload(item) }
                    }
                }
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(error: Exception) { summary.text="日志读取失败，请重试" }
        }
    }
    private fun show(time: Long,category: String,action: String,result: String,source: String,detail: String,taskId: String,origin: String) {
        val format=SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.CHINA).apply { timeZone=TimeZone.getTimeZone("Asia/Shanghai") }
        text("${format.format(Date(time))} · ${FlightLogPolicy.sourceLabel(source)} · $origin\n${FlightLogPolicy.categoryLabel(category)} / ${FlightLogPolicy.clean(action)} · ${FlightLogPolicy.clean(result)}\n"+
            (if(taskId.isNotBlank()) "任务：${FlightLogPolicy.clean(taskId)}\n" else "")+FlightLogPolicy.clean(detail),entries)
    }
    private fun upload(entry: FlightLogEntry) {
        val lease=accountLease() ?: return
        if(entry.owner!="user:${lease.userId}") { toast("日志不属于当前账号"); return }
        val body=runCatching { store.prepareLogUpload(entry.owner,entry.clientId) { item ->
            check(AccountFieldRules.validText(item.action,128,true) && AccountFieldRules.validText(item.result,64,true) &&
                item.detail.codePointCount(0,item.detail.length)<=2000 && AccountFieldRules.validText(item.taskId,128)) { "日志内容超出接口限制，已保留本机原记录" }
            json.toJson(FlightLogCreate(item.clientId,FlightLogPolicy.category(item.category),item.action,item.result,item.sourceCode,
                item.detail,item.taskId,FlightLogPolicy.utc(item.time),idempotencyKey=UUID.randomUUID().toString()))
        } }.getOrElse { cloudStatus.text=it.message ?: "待上报请求保存失败"; return }
        cloud({api,current -> check(current.userId==lease.userId); api.uploadFlightLog(current,body,entry.clientId)}) { _,_ ->
            store.confirmLogUpload(entry.owner,entry.clientId,body)
            cloudStatus.text="事件已上报；它仍是客户端报告，不构成飞行证明"
            refresh(false)
        }
    }
}
