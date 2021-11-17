package com.metalichesky.screenrecorder.model

import com.metalichesky.screenrecorder.model.control.AudioCodec
import com.metalichesky.screenrecorder.model.control.AudioSource
import com.metalichesky.screenrecorder.model.control.VideoCodec
import com.metalichesky.screenrecorder.util.Size
import com.metalichesky.screenrecorder.util.video.DeviceEncoders
import java.io.Serializable

data class ScreenRecordParams(
    val screenSize: Size,
    val screenDensity: Int,
    val videoSize: Size,
    val videoFrameRate: Int = 30,
    val videoCodec: VideoCodec = VideoCodec(VideoCodec.Type.H_264),
    val videoBitRate: Int = DeviceEncoders.estimateVideoBitRate(videoSize, videoFrameRate),
    val videoFilePath: String,
    val audioChannelsCount: Int = 1,
    val audioBitRate: Int = 64000,
    val audioSampleRate: Int = 44100,
    val audioCodec: AudioCodec = AudioCodec(AudioCodec.Type.AMR_NB),
    val audioSource: AudioSource = AudioSource()
) : Serializable