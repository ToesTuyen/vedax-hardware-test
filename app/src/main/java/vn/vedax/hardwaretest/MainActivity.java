package vn.vedax.hardwaretest;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.media.FaceDetector;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PrintManager;
import android.print.pdf.PrintedPdfDocument;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.graphics.pdf.PdfDocument;
import android.util.Log;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String LOG_TAG = "IVISTA_TECH";
    private static final int CAMERA_PERMISSION_REQUEST = 7;
    public static final String EXTRA_CATEGORY = "category";
    public static final String CATEGORY_NFC = "nfc";
    public static final String CATEGORY_FACE = "face";
    public static final String CATEGORY_CAMERA = "camera";
    public static final String CATEGORY_PRINT = "print";
    public static final String CATEGORY_USB = "usb";
    private final List<String> cameraIds = new ArrayList<>();
    private CameraManager cameraManager;
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraSession;
    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private TextureView preview;
    private Spinner cameraPicker;
    private TextView cameraStatus;
    private TextView faceStatus;
    private TextView nfcStatus;
    private TextView nfcResultTitle;
    private TextView nfcResult;
    private Button nfcStartButton;
    private TextView usbStatus;
    private TextView printStatus;
    private NfcAdapter nfcAdapter;
    private String category;
    private volatile boolean nfcReaderEnabled;
    private long lastTagAt;
    private boolean cameraOpening;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        category = getIntent().getStringExtra(EXTRA_CATEGORY);
        if (category == null) category = CATEGORY_CAMERA;
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        buildUi();
        Log.i(LOG_TAG, "Category started: " + category + "; Android=" + Build.VERSION.RELEASE + "; model=" + Build.MODEL);
        if (CATEGORY_CAMERA.equals(category) || CATEGORY_FACE.equals(category)) {
            refreshCameras();
            if (CATEGORY_FACE.equals(category)) selectFrontCamera();
        } else if (CATEGORY_NFC.equals(category)) {
            refreshNfc();
        } else if (CATEGORY_USB.equals(category)) {
            refreshUsb();
        } else if (CATEGORY_PRINT.equals(category)) {
            printStatus.post(this::printTestPage);
        }
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(22), dp(24), dp(30));
        root.setBackground(gradient(0xfff7fbff, 0xffc8d9ec, 0));
        scroll.addView(root);

        TextView brand = text("IVISTA  /  TECH LAB", 12, true);
        brand.setLetterSpacing(0.16f);
        brand.setTextColor(0xff557399);
        root.addView(brand);
        TextView back = text("‹  Tất cả chức năng", 14, true);
        back.setTextColor(0xff305b99);
        back.setOnClickListener(v -> finish());
        root.addView(back);
        String heading = CATEGORY_NFC.equals(category) ? "CCCD / NFC" :
                CATEGORY_FACE.equals(category) ? "FaceID" :
                CATEGORY_PRINT.equals(category) ? "In phiếu" :
                CATEGORY_USB.equals(category) ? "Thiết bị kết nối" : "Camera";
        TextView title = text(heading, 30, true);
        title.setTextColor(0xff112d53);
        root.addView(title);
        TextView subtitle = text(CATEGORY_NFC.equals(category) ?
                "Nhấn Bắt đầu quét để kích hoạt đầu đọc NFC" :
                "Chức năng bắt đầu khi bạn mở mục này", 14, false);
        subtitle.setTextColor(0xff617b9a);
        root.addView(subtitle);

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(22), dp(18), dp(22), dp(18));
        hero.setBackground(gradient(0xffedf5ff, 0xffb8cde8, dp(28)));
        hero.setElevation(dp(6));
        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(-1, -2);
        heroParams.topMargin = dp(18);
        root.addView(hero, heroParams);
        TextView heroEyebrow = text("ANDROID " + Build.VERSION.RELEASE + "  •  " + Build.MODEL, 12, true);
        heroEyebrow.setLetterSpacing(0.1f);
        heroEyebrow.setTextColor(0xff41668f);
        hero.addView(heroEyebrow);
        TextView heroTitle = text(heading, 22, true);
        heroTitle.setTextColor(0xff193a65);
        hero.addView(heroTitle);
        TextView device = text(Build.MODEL + "  •  Trạng thái hiển thị ngay bên dưới", 13, false);
        device.setTextColor(0xff45698f);
        hero.addView(device);

        boolean wide = getResources().getConfiguration().screenWidthDp >= 700;
        LinearLayout sections = new LinearLayout(this);
        sections.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        LinearLayout.LayoutParams sectionsParams = new LinearLayout.LayoutParams(-1, -2);
        sectionsParams.topMargin = dp(2);
        root.addView(sections, sectionsParams);

        if (CATEGORY_CAMERA.equals(category) || CATEGORY_FACE.equals(category)) {
        LinearLayout cameraCard = card(sections, CATEGORY_FACE.equals(category) ? "Camera FaceID" : "Camera",
                CATEGORY_FACE.equals(category) ? "Tìm khuôn mặt trong khung hình" : "Xem hình trực tiếp");
        cameraPicker = new Spinner(this);
        cameraPicker.setBackground(rounded(0xffedf3fa, dp(14)));
        cameraPicker.setPadding(dp(10), dp(8), dp(10), dp(8));
        cameraCard.addView(cameraPicker);
        LinearLayout cameraActions = row(cameraCard);
        button(cameraActions, "Quét", v -> refreshCameras());
        button(cameraActions, "Mở camera", v -> startCamera());
        button(cameraActions, "Tắt", v -> stopCamera());
        preview = new TextureView(this);
        FrameLayout previewContainer = new FrameLayout(this);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(wide ? 330 : 260));
        previewParams.topMargin = dp(10);
        previewContainer.setBackground(rounded(0xff152c4c, dp(20)));
        previewContainer.setClipToOutline(true);
        previewContainer.addView(preview, new FrameLayout.LayoutParams(-1, -1));
        cameraCard.addView(previewContainer, previewParams);
        preview.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                startCamera();
            }
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) { }
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                stopCamera();
                return true;
            }
            @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) { }
        });
        cameraStatus = text("Chưa kiểm tra", 14, false);
        cameraStatus.setBackground(rounded(0xffeef4fb, dp(14)));
        cameraStatus.setPadding(dp(12), dp(10), dp(12), dp(10));
        cameraCard.addView(cameraStatus);
        if (CATEGORY_FACE.equals(category)) button(cameraCard, "Kiểm tra lại khuôn mặt", v -> detectFaces());
        faceStatus = text("FaceID · Đang chờ camera", 14, false);
        faceStatus.setBackground(rounded(0xffeef4fb, dp(14)));
        faceStatus.setPadding(dp(12), dp(10), dp(12), dp(10));
        if (CATEGORY_FACE.equals(category)) cameraCard.addView(faceStatus);
        }

        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        if (wide) {
            LinearLayout.LayoutParams sideParams = new LinearLayout.LayoutParams(0, -2, 1);
            sideParams.leftMargin = dp(8);
            sections.addView(side, sideParams);
        } else {
            sections.addView(side, new LinearLayout.LayoutParams(-1, -2));
        }

        if (CATEGORY_NFC.equals(category)) {
        LinearLayout nfcCard = card(side, "Đọc thẻ căn cước", "Đặt thẻ lên vùng NFC và giữ yên");
        nfcStatus = text("Nhấn Bắt đầu quét để tìm thẻ.", 14, false);
        nfcStatus.setBackground(rounded(0xffeef4fb, dp(14)));
        nfcStatus.setPadding(dp(12), dp(10), dp(12), dp(10));
        nfcCard.addView(nfcStatus);
        nfcStartButton = button(nfcCard, "Bắt đầu quét", v -> startNfcScan());
        nfcResultTitle = text("KẾT QUẢ QUÉT", 12, true);
        nfcResultTitle.setTextColor(0xff41668f);
        nfcResultTitle.setVisibility(View.GONE);
        nfcCard.addView(nfcResultTitle);
        nfcResult = text("", 14, false);
        nfcResult.setBackground(rounded(0xffe4eefb, dp(14)));
        nfcResult.setPadding(dp(12), dp(10), dp(12), dp(10));
        nfcResult.setVisibility(View.GONE);
        nfcCard.addView(nfcResult);
        nfcCard.addView(footnote("UID không phải số CCCD. Đọc thông tin cá nhân trong chip cần giao thức và quyền truy cập phù hợp."));
        }

        if (CATEGORY_USB.equals(category)) {
        LinearLayout usbCard = card(side, "Thiết bị kết nối", "Camera · Đầu đọc · Máy in");
        usbStatus = text("Đang kiểm tra USB…", 14, false);
        usbStatus.setBackground(rounded(0xffeef4fb, dp(14)));
        usbStatus.setPadding(dp(12), dp(10), dp(12), dp(10));
        usbCard.addView(usbStatus);
        button(usbCard, "Quét lại thiết bị USB", v -> refreshUsb());
        }

        if (CATEGORY_PRINT.equals(category)) {
        LinearLayout printCard = card(side, "In phiếu", "Kiểm tra đường in của Android");
        printStatus = text("Chưa gửi lệnh in", 14, false);
        printStatus.setBackground(rounded(0xffeef4fb, dp(14)));
        printStatus.setPadding(dp(12), dp(10), dp(12), dp(10));
        printCard.addView(printStatus);
        button(printCard, "In trang kiểm tra", v -> printTestPage());
        printCard.addView(footnote("Máy in tích hợp cần dịch vụ in hoặc giao thức do hãng cung cấp."));
        }

        setContentView(scroll);
    }

    private void refreshCameras() {
        cameraIds.clear();
        List<String> labels = new ArrayList<>();
        try {
            for (String id : cameraManager.getCameraIdList()) {
                CameraCharacteristics info = cameraManager.getCameraCharacteristics(id);
                Integer facing = info.get(CameraCharacteristics.LENS_FACING);
                String side = facing == null ? "không rõ" : facing == CameraCharacteristics.LENS_FACING_FRONT ? "trước" :
                        facing == CameraCharacteristics.LENS_FACING_BACK ? "sau" : "ngoài";
                cameraIds.add(id);
                labels.add("Camera " + id + " (" + side + ")");
            }
            cameraStatus.setText(cameraIds.isEmpty() ?
                    "Android Camera2 không thấy camera. Nếu USB có camera, có thể cần SDK UVC của hãng." :
                    "Camera2 nhận " + cameraIds.size() + " camera. Chọn một camera để xem hình.");
            Log.i(LOG_TAG, "Camera2 scan: " + cameraIds.size() + " camera(s); IDs=" + cameraIds);
        } catch (Exception e) {
            cameraStatus.setText("Không quét được camera: " + e.getMessage());
            Log.e(LOG_TAG, "Camera2 scan failed", e);
        }
        cameraPicker.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
    }

    private void selectFrontCamera() {
        for (int i = 0; i < cameraIds.size(); i++) {
            try {
                Integer facing = cameraManager.getCameraCharacteristics(cameraIds.get(i))
                        .get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    cameraPicker.setSelection(i);
                    return;
                }
            } catch (CameraAccessException ignored) { }
        }
    }

    private void startCamera() {
        if (cameraIds.isEmpty()) {
            cameraStatus.setText("Không có camera trong Camera2.");
            Log.w(LOG_TAG, "Camera open skipped: Camera2 has no camera");
            return;
        }
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.i(LOG_TAG, "Requesting camera permission");
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
            return;
        }
        if (!preview.isAvailable()) {
            cameraStatus.setText("Màn xem hình chưa sẵn sàng. Thử lại sau một giây.");
            return;
        }
        stopCamera();
        int selected = cameraPicker.getSelectedItemPosition();
        if (selected < 0 || selected >= cameraIds.size()) selected = 0;
        String id = cameraIds.get(selected);
        cameraThread = new HandlerThread("CameraPreview");
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());
        cameraOpening = true;
        cameraStatus.setText("Đang mở camera " + id + "…");
        Log.i(LOG_TAG, "Opening camera ID=" + id);
        try {
            cameraManager.openCamera(id, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice device) {
                    cameraOpening = false;
                    cameraDevice = device;
                    createPreview(device);
                }
                @Override public void onDisconnected(CameraDevice device) {
                    device.close();
                    if (cameraDevice == device) cameraDevice = null;
                    Log.w(LOG_TAG, "Camera disconnected; ID=" + device.getId());
                    runOnUiThread(() -> cameraStatus.setText("Camera đã ngắt kết nối."));
                }
                @Override public void onError(CameraDevice device, int error) {
                    device.close();
                    if (cameraDevice == device) cameraDevice = null;
                    Log.e(LOG_TAG, "Camera open error; ID=" + device.getId() + "; code=" + error);
                    runOnUiThread(() -> cameraStatus.setText("Mở camera lỗi mã " + error));
                }
            }, cameraHandler);
        } catch (Exception e) {
            cameraOpening = false;
            cameraStatus.setText("Mở camera thất bại: " + e.getMessage());
            Log.e(LOG_TAG, "Camera open failed; ID=" + id, e);
            stopCamera();
        }
    }

    private void createPreview(CameraDevice device) {
        try {
            SurfaceTexture texture = preview.getSurfaceTexture();
            if (texture == null) throw new IllegalStateException("Màn xem hình không còn sẵn sàng");
            texture.setDefaultBufferSize(640, 480);
            Surface surface = new Surface(texture);
            CaptureRequest.Builder request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            request.addTarget(surface);
            device.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                @Override public void onConfigured(CameraCaptureSession session) {
                    if (cameraDevice != device) {
                        session.close();
                        return;
                    }
                    cameraSession = session;
                    try {
                        session.setRepeatingRequest(request.build(), null, cameraHandler);
                        Log.i(LOG_TAG, "Camera preview running; ID=" + device.getId());
                        runOnUiThread(() -> cameraStatus.setText("Camera đang hiển thị hình ảnh."));
                        if (CATEGORY_FACE.equals(category)) {
                            runOnUiThread(() -> preview.postDelayed(MainActivity.this::detectFaces, 1200));
                        }
                    } catch (CameraAccessException e) {
                        Log.e(LOG_TAG, "Camera preview request failed", e);
                        runOnUiThread(() -> cameraStatus.setText("Camera không phát hình: " + e.getMessage()));
                    }
                }
                @Override public void onConfigureFailed(CameraCaptureSession session) {
                    session.close();
                    Log.e(LOG_TAG, "Camera preview session configuration failed");
                    runOnUiThread(() -> cameraStatus.setText("Camera không tạo được luồng xem hình."));
                }
            }, cameraHandler);
        } catch (Exception e) {
            Log.e(LOG_TAG, "Camera preview setup failed", e);
            runOnUiThread(() -> cameraStatus.setText("Camera không phát hình: " + e.getMessage()));
        }
    }

    private void stopCamera() {
        if (cameraSession != null) {
            cameraSession.close();
            cameraSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (cameraThread != null && !cameraOpening) {
            cameraThread.quitSafely();
            cameraThread = null;
            cameraHandler = null;
        }
    }

    private void detectFaces() {
        if (cameraDevice == null || !preview.isAvailable()) {
            faceStatus.setText("Hãy bật camera và đợi thấy hình trước.");
            Log.w(LOG_TAG, "Face check skipped: preview not running");
            return;
        }
        Bitmap frame = preview.getBitmap(640, 480);
        if (frame == null) {
            faceStatus.setText("Không lấy được khung hình.");
            return;
        }
        faceStatus.setText("Đang tìm khuôn mặt…");
        new Thread(() -> {
            try {
                Bitmap rgb565 = frame.copy(Bitmap.Config.RGB_565, false);
                FaceDetector.Face[] faces = new FaceDetector.Face[5];
                int count = new FaceDetector(rgb565.getWidth(), rgb565.getHeight(), faces.length).findFaces(rgb565, faces);
                rgb565.recycle();
                Log.i(LOG_TAG, "Face detection result: count=" + count + "; identity verification=not implemented");
                runOnUiThread(() -> faceStatus.setText("Phát hiện " + count + " khuôn mặt trong ảnh. Đây chưa phải xác thực FaceID."));
            } catch (Exception e) {
                Log.e(LOG_TAG, "Face detection failed", e);
                runOnUiThread(() -> faceStatus.setText("Không phân tích được ảnh: " + e.getMessage()));
            } finally {
                frame.recycle();
            }
        }, "FaceCheck").start();
    }

    private void refreshNfc() {
        if (nfcAdapter == null) {
            nfcStatus.setText("Android không cung cấp NFC cho app. Kiểm tra phần cứng/firmware hoặc SDK của đầu đọc CCCD.");
            Log.w(LOG_TAG, "Android NFC adapter unavailable");
        } else {
            nfcStatus.setText(nfcAdapter.isEnabled() ?
                    "NFC đã sẵn sàng. Nhấn Bắt đầu quét rồi đưa CCCD vào vùng đọc." :
                    "NFC đang tắt. Bật NFC trong Cài đặt rồi nhấn Bắt đầu quét.");
            Log.i(LOG_TAG, "Android NFC adapter available; enabled=" + nfcAdapter.isEnabled());
        }
    }

    private void startNfcScan() {
        if (!CATEGORY_NFC.equals(category) || nfcStatus == null) return;
        if (nfcAdapter == null || !nfcAdapter.isEnabled()) {
            refreshNfc();
            return;
        }
        Log.i(LOG_TAG, "NFC scan requested by user");
        try {
            if (nfcReaderEnabled) nfcAdapter.disableReaderMode(this);
            nfcResultTitle.setVisibility(View.GONE);
            nfcResult.setVisibility(View.GONE);
            int flags = NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_NFC_B |
                    NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK;
            nfcAdapter.enableReaderMode(this, this::readTag, flags, null);
            nfcReaderEnabled = true;
            lastTagAt = 0;
            nfcStatus.setText("● ĐANG QUÉT\nĐưa CCCD sát vùng NFC ở lưng máy và giữ yên vài giây.");
            nfcStartButton.setText("Quét lại thẻ");
            Log.i(LOG_TAG, "NFC reader mode started; NFC-A/B + ISO-DEP");
        } catch (Exception e) {
            nfcReaderEnabled = false;
            nfcStatus.setText("Không bật được chế độ đọc NFC: " + e.getMessage());
            nfcStartButton.setText("Bắt đầu quét");
            Log.e(LOG_TAG, "NFC reader mode failed", e);
        }
    }

    private void readTag(Tag tag) {
        if (!nfcReaderEnabled) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastTagAt < 2500) return;
        lastTagAt = now;
        Log.i(LOG_TAG, "NFC tag discovered; technologies=" + Arrays.toString(tag.getTechList()));
        runOnUiThread(() -> nfcStatus.setText("● READING…\nĐã nhận thẻ. Đang kết nối chip; giữ thẻ yên."));
        IsoDep isoDep = IsoDep.get(tag);
        String uid = hex(tag.getId());
        String technologies = Arrays.toString(tag.getTechList()).replace("android.nfc.tech.", "");
        if (isoDep == null) {
            showNfcResult("ĐÃ NHẬN THẺ NFC", "UID (không phải số CCCD): " + uid +
                    "\nCông nghệ: " + technologies +
                    "\nKết nối ISO-DEP: không hỗ trợ" +
                    "\nSố CCCD / họ tên: chưa đọc được");
            Log.w(LOG_TAG, "NFC tag has no ISO-DEP interface");
            return;
        }
        try {
            isoDep.connect();
            String result = "UID (không phải số CCCD): " + uid +
                    "\nCông nghệ: " + technologies +
                    "\nKết nối chip: thành công" +
                    "\nAPDU tối đa: " + isoDep.getMaxTransceiveLength() + " byte" +
                    "\nSố CCCD / họ tên: chưa đọc được";
            showNfcResult("ĐÃ NHẬN THẺ ISO-DEP", result);
            Log.i(LOG_TAG, "NFC ISO-DEP chip connected; maxTransceive=" + isoDep.getMaxTransceiveLength());
        } catch (Exception e) {
            showNfcResult("ĐÃ THẤY THẺ, CHƯA KẾT NỐI CHIP",
                    "UID (không phải số CCCD): " + uid +
                    "\nCông nghệ: " + technologies +
                    "\nLỗi: " + e.getMessage() +
                    "\nGiữ thẻ sát vùng NFC rồi quét lại.");
            Log.e(LOG_TAG, "NFC ISO-DEP connection failed", e);
        } finally {
            try { isoDep.close(); } catch (IOException ignored) { }
        }
    }

    private void showNfcResult(String status, String details) {
        runOnUiThread(() -> {
            nfcStatus.setText(status);
            nfcResultTitle.setVisibility(View.VISIBLE);
            nfcResult.setText(details);
            nfcResult.setVisibility(View.VISIBLE);
        });
    }

    private String hex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "(không có)";
        StringBuilder value = new StringBuilder();
        for (byte b : bytes) value.append(String.format(Locale.ROOT, "%02X", b & 0xff));
        return value.toString();
    }

    @Override protected void onResume() {
        super.onResume();
        if (CATEGORY_NFC.equals(category) && nfcStatus != null) refreshNfc();
    }

    @Override protected void onPause() {
        if (nfcAdapter != null && nfcReaderEnabled) {
            try { nfcAdapter.disableReaderMode(this); } catch (Exception ignored) { }
            nfcReaderEnabled = false;
            Log.i(LOG_TAG, "NFC reader mode stopped");
        }
        if (CATEGORY_NFC.equals(category) && nfcStartButton != null) {
            nfcStartButton.setText("Bắt đầu quét");
        }
        stopCamera();
        super.onPause();
    }

    private void refreshUsb() {
        UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> devices = manager.getDeviceList();
        if (devices.isEmpty()) {
            usbStatus.setText("Android USB host chưa thấy thiết bị nào.");
            Log.w(LOG_TAG, "USB host scan: no devices");
            return;
        }
        StringBuilder result = new StringBuilder("Đã thấy " + devices.size() + " thiết bị:\n");
        for (UsbDevice device : devices.values()) {
            Log.i(LOG_TAG, "USB device: VID:PID=" +
                    String.format(Locale.ROOT, "%04X:%04X", device.getVendorId(), device.getProductId()) +
                    "; product=" + device.getProductName() + "; interfaces=" + device.getInterfaceCount());
            result.append("\n")
                    .append(device.getProductName() == null ? "Thiết bị USB" : device.getProductName())
                    .append("  VID:PID ")
                    .append(String.format(Locale.ROOT, "%04X:%04X", device.getVendorId(), device.getProductId()));
            for (int i = 0; i < device.getInterfaceCount(); i++) {
                UsbInterface iface = device.getInterface(i);
                result.append("\n  Interface ").append(i).append(": class ").append(iface.getInterfaceClass());
                if (iface.getInterfaceClass() == 7) result.append(" (USB printer)");
                if (iface.getInterfaceClass() == 14) result.append(" (USB camera)");
            }
        }
        usbStatus.setText(result.toString());
    }

    private void printTestPage() {
        PrintManager manager = (PrintManager) getSystemService(Context.PRINT_SERVICE);
        if (manager == null) {
            printStatus.setText("Android không có dịch vụ in.");
            Log.e(LOG_TAG, "PrintManager unavailable");
            return;
        }
        try {
            manager.print("VedaX hardware test", new TestPrintAdapter(), null);
            printStatus.setText("Đã mở hộp thoại in. Chọn máy in và xác nhận để kiểm tra giấy in.");
            Log.i(LOG_TAG, "Android print dialog opened");
        } catch (Exception e) {
            printStatus.setText("Không mở được hộp thoại in: " + e.getMessage());
            Log.e(LOG_TAG, "Android print dialog failed", e);
        }
    }

    private class TestPrintAdapter extends PrintDocumentAdapter {
        private PrintAttributes attributes;
        @Override public void onLayout(PrintAttributes oldAttributes, PrintAttributes newAttributes,
                                       CancellationSignal cancellation, LayoutResultCallback callback, Bundle extras) {
            attributes = newAttributes;
            if (cancellation.isCanceled()) { callback.onLayoutCancelled(); return; }
            PrintDocumentInfo info = new PrintDocumentInfo.Builder("vedax-test.pdf")
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(1).build();
            callback.onLayoutFinished(info, !newAttributes.equals(oldAttributes));
        }
        @Override public void onWrite(android.print.PageRange[] pages, ParcelFileDescriptor destination,
                                      CancellationSignal cancellation, WriteResultCallback callback) {
            if (cancellation.isCanceled()) { callback.onWriteCancelled(); return; }
            PrintedPdfDocument document = new PrintedPdfDocument(MainActivity.this, attributes);
            try {
                PdfDocument.Page page = document.startPage(0);
                Canvas canvas = page.getCanvas();
                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                paint.setColor(Color.BLACK);
                paint.setTextSize(18);
                paint.setTypeface(Typeface.DEFAULT_BOLD);
                canvas.drawText("VEDAX HARDWARE TEST", 40, 60, paint);
                paint.setTypeface(Typeface.DEFAULT);
                paint.setTextSize(13);
                canvas.drawText("Android print service", 40, 90, paint);
                canvas.drawText(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(new Date()), 40, 115, paint);
                canvas.drawText("Neu thay dong nay tren giay: in thanh cong.", 40, 140, paint);
                document.finishPage(page);
                try (FileOutputStream out = new FileOutputStream(destination.getFileDescriptor())) {
                    document.writeTo(out);
                }
                Log.i(LOG_TAG, "Android print test page generated; awaiting print-service result");
                callback.onWriteFinished(new android.print.PageRange[]{android.print.PageRange.ALL_PAGES});
            } catch (IOException | RuntimeException e) {
                Log.e(LOG_TAG, "Android print page generation failed", e);
                callback.onWriteFailed(e.getMessage());
            } finally {
                document.close();
            }
        }
    }

    private LinearLayout card(LinearLayout root, String heading, String description) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(16), dp(18), dp(18));
        box.setBackground(gradient(0xf5ffffff, 0xe9eef6ff, dp(26)));
        box.setElevation(dp(4));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(16);
        root.addView(box, params);
        TextView label = text(heading, 21, true);
        label.setTextColor(0xff183c68);
        box.addView(label);
        TextView sub = text(description, 13, false);
        sub.setTextColor(0xff6b819d);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(-1, -2);
        subParams.bottomMargin = dp(8);
        box.addView(sub, subParams);
        return box;
    }

    private LinearLayout row(LinearLayout parent) {
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(8);
        params.bottomMargin = dp(4);
        parent.addView(line, params);
        return line;
    }

    private Button button(LinearLayout parent, String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setTextColor(Color.WHITE);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(gradient(0xff5277b2, 0xff254b88, dp(22)));
        button.setElevation(dp(2));
        button.setPadding(dp(11), dp(5), dp(11), dp(5));
        button.setOnClickListener(listener);
        if (parent.getOrientation() == LinearLayout.HORIZONTAL) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), 1);
            params.rightMargin = dp(5);
            parent.addView(button, params);
        } else {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(48));
            params.topMargin = dp(10);
            params.bottomMargin = dp(7);
            parent.addView(button, params);
        }
        return button;
    }

    private TextView footnote(String value) {
        TextView view = text(value, 12, false);
        view.setTextColor(0xff7289a6);
        return view;
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private GradientDrawable gradient(int start, int end, int radius) {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{start, end});
        drawable.setCornerRadius(radius);
        if (radius > 0) drawable.setStroke(dp(1), 0x90ffffff);
        return drawable;
    }

    private TextView text(String value, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(Color.rgb(27, 40, 54));
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setPadding(0, dp(5), 0, dp(5));
        return view;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST && grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.i(LOG_TAG, "Camera permission granted");
            startCamera();
        } else if (requestCode == CAMERA_PERMISSION_REQUEST) {
            cameraStatus.setText("Chưa được cấp quyền camera.");
            Log.w(LOG_TAG, "Camera permission denied");
        }
    }
}
