package vn.vedax.hardwaretest;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads a public GitHub Release and downloads its single APK asset. */
final class GitHubReleaseClient {
    private static final String API_ROOT =
            "https://api.github.com/repos/ToesTuyen/vedax-hardware-test/releases";
    private static final long MAX_APK_BYTES = 150L * 1024L * 1024L;
    private static final Pattern VERSION = Pattern.compile("^v?(\\d+)\\.(\\d+)(?:\\.(\\d+))?$");
    private static final Pattern SHA256 = Pattern.compile("^sha256:([0-9a-fA-F]{64})$");

    static final class Release {
        final String tag;
        final long assetId;
        final long assetSize;
        final String sha256;

        Release(String tag, long assetId, long assetSize, String sha256) {
            this.tag = tag;
            this.assetId = assetId;
            this.assetSize = assetSize;
            this.sha256 = sha256;
        }
    }

    static final class ApiException extends IOException {
        final int status;

        ApiException(int status) {
            super("GitHub HTTP " + status);
            this.status = status;
        }
    }

    Release latest() throws Exception {
        HttpURLConnection connection = open(API_ROOT + "/latest",
                "application/vnd.github+json");
        try (InputStream stream = connection.getInputStream()) {
            byte[] body = readLimited(stream, 1024 * 1024);
            JSONObject json = new JSONObject(new String(body, StandardCharsets.UTF_8));
            String tag = json.getString("tag_name");
            JSONArray assets = json.getJSONArray("assets");
            JSONObject apk = null;
            for (int i = 0; i < assets.length(); i++) {
                JSONObject candidate = assets.getJSONObject(i);
                if (candidate.optString("name").toLowerCase(Locale.ROOT).endsWith(".apk")) {
                    if (apk != null) throw new IOException("Release có nhiều APK; không biết chọn bản nào");
                    apk = candidate;
                }
            }
            if (apk == null) throw new IOException("Release chưa có tệp APK");
            Matcher digest = SHA256.matcher(apk.optString("digest"));
            if (!digest.matches()) throw new IOException("Release thiếu SHA-256 hợp lệ cho APK");
            long size = apk.getLong("size");
            if (size <= 0 || size > MAX_APK_BYTES) throw new IOException("Kích thước APK không hợp lệ");
            return new Release(tag, apk.getLong("id"), size,
                    digest.group(1).toLowerCase(Locale.ROOT));
        } finally {
            connection.disconnect();
        }
    }

    File download(Context context, Release release) throws Exception {
        File target = UpdateApkProvider.apkFile(context);
        File folder = target.getParentFile();
        if (!folder.isDirectory() && !folder.mkdirs()) {
            throw new IOException("Không tạo được thư mục cập nhật");
        }
        File partial = new File(folder, "update.apk.part");
        HttpURLConnection connection = null;
        try {
            connection = open(API_ROOT + "/assets/" + release.assetId,
                    "application/octet-stream");
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            long count = 0;
            try (InputStream input = connection.getInputStream();
                 FileOutputStream output = new FileOutputStream(partial)) {
                byte[] buffer = new byte[16384];
                int n;
                while ((n = input.read(buffer)) != -1) {
                    count += n;
                    if (count > MAX_APK_BYTES || count > release.assetSize) {
                        throw new IOException("APK vượt quá kích thước công bố");
                    }
                    sha.update(buffer, 0, n);
                    output.write(buffer, 0, n);
                }
                output.getFD().sync();
            }
            if (count != release.assetSize || !hex(sha.digest()).equals(release.sha256)) {
                throw new IOException("APK tải về không khớp SHA-256 của Release");
            }
            Files.move(partial.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return target;
        } finally {
            if (connection != null) connection.disconnect();
            if (partial.exists()) partial.delete();
        }
    }

    static int compareVersions(String candidate, String current) {
        long[] a = parseVersion(candidate);
        long[] b = parseVersion(current);
        for (int i = 0; i < 3; i++) {
            if (a[i] != b[i]) return Long.compare(a[i], b[i]);
        }
        return 0;
    }

    private static long[] parseVersion(String text) {
        Matcher match = VERSION.matcher(text);
        if (!match.matches()) throw new IllegalArgumentException("Tag phiên bản không đúng dạng v1.2.3: " + text);
        return new long[]{Long.parseLong(match.group(1)), Long.parseLong(match.group(2)),
                match.group(3) == null ? 0 : Long.parseLong(match.group(3))};
    }

    private static HttpURLConnection open(String address, String accept) throws IOException {
        URL url = new URL(address);
        for (int redirect = 0; redirect < 6; redirect++) {
            String host = url.getHost().toLowerCase(Locale.ROOT);
            boolean githubHost = host.equals("api.github.com") || host.equals("github.com") ||
                    host.endsWith(".githubusercontent.com");
            if (!"https".equals(url.getProtocol()) || !githubHost) {
                throw new IOException("GitHub chuyển hướng tới địa chỉ không tin cậy");
            }
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setRequestProperty("Accept", accept);
            connection.setRequestProperty("User-Agent", "VedaX-Hardware-Test/" + BuildConfig.VERSION_NAME);
            if (host.equals("api.github.com")) {
                connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
            }
            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_OK) return connection;
            if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null) throw new IOException("GitHub chuyển hướng không có địa chỉ");
                url = new URL(url, location);
                continue;
            }
            connection.disconnect();
            throw new ApiException(status);
        }
        throw new IOException("GitHub chuyển hướng quá nhiều lần");
    }

    private static byte[] readLimited(InputStream stream, int maximum) throws IOException {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n;
        while ((n = stream.read(buffer)) != -1) {
            if (output.size() + n > maximum) throw new IOException("Phản hồi GitHub quá lớn");
            output.write(buffer, 0, n);
        }
        return output.toByteArray();
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) result.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return result.toString();
    }
}
