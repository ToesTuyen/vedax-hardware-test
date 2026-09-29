package vn.vedax.hardwaretest;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class CategoryActivity extends Activity {
    private UpdateManager updateManager;

    @Override protected void onResume() {
        super.onResume();
        if (updateManager != null) updateManager.onHostResume();
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(28), dp(24), dp(34));
        root.setBackground(gradient(0xfff7fbff, 0xffc8d9ec, 0));
        scroll.addView(root);

        TextView brand = label("IVISTA  /  TECH LAB", 12, true, 0xff557399);
        brand.setLetterSpacing(0.16f);
        root.addView(brand);
        root.addView(label("Chạm vào một mục để bắt đầu thử trên thiết bị.", 15, false, 0xff617b9a));

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(22), dp(20), dp(22), dp(20));
        hero.setBackground(gradient(0xffedf5ff, 0xffb8cde8, dp(28)));
        hero.setElevation(dp(6));
        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(-1, -2);
        heroParams.topMargin = dp(22);
        heroParams.bottomMargin = dp(8);
        root.addView(hero, heroParams);
        TextView kicker = label("ANDROID " + Build.VERSION.RELEASE + "  •  " + Build.MODEL, 12, true, 0xff41668f);
        kicker.setLetterSpacing(0.1f);
        hero.addView(kicker);
        hero.addView(label("Sẵn sàng kiểm tra", 23, true, 0xff193a65));
        hero.addView(label("CCCD  ·  FaceID  ·  Camera  ·  In phiếu", 14, false, 0xff45698f));

        LinearLayout first = row(root);
        category(first, "CCCD / NFC", "Đưa thẻ lên đầu đọc", MainActivity.CATEGORY_NFC, R.drawable.ic_nfc, true);
        category(first, "FaceID", "Tìm khuôn mặt", MainActivity.CATEGORY_FACE, R.drawable.ic_face, true);
        LinearLayout second = row(root);
        category(second, "Camera", "Xem hình trực tiếp", MainActivity.CATEGORY_CAMERA, R.drawable.ic_camera, true);
        category(second, "In phiếu", "Gửi trang in thử", MainActivity.CATEGORY_PRINT, R.drawable.ic_print, true);
        category(root, "Thiết bị kết nối", "Xem USB và mã thiết bị", MainActivity.CATEGORY_USB, R.drawable.ic_usb, false);
        TextView updates = label("Kiểm tra cập nhật ứng dụng  ↗", 13, true, 0xff305b99);
        LinearLayout.LayoutParams updatesParams = new LinearLayout.LayoutParams(-1, -2);
        updatesParams.topMargin = dp(18);
        root.addView(updates, updatesParams);
        updates.setOnClickListener(v -> updateManager.checkManually());
        setContentView(scroll);
        updateManager = new UpdateManager(this);
        scroll.post(() -> updateManager.checkOnLaunch());
    }

    private LinearLayout row(LinearLayout parent) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
        return row;
    }

    private void category(LinearLayout parent, String title, String subtitle,
                          String category, int iconResource, boolean equalWidth) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(dp(16), dp(14), dp(16), dp(14));
        tile.setMinimumHeight(dp(145));
        tile.setBackground(gradient(0xfaffffff, 0xe7ecf5ff, dp(26)));
        tile.setElevation(dp(4));
        tile.setClickable(true);
        tile.setFocusable(true);
        tile.setContentDescription(title + ". " + subtitle);
        LinearLayout.LayoutParams params = equalWidth ?
                new LinearLayout.LayoutParams(0, -2, 1) : new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(16);
        if (equalWidth) {
            params.leftMargin = dp(6);
            params.rightMargin = dp(6);
        }
        parent.addView(tile, params);
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconResource);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setPadding(dp(9), dp(9), dp(9), dp(9));
        icon.setBackground(gradient(0xfff8fbff, 0xffdce9f9, dp(15)));
        tile.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(42)));
        tile.addView(label(title, 19, true, 0xff193a65));
        tile.addView(label(subtitle, 12, false, 0xff6b819d));
        tile.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra(MainActivity.EXTRA_CATEGORY, category);
            startActivity(intent);
        });
    }

    private TextView label(String value, int size, boolean bold, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setPadding(0, dp(5), 0, dp(5));
        return view;
    }

    private GradientDrawable gradient(int start, int end, int radius) {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{start, end});
        drawable.setCornerRadius(radius);
        if (radius > 0) drawable.setStroke(dp(1), 0x90ffffff);
        return drawable;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
