package vn.vedax.hardwaretest.ui.features

import android.app.Activity
import android.widget.LinearLayout
import android.widget.TextView
import vn.vedax.hardwaretest.device.PrintTest
import vn.vedax.hardwaretest.ui.FeaturePage
import vn.vedax.hardwaretest.ui.UiKit

class PrintPage(activity: Activity, private val ui: UiKit) : FeaturePage {
    private val printer = PrintTest(activity)
    private lateinit var status: TextView

    override fun show(parent: LinearLayout) {
        val card = ui.card(parent, "In phiếu", "Kiểm tra đường in của Android")
        status = ui.status("Chưa gửi lệnh in")
        card.addView(status)
        ui.button(card, "In trang kiểm tra", ::print)
        card.addView(ui.footnote("Máy in tích hợp cần dịch vụ in hoặc giao thức do hãng cung cấp."))
        status.post(::print)
    }

    private fun print() { printer.print { status.text = it } }
}
