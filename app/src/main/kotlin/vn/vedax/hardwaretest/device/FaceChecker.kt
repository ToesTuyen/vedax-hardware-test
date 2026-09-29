package vn.vedax.hardwaretest.device

import android.graphics.Bitmap
import android.media.FaceDetector
import android.util.Log
import android.view.TextureView
import vn.vedax.hardwaretest.core.AppLog

/** Detects a face in one frame; this is not identity matching or FaceID authentication. */
class FaceChecker {
    fun check(preview: TextureView, onResult: (String) -> Unit) {
        val frame = preview.getBitmap(640, 480)
        if (frame == null) {
            onResult("Không lấy được khung hình.")
            return
        }
        onResult("Đang tìm khuôn mặt…")
        Thread({
            try {
                val rgb565 = frame.copy(Bitmap.Config.RGB_565, false)
                try {
                    val faces = arrayOfNulls<FaceDetector.Face>(5)
                    val count = FaceDetector(rgb565.width, rgb565.height, faces.size).findFaces(rgb565, faces)
                    Log.i(AppLog.TAG, "Face detection result: count=$count; identity verification=not implemented")
                    preview.post { onResult("Phát hiện $count khuôn mặt trong ảnh. Đây chưa phải xác thực FaceID.") }
                } finally {
                    rgb565.recycle()
                }
            } catch (error: Exception) {
                Log.e(AppLog.TAG, "Face detection failed", error)
                preview.post { onResult("Không phân tích được ảnh: ${error.message}") }
            } finally {
                frame.recycle()
            }
        }, "FaceCheck").start()
    }
}
