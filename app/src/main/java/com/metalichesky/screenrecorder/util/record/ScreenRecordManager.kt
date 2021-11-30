package com.metalichesky.screenrecorder.util.record

import android.app.Service
import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.util.Log
import com.metalichesky.screenrecorder.model.MediaProjectionParams
import com.metalichesky.screenrecorder.model.RecordState
import com.metalichesky.screenrecorder.model.ScreenRecordParams
import com.metalichesky.screenrecorder.model.control.AudioCodec
import com.metalichesky.screenrecorder.model.control.AudioSource
import com.metalichesky.screenrecorder.model.control.VideoCodec
import com.metalichesky.screenrecorder.service.ScreenRecordingService
import com.metalichesky.screenrecorder.util.FileUtils
import com.metalichesky.screenrecorder.util.Size
import com.metalichesky.screenrecorder.util.video.DeviceEncoders
import java.io.File
import java.io.IOException

class ScreenRecordManager(
    val context: Context,
    var listener: ScreenRecordListener?
) {
    var recordState: RecordState = RecordState.IDLE
        private set
    private var recordParams: ScreenRecordParams? = null
        private set

    private var mediaRecorder: MediaRecorder? = null
    private var projectionManager: MediaProjectionManager? = null
    private var mediaProjection: MediaProjection? = null
    private var mediaProjectionCallback: MediaProjection.Callback =
        object : MediaProjection.Callback() {
            override fun onStop() {
                super.onStop()
                stopRecording(false)
            }
        }
    private var virtualDisplay: VirtualDisplay? = null

    init {
        projectionManager =
            context.getSystemService(Service.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    fun isRecording(): Boolean {
        return recordState == RecordState.RECORDING
    }

    private fun setRecordState(newState: RecordState) {
        val oldState = recordState
        recordState = newState
        if (oldState != newState) {
            listener?.onRecordStateChanged(newState)
        }
    }

    fun isMediaProjectionConfigured(): Boolean {
        return mediaProjection != null
    }

    fun setupMediaProjection(params: MediaProjectionParams) {
        mediaProjection = projectionManager?.getMediaProjection(params.resultCode, params.data)
        mediaProjection?.registerCallback(mediaProjectionCallback, null)
    }

    fun isConfigured(): Boolean {
        return mediaRecorder != null && recordParams != null
    }

    fun setup(params: ScreenRecordParams) {
        this.recordParams = params
        Log.d(ScreenRecordingService.LOG_TAG, "setupRecorder() params: ${params}")
        try {
            destroyRecorder()

            val mediaRecorder = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                MediaRecorder()
            } else {
                MediaRecorder(context)
            }
            this.mediaRecorder = mediaRecorder

            var videoFrameRate = if (params.videoFrameRate <= 0) {
                30
            } else {
                params.videoFrameRate
            }
            var videoSize = params.videoSize
            var videoBitRate = if (params.videoBitRate <= 0) {
                DeviceEncoders.estimateVideoBitRate(videoSize, videoFrameRate)
            } else {
                params.videoBitRate
            }
            val videoCodec = when (params.videoCodec.type) {
                VideoCodec.Type.H_263 -> MediaRecorder.VideoEncoder.H263
                VideoCodec.Type.H_264 -> MediaRecorder.VideoEncoder.H264
                VideoCodec.Type.HEVC -> MediaRecorder.VideoEncoder.HEVC
                VideoCodec.Type.VP8 -> MediaRecorder.VideoEncoder.VP8
                VideoCodec.Type.MPEG_4_SP -> MediaRecorder.VideoEncoder.MPEG_4_SP
                else -> MediaRecorder.VideoEncoder.H264
            }
            val fileFormat: Int = when (params.videoCodec.type) {
                VideoCodec.Type.H_263 -> MediaRecorder.OutputFormat.MPEG_4
                VideoCodec.Type.H_264 -> MediaRecorder.OutputFormat.MPEG_4
                VideoCodec.Type.HEVC -> MediaRecorder.OutputFormat.MPEG_4
                VideoCodec.Type.VP8 -> MediaRecorder.OutputFormat.WEBM
                VideoCodec.Type.MPEG_4_SP -> MediaRecorder.OutputFormat.MPEG_4
                else -> MediaRecorder.OutputFormat.DEFAULT
            }

            var audioBitRate = params.audioBitRate
            val hasAudio =
                params.audioChannelsCount > 0 && params.audioSource.type != AudioSource.Type.NONE
            val audioCodec = when (params.audioCodec.type) {
                AudioCodec.Type.AAC -> MediaRecorder.AudioEncoder.AAC
                AudioCodec.Type.AAC_ELD -> MediaRecorder.AudioEncoder.AAC_ELD
                AudioCodec.Type.HE_AAC -> MediaRecorder.AudioEncoder.HE_AAC
                AudioCodec.Type.VORBIS -> MediaRecorder.AudioEncoder.VORBIS
                AudioCodec.Type.AMR_WB -> MediaRecorder.AudioEncoder.AMR_WB
                AudioCodec.Type.AMR_NB -> MediaRecorder.AudioEncoder.AMR_NB
                else -> MediaRecorder.AudioEncoder.DEFAULT
            }
            val audioSampleRate = params.audioSampleRate

            if (ScreenRecordingService.CHECK_DEVICE_ENCODERS) {
                // A. Get the audio mime type
                // https://android.googlesource.com/platform/frameworks/av/+/master/media/libmediaplayerservice/StagefrightRecorder.cpp#1096
                // https://github.com/MrAlex94/Waterfox-Old/blob/master/media/libstagefright/frameworks/av/media/libstagefright/MediaDefs.cpp
                val audioMimeType = params.audioCodec.type.mimeType ?: ""
                // B. Get the video mime type
                // https://android.googlesource.com/platform/frameworks/av/+/master/media/libmediaplayerservice/StagefrightRecorder.cpp#1650
                // https://github.com/MrAlex94/Waterfox-Old/blob/master/media/libstagefright/frameworks/av/media/libstagefright/MediaDefs.cpp
                val videoMimeType = params.videoCodec.type.mimeType ?: ""

                var newVideoSize: Size = videoSize
                var newVideoBitRate = 0
                var newAudioBitRate = 0
                var newVideoFrameRate = 0
                var videoEncoderOffset = 0
                var audioEncoderOffset = 0
                var encodersFound = false

                while (!encodersFound) {
                    Log.d(
                        ScreenRecordingService.LOG_TAG,
                        "setupRecorder() Checking DeviceEncoders... " +
                                "videoOffset:$videoEncoderOffset " +
                                "audioOffset:$audioEncoderOffset"
                    )
                    var encoders: DeviceEncoders? = null
                    try {
                        encoders = DeviceEncoders(
                            DeviceEncoders.MODE_RESPECT_ORDER,
                            videoMimeType, audioMimeType,
                            videoEncoderOffset, audioEncoderOffset
                        )
                    } catch (e: RuntimeException) {
                        Log.w(
                            ScreenRecordingService.LOG_TAG,
                            "setupRecorder() Could not respect encoders parameters. " +
                                    "Trying again without checking encoders."
                        )
                        break
                    }
                    try {
                        newVideoSize = encoders.getSupportedVideoSize(params.videoSize)
                        newVideoBitRate = encoders.getSupportedVideoBitRate(params.videoBitRate)
                        newVideoFrameRate = encoders.getSupportedVideoFrameRate(
                            newVideoSize,
                            params.videoFrameRate
                        )
                        encoders.tryConfigureVideo(
                            videoMimeType, newVideoSize,
                            newVideoFrameRate, newVideoBitRate
                        )
                        if (hasAudio) {
                            newAudioBitRate = encoders.getSupportedAudioBitRate(params.audioBitRate)
                            encoders.tryConfigureAudio(
                                audioMimeType, newAudioBitRate,
                                params.audioSampleRate, params.audioChannelsCount
                            )
                        }
                        encodersFound = true
                    } catch (videoException: DeviceEncoders.VideoException) {
                        Log.i(
                            ScreenRecordingService.LOG_TAG,
                            "setupRecorder() Got VideoException ${videoException.message}"
                        )
                        videoEncoderOffset++
                    } catch (audioException: DeviceEncoders.AudioException) {
                        Log.i(
                            ScreenRecordingService.LOG_TAG,
                            "setupRecorder() Got AudioException: ${audioException.message}"
                        )
                        audioEncoderOffset++
                    }
                }
                if (encodersFound) {
                    videoSize = newVideoSize
                    videoBitRate = newVideoBitRate
                    audioBitRate = newAudioBitRate
                    videoFrameRate = newVideoFrameRate
                }
            }
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            if (hasAudio) {
                mediaRecorder.setAudioSource(
                    when (params.audioSource.type) {
                        AudioSource.Type.MIC -> MediaRecorder.AudioSource.MIC
                        else -> MediaRecorder.AudioSource.DEFAULT
                    }
                )
            }
            mediaRecorder.setOutputFormat(fileFormat)
            if (hasAudio) {
                mediaRecorder.setAudioEncoder(audioCodec)
                mediaRecorder.setAudioSamplingRate(audioSampleRate)
                mediaRecorder.setAudioEncodingBitRate(audioBitRate)
            }
            mediaRecorder.setVideoEncoder(videoCodec)
            mediaRecorder.setVideoEncodingBitRate(videoBitRate)
            mediaRecorder.setVideoFrameRate(videoFrameRate)
            mediaRecorder.setVideoSize(videoSize.width, videoSize.height)
            mediaRecorder.setOutputFile(params.videoFilePath)
            mediaRecorder.prepare()
            createVirtualDisplay(params)
            setRecordState(RecordState.PREPARED)
        } catch (e: IllegalStateException) {
            Log.e(ScreenRecordingService.LOG_TAG, e.message, e)
            destroyRecorder()
        } catch (e: IOException) {
            Log.e(ScreenRecordingService.LOG_TAG, e.message, e)
            destroyRecorder()
        } catch (e: Exception) {
            Log.e(ScreenRecordingService.LOG_TAG, e.message, e)
            destroyRecorder()
        }
    }

    private fun createVirtualDisplay(recordParams: ScreenRecordParams?): VirtualDisplay? {
        recordParams ?: return null
        destroyVirtualDisplay()
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenRecordingService",
            recordParams.videoSize.width,
            recordParams.videoSize.height,
            recordParams.screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            mediaRecorder?.surface,
            null /*Callbacks*/,
            null /*Handler*/
        )
        return virtualDisplay
    }

    fun startRecording() {
        if (mediaProjection == null) {
            listener?.onNeedSetupMediaProjection()
            return
        }
        if (mediaRecorder == null) {
            val recordParams = recordParams
            if (recordParams != null) {
                setup(recordParams)
            } else {
                listener?.onNeedSetupMediaRecorder()
                return
            }
        }
        if (virtualDisplay == null) {
            createVirtualDisplay(recordParams)
        }
        mediaRecorder?.start()
        listener?.onRecordStarted()
        setRecordState(RecordState.RECORDING)
    }

    fun stopRecording(destroyMediaProjection: Boolean = false) {
        Log.d(ScreenRecordingService.LOG_TAG, "stopSharing()")
        try {
            try {
                mediaRecorder?.stop()
            } catch (ex: Exception) {
                // ignore
            }
            val resultFile = File(recordParams?.videoFilePath ?: "")
            val mimeType = recordParams?.videoCodec?.type?.mimeType
            FileUtils.addVideoFileToPublic(
                context = context,
                file = resultFile,
                mimeType = mimeType
            )
            listener?.onRecordStopped(recordParams?.videoFilePath)
            setRecordState(RecordState.IDLE)
            destroyVirtualDisplay()
            destroyRecorder()
            if (destroyMediaProjection) {
                destroyMediaProjection()
            }
        } catch (ex: Exception) {
            Log.e(ScreenRecordingService.LOG_TAG, ex.message, ex)
        }
    }

    private fun destroyMediaProjection() {
        Log.d(ScreenRecordingService.LOG_TAG, "destroyMediaProjection()")
        mediaProjection?.unregisterCallback(mediaProjectionCallback)
        mediaProjection?.stop()
        mediaProjection = null
    }

    private fun destroyVirtualDisplay() {
        virtualDisplay?.release()
        virtualDisplay = null
    }

    private fun destroyRecorder() {
        try {
            mediaRecorder?.release()
        } catch (ex: Exception) {
            Log.e(ScreenRecordingService.LOG_TAG, ex.message, ex)
        }
        mediaRecorder = null
    }


}