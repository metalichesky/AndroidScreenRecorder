package com.metalichesky.screenrecorder.model.control

class AudioSource(
    val type: Type = DEFAULT_TYPE
) : Control() {
    companion object {
        val DEFAULT_TYPE = Type.DEFAULT
        fun getType(value: Int): Type {
            return Type.values().find {
                it.value == value
            } ?: DEFAULT_TYPE
        }
    }

    enum class Type(val value: Int) {
        DEFAULT(0),
        MIC(1),
        NONE(2)
    }

    override fun equals(other: Any?): Boolean {
        return (other is AudioSource) && other.type == type
    }
}