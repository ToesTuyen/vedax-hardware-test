package vn.vedax.hardwaretest.update

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import vn.vedax.hardwaretest.BuildConfig
import vn.vedax.hardwaretest.core.AppLog
import vn.vedax.hardwaretest.core.SemanticVersion
import java.security.KeyStore
import java.util.concurrent.atomic.AtomicBoolean

/** Presentation coordinator for public GitHub Releases. Network and APK validation are separate. */
class UpdateCoordinator(private val activity: Activity) {
    private val releases = GitHubReleaseClient()
    private val verifier = ApkVerifier(activity)
    private val checking = AtomicBoolean(false)
    private var waitingForInstallPermission = false

    init { clearLegacyPrivateRepoToken() }

    fun checkOnLaunch() = checkForUpdate(false)
    fun checkManually() = checkForUpdate(true)

    fun onHostResume() {
        if (!waitingForInstallPermission) return
        waitingForInstallPermission = false
        if (activity.packageManager.canRequestPackageInstalls()) launchInstaller()
        else toast("Chưa được phép cài đặt bản cập nhật")
    }

    private fun checkForUpdate(manual: Boolean) {
        if (!checking.compareAndSet(false, true)) return
        if (manual) toast("Đang kiểm tra GitHub Releases…")
        Thread({
            try {
                val release = releases.latest()
                val newer = SemanticVersion.compare(release.tag, BuildConfig.VERSION_NAME) > 0
                onUi {
                    if (newer) showUpgradePrompt(release)
                    else if (manual) showMessage("Đã là bản mới nhất", "Phiên bản hiện tại: ${BuildConfig.VERSION_NAME}")
                }
                Log.i(AppLog.TAG, "Release check succeeded; current=${BuildConfig.VERSION_NAME}; latest=${release.tag}")
            } catch (error: GitHubReleaseClient.ApiException) {
                if (manual || error.status != 404) onUi {
                    showCheckFailure(if (error.status == 404) "Chưa có GitHub Release nào được xuất bản."
                        else "GitHub trả về lỗi HTTP ${error.status}.")
                }
                Log.w(AppLog.TAG, "Release check failed; HTTP ${error.status}")
            } catch (error: Exception) {
                onUi { showCheckFailure("Không kiểm tra được bản cập nhật: ${error.message}") }
                Log.w(AppLog.TAG, "Release check failed: ${error.javaClass.simpleName}")
            } finally {
                checking.set(false)
            }
        }, "ReleaseCheck").start()
    }

    private fun showUpgradePrompt(release: GitHubReleaseClient.Release) {
        val version = release.tag.removePrefix("v")
        AlertDialog.Builder(activity)
            .setTitle("Có phiên bản mới $version")
            .setMessage("Đang dùng ${BuildConfig.VERSION_NAME}. Tải APK từ GitHub Releases " +
                "(${maxOf(1L, release.assetSize / 1024)} KB) và cài đặt? Android sẽ yêu cầu bạn xác nhận.")
            .setPositiveButton("Nâng cấp") { _, _ -> downloadAndInstall(release) }
            .setNegativeButton("Để sau", null)
            .show()
    }

    private fun downloadAndInstall(release: GitHubReleaseClient.Release) {
        val spinner = ProgressBar(activity)
        val wrapper = LinearLayout(activity).apply {
            gravity = Gravity.CENTER
            val padding = (20 * resources.displayMetrics.density + 0.5f).toInt()
            setPadding(0, padding, 0, padding)
            addView(spinner)
        }
        val busy = AlertDialog.Builder(activity)
            .setTitle("Đang tải bản cập nhật")
            .setMessage("Đang xác minh APK từ GitHub…")
            .setView(wrapper)
            .setCancelable(false)
            .create()
        busy.show()
        Thread({
            try {
                val apk = releases.download(activity, release)
                verifier.verify(apk, release)
                Log.i(AppLog.TAG, "Update APK downloaded and verified; version=${release.tag}")
                onUi {
                    busy.dismiss()
                    requestInstall()
                }
            } catch (error: Exception) {
                Log.w(AppLog.TAG, "Update download/verification failed: ${error.javaClass.simpleName}")
                onUi {
                    busy.dismiss()
                    showMessage("Cập nhật thất bại", "Không tải/cài được bản cập nhật: ${error.message}")
                }
            }
        }, "ReleaseDownload").start()
    }

    private fun requestInstall() {
        if (!activity.packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(activity)
                .setTitle("Cho phép cài đặt bản cập nhật")
                .setMessage("Android yêu cầu cho phép ứng dụng này cài APK. Bật quyền rồi quay lại để tiếp tục.")
                .setPositiveButton("Mở cài đặt") { _, _ ->
                    waitingForInstallPermission = true
                    activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${activity.packageName}")))
                }
                .setNegativeButton("Để sau", null)
                .show()
            return
        }
        launchInstaller()
    }

    private fun launchInstaller() {
        try {
            val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                setDataAndType(UpdateApkProvider.APK_URI, "application/vnd.android.package-archive")
                clipData = ClipData.newRawUri("VedaX update", UpdateApkProvider.APK_URI)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivity(intent)
            Log.i(AppLog.TAG, "Android package installer opened for update")
        } catch (error: Exception) {
            showMessage("Cập nhật thất bại", "Không mở được trình cài đặt Android: ${error.message}")
            Log.e(AppLog.TAG, "Android package installer failed", error)
        }
    }

    private fun showCheckFailure(message: String) {
        showMessage("Không kiểm tra được cập nhật", message)
    }

    private fun showMessage(title: String, message: String) {
        AlertDialog.Builder(activity).setTitle(title).setMessage(message)
            .setPositiveButton("OK", null).show()
    }

    private fun toast(message: String) { Toast.makeText(activity, message, Toast.LENGTH_SHORT).show() }

    private fun onUi(action: () -> Unit) {
        activity.runOnUiThread {
            if (!activity.isFinishing && !activity.isDestroyed) action()
        }
    }

    private fun clearLegacyPrivateRepoToken() {
        activity.getSharedPreferences("private_release_updates", Activity.MODE_PRIVATE).edit().clear().apply()
        try {
            val store = KeyStore.getInstance("AndroidKeyStore")
            store.load(null)
            if (store.containsAlias("vedax_private_release_token")) store.deleteEntry("vedax_private_release_token")
        } catch (_: Exception) {
            Log.w(AppLog.TAG, "Could not remove legacy private update key")
        }
    }
}
