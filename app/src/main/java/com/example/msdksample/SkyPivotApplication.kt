package com.example.msdksample

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.cySdkyc.clx.Helper
import com.example.msdksample.drone.DroneManager

/**
 * 统一 Application 入口。
 *
 * 原项目 aerial/asuka/kami 三个模块各写了一个 Application（MyApplication、
 * InspectionApplication、KamiApplication），但 Manifest 从未指定 android:name，
 * 导致它们都不生效、DJI SDK 从未被初始化。
 *
 * 本类接管全局初始化，将 DJI MSDK v5 的初始化/注册/连接回调统一收敛到
 * [DroneManager]（单例），供主页、设置页、航拍/航线/巡检等所有模块共享。
 *
 * 【DJI MSDK v5 加固加载机制（关键）】
 * 5.18.0 的 dji-sdk-v5-aircraft AAR 是 secneo 加固产物：
 *   - classes.jar 内仅有 com.cySdkyc.clx.Helper（解密入口）；
 *   - 真正的 SDK Java API 类以加密形式打包在 AAR 的 assets 中；
 *   - SDK 编译期 API（dji.v5.* / dji.sdk.*）由 -aircraft-provided jar 提供
 *     （仅 compileOnly，不打进 APK）。
 * 因此必须在【任何 MSDK API 调用之前】、于 attachBaseContext 中调用
 * Helper.install(this)，由 Helper 解密并把 SDK 类注入运行时——
 * 否则运行时找不到类会抛 NoClassDefFoundError；
 * 反之若把 provided jar 直接打进 APK，其加固剥离后的字节码会被 ART 拒绝，
 * 抛 VerifyError（例如 ProductType 的 Constructor 未调用 superclass 构造器）。
 *
 * 其余防闪退设计：
 *   1) SDK 初始化延迟到主线程空闲（Handler.post），首页先渲染；
 *   2) 初始化与 Helper 调用均包 Throwable 兜底，绝不影响 App 启动；
 *   3) 全局未捕获异常记录器把堆栈写入 logcat，便于真机定位。
 */
class SkyPivotApplication : Application() {

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        // DJI MSDK v5（≥5.10.0）加固库：必须先于任何 MSDK API 调用执行解密注入
        try {
            Helper.install(this)
        } catch (t: Throwable) {
            Log.e(TAG, "DJI Helper.install 失败（SDK 不可用，不影响 UI）", t)
        }
    }

    override fun onCreate() {
        super.onCreate()
        installCrashLogRecorder()

        // 延迟到主线程空闲后初始化 DJI SDK：保证首页先渲染，
        // SDK 是否可用都不阻断进程启动。
        Handler(Looper.getMainLooper()).post {
            try {
                DroneManager.getInstance().initSDK(applicationContext) { success, message ->
                    Log.i(TAG, "DJI SDK init result: success=$success, message=$message")
                }
            } catch (t: Throwable) {
                // 双重保险：即便仍有 Error 逃逸，也不让 Application.onCreate 崩溃
                Log.e(TAG, "DJI SDK 延迟初始化异常（已忽略，不影响 UI）", t)
            }
        }
    }

    /** 注册未捕获异常记录器：堆栈写入 logcat 后仍交给系统默认处理 */
    private fun installCrashLogRecorder() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Log.e(TAG, "Uncaught exception on ${thread.name}: ${throwable.message}", throwable)
            } catch (ignored: Throwable) {
                // 记录过程不允许再抛异常
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        private const val TAG = "SkyPivotApplication"
    }
}
