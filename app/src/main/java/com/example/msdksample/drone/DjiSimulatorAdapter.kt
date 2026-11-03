package com.example.msdksample.drone

import dji.sdk.keyvalue.key.FlightControllerKey
import dji.sdk.keyvalue.key.KeyTools
import dji.sdk.keyvalue.value.common.EmptyMsg
import dji.sdk.keyvalue.value.flightcontroller.SimulatorInitializationSettings
import dji.v5.common.callback.CommonCallbacks
import dji.v5.common.error.IDJIError
import dji.v5.common.utils.RxUtil
import dji.v5.manager.KeyManager

/** Only F02 calls this adapter. No SDK class is loaded for a local demo. */
internal object DjiSimulatorAdapter {
    fun change(enabled: Boolean, callback: (Boolean, String?) -> Unit) {
        try {
            if (enabled) {
                RxUtil.performActionWithOutResult(KeyTools.createKey(FlightControllerKey.KeyStartSimulator),
                    SimulatorInitializationSettings(22.58, 113.93, 10))
                    .subscribe({ callback(true, "DJI SDK 模拟器已启动，待设备验证") },
                        { callback(false, it.message ?: "SDK_SIMULATOR_ERROR") })
            } else {
                KeyManager.getInstance().performAction(KeyTools.createKey(FlightControllerKey.KeyStopSimulator),
                    object : CommonCallbacks.CompletionCallbackWithParam<EmptyMsg> {
                        override fun onSuccess(value: EmptyMsg?) { callback(true, "DJI SDK 模拟器已停止，待设备验证") }
                        override fun onFailure(error: IDJIError) { callback(false, error.description()) }
                    })
            }
        } catch (error: Throwable) { callback(false, error.message ?: "SDK_SIMULATOR_ERROR") }
    }
}
