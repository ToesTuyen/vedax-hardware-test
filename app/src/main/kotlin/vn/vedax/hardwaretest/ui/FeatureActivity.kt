package vn.vedax.hardwaretest.ui

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.LinearLayout
import android.widget.ScrollView
import vn.vedax.hardwaretest.core.AppLog
import vn.vedax.hardwaretest.core.FeatureCategory
import vn.vedax.hardwaretest.ui.features.CameraPage
import vn.vedax.hardwaretest.ui.features.NfcPage
import vn.vedax.hardwaretest.ui.features.PrintPage
import vn.vedax.hardwaretest.ui.features.UsbPage

/** Activity owns navigation/lifecycle. Pages own presentation; device classes own hardware I/O. */
class FeatureActivity : Activity() {
    companion object { const val EXTRA_CATEGORY = "category" }

    private val ui by lazy { UiKit(this) }
    private lateinit var page: FeaturePage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val category = FeatureCategory.fromId(intent.getStringExtra(EXTRA_CATEGORY))
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(24), ui.dp(22), ui.dp(24), ui.dp(30))
            background = ui.gradient(0xfff7fbff.toInt(), 0xffc8d9ec.toInt(), 0)
        }
        scroll.addView(root)
        root.addView(ui.label("IVISTA  /  TECH LAB", 12, true, 0xff557399.toInt()).apply {
            letterSpacing = 0.16f
        })
        root.addView(ui.label("‹  Tất cả chức năng", 14, true, 0xff305b99.toInt()).apply {
            setOnClickListener { finish() }
        })
        root.addView(ui.label(category.title, 30, true, 0xff112d53.toInt()))
        root.addView(ui.label(if (category == FeatureCategory.NFC)
            "Nhấn Bắt đầu quét để kích hoạt đầu đọc NFC" else "Chức năng bắt đầu khi bạn mở mục này",
            14, color = 0xff617b9a.toInt()))

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(22), ui.dp(18), ui.dp(22), ui.dp(18))
            background = ui.gradient(0xffedf5ff.toInt(), 0xffb8cde8.toInt(), ui.dp(28))
            elevation = ui.dp(6).toFloat()
        }
        root.addView(hero, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(18) })
        hero.addView(ui.label("ANDROID ${Build.VERSION.RELEASE}  •  ${Build.MODEL}", 12, true,
            0xff41668f.toInt()).apply { letterSpacing = 0.1f })
        hero.addView(ui.label(category.title, 22, true, 0xff193a65.toInt()))
        hero.addView(ui.label("${Build.MODEL}  •  Trạng thái hiển thị ngay bên dưới", 13,
            color = 0xff45698f.toInt()))

        val sections = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(sections, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(2) })
        page = when (category) {
            FeatureCategory.NFC -> NfcPage(this, ui)
            FeatureCategory.FACE -> CameraPage(this, ui, true)
            FeatureCategory.CAMERA -> CameraPage(this, ui, false)
            FeatureCategory.PRINT -> PrintPage(this, ui)
            FeatureCategory.USB -> UsbPage(this, ui)
        }
        page.show(sections)
        setContentView(scroll)
        Log.i(AppLog.TAG, "Category started: ${category.id}; Android=${Build.VERSION.RELEASE}; model=${Build.MODEL}")
    }

    override fun onResume() {
        super.onResume()
        if (::page.isInitialized) page.onResume()
    }

    override fun onPause() {
        if (::page.isInitialized) page.onPause()
        super.onPause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (::page.isInitialized) page.onPermissionResult(requestCode, grantResults)
    }
}
