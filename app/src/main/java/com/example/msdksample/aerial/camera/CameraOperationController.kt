package com.example.msdksample.aerial.camera

enum class CameraMode { PHOTO, VIDEO }
enum class CameraAction { PHOTO, START_RECORD, STOP_RECORD }
enum class CameraState { IDLE, BUSY, RECORDING }
enum class CameraOperationStatus { SUCCEEDED, FAILED, REJECTED }

data class CameraOperationResult(val status: CameraOperationStatus, val message: String)

interface CameraGateway {
    fun setMode(mode: CameraMode, callback: (String?) -> Unit)
    fun takePhoto(callback: (String?) -> Unit)
    fun startRecording(callback: (String?) -> Unit)
    fun stopRecording(callback: (String?) -> Unit)
}

/** Serializes camera operations and only advances after each DJI callback succeeds. */
class CameraOperationController(private val gateway: CameraGateway) {
    var state: CameraState = CameraState.IDLE
        private set

    private var busy = false

    fun execute(action: CameraAction, callback: (CameraOperationResult) -> Unit) {
        if (busy || (action == CameraAction.START_RECORD && state == CameraState.RECORDING) ||
            (action == CameraAction.STOP_RECORD && state != CameraState.RECORDING)
        ) {
            callback(CameraOperationResult(CameraOperationStatus.REJECTED, "相机当前状态不允许此操作"))
            return
        }

        busy = true
        state = CameraState.BUSY
        var completed = false
        fun finish(status: CameraOperationStatus, message: String, nextState: CameraState) {
            if (completed) return
            completed = true
            busy = false
            state = nextState
            callback(CameraOperationResult(status, message))
        }

        fun send(actionCall: ((String?) -> Unit) -> Unit, successState: CameraState) {
            actionCall { error ->
                if (error == null) {
                    finish(CameraOperationStatus.SUCCEEDED, "操作成功", successState)
                } else {
                    val previousState = if (action == CameraAction.STOP_RECORD) CameraState.RECORDING else CameraState.IDLE
                    finish(CameraOperationStatus.FAILED, error, previousState)
                }
            }
        }

        when (action) {
            CameraAction.PHOTO -> gateway.setMode(CameraMode.PHOTO) { error ->
                if (error != null) finish(CameraOperationStatus.FAILED, error, CameraState.IDLE)
                else send(gateway::takePhoto, CameraState.IDLE)
            }
            CameraAction.START_RECORD -> gateway.setMode(CameraMode.VIDEO) { error ->
                if (error != null) finish(CameraOperationStatus.FAILED, error, CameraState.IDLE)
                else send(gateway::startRecording, CameraState.RECORDING)
            }
            CameraAction.STOP_RECORD -> send(gateway::stopRecording, CameraState.IDLE)
        }
    }
}
