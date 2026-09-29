package vn.vedax.hardwaretest.core

import vn.vedax.hardwaretest.R

enum class FeatureCategory(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: Int,
) {
    NFC("nfc", "CCCD / NFC", "Đưa thẻ lên đầu đọc", R.drawable.ic_nfc),
    FACE("face", "FaceID", "Tìm khuôn mặt", R.drawable.ic_face),
    CAMERA("camera", "Camera", "Xem hình trực tiếp", R.drawable.ic_camera),
    PRINT("print", "In phiếu", "Gửi trang in thử", R.drawable.ic_print),
    USB("usb", "Thiết bị kết nối", "Xem USB và mã thiết bị", R.drawable.ic_usb);

    companion object {
        fun fromId(id: String?): FeatureCategory = entries.firstOrNull { it.id == id } ?: CAMERA
    }
}
