package vn.vedax.hardwaretest.ui.features

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.util.Log
import android.view.TextureView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import vn.vedax.hardwaretest.core.AppLog
import vn.vedax.hardwaretest.device.CameraController
import vn.vedax.hardwaretest.device.CameraOption
import vn.vedax.hardwaretest.device.FaceChecker
import vn.vedax.hardwaretest.ui.FeaturePage
import vn.vedax.hardwaretest.ui.UiKit

class CameraPage(private val activity: Activity, private val ui: UiKit, private val faceMode: Boolean) : FeaturePage {
    companion object { private const val CAMERA_PERMISSION_REQUEST = 7 }

    private lateinit var picker: Spinner
    private lateinit var preview: TextureView
    private lateinit var status: TextView
    private lateinit var faceStatus: TextView
    private var options = emptyList<CameraOption>()
    private val faceChecker = FaceChecker()
    private val camera by lazy {
        CameraController(activity, { text -> activity.runOnUiThread { status.text = text } }, {
            if (faceMode) preview.postDelayed(::checkFace, 1200)
        })
    }

    override fun show(parent: LinearLayout) {
        val card = ui.card(parent, if (faceMode) "Camera FaceID" else "Camera",
            if (faceMode) "Tìm khuôn mặt trong khung hình" else "Xem hình trực tiếp")
        picker = Spinner(activity).apply {
            background = ui.rounded(0xffedf3fa.toInt(), ui.dp(14))
            setPadding(ui.dp(10), ui.dp(8), ui.dp(10), ui.dp(8))
        }
        card.addView(picker)
        val actions = ui.row(card)
        ui.button(actions, "Quét", ::refresh)
        ui.button(actions, "Mở camera", ::start)
        ui.button(actions, "Tắt", camera::stop)
        preview = TextureView(activity)
        val previewContainer = FrameLayout(activity).apply {
            background = ui.rounded(0xff152c4c.toInt(), ui.dp(20))
            clipToOutline = true
            addView(preview, FrameLayout.LayoutParams(-1, -1))
        }
        card.addView(previewContainer, LinearLayout.LayoutParams(-1,
            ui.dp(if (activity.resources.configuration.screenWidthDp >= 700) 330 else 260)).apply {
            topMargin = ui.dp(10)
        })
        preview.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) = start()
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                camera.stop()
                return true
            }
            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
        }
        status = ui.status("Chưa kiểm tra")
        card.addView(status)
        if (faceMode) ui.button(card, "Kiểm tra lại khuôn mặt", ::checkFace)
        faceStatus = ui.status("FaceID · Đang chờ camera")
        if (faceMode) card.addView(faceStatus)
        refresh()
    }

    private fun refresh() {
        options = camera.scan()
        picker.adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item,
            options.map { it.label })
        if (faceMode) picker.setSelection(options.indexOfFirst { it.front }.coerceAtLeast(0))
    }

    private fun start() {
        if (options.isEmpty()) {
            status.text = "Không có camera trong Camera2."
            Log.w(AppLog.TAG, "Camera open skipped: Camera2 has no camera")
            return
        }
        if (activity.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.i(AppLog.TAG, "Requesting camera permission")
            activity.requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
            return
        }
        camera.start(options[picker.selectedItemPosition.coerceIn(0, options.lastIndex)], preview)
    }

    private fun checkFace() {
        if (!camera.isRunning || !preview.isAvailable) {
            faceStatus.text = "Hãy bật camera và đợi thấy hình trước."
            Log.w(AppLog.TAG, "Face check skipped: preview not running")
            return
        }
        faceChecker.check(preview) { faceStatus.text = it }
    }

    override fun onPause() { camera.stop() }

    override fun onPermissionResult(requestCode: Int, grantResults: IntArray) {
        if (requestCode != CAMERA_PERMISSION_REQUEST) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            Log.i(AppLog.TAG, "Camera permission granted")
            start()
        } else {
            status.text = "Chưa được cấp quyền camera."
            Log.w(AppLog.TAG, "Camera permission denied")
        }
    }
}
