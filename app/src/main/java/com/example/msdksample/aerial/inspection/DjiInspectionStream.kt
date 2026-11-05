package com.example.msdksample.aerial.inspection

import android.view.SurfaceHolder
import android.view.SurfaceView
import dji.sdk.keyvalue.value.common.ComponentIndexType
import dji.v5.manager.datacenter.MediaDataCenter
import dji.v5.manager.interfaces.ICameraStreamManager
import dji.v5.manager.interfaces.ICameraStreamManager.CameraFrameListener
import dji.v5.manager.interfaces.ICameraStreamManager.FrameFormat
import dji.v5.manager.interfaces.ICameraStreamManager.ScaleType

class DjiInspectionStream(
    private val surfaceView: SurfaceView,
    private val onRgbaFrame: (ByteArray, Int, Int, Int, Int) -> Unit,
    private val onError: () -> Unit
) : SurfaceHolder.Callback {

    private val cameraIndex = ComponentIndexType.LEFT_OR_MAIN
    private var cameraStreamManager: ICameraStreamManager? = null
    private var surfaceHolder: SurfaceHolder? = null
    private var enabled = true
    private var closed = false
    private var listenerAttached = false

    private val frameListener = object : CameraFrameListener {
        override fun onFrame(
            frameData: ByteArray,
            offset: Int,
            length: Int,
            width: Int,
            height: Int,
            format: FrameFormat
        ) {
            if (format == FrameFormat.RGBA_8888) {
                onRgbaFrame(frameData, offset, length, width, height)
            }
        }
    }

    init {
        surfaceView.holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceHolder = holder
        attachIfReady()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        surfaceHolder = holder
        val manager = cameraStreamManager
        if (enabled && listenerAttached && holder.surface.isValid && manager != null) {
            try {
                manager.putCameraStreamSurface(cameraIndex, holder.surface, width, height, ScaleType.CENTER_INSIDE)
            } catch (_: RuntimeException) {
                onError()
            }
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        if (surfaceHolder === holder) surfaceHolder = null
        detach()
    }

    fun pause() {
        enabled = false
        detach()
    }

    fun resume() {
        if (closed) return
        enabled = true
        attachIfReady()
    }

    fun release() {
        closed = true
        enabled = false
        detach()
        surfaceView.holder.removeCallback(this)
        surfaceHolder = null
    }

    private fun attachIfReady() {
        val holder = surfaceHolder ?: return
        if (!enabled || closed || listenerAttached || !holder.surface.isValid) return
        try {
            val manager = MediaDataCenter.getInstance().cameraStreamManager
            cameraStreamManager = manager
            manager.addFrameListener(cameraIndex, FrameFormat.RGBA_8888, frameListener)
            listenerAttached = true
            manager.putCameraStreamSurface(
                cameraIndex,
                holder.surface,
                surfaceView.width.coerceAtLeast(1),
                surfaceView.height.coerceAtLeast(1),
                ScaleType.CENTER_INSIDE
            )
        } catch (_: RuntimeException) {
            detach()
            onError()
        }
    }

    private fun detach() {
        val manager = cameraStreamManager ?: return
        if (listenerAttached) {
            try {
                manager.removeFrameListener(frameListener)
            } catch (_: RuntimeException) {
                // SDK 断开或表面销毁期间可能已经清理 listener。
            }
            listenerAttached = false
        }
        try {
            surfaceHolder?.surface?.takeIf { it.isValid }?.let(manager::removeCameraStreamSurface)
        } catch (_: RuntimeException) {
            // Surface 可能已由系统释放。
        }
        cameraStreamManager = null
    }
}
