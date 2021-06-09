package eu.programisci.file_management

enum class EMimeType(val mimeType: String, val extension: String) {
    JPEG("image/jpeg", ".jpeg"),
    JPG("image/jpeg", ".jpg"),
    PNG("image/png", ".png"),
    AVI("video/x-msvideo", ".avi"),
    WMV("video/x-ms-wmv", ".wmv"),
    MP4("video/mp4", ".mp4"),
    MPG("video/mpeg", ".mpg"),
    MPEG("video/mpeg", ".mpeg")
}
fun EMimeType.isVideo() = mutableListOf(EMimeType.AVI, EMimeType.WMV, EMimeType.MP4,
        EMimeType.MPG, EMimeType.MPEG).contains(this)