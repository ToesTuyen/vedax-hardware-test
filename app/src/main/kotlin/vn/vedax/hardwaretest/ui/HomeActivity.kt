package vn.vedax.hardwaretest.ui

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import vn.vedax.hardwaretest.core.FeatureCategory
import vn.vedax.hardwaretest.update.UpdateCoordinator

class HomeActivity : Activity() {
    private val ui by lazy { UiKit(this) }
    private lateinit var updates: UpdateCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updates = UpdateCoordinator(this)
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(24), ui.dp(28), ui.dp(24), ui.dp(34))
            background = ui.gradient(0xfff7fbff.toInt(), 0xffc8d9ec.toInt(), 0)
        }
        scroll.addView(root)
        root.addView(ui.label("IVISTA  /  TECH LAB", 12, true, 0xff557399.toInt()).apply {
            letterSpacing = 0.16f
        })
        root.addView(ui.label("Chạm vào một mục để bắt đầu thử trên thiết bị.", 15,
            color = 0xff617b9a.toInt()))

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(22), ui.dp(20), ui.dp(22), ui.dp(20))
            background = ui.gradient(0xffedf5ff.toInt(), 0xffb8cde8.toInt(), ui.dp(28))
            elevation = ui.dp(6).toFloat()
        }
        root.addView(hero, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = ui.dp(22)
            bottomMargin = ui.dp(8)
        })
        hero.addView(ui.label("ANDROID ${Build.VERSION.RELEASE}  •  ${Build.MODEL}", 12, true,
            0xff41668f.toInt()).apply { letterSpacing = 0.1f })
        hero.addView(ui.label("Sẵn sàng kiểm tra", 23, true, 0xff193a65.toInt()))
        hero.addView(ui.label("CCCD  ·  FaceID  ·  Camera  ·  In phiếu", 14,
            color = 0xff45698f.toInt()))

        ui.row(root).also { row ->
            tile(row, FeatureCategory.NFC, true)
            tile(row, FeatureCategory.FACE, true)
        }
        ui.row(root).also { row ->
            tile(row, FeatureCategory.CAMERA, true)
            tile(row, FeatureCategory.PRINT, true)
        }
        tile(root, FeatureCategory.USB, false)
        root.addView(ui.label("Kiểm tra cập nhật ứng dụng  ↗", 13, true,
            0xff305b99.toInt()).apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(18) }
            setOnClickListener { updates.checkManually() }
        })
        setContentView(scroll)
        scroll.post { updates.checkOnLaunch() }
    }

    override fun onResume() {
        super.onResume()
        if (::updates.isInitialized) updates.onHostResume()
    }

    private fun tile(parent: LinearLayout, category: FeatureCategory, equalWidth: Boolean) {
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14))
            minimumHeight = ui.dp(145)
            background = ui.gradient(0xfaffffff.toInt(), 0xe7ecf5ff.toInt(), ui.dp(26))
            elevation = ui.dp(4).toFloat()
            isClickable = true
            isFocusable = true
            contentDescription = "${category.title}. ${category.subtitle}"
        }
        parent.addView(tile, (if (equalWidth) LinearLayout.LayoutParams(0, -2, 1f)
            else LinearLayout.LayoutParams(-1, -2)).apply {
            topMargin = ui.dp(16)
            if (equalWidth) {
                leftMargin = ui.dp(6)
                rightMargin = ui.dp(6)
            }
        })
        tile.addView(ImageView(this).apply {
            setImageResource(category.icon)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(ui.dp(9), ui.dp(9), ui.dp(9), ui.dp(9))
            background = ui.gradient(0xfff8fbff.toInt(), 0xffdce9f9.toInt(), ui.dp(15))
        }, LinearLayout.LayoutParams(ui.dp(42), ui.dp(42)))
        tile.addView(ui.label(category.title, 19, true, 0xff193a65.toInt()))
        tile.addView(ui.label(category.subtitle, 12, color = 0xff6b819d.toInt()))
        tile.setOnClickListener {
            startActivity(Intent(this, FeatureActivity::class.java).putExtra(FeatureActivity.EXTRA_CATEGORY, category.id))
        }
    }
}
