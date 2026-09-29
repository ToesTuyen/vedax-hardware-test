package vn.vedax.hardwaretest.device

import android.content.Context
import android.hardware.usb.UsbManager
import android.util.Log
import vn.vedax.hardwaretest.core.AppLog
import java.util.Locale

class UsbScanner(private val context: Context) {
    fun scan(): String {
        val devices = (context.getSystemService(Context.USB_SERVICE) as UsbManager).deviceList.values
        if (devices.isEmpty()) {
            Log.w(AppLog.TAG, "USB host scan: no devices")
            return "Android USB host chưa thấy thiết bị nào."
        }
        return buildString {
            append("Đã thấy ${devices.size} thiết bị:\n")
            for (device in devices) {
                val vidPid = String.format(Locale.ROOT, "%04X:%04X", device.vendorId, device.productId)
                Log.i(AppLog.TAG, "USB device: VID:PID=$vidPid; product=${device.productName}; interfaces=${device.interfaceCount}")
                append("\n${device.productName ?: "Thiết bị USB"}  VID:PID $vidPid")
                for (index in 0 until device.interfaceCount) {
                    val deviceClass = device.getInterface(index).interfaceClass
                    append("\n  Interface $index: class $deviceClass")
                    if (deviceClass == 7) append(" (USB printer)")
                    if (deviceClass == 14) append(" (USB camera)")
                }
            }
        }
    }
}
