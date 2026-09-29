package vn.vedax.hardwaretest.device

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.SystemClock
import android.util.Log
import vn.vedax.hardwaretest.core.AppLog
import java.io.IOException
import java.util.Locale

/** Android NFC reader only. A vendor reader needs its own implementation. */
class NfcReader(
    private val activity: Activity,
    private val onStatus: (String) -> Unit,
    private val onResult: (String, String) -> Unit,
) {
    private val adapter = NfcAdapter.getDefaultAdapter(activity)
    @Volatile private var enabled = false
    @Volatile private var lastTagAt = 0L

    fun availability(): String {
        val nfc = adapter ?: run {
            Log.w(AppLog.TAG, "Android NFC adapter unavailable")
            return "Android không cung cấp NFC cho app. Kiểm tra phần cứng/firmware hoặc SDK của đầu đọc CCCD."
        }
        Log.i(AppLog.TAG, "Android NFC adapter available; enabled=${nfc.isEnabled}")
        return if (nfc.isEnabled) "NFC đã sẵn sàng. Nhấn Bắt đầu quét rồi đưa CCCD vào vùng đọc."
        else "NFC đang tắt. Bật NFC trong Cài đặt rồi nhấn Bắt đầu quét."
    }

    fun start(): Boolean {
        val nfc = adapter
        if (nfc == null || !nfc.isEnabled) {
            onStatus(availability())
            return false
        }
        Log.i(AppLog.TAG, "NFC scan requested by user")
        return try {
            if (enabled) nfc.disableReaderMode(activity)
            enabled = false
            val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
            nfc.enableReaderMode(activity, ::readTag, flags, null)
            enabled = true
            lastTagAt = 0L
            onStatus("● ĐANG QUÉT\nĐưa CCCD sát vùng NFC ở lưng máy và giữ yên vài giây.")
            Log.i(AppLog.TAG, "NFC reader mode started; NFC-A/B + ISO-DEP")
            true
        } catch (error: Exception) {
            enabled = false
            onStatus("Không bật được chế độ đọc NFC: ${error.message}")
            Log.e(AppLog.TAG, "NFC reader mode failed", error)
            false
        }
    }

    fun stop() {
        if (!enabled) return
        enabled = false
        try { adapter?.disableReaderMode(activity) } catch (_: Exception) { }
        Log.i(AppLog.TAG, "NFC reader mode stopped")
    }

    private fun readTag(tag: Tag) {
        if (!enabled) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastTagAt < 2500) return
        lastTagAt = now
        Log.i(AppLog.TAG, "NFC tag discovered; technologies=${tag.techList.contentToString()}")
        dispatch { onStatus("● READING…\nĐã nhận thẻ. Đang kết nối chip; giữ thẻ yên.") }
        val id = tag.id
        val uid = if (id == null || id.isEmpty()) "(không có)" else
            id.joinToString("") { String.format(Locale.ROOT, "%02X", it.toInt() and 0xff) }
        val technologies = tag.techList.contentToString().replace("android.nfc.tech.", "")
        val isoDep = IsoDep.get(tag)
        if (isoDep == null) {
            dispatch { onResult("ĐÃ NHẬN THẺ NFC", "UID (không phải số CCCD): $uid\n" +
                "Công nghệ: $technologies\nKết nối ISO-DEP: không hỗ trợ\nSố CCCD / họ tên: chưa đọc được") }
            Log.w(AppLog.TAG, "NFC tag has no ISO-DEP interface")
            return
        }
        try {
            isoDep.connect()
            dispatch { onResult("ĐÃ NHẬN THẺ ISO-DEP", "UID (không phải số CCCD): $uid\n" +
                "Công nghệ: $technologies\nKết nối chip: thành công\n" +
                "APDU tối đa: ${isoDep.maxTransceiveLength} byte\nSố CCCD / họ tên: chưa đọc được") }
            Log.i(AppLog.TAG, "NFC ISO-DEP chip connected; maxTransceive=${isoDep.maxTransceiveLength}")
        } catch (error: Exception) {
            dispatch { onResult("ĐÃ THẤY THẺ, CHƯA KẾT NỐI CHIP", "UID (không phải số CCCD): $uid\n" +
                "Công nghệ: $technologies\nLỗi: ${error.message}\nGiữ thẻ sát vùng NFC rồi quét lại.") }
            Log.e(AppLog.TAG, "NFC ISO-DEP connection failed", error)
        } finally {
            try { isoDep.close() } catch (_: IOException) { }
        }
    }

    private fun dispatch(action: () -> Unit) {
        activity.runOnUiThread {
            if (enabled && !activity.isFinishing && !activity.isDestroyed) action()
        }
    }
}
