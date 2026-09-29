package vn.vedax.hardwaretest.update

import android.content.Context
import org.json.JSONObject
import vn.vedax.hardwaretest.BuildConfig
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Locale

/** Network + SHA-256 layer. It does not know about dialogs or the Android installer. */
class GitHubReleaseClient {
    data class Release(val tag: String, val assetId: Long, val assetSize: Long, val sha256: String)
    class ApiException(val status: Int) : IOException("GitHub HTTP $status")

    companion object {
        private const val API_ROOT = "https://api.github.com/repos/ToesTuyen/vedax-hardware-test/releases"
        private const val MAX_APK_BYTES = 150L * 1024L * 1024L
        private val SHA256 = Regex("^sha256:([0-9a-fA-F]{64})$")

        private fun open(address: String, accept: String): HttpURLConnection {
            var url = URL(address)
            repeat(6) {
                val host = url.host.lowercase(Locale.ROOT)
                val githubHost = host == "api.github.com" || host == "github.com" ||
                    host.endsWith(".githubusercontent.com")
                if (url.protocol != "https" || !githubHost) {
                    throw IOException("GitHub chuyển hướng tới địa chỉ không tin cậy")
                }
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 15000
                    readTimeout = 30000
                    setRequestProperty("Accept", accept)
                    setRequestProperty("User-Agent", "VedaX-Hardware-Test/${BuildConfig.VERSION_NAME}")
                    if (host == "api.github.com") setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                }
                val status = connection.responseCode
                if (status == HttpURLConnection.HTTP_OK) return connection
                if (status in listOf(301, 302, 303, 307, 308)) {
                    val location = connection.getHeaderField("Location")
                    connection.disconnect()
                    if (location == null) throw IOException("GitHub chuyển hướng không có địa chỉ")
                    url = URL(url, location)
                } else {
                    connection.disconnect()
                    throw ApiException(status)
                }
            }
            throw IOException("GitHub chuyển hướng quá nhiều lần")
        }

        private fun readLimited(connection: HttpURLConnection, limit: Int): ByteArray {
            connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > limit) throw IOException("Phản hồi GitHub quá lớn")
                    output.write(buffer, 0, count)
                }
                return output.toByteArray()
            }
        }
    }

    fun latest(): Release {
        val connection = open("$API_ROOT/latest", "application/vnd.github+json")
        try {
            val json = JSONObject(String(readLimited(connection, 1024 * 1024), Charsets.UTF_8))
            val assets = json.getJSONArray("assets")
            val apks = (0 until assets.length()).map { assets.getJSONObject(it) }
                .filter { it.optString("name").lowercase(Locale.ROOT).endsWith(".apk") }
            if (apks.isEmpty()) throw IOException("Release chưa có tệp APK")
            if (apks.size != 1) throw IOException("Release có nhiều APK; không biết chọn bản nào")
            val apk = apks.single()
            val digest = SHA256.matchEntire(apk.optString("digest"))
                ?: throw IOException("Release thiếu SHA-256 hợp lệ cho APK")
            val size = apk.getLong("size")
            if (size <= 0 || size > MAX_APK_BYTES) throw IOException("Kích thước APK không hợp lệ")
            return Release(json.getString("tag_name"), apk.getLong("id"), size,
                digest.groupValues[1].lowercase(Locale.ROOT))
        } finally {
            connection.disconnect()
        }
    }

    fun download(context: Context, release: Release): File {
        val target = UpdateApkProvider.apkFile(context)
        val folder = target.parentFile ?: throw IOException("Không có thư mục cập nhật")
        if (!folder.isDirectory && !folder.mkdirs()) throw IOException("Không tạo được thư mục cập nhật")
        val partial = File(folder, "update.apk.part")
        var connection: HttpURLConnection? = null
        try {
            connection = open("$API_ROOT/assets/${release.assetId}", "application/octet-stream")
            val sha = MessageDigest.getInstance("SHA-256")
            var count = 0L
            connection.inputStream.use { input ->
                FileOutputStream(partial).use { output ->
                    val buffer = ByteArray(16384)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        count += read
                        if (count > MAX_APK_BYTES || count > release.assetSize) {
                            throw IOException("APK vượt quá kích thước công bố")
                        }
                        sha.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                    }
                    output.fd.sync()
                }
            }
            val digest = sha.digest().joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 0xff) }
            if (count != release.assetSize || digest != release.sha256) {
                throw IOException("APK tải về không khớp SHA-256 của Release")
            }
            Files.move(partial.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            return target
        } finally {
            connection?.disconnect()
            if (partial.exists()) partial.delete()
        }
    }
}
