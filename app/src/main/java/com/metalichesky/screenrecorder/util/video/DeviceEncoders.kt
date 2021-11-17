package com.metalichesky.screenrecorder.util.video

import android.annotation.SuppressLint
import android.media.*
import android.util.Log
import com.metalichesky.screenrecorder.util.Size
import kotlin.math.roundToInt

class DeviceEncoders @SuppressLint("NewApi") constructor(
    mode: Int,
    videoType: String,
    audioType: String,
    videoOffset: Int,
    audioOffset: Int
) {
    companion object {
        private val LOG_TAG = DeviceEncoders::class.java.simpleName
        val MODE_RESPECT_ORDER = 0
        val MODE_PREFER_HARDWARE = 1

        // Assuming low motion, we don't want to put this too high for default usage,
        // advanced users are still free to change this for each video.
        // [image width] x [image height] x [framerate] x [motion rank] x 0.07 = [desired bitrate]
        fun estimateVideoBitRate(size: Size, frameRate: Int, quality: Float = 1.0f): Int {
            return ((1f + quality * 3f) * 0.07f * size.width * size.height * frameRate).roundToInt()
        }
    }

    private var mVideoEncoder: MediaCodecInfo? = null
    private var mAudioEncoder: MediaCodecInfo? = null
    private var mVideoCapabilities: MediaCodecInfo.VideoCapabilities? = null
    private var mAudioCapabilities: MediaCodecInfo.AudioCapabilities? = null

    init {
        val encoders = deviceEncoders
        mVideoEncoder = findDeviceEncoder(encoders, videoType, mode, videoOffset)
        Log.i(LOG_TAG, "Found video encoder: ${mVideoEncoder?.getName()}")
        mAudioEncoder = findDeviceEncoder(encoders, audioType, mode, audioOffset)
        Log.i(LOG_TAG, "Found audio encoder: ${mAudioEncoder?.getName()}")
        mVideoCapabilities = mVideoEncoder?.getCapabilitiesForType(videoType)?.videoCapabilities
        mAudioCapabilities = mAudioEncoder?.getCapabilitiesForType(audioType)?.audioCapabilities
    }

    /**
     * Collects all the device encoders, which means excluding decoders.
     * @return encoders
     */
    @get:SuppressLint("NewApi")
    val deviceEncoders: List<MediaCodecInfo>
        get() {
            val results = ArrayList<MediaCodecInfo>()
            val array = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            for (info: MediaCodecInfo in array) {
                if (info.isEncoder) results.add(info)
            }
            return results
        }

    /**
     * Whether an encoder is a hardware encoder or not. We don't have an API to check this,
     * but we can follow what libstagefright does:
     * https://android.googlesource.com/platform/frameworks/av/+/master/media/libstagefright/MediaCodecList.cpp#293
     *
     * @param encoder encoder
     * @return true if hardware
     */
    @SuppressLint("NewApi")
    fun isHardwareEncoder(encoder: String): Boolean {
        var encoder = encoder.lowercase()
        val isSoftwareEncoder = (encoder.startsWith("omx.google.")
                || encoder.startsWith("c2.android.")
                || !encoder.startsWith("omx.") && !encoder.startsWith("c2."))
        return !isSoftwareEncoder
    }

    /**
     * Finds the encoder we'll be using, depending on the given mode flag:
     * - [.MODE_RESPECT_ORDER] will just take the first of the list
     * - [.MODE_PREFER_HARDWARE] will prefer hardware encoders
     * Throws if we find no encoder for this type.
     *
     * @param encoders encoders
     * @param mimeType mime type
     * @param mode mode
     * @return encoder
     */
    @SuppressLint("NewApi")
    fun findDeviceEncoder(
        encoders: List<MediaCodecInfo>,
        mimeType: String,
        mode: Int,
        offset: Int
    ): MediaCodecInfo {
        var results: MutableList<MediaCodecInfo> = ArrayList<MediaCodecInfo>()
        for (encoder: MediaCodecInfo in encoders) {
            val types = encoder.supportedTypes
            for (type: String in types) {
                if (type.equals(mimeType, ignoreCase = true)) {
                    results.add(encoder)
                    break
                }
            }
        }
        Log.i(LOG_TAG, "findDeviceEncoder() type:$mimeType encoders: ${results.size}")
        if (mode == MODE_PREFER_HARDWARE) {
            results = results.sortedBy { isHardwareEncoder(it.name) }.toMutableList()
        }
        if (results.size < offset + 1) {
            // This should not be a VideoException or AudioException - we want the process
            // to crash here.
            throw RuntimeException("No encoders for type:$mimeType")
        }
        return results[offset]
    }

    /**
     * Returns a video size supported by the device encoders.
     * Throws if input width or height are out of the supported boundaries.
     *
     * @param size input size
     * @return adjusted size
     */
    @SuppressLint("NewApi")
    fun getSupportedVideoSize(size: Size?): Size {
        var width: Int = size?.width ?: 0
        var height: Int = size?.height ?: 0
        val aspect = width.toDouble() / height
        Log.i(LOG_TAG, "getSupportedVideoSize() - started. width: $width height: $height")
        val videoCapabilities = mVideoCapabilities ?: return Size(width, height)

        // If width is too large, scale down, but keep aspect ratio.
        if (videoCapabilities.supportedWidths.upper < width) {
            width = videoCapabilities.supportedWidths.upper
            height = Math.round(width / aspect).toInt()
            Log.i(
                LOG_TAG,
                "getSupportedVideoSize() - exceeds maxWidth! width:$width height:$height"
            )
        }

        // If height is too large, scale down, but keep aspect ratio.
        if (videoCapabilities.supportedHeights.upper < height) {
            height = videoCapabilities.supportedHeights.upper
            width = Math.round(aspect * height).toInt()
            Log.i(
                LOG_TAG,
                "getSupportedVideoSize() - exceeds maxHeight! width:$width height:$height"
            )
        }

        // Adjust the alignment.
        while (width % videoCapabilities.widthAlignment != 0) width--
        while (height % videoCapabilities.heightAlignment != 0) height--
        Log.i(LOG_TAG, "getSupportedVideoSize() - aligned. width:$width height:$height")

        // It's still possible that we're BELOW the lower.
        if (!videoCapabilities.supportedWidths.contains(width)) {
            throw VideoException(
                "Width not supported after adjustment." +
                        " Desired:" + width +
                        " Range:" + videoCapabilities.supportedWidths
            )
        }
        if (!videoCapabilities.supportedHeights.contains(height)) {
            throw VideoException(
                ("Height not supported after adjustment." +
                        " Desired:" + height +
                        " Range:" + videoCapabilities.supportedHeights)
            )
        }

        // We cannot change the aspect ratio, but the max block count might also be the
        // issue. Try to find a width that contains a height that would accept our AR.
        try {
            if (!videoCapabilities.getSupportedHeightsFor(width).contains(height)) {
                var candidateWidth = width
                val minWidth = videoCapabilities.supportedWidths.lower
                val widthAlignment = videoCapabilities.widthAlignment
                while (candidateWidth >= minWidth) {
                    // Reduce by 32 and realign just in case, then check if our AR is now
                    // supported. If it is, restart from scratch to go through the other checks.
                    candidateWidth -= 32
                    while (candidateWidth % widthAlignment != 0) candidateWidth--
                    val candidateHeight =
                        Math.round(candidateWidth / aspect).toInt()
                    if (videoCapabilities.getSupportedHeightsFor(candidateWidth)
                            .contains(candidateHeight)
                    ) {
                        Log.i(LOG_TAG, "getSupportedVideoSize() - restarting with smaller size.")
                        return getSupportedVideoSize(Size(candidateWidth, candidateHeight))
                    }
                }
            }
        } catch (ignore: IllegalArgumentException) {
        }

        // It's still possible that we're unsupported for other reasons.
        if (!videoCapabilities.isSizeSupported(width, height)) {
            throw VideoException(
                ("Size not supported for unknown reason." +
                        " Might be an aspect ratio issue." +
                        " Desired size:" + Size(width, height))
            )
        }
        return Size(width, height)
    }

    /**
     * Returns a video bit rate supported by the device encoders.
     * This means adjusting the input bit rate if needed, to match encoder constraints.
     *
     * @param bitRate input rate
     * @return adjusted rate
     */
    @SuppressLint("NewApi", "BinaryOperationInTimber")
    fun getSupportedVideoBitRate(bitRate: Int): Int {
        val newBitRate = mVideoCapabilities?.bitrateRange?.clamp(bitRate) ?: bitRate
        Log.i(LOG_TAG, "getSupportedVideoBitRate() inputRate:$bitRate adjustedRate:$newBitRate")
        return newBitRate
    }

    /**
     * Returns a video frame rate supported by the device encoders.
     * This means adjusting the input frame rate if needed, to match encoder constraints.
     *
     * @param frameRate input rate
     * @return adjusted rate
     */
    @SuppressLint("NewApi")
    fun getSupportedVideoFrameRate(size: Size, frameRate: Int): Int {
        val newFrameRate = mVideoCapabilities?.getSupportedFrameRatesFor(size.width, size.height)
            ?.clamp(frameRate.toDouble())
            ?.toInt() ?: frameRate
        Log.i( LOG_TAG,
            "getSupportedVideoFrameRate() inputRate:$frameRate adjustedRate:$newFrameRate"
        )
        return newFrameRate
    }

    /**
     * Returns an audio bit rate supported by the device encoders.
     * This means adjusting the input bit rate if needed, to match encoder constraints.
     *
     * @param bitRate input rate
     * @return adjusted rate
     */
    @SuppressLint("NewApi")
    fun getSupportedAudioBitRate(bitRate: Int): Int {
        val newBitRate = mAudioCapabilities?.bitrateRange?.clamp(bitRate) ?: bitRate
        Log.i(LOG_TAG, "getSupportedAudioBitRate() inputRate:$bitRate adjustedRate:$newBitRate")
        return newBitRate
    }
    // Won't do this for audio sample rate. As far as I remember, the value we're using,
    // 44.1kHz, is guaranteed to be available, and it's not configurable.
    /**
     * Returns the name of the video encoder if we were able to determine one.
     * @return encoder name
     */
    @get:SuppressLint("NewApi")
    val videoCodecName: String?
        get() {
            return mVideoEncoder?.name
        }

    /**
     * Returns the name of the audio encoder if we were able to determine one.
     * @return encoder name
     */
    @get:SuppressLint("NewApi")
    val audioCodecName: String?
        get() {
            return mAudioEncoder?.name
        }

    @SuppressLint("NewApi")
    fun tryConfigureVideo(
        mimeType: String,
        size: Size,
        frameRate: Int,
        bitRate: Int
    ) {
        val videoEncoder = mVideoEncoder
        if (videoEncoder != null) {
            var codec: MediaCodec? = null
            try {
                val format = MediaFormat.createVideoFormat(
                    mimeType,
                    size.width,
                    size.height
                )
                format.setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                )
                format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                codec = MediaCodec.createByCodecName(videoEncoder.name)
                codec.configure(
                    format, null, null,
                    MediaCodec.CONFIGURE_FLAG_ENCODE
                )
            } catch (e: Exception) {
                throw VideoException("Failed to configure video codec: " + e.message)
            } finally {
                if (codec != null) {
                    try {
                        codec.release()
                    } catch (ignore: Exception) {
                    }
                }
            }
        }
    }

    @SuppressLint("NewApi")
    fun tryConfigureAudio(
        mimeType: String,
        bitRate: Int,
        sampleRate: Int,
        channels: Int
    ) {
        val audioEncoder = mAudioEncoder
        if (audioEncoder != null) {
            var codec: MediaCodec? = null
            try {
                val format = MediaFormat.createAudioFormat(
                    mimeType, sampleRate,
                    channels
                )
                val channelMask = if (channels == 2) {
                    AudioFormat.CHANNEL_IN_STEREO
                } else {
                    AudioFormat.CHANNEL_IN_MONO
                }
                format.setInteger(MediaFormat.KEY_CHANNEL_MASK, channelMask)
                format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                codec = MediaCodec.createByCodecName(audioEncoder.name)
                codec.configure(
                    format, null, null,
                    MediaCodec.CONFIGURE_FLAG_ENCODE
                )
            } catch (e: Exception) {
                throw AudioException("Failed to configure video audio: " + e.message)
            } finally {
                if (codec != null) {
                    try {
                        codec.release()
                    } catch (ignore: Exception) {
                    }
                }
            }
        }
    }

    /**
     * Exception thrown when trying to find appropriate values
     * for a video encoder.
     */
    inner class VideoException constructor(message: String) : RuntimeException(message)

    /**
     * Exception thrown when trying to find appropriate values
     * for an audio encoder. Currently never thrown.
     */
    inner class AudioException constructor(message: String) : RuntimeException(message)
}