package vn.vedax.hardwaretest.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import vn.vedax.hardwaretest.BuildConfig
import java.io.File
import java.util.Arrays

/** Ensures the downloaded release can replace the installed app before launching Android's installer. */
class ApkVerifier(private val context: Context) {
    fun verify(apk: File, release: GitHubReleaseClient.Release) {
        val manager = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
        else PackageManager.GET_SIGNATURES
        val next = manager.getPackageArchiveInfo(apk.absolutePath, flags)
        val current = manager.getPackageInfo(context.packageName, flags)
        if (next == null || next.packageName != context.packageName) {
            throw SecurityException("APK không đúng ứng dụng VedaX Hardware Test")
        }
        val nextCode = if (Build.VERSION.SDK_INT >= 28) next.longVersionCode else next.versionCode.toLong()
        if (nextCode <= BuildConfig.VERSION_CODE) throw SecurityException("APK không có versionCode mới hơn")
        val tagVersion = release.tag.removePrefix("v")
        if (tagVersion != next.versionName) throw SecurityException("Phiên bản APK không khớp GitHub Release")
        val oldSigners = if (Build.VERSION.SDK_INT >= 28) current.signingInfo?.apkContentsSigners
        else current.signatures
        val newSigners = if (Build.VERSION.SDK_INT >= 28) next.signingInfo?.apkContentsSigners
        else next.signatures
        if (oldSigners == null || newSigners == null || !Arrays.equals(oldSigners, newSigners)) {
            throw SecurityException("Chữ ký APK khác bản đang cài")
        }
    }
}
