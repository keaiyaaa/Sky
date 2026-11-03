package com.example.msdksample.aerial

import android.util.Log
import com.example.msdksample.drone.DroneManager
import com.example.msdksample.drone.state.DeviceAction
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
    private val cameraIndex get() = ComponentIndexType.LEFT_OR_MAIN

    private fun requireRealDevice(callback: SimulatorCallback?, action: DeviceAction = DeviceAction.PHOTO): Boolean {
        val manager = DroneManager.getInstance()
        val generation = manager.deviceStateProvider.currentSnapshot().generation
        val result = manager.checkDeviceAction(action, generation)
        if (!result.allowed) callback?.onFailure("设备动作已拒绝：${result.errorCode}")
        return result.allowed
    }

    val isSimulatorActive: Boolean get() = DroneManager.getInstance().isDjiSdkSimulatorEnabled() == true

    fun enableSimulator(latitude: Double = 22.58, longitude: Double = 113.93, callback: SimulatorCallback? = null) {
        // Retain the legacy signature; F02 owns mode switching.
        if (latitude != 22.58 || longitude != 113.93) {
            callback?.onFailure("自定义 SDK 模拟位置尚未通过 F02 适配验证")
            return
        }
        DroneManager.getInstance().requestDjiSimulator(true) { success, message ->
            if (success) callback?.onSuccess() else callback?.onFailure(message ?: "SDK_SIMULATOR_ERROR")
        }
    }

    fun disableSimulator(callback: SimulatorCallback? = null) {
        DroneManager.getInstance().requestDjiSimulator(false) { success, message ->
            if (success) callback?.onSuccess() else callback?.onFailure(message ?: "SDK_SIMULATOR_ERROR")
        }
    }
    fun startShootPhoto(callback: SimulatorCallback? = null) {
        if (!requireRealDevice(callback)) return
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
        if (!requireRealDevice(callback, DeviceAction.VIDEO)) return
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
        if (!requireRealDevice(callback, DeviceAction.VIDEO)) return
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
        if (!requireRealDevice(callback)) return
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
        if (!requireRealDevice(callback, DeviceAction.VIDEO)) return
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
