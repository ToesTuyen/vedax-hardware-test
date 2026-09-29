# VedaX Hardware Test (Android 12)

App kiểm tra phần cứng cho Android 12 trở lên, cũng có thể chạy trên điện thoại Samsung để thử giao diện. Màn hình chính hiển thị các mục theo lưới 2 cột, mỗi mục có icon và chỉ bắt đầu kiểm tra khi được chạm vào. Mỗi kết quả chỉ xác nhận đường phần cứng/API tương ứng; app không lưu ảnh, UID hoặc thông tin căn cước.

Phiên bản hiện tại: **0.2.0** (`versionCode` 2). Khi phát hành bản mới, tăng cả `versionCode` và `versionName` trong `app/build.gradle`; Android chỉ chấp nhận cài đè nếu APK mới có `versionCode` cao hơn và cùng chữ ký.

| Mục | Bản test này kiểm tra | Muốn tích hợp đầy đủ |
| --- | --- | --- |
| Camera | Liệt kê Camera2, xem hình trực tiếp | Nếu camera USB không vào Camera2, cần SDK UVC hoặc HAL của hãng |
| Khuôn mặt | Đếm khuôn mặt trong một khung hình | FaceID/so khớp danh tính cần SDK và dữ liệu đăng ký mẫu |
| NFC/CCCD | Nhấn Bắt đầu quét để bật đầu đọc, nhận thẻ, công nghệ ISO-DEP và kết nối chip; hiển thị Đang quét/Đang đọc | Đọc trường CCCD trong chip cần SDK/giao thức và điều kiện truy cập phù hợp |
| USB | Tên, VID:PID, interface class | Giúp xác định đầu đọc/máy in nào đang kết nối |
| In | Gửi trang thử qua Android Print Service | Máy in tích hợp có thể dùng SDK, USB hoặc cổng nối tiếp riêng |

## Build và cài

Mở thư mục này trong Android Studio rồi chạy cấu hình `app`, hoặc:

```bash
./gradlew assembleDebug
adb devices -l
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Nếu máy tính có nhiều thiết bị ADB, dùng `adb -s SERIAL install -r ...` với đúng serial đích. Nếu Samsung chặn cài bằng streaming, thử `adb -s SERIAL install --no-streaming -r ...`.

Mọi log do app phát ra dùng đúng một tag `IVISTA_TECH`:

```bash
adb logcat -v time IVISTA_TECH:V '*:S'
```

Trong Windows CMD có thể thay `'*:S'` bằng `"*:S"`. App không ghi UID hoặc dữ liệu CCCD vào logcat.

## Tình trạng phần cứng thấy trong logcat người dùng cung cấp

- Bo khởi động với model `Rockchip RK3588 DXB LP4 V10 Board`.
- USB host phát hiện `Sonix USB camera1` (VID:PID `26e0:6200`). Cần kiểm tra tiếp nó có được Camera2 liệt kê không.
- Log có lỗi package `com.android.nfc` thiếu shared library `com.nxp.nfc`. Điều này có thể ngăn NFC API hoạt động trên firmware hiện tại.
- Log chưa cho thấy máy in USB. Máy in có thể dùng kết nối khác, nên chưa thể khẳng định bằng log này.

Để hoàn thiện đọc CCCD/FaceID/in trực tiếp cần tài liệu hoặc APK/SDK mẫu của nhà cung cấp, hoặc kết quả khảo sát ADB từ đúng robot.

## Nguồn icon

Năm icon NFC, Face, Camera, Print và USB được chuyển từ [Google Material Icons](https://github.com/google/material-design-icons) sang Android VectorDrawable, theo giấy phép Apache License 2.0. Xem [LICENSE-MATERIAL-ICONS](LICENSE-MATERIAL-ICONS).
