package com.metalichesky.screenrecorder.model.control

class AudioCodec(
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
        AAC(1, "audio/mp4a-latm"),
        HE_AAC(2, "audio/mp4a-latm"),
        AAC_ELD(3, "audio/mp4a-latm"),
        AMR_NB(4, "audio/3gpp"),
        AMR_WB(5, "audio/amr-wb"),
        VORBIS(6, "audio/vorbis");
    }

    override fun equals(other: Any?): Boolean {
        return (other is AudioCodec) && other.type == type
    }
}