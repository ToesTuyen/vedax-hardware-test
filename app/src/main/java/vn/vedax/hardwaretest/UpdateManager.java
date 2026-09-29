package vn.vedax.hardwaretest;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.File;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/** Checks the public GitHub Release on launch; download/install needs user consent. */
final class UpdateManager {
    private static final String LOG_TAG = "IVISTA_TECH";
    private static final String APK_MIME = "application/vnd.android.package-archive";
    private final Activity activity;
    private final GitHubReleaseClient releases = new GitHubReleaseClient();
    private final AtomicBoolean checking = new AtomicBoolean(false);
    private boolean waitingForInstallPermission;

    UpdateManager(Activity activity) {
        this.activity = activity;
        clearLegacyPrivateRepoToken();
    }

    private void clearLegacyPrivateRepoToken() {
        activity.getSharedPreferences("private_release_updates", Activity.MODE_PRIVATE)
                .edit().clear().apply();
        try {
            KeyStore store = KeyStore.getInstance("AndroidKeyStore");
            store.load(null);
            if (store.containsAlias("vedax_private_release_token")) {
                store.deleteEntry("vedax_private_release_token");
            }
        } catch (Exception e) {
            Log.w(LOG_TAG, "Could not remove legacy private update key");
        }
    }

    void checkOnLaunch() {
        checkForUpdate(false);
    }

    void checkManually() {
        checkForUpdate(true);
    }

    void onHostResume() {
        if (!waitingForInstallPermission) return;
        waitingForInstallPermission = false;
        if (activity.getPackageManager().canRequestPackageInstalls()) {
            launchInstaller();
        } else {
            toast("Chưa được phép cài đặt bản cập nhật");
        }
    }

    private void checkForUpdate(boolean manual) {
        if (!checking.compareAndSet(false, true)) return;
        if (manual) toast("Đang kiểm tra GitHub Releases…");
        new Thread(() -> {
            try {
                GitHubReleaseClient.Release release = releases.latest();
                int comparison = GitHubReleaseClient.compareVersions(release.tag,
                        BuildConfig.VERSION_NAME);
                onUi(() -> {
                    if (comparison > 0) showUpgradePrompt(release);
                    else if (manual) showMessage("Đã là bản mới nhất",
                            "Phiên bản hiện tại: " + BuildConfig.VERSION_NAME);
                });
                Log.i(LOG_TAG, "Release check succeeded; current=" + BuildConfig.VERSION_NAME +
                        "; latest=" + release.tag);
            } catch (GitHubReleaseClient.ApiException e) {
                if (manual || e.status != 404) {
                    onUi(() -> showCheckFailure(e.status == 404 ?
                            "Chưa có GitHub Release nào được xuất bản." :
                            "GitHub trả về lỗi HTTP " + e.status + "."));
                }
                Log.w(LOG_TAG, "Release check failed; HTTP " + e.status);
            } catch (Exception e) {
                onUi(() -> showCheckFailure("Không kiểm tra được bản cập nhật: " + e.getMessage()));
                Log.w(LOG_TAG, "Release check failed: " + e.getClass().getSimpleName());
            } finally {
                checking.set(false);
            }
        }, "ReleaseCheck").start();
    }

    private void showUpgradePrompt(GitHubReleaseClient.Release release) {
        String version = release.tag.startsWith("v") ? release.tag.substring(1) : release.tag;
        new AlertDialog.Builder(activity)
                .setTitle("Có phiên bản mới " + version)
                .setMessage("Đang dùng " + BuildConfig.VERSION_NAME + ". Tải APK từ GitHub Releases " +
                        "(" + Math.max(1, release.assetSize / 1024) + " KB) và cài đặt? " +
                        "Android sẽ yêu cầu bạn xác nhận.")
                .setPositiveButton("Nâng cấp", (dialog, which) -> downloadAndInstall(release))
                .setNegativeButton("Để sau", null)
                .show();
    }

    private void downloadAndInstall(GitHubReleaseClient.Release release) {
        ProgressBar spinner = new ProgressBar(activity);
        LinearLayout wrapper = new LinearLayout(activity);
        wrapper.setGravity(Gravity.CENTER);
        wrapper.setPadding(0, dp(20), 0, dp(20));
        wrapper.addView(spinner);
        AlertDialog busy = new AlertDialog.Builder(activity)
                .setTitle("Đang tải bản cập nhật")
                .setMessage("Đang xác minh APK từ GitHub…")
                .setView(wrapper)
                .setCancelable(false)
                .create();
        busy.show();
        new Thread(() -> {
            try {
                File apk = releases.download(activity, release);
                verifyApk(apk, release);
                Log.i(LOG_TAG, "Update APK downloaded and verified; version=" + release.tag);
                onUi(() -> {
                    busy.dismiss();
                    requestInstall();
                });
            } catch (Exception e) {
                Log.w(LOG_TAG, "Update download/verification failed: " + e.getClass().getSimpleName());
                onUi(() -> {
                    busy.dismiss();
                    showError("Không tải/cài được bản cập nhật: " + e.getMessage());
                });
            }
        }, "ReleaseDownload").start();
    }

    private void verifyApk(File apk, GitHubReleaseClient.Release release) throws Exception {
        PackageManager manager = activity.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES :
                PackageManager.GET_SIGNATURES;
        PackageInfo next = manager.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
        PackageInfo current = manager.getPackageInfo(activity.getPackageName(), flags);
        if (next == null || !activity.getPackageName().equals(next.packageName)) {
            throw new SecurityException("APK không đúng ứng dụng VedaX Hardware Test");
        }
        long nextCode = Build.VERSION.SDK_INT >= 28 ? next.getLongVersionCode() : next.versionCode;
        if (nextCode <= BuildConfig.VERSION_CODE) {
            throw new SecurityException("APK không có versionCode mới hơn");
        }
        String tagVersion = release.tag.startsWith("v") ? release.tag.substring(1) : release.tag;
        if (!tagVersion.equals(next.versionName)) {
            throw new SecurityException("Phiên bản APK không khớp GitHub Release");
        }
        if (Build.VERSION.SDK_INT >= 28 && (current.signingInfo == null || next.signingInfo == null)) {
            throw new SecurityException("Không đọc được chữ ký APK");
        }
        Signature[] oldSigners = Build.VERSION.SDK_INT >= 28 ?
                current.signingInfo.getApkContentsSigners() : current.signatures;
        Signature[] newSigners = Build.VERSION.SDK_INT >= 28 ?
                next.signingInfo.getApkContentsSigners() : next.signatures;
        if (oldSigners == null || newSigners == null || !Arrays.equals(oldSigners, newSigners)) {
            throw new SecurityException("Chữ ký APK khác bản đang cài");
        }
    }

    private void requestInstall() {
        if (!activity.getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(activity)
                    .setTitle("Cho phép cài đặt bản cập nhật")
                    .setMessage("Android yêu cầu cho phép ứng dụng này cài APK. " +
                            "Bật quyền rồi quay lại để tiếp tục.")
                    .setPositiveButton("Mở cài đặt", (dialog, which) -> {
                        waitingForInstallPermission = true;
                        Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + activity.getPackageName()));
                        activity.startActivity(settings);
                    })
                    .setNegativeButton("Để sau", null)
                    .show();
            return;
        }
        launchInstaller();
    }

    private void launchInstaller() {
        try {
            Intent install = new Intent(Intent.ACTION_INSTALL_PACKAGE);
            install.setDataAndType(UpdateApkProvider.APK_URI, APK_MIME);
            install.setClipData(ClipData.newRawUri("VedaX update", UpdateApkProvider.APK_URI));
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(install);
            Log.i(LOG_TAG, "Android package installer opened for update");
        } catch (Exception e) {
            showError("Không mở được trình cài đặt Android: " + e.getMessage());
            Log.e(LOG_TAG, "Android package installer failed", e);
        }
    }

    private void showCheckFailure(String message) {
        new AlertDialog.Builder(activity)
                .setTitle("Không kiểm tra được cập nhật")
                .setMessage(message)
                .setPositiveButton("Đóng", null)
                .show();
    }

    private void showError(String message) {
        showMessage("Cập nhật thất bại", message);
    }

    private void showMessage(String title, String message) {
        new AlertDialog.Builder(activity).setTitle(title).setMessage(message)
                .setPositiveButton("OK", null).show();
    }

    private void toast(String message) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
    }

    private void onUi(Runnable action) {
        activity.runOnUiThread(() -> {
            if (!activity.isFinishing() && !activity.isDestroyed()) action.run();
        });
    }

    private int dp(int value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density + 0.5f);
    }
}
