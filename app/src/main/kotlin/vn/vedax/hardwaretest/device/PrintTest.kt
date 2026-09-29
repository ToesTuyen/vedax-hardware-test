package vn.vedax.hardwaretest.device

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import android.util.Log
import vn.vedax.hardwaretest.core.AppLog
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Keeps the v0.3.3 Android PrintManager path and test-page content unchanged. */
class PrintTest(private val activity: Activity) {
    fun print(onStatus: (String) -> Unit) {
        val manager = activity.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (manager == null) {
            onStatus("Android không có dịch vụ in.")
            Log.e(AppLog.TAG, "PrintManager unavailable")
            return
        }
        try {
            manager.print("VedaX hardware test", TestPrintAdapter(), null)
            onStatus("Đã mở hộp thoại in. Chọn máy in và xác nhận để kiểm tra giấy in.")
            Log.i(AppLog.TAG, "Android print dialog opened")
        } catch (error: Exception) {
            onStatus("Không mở được hộp thoại in: ${error.message}")
            Log.e(AppLog.TAG, "Android print dialog failed", error)
        }
    }

    private inner class TestPrintAdapter : PrintDocumentAdapter() {
        private lateinit var attributes: PrintAttributes

        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes,
            cancellationSignal: CancellationSignal,
            callback: LayoutResultCallback,
            extras: Bundle?,
        ) {
            attributes = newAttributes
            if (cancellationSignal.isCanceled) {
                callback.onLayoutCancelled()
                return
            }
            val info = PrintDocumentInfo.Builder("vedax-test.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(1)
                .build()
            callback.onLayoutFinished(info, newAttributes != oldAttributes)
        }

        override fun onWrite(
            pages: Array<out PageRange>,
            destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal,
            callback: WriteResultCallback,
        ) {
            if (cancellationSignal.isCanceled) {
                callback.onWriteCancelled()
                return
            }
            val document = PrintedPdfDocument(activity, attributes)
            try {
                val page = document.startPage(0)
                val canvas = page.canvas
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    textSize = 18f
                    typeface = Typeface.DEFAULT_BOLD
                }
                canvas.drawText("VEDAX HARDWARE TEST", 40f, 60f, paint)
                paint.typeface = Typeface.DEFAULT
                paint.textSize = 13f
                canvas.drawText("Android print service", 40f, 90f, paint)
                canvas.drawText(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date()),
                    40f, 115f, paint)
                canvas.drawText("Neu thay dong nay tren giay: in thanh cong.", 40f, 140f, paint)
                document.finishPage(page)
                FileOutputStream(destination.fileDescriptor).use(document::writeTo)
                Log.i(AppLog.TAG, "Android print test page generated; awaiting print-service result")
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (error: Exception) {
                Log.e(AppLog.TAG, "Android print page generation failed", error)
                callback.onWriteFailed(error.message)
            } finally {
                document.close()
            }
        }
    }
}
