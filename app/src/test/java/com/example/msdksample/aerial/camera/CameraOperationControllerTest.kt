package com.example.msdksample.aerial.camera

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraOperationControllerTest {
    private class FakeCameraGateway : CameraGateway {
        var modeError: String? = null
        var stopError: String? = null
        var photoCalls = 0
        var startCalls = 0
        var stopCalls = 0
        var holdModeCallback = false
        var pendingModeCallback: ((String?) -> Unit)? = null
        override fun setMode(mode: CameraMode, callback: (String?) -> Unit) {
            if (holdModeCallback) pendingModeCallback = callback else callback(modeError)
        }
        override fun takePhoto(callback: (String?) -> Unit) { photoCalls++; callback(null) }
        override fun startRecording(callback: (String?) -> Unit) { startCalls++; callback(null) }
        override fun stopRecording(callback: (String?) -> Unit) { stopCalls++; callback(stopError) }
    }

    @Test fun modeSwitchFailureNeverSendsPhotoCommand() {
        val gateway = FakeCameraGateway().apply { modeError = "模式切换失败" }
        val controller = CameraOperationController(gateway)
        var result: CameraOperationResult? = null
        controller.execute(CameraAction.PHOTO) { result = it }
        assertEquals(0, gateway.photoCalls)
        assertEquals(CameraOperationStatus.FAILED, result?.status)
        assertEquals(CameraState.IDLE, controller.state)
    }

    @Test fun videoModeFailureNeverStartsRecording() {
        val gateway = FakeCameraGateway().apply { modeError = "录像模式切换失败" }
        val controller = CameraOperationController(gateway)
        var result: CameraOperationResult? = null
        controller.execute(CameraAction.START_RECORD) { result = it }
        assertEquals(0, gateway.startCalls)
        assertEquals(CameraOperationStatus.FAILED, result?.status)
        assertEquals(CameraState.IDLE, controller.state)
    }

    @Test fun repeatedStartWhileBusyIsRejectedAndSendsOnlyOneCommand() {
        val gateway = FakeCameraGateway()
        gateway.holdModeCallback = true
        val controller = CameraOperationController(gateway)
        var first: CameraOperationResult? = null
        var second: CameraOperationResult? = null
        controller.execute(CameraAction.START_RECORD) { first = it }
        controller.execute(CameraAction.START_RECORD) { second = it }
        assertEquals(CameraOperationStatus.REJECTED, second?.status)
        assertEquals(0, gateway.startCalls)
        gateway.pendingModeCallback?.invoke(null)
        assertEquals(1, gateway.startCalls)
        assertEquals(CameraOperationStatus.SUCCEEDED, first?.status)
        assertEquals(CameraState.RECORDING, controller.state)
        controller.execute(CameraAction.START_RECORD) { second = it }
        assertEquals(CameraOperationStatus.REJECTED, second?.status)
        assertEquals(1, gateway.startCalls)
    }

    @Test fun failedStopKeepsRecordingStateAndReportsFailure() {
        val gateway = FakeCameraGateway().apply { stopError = "设备断开" }
        val controller = CameraOperationController(gateway)
        controller.execute(CameraAction.START_RECORD) {}
        var result: CameraOperationResult? = null
        controller.execute(CameraAction.STOP_RECORD) { result = it }
        assertEquals(CameraOperationStatus.FAILED, result?.status)
        assertEquals(CameraState.RECORDING, controller.state)
    }
}
