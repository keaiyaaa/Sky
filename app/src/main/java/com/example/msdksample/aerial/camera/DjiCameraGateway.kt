package com.example.msdksample.aerial.camera

import com.example.msdksample.aerial.AerialDroneManager

class DjiCameraGateway : CameraGateway {
    override fun setMode(mode: CameraMode, callback: (String?) -> Unit) {
        val result = object : AerialDroneManager.SimulatorCallback {
            override fun onSuccess() = callback(null)
            override fun onFailure(message: String) = callback(message)
        }
        when (mode) {
            CameraMode.PHOTO -> AerialDroneManager.setCameraModeToPhoto(result)
            CameraMode.VIDEO -> AerialDroneManager.setCameraModeToVideo(result)
        }
    }

    override fun takePhoto(callback: (String?) -> Unit) = perform(callback, AerialDroneManager::startShootPhoto)
    override fun startRecording(callback: (String?) -> Unit) = perform(callback, AerialDroneManager::startRecordVideo)
    override fun stopRecording(callback: (String?) -> Unit) = perform(callback, AerialDroneManager::stopRecordVideo)

    private fun perform(
        callback: (String?) -> Unit,
        operation: (AerialDroneManager.SimulatorCallback?) -> Unit
    ) {
        operation(object : AerialDroneManager.SimulatorCallback {
            override fun onSuccess() = callback(null)
            override fun onFailure(message: String) = callback(message)
        })
    }
}
