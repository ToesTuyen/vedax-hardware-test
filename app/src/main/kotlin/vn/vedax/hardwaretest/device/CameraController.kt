package vn.vedax.hardwaretest.device

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import android.view.TextureView
import vn.vedax.hardwaretest.core.AppLog

data class CameraOption(val id: String, val label: String, val front: Boolean)

/** Owns Camera2 resources; the page only receives status and preview-ready events. */
class CameraController(
    private val context: Context,
    private val onStatus: (String) -> Unit,
    private val onPreviewReady: () -> Unit,
) {
    private val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var surface: Surface? = null
    private var generation = 0

    val isRunning: Boolean get() = device != null && session != null

    fun scan(): List<CameraOption> = try {
        val cameras = manager.cameraIdList.map { id ->
            val facing = manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING)
            val side = when (facing) {
                CameraCharacteristics.LENS_FACING_FRONT -> "trước"
                CameraCharacteristics.LENS_FACING_BACK -> "sau"
                else -> "ngoài"
            }
            CameraOption(id, "Camera $id ($side)", facing == CameraCharacteristics.LENS_FACING_FRONT)
        }
        onStatus(if (cameras.isEmpty())
            "Android Camera2 không thấy camera. Nếu USB có camera, có thể cần SDK UVC của hãng."
        else "Camera2 nhận ${cameras.size} camera. Chọn một camera để xem hình.")
        Log.i(AppLog.TAG, "Camera2 scan: ${cameras.size} camera(s); IDs=${cameras.map { it.id }}")
        cameras
    } catch (error: Exception) {
        onStatus("Không quét được camera: ${error.message}")
        Log.e(AppLog.TAG, "Camera2 scan failed", error)
        emptyList()
    }

    fun start(option: CameraOption, preview: TextureView) {
        if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            onStatus("Chưa được cấp quyền camera.")
            return
        }
        val texture = preview.surfaceTexture
        if (texture == null || !preview.isAvailable) {
            onStatus("Màn xem hình chưa sẵn sàng. Thử lại sau một giây.")
            return
        }
        stop()
        val token = generation
        val cameraThread = HandlerThread("CameraPreview").apply { start() }
        thread = cameraThread
        handler = Handler(cameraThread.looper)
        onStatus("Đang mở camera ${option.id}…")
        Log.i(AppLog.TAG, "Opening camera ID=${option.id}")
        try {
            manager.openCamera(option.id, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    if (token != generation) {
                        camera.close()
                        return
                    }
                    device = camera
                    configurePreview(camera, texture, token)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    if (device === camera) device = null
                    Log.w(AppLog.TAG, "Camera disconnected; ID=${camera.id}")
                    dispatch { onStatus("Camera đã ngắt kết nối.") }
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    if (device === camera) device = null
                    Log.e(AppLog.TAG, "Camera open error; ID=${camera.id}; code=$error")
                    dispatch { onStatus("Mở camera lỗi mã $error") }
                }
            }, handler)
        } catch (error: Exception) {
            onStatus("Mở camera thất bại: ${error.message}")
            Log.e(AppLog.TAG, "Camera open failed; ID=${option.id}", error)
            stop()
        }
    }

    private fun configurePreview(camera: CameraDevice, texture: SurfaceTexture, token: Int) {
        try {
            texture.setDefaultBufferSize(640, 480)
            val target = Surface(texture)
            surface = target
            val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply { addTarget(target) }
            camera.createCaptureSession(listOf(target), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(configured: CameraCaptureSession) {
                    if (token != generation || device !== camera) {
                        configured.close()
                        return
                    }
                    session = configured
                    try {
                        configured.setRepeatingRequest(request.build(), null, handler)
                        Log.i(AppLog.TAG, "Camera preview running; ID=${camera.id}")
                        dispatch {
                            onStatus("Camera đang hiển thị hình ảnh.")
                            onPreviewReady()
                        }
                    } catch (error: Exception) {
                        Log.e(AppLog.TAG, "Camera preview request failed", error)
                        dispatch { onStatus("Camera không phát hình: ${error.message}") }
                    }
                }

                override fun onConfigureFailed(configured: CameraCaptureSession) {
                    configured.close()
                    Log.e(AppLog.TAG, "Camera preview session configuration failed")
                    dispatch { onStatus("Camera không tạo được luồng xem hình.") }
                }
            }, handler)
        } catch (error: Exception) {
            Log.e(AppLog.TAG, "Camera preview setup failed", error)
            dispatch { onStatus("Camera không phát hình: ${error.message}") }
        }
    }

    fun stop() {
        generation++
        session?.close()
        session = null
        device?.close()
        device = null
        surface?.release()
        surface = null
        thread?.quitSafely()
        thread = null
        handler = null
    }

    private fun dispatch(action: () -> Unit) {
        val main = Handler(android.os.Looper.getMainLooper())
        main.post(action)
    }
}
