package com.example.msdksample.aerial

import android.app.Application
import android.content.Context
import android.util.Log
// import com.cySdkyc.clx.Helper  // 第三方加固库，当前项目未集成
import dji.v5.common.register.DJISDKInitEvent
import dji.v5.manager.SDKManager
import dji.v5.common.error.IDJIError
import dji.v5.manager.interfaces.SDKManagerCallback

class MyApplication : Application() {

    private val TAG = this::class.simpleName

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        // 在调用install前，请勿调用任何MSDK相关
        // MSDK v5.10.0 之前的版本请使用 com.secneo.sdk.Helper.install(this)
        // com.cySdkyc.clx.Helper.install(this)  // 第三方加固库，当前项目未集成
    }

    override fun onCreate() {
        super.onCreate()
        initSDK()
    }

    private fun initSDK() {
        try {
            SDKManager.getInstance().init(this, object : SDKManagerCallback {
                override fun onInitProcess(event: DJISDKInitEvent?, totalProcess: Int) {
                    Log.i(TAG, "onInitProcess: $event, progress: $totalProcess")
                    if (event == DJISDKInitEvent.INITIALIZE_COMPLETE) {
                        SDKManager.getInstance().registerApp()
                    }
                }

                override fun onRegisterSuccess() {
                    Log.i(TAG, "SDK registered successfully")
                    AerialDroneManager.enableSimulator(
                        callback = object : AerialDroneManager.SimulatorCallback {
                            override fun onSuccess() {
                                Log.i(TAG, "Simulator auto-started (SDK-level, no real drone)")
                            }
                            override fun onFailure(message: String) {
                                Log.w(TAG, "Auto simulator start failed: $message")
                            }
                        }
                    )
                }

                override fun onRegisterFailure(error: IDJIError?) {
                    Log.e(TAG, "SDK register failed: ${error?.description()}")
                }

                override fun onProductConnect(productId: Int) {
                    Log.i(TAG, "Product connected: $productId")
                }

                override fun onProductDisconnect(productId: Int) {
                    Log.i(TAG, "Product disconnected: $productId")
                }

                override fun onProductChanged(productId: Int) {
                    Log.i(TAG, "Product changed: $productId")
                }

                override fun onDatabaseDownloadProgress(current: Long, total: Long) {
                    val progress = if (total > 0) current.toFloat() / total else 0f
                    Log.i(TAG, "Database download progress: $progress")
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "MSDK init failed", e)
        }
    }
}