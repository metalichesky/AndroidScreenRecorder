package com.metalichesky.screenrecorder.model.control

class VideoCodec(
    val type: Type = DEFAULT_TYPE
) : Control() {
    companion object {
        val DEFAULT_TYPE = Type.DEVICE_DEFAULT
        fun getType(value: Int): Type {
            return Type.values().find {
                it.value == value
            } ?: DEFAULT_TYPE
        }
    }

    enum class Type(val value: Int, val mimeType: String?) {
        /**
         * Let the device choose its codec.
         */
        DEVICE_DEFAULT(0, null),

        H_263(1, "video/3gpp"),

        H_264(2, "video/avc"),

        HEVC(3, "video/hevc"),

        VP8(4, "video/x-vnd.on2.vp8"),

        MPEG_4_SP(5, "video/mp4v-es")
    }

    override fun equals(other: Any?): Boolean {
        return (other is VideoCodec) && other.type == type
    }
}