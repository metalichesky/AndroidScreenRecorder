package com.metalichesky.screenrecorder.util

class Size(val width: Int, val height: Int) : Comparable<Size?> {
    companion object {
        val comparator = object: Comparator<Size?> {
            override fun compare(o1: Size?, o2: Size?): Int {
                return if (o1 == null || o2 == null) {
                    -1
                } else {
                    o1.compareTo(o2)
                }
            }
        }
        val EMPTY = Size(0,0)
    }

    /**
     * Returns a flipped size, with height equal to this size's width
     * and width equal to this size's height.
     *
     * @return a flipped size
     */
    fun flip(): Size {
        return Size(height, width)
    }

    override fun equals(o: Any?): Boolean {
        if (o == null) {
            return false
        }
        if (this === o) {
            return true
        }
        if (o is Size) {
            val size = o
            return width == size.width && height == size.height
        }
        return false
    }

    override fun toString(): String {
        return width.toString() + "x" + height
    }

    override fun hashCode(): Int {
        return height xor (width shl Integer.SIZE / 2 or (width ushr Integer.SIZE / 2))
    }

    override operator fun compareTo(other: Size?): Int {
        return width * height - (other?.width ?: 0) * (other?.height ?: 0)
    }
}