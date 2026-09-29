# VedaX Hardware Test (Android 12)

App kiểm tra phần cứng cho Android 12 trở lên, cũng có thể chạy trên điện thoại Samsung để thử giao diện. Màn hình chính hiển thị các mục theo lưới 2 cột, mỗi mục có icon và chỉ bắt đầu kiểm tra khi được chạm vào. Mỗi kết quả chỉ xác nhận đường phần cứng/API tương ứng; app không lưu ảnh, UID hoặc thông tin căn cước.

Phiên bản nguồn mã hiện tại: **0.4.0** (`versionCode` 7). Mã ứng dụng đã chuyển hoàn toàn sang Kotlin. Chưa phát hành GitHub Release v0.4.0 để tránh tự đẩy bản refactor tới robot trước khi kiểm tra trên phần cứng. Khi phát hành bản mới, tăng cả `versionCode` và `versionName` trong `app/build.gradle`; Android chỉ chấp nhận cài đè nếu APK mới có `versionCode` cao hơn và cùng chữ ký. Launcher icon VedaX dạng adaptive vector được đặt trong `src/main`, nên cả debug và release dùng cùng logo; Android 13 trở lên có thêm phiên bản monochrome cho themed icons.

## Cấu trúc mã

`app/src/main/kotlin/vn/vedax/hardwaretest/` chia theo trách nhiệm:

| Gói | Trách nhiệm |
| --- | --- |
| `core` | Danh mục tính năng, tag log và so sánh phiên bản |
| `ui` | Trang chủ, trang chức năng và thành phần giao diện dùng chung |
| `ui/features` | Mỗi màn hình tính năng; quản lý trạng thái và vòng đời giao diện |
| `device` | Camera2, nhận diện khuôn mặt, NFC, USB và Android PrintManager |
| `update` | Đọc GitHub Releases, xác minh APK/chữ ký và mở trình cài Android |

Activity không tự đọc thiết bị hay gọi mạng. `PrintTest` giữ đường in `PrintManager` và nội dung trang thử của v0.3.3. `NfcReader` chỉ dùng Android NFC API; nếu firmware không cung cấp `NfcAdapter`, cần SDK/driver của đầu đọc riêng, không thể khắc phục bằng xin quyền NFC lần nữa.

## Cập nhật từ GitHub Releases

App kiểm tra [Release mới nhất](https://github.com/ToesTuyen/vedax-hardware-test/releases) của repo Public khi mở màn hình chính, không cần token. Nếu phiên bản mới hơn, app hiện popup để người dùng quyết định tải và cài APK; không tự cài âm thầm. Có thể kiểm tra lại bằng liên kết **Kiểm tra cập nhật ứng dụng** cuối màn hình.

Nếu đã chạy bản thử nghiệm yêu cầu token cho repo Private, bản Public mới sẽ xóa token cũ đã lưu trên thiết bị; không còn dùng xác thực GitHub.

Release cần có tag dạng `v0.4.0`, đúng một tệp `.apk`, và SHA-256 do GitHub công bố. App xác minh hash, package name, versionCode, versionName và chữ ký trước khi chuyển APK cho trình cài đặt Android. Android có thể yêu cầu cho phép **Cài ứng dụng không rõ nguồn gốc** cho app này. Sau khi tải, việc xác nhận cài vẫn do người dùng thực hiện.

Từ **v0.3.3**, GitHub Release chứa APK release ký bằng khóa riêng, không phải khóa debug. **Không thể cài đè bản debug cũ:** gỡ bản debug trước khi cài v0.3.3 (sao lưu dữ liệu nếu cần). Các bản release tiếp theo phải dùng lại đúng keystore này để cập nhật trong app; hãy sao lưu keystore và mật khẩu ở nơi an toàn. Khóa ký không nằm trong GitHub repo.

Để build release có chữ ký, đặt bốn biến môi trường `VEDAX_RELEASE_KEYSTORE`, `VEDAX_RELEASE_STORE_PASSWORD`, `VEDAX_RELEASE_KEY_ALIAS`, `VEDAX_RELEASE_KEY_PASSWORD`, rồi chạy `./gradlew assembleRelease`. Trên máy Mac đã tạo khóa này, mật khẩu nằm trong Keychain dưới service `vedax-hardware-test-release-signing`:

```bash
export VEDAX_RELEASE_KEYSTORE="$HOME/.local/share/vedax-hardware-test/release.p12"
export VEDAX_RELEASE_KEY_ALIAS=vedax_release
export VEDAX_RELEASE_STORE_PASSWORD="$(security find-generic-password -s vedax-hardware-test-release-signing -w)"
export VEDAX_RELEASE_KEY_PASSWORD="$VEDAX_RELEASE_STORE_PASSWORD"
./gradlew assembleRelease
unset VEDAX_RELEASE_KEYSTORE VEDAX_RELEASE_KEY_ALIAS VEDAX_RELEASE_STORE_PASSWORD VEDAX_RELEASE_KEY_PASSWORD
```

Không có bốn giá trị này, Gradle có thể tạo APK release *chưa ký*, không dùng để cài đặt. Bản này phân phối qua GitHub Releases, chưa chuẩn bị để đăng Google Play.

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
adb install -r app/build/outputs/apk/debug/VedaX-Hardware-Test-v0.4.0-debug.apk
```

Chạy unit test phần so sánh phiên bản: `./gradlew testDebugUnitTest`.

Gradle đặt tên APK theo mẫu `VedaX-Hardware-Test-v<versionName>-<buildType>.apk` để nhìn rõ ứng dụng, phiên bản và loại bản build. Khi tăng phiên bản, cập nhật tên tệp trong lệnh `adb install` tương ứng.

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
