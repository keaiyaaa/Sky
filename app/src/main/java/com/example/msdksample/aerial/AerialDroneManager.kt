package com.example.msdksample.aerial

import android.util.Log
import dji.sdk.keyvalue.key.CameraKey
import dji.sdk.keyvalue.key.FlightControllerKey
import dji.sdk.keyvalue.key.KeyTools
import dji.sdk.keyvalue.value.common.ComponentIndexType
import dji.sdk.keyvalue.value.common.EmptyMsg
import dji.sdk.keyvalue.value.flightcontroller.SimulatorInitializationSettings
import dji.v5.common.callback.CommonCallbacks
import dji.v5.common.error.IDJIError
import dji.v5.common.utils.RxUtil
import dji.v5.manager.KeyManager

object AerialDroneManager {

    private const val TAG = "AerialDroneManager"
    private const val DEFAULT_SATELLITE_COUNT = 10
    private val cameraIndex = ComponentIndexType.LEFT_OR_MAIN

    private var isSimulatorEnabled = false

    val isSimulatorActive: Boolean get() = isSimulatorEnabled

    fun enableSimulator(
        latitude: Double = 22.58,
        longitude: Double = 113.93,
        callback: SimulatorCallback? = null
    ) {
        Log.i(TAG, "Starting simulator at ($latitude, $longitude)...")
        val settings = SimulatorInitializationSettings(latitude, longitude, DEFAULT_SATELLITE_COUNT)
        val key = KeyTools.createKey(FlightControllerKey.KeyStartSimulator)
        RxUtil.performActionWithOutResult(key, settings)
            .subscribe({
                isSimulatorEnabled = true
                Log.i(TAG, "Simulator started — drone connected, motors NOT spinning")
                callback?.onSuccess()
            }) { error ->
                val msg = (error as? IDJIError)?.description() ?: error.message ?: "unknown"
                Log.e(TAG, "Simulator start failed: $msg")
                callback?.onFailure("启动模拟器失败: $msg")
            }
    }

    fun disableSimulator(callback: SimulatorCallback? = null) {
        val key = KeyTools.createKey(FlightControllerKey.KeyStopSimulator)
        KeyManager.getInstance().performAction(key, object : CommonCallbacks.CompletionCallbackWithParam<EmptyMsg> {
            override fun onSuccess(t: EmptyMsg?) {
                isSimulatorEnabled = false
                Log.i(TAG, "Simulator stopped")
                callback?.onSuccess()
            }
            override fun onFailure(error: IDJIError) {
                Log.e(TAG, "Simulator stop failed: ${error.description()}")
                callback?.onFailure("停用模拟器失败: ${error.description()}")
            }
        })
    }

    fun startShootPhoto(callback: SimulatorCallback? = null) {
        val key = KeyTools.createKey(CameraKey.KeyStartShootPhoto, cameraIndex)
        KeyManager.getInstance().performAction(key, object : CommonCallbacks.CompletionCallbackWithParam<EmptyMsg> {
            override fun onSuccess(t: EmptyMsg?) {
                Log.i(TAG, "Photo captured successfully")
                callback?.onSuccess()
            }
            override fun onFailure(error: IDJIError) {
                Log.e(TAG, "Photo capture failed: ${error.description()}")
                callback?.onFailure("拍照失败: ${error.description()}")
            }
        })
    }

    fun startRecordVideo(callback: SimulatorCallback? = null) {
        val key = KeyTools.createKey(CameraKey.KeyStartRecord, cameraIndex)
        KeyManager.getInstance().performAction(key, object : CommonCallbacks.CompletionCallbackWithParam<EmptyMsg> {
            override fun onSuccess(t: EmptyMsg?) {
                Log.i(TAG, "Video recording started")
                callback?.onSuccess()
            }
            override fun onFailure(error: IDJIError) {
                Log.e(TAG, "Start recording failed: ${error.description()}")
                callback?.onFailure("开始录像失败: ${error.description()}")
            }
        })
    }

    fun stopRecordVideo(callback: SimulatorCallback? = null) {
        val key = KeyTools.createKey(CameraKey.KeyStopRecord, cameraIndex)
        KeyManager.getInstance().performAction(key, object : CommonCallbacks.CompletionCallbackWithParam<EmptyMsg> {
            override fun onSuccess(t: EmptyMsg?) {
                Log.i(TAG, "Video recording stopped")
                callback?.onSuccess()
            }
            override fun onFailure(error: IDJIError) {
                Log.e(TAG, "Stop recording failed: ${error.description()}")
                callback?.onFailure("停止录像失败: ${error.description()}")
            }
        })
    }

    fun setCameraModeToPhoto(callback: SimulatorCallback? = null) {
        val key = KeyTools.createKey(CameraKey.KeyCameraMode, cameraIndex)
        KeyManager.getInstance().setValue(key, dji.sdk.keyvalue.value.camera.CameraMode.PHOTO_NORMAL, object : CommonCallbacks.CompletionCallback {
            override fun onSuccess() {
                Log.i(TAG, "Camera mode set to PHOTO")
                callback?.onSuccess()
            }
            override fun onFailure(error: IDJIError) {
                Log.e(TAG, "Set camera mode failed: ${error.description()}")
                callback?.onFailure("切换拍照模式失败: ${error.description()}")
            }
        })
    }

    fun setCameraModeToVideo(callback: SimulatorCallback? = null) {
        val key = KeyTools.createKey(CameraKey.KeyCameraMode, cameraIndex)
        KeyManager.getInstance().setValue(key, dji.sdk.keyvalue.value.camera.CameraMode.VIDEO_NORMAL, object : CommonCallbacks.CompletionCallback {
            override fun onSuccess() {
                Log.i(TAG, "Camera mode set to VIDEO")
                callback?.onSuccess()
            }
            override fun onFailure(error: IDJIError) {
                Log.e(TAG, "Set camera mode failed: ${error.description()}")
                callback?.onFailure("切换录像模式失败: ${error.description()}")
            }
        })
    }

    interface SimulatorCallback {
        fun onSuccess()
        fun onFailure(message: String)
    }
}
