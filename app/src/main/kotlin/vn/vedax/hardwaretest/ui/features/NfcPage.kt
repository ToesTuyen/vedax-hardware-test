package vn.vedax.hardwaretest.ui.features

import android.app.Activity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import vn.vedax.hardwaretest.device.NfcReader
import vn.vedax.hardwaretest.ui.FeaturePage
import vn.vedax.hardwaretest.ui.UiKit

class NfcPage(private val activity: Activity, private val ui: UiKit) : FeaturePage {
    private lateinit var status: TextView
    private lateinit var resultTitle: TextView
    private lateinit var result: TextView
    private lateinit var startButton: Button
    private val reader by lazy {
        NfcReader(activity, { status.text = it }, { title, details ->
            status.text = title
            result.text = details
            resultTitle.visibility = View.VISIBLE
            result.visibility = View.VISIBLE
        })
    }

    override fun show(parent: LinearLayout) {
        val card = ui.card(parent, "Đọc thẻ căn cước", "Đặt thẻ lên vùng NFC và giữ yên")
        status = ui.status("Nhấn Bắt đầu quét để tìm thẻ.")
        card.addView(status)
        startButton = ui.button(card, "Bắt đầu quét") {
            resultTitle.visibility = View.GONE
            result.visibility = View.GONE
            startButton.text = if (reader.start()) "Quét lại thẻ" else "Bắt đầu quét"
        }
        resultTitle = ui.label("KẾT QUẢ QUÉT", 12, true, 0xff41668f.toInt()).apply {
            visibility = View.GONE
        }
        card.addView(resultTitle)
        result = ui.status("", highlight = true).apply { visibility = View.GONE }
        card.addView(result)
        card.addView(ui.footnote("UID không phải số CCCD. Đọc thông tin cá nhân trong chip cần giao thức và quyền truy cập phù hợp."))
        status.text = reader.availability()
    }

    override fun onResume() {
        status.text = reader.availability()
    }

    override fun onPause() {
        reader.stop()
        startButton.text = "Bắt đầu quét"
    }
}
