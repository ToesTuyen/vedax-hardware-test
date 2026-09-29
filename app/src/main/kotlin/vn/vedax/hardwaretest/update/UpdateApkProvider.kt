package vn.vedax.hardwaretest.update

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/** Grants the package installer read-only access to one verified cache file. */
class UpdateApkProvider : ContentProvider() {
    companion object {
        val APK_URI: Uri = Uri.parse("content://vn.vedax.hardwaretest.updates/apk")
        private const val APK_MIME = "application/vnd.android.package-archive"

        fun apkFile(context: Context): File = File(File(context.cacheDir, "updates"), "update.apk")
    }

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String {
        requireApkUri(uri)
        return APK_MIME
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        requireApkUri(uri)
        if (mode != "r") throw SecurityException("Update APK is read-only")
        val file = apkFile(requireNotNull(context))
        if (!file.isFile) throw FileNotFoundException("Update APK is unavailable")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                       selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        requireApkUri(uri)
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val file = apkFile(requireNotNull(context))
        return MatrixCursor(columns, 1).apply {
            addRow(columns.map { column ->
                when (column) {
                    OpenableColumns.DISPLAY_NAME -> "vedax-update.apk"
                    OpenableColumns.SIZE -> file.length()
                    else -> null
                }
            }.toTypedArray<Any?>())
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("Read-only provider")

    override fun update(uri: Uri, values: ContentValues?, selection: String?,
                        selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("Read-only provider")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("Read-only provider")

    private fun requireApkUri(uri: Uri) {
        require(uri == APK_URI) { "Unknown update URI" }
    }
}
