package vn.vedax.hardwaretest.ui.features

import android.content.Context
import android.widget.LinearLayout
import android.widget.TextView
import vn.vedax.hardwaretest.device.UsbScanner
import vn.vedax.hardwaretest.ui.FeaturePage
import vn.vedax.hardwaretest.ui.UiKit

class UsbPage(context: Context, private val ui: UiKit) : FeaturePage {
    private val scanner = UsbScanner(context)
    private lateinit var status: TextView

    override fun show(parent: LinearLayout) {
        val card = ui.card(parent, "Thiết bị kết nối", "Camera · Đầu đọc · Máy in")
        status = ui.status("Đang kiểm tra USB…")
        card.addView(status)
        ui.button(card, "Quét lại thiết bị USB", ::refresh)
        refresh()
    }

    private fun refresh() { status.text = scanner.scan() }
}
