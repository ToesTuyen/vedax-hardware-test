package vn.vedax.hardwaretest;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/** Shares only the downloaded update APK with Android's package installer. */
public final class UpdateApkProvider extends ContentProvider {
    public static final Uri APK_URI = Uri.parse("content://vn.vedax.hardwaretest.updates/apk");
    private static final String APK_MIME = "application/vnd.android.package-archive";

    public static File apkFile(Context context) {
        return new File(new File(context.getCacheDir(), "updates"), "update.apk");
    }

    private void requireApkUri(Uri uri) {
        if (!APK_URI.equals(uri)) throw new IllegalArgumentException("Unknown update URI");
    }

    @Override public boolean onCreate() { return true; }

    @Override public String getType(Uri uri) {
        requireApkUri(uri);
        return APK_MIME;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        requireApkUri(uri);
        if (!"r".equals(mode)) throw new SecurityException("Update APK is read-only");
        File file = apkFile(getContext());
        if (!file.isFile()) throw new FileNotFoundException("Update APK is unavailable");
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) {
        requireApkUri(uri);
        String[] columns = projection == null ?
                new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
        MatrixCursor result = new MatrixCursor(columns, 1);
        Object[] values = new Object[columns.length];
        File file = apkFile(getContext());
        for (int i = 0; i < columns.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) values[i] = "vedax-update.apk";
            else if (OpenableColumns.SIZE.equals(columns[i])) values[i] = file.length();
        }
        result.addRow(values);
        return result;
    }

    @Override public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Read-only provider");
    }

    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Read-only provider");
    }

    @Override public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Read-only provider");
    }
}
