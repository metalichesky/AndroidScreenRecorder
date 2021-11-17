package com.metalichesky.screenrecorder.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodecInfo.*
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.metalichesky.screenrecorder.R
import com.metalichesky.screenrecorder.model.MediaProjectionParams
import com.metalichesky.screenrecorder.model.RecordingState
import com.metalichesky.screenrecorder.model.ScreenRecordParams
import com.metalichesky.screenrecorder.model.control.AudioCodec
import com.metalichesky.screenrecorder.model.control.AudioSource
import com.metalichesky.screenrecorder.model.control.VideoCodec
import com.metalichesky.screenrecorder.ui.MainActivity
import com.metalichesky.screenrecorder.util.Constants
import com.metalichesky.screenrecorder.util.FileUtils
import com.metalichesky.screenrecorder.util.NotificationUtils
import com.metalichesky.screenrecorder.util.Size
import com.metalichesky.screenrecorder.util.video.DeviceEncoders
import java.io.File
import java.io.IOException


class ScreenRecordingService : Service(), ScreenRecordingServiceBridge {
    companion object {
        val LOG_TAG = ScreenRecordingService.javaClass.simpleName
        const val CHECK_DEVICE_ENCODERS = true

        const val ACTION_SETUP_RECORDER = "ACTION_SETUP_RECORDER"
        const val ACTION_SETUP_MEDIA_PROJECTION = "ACTION_SETUP_MEDIA_PROJECTION"
        const val ACTION_START_RECORDING = "ACTION_START_RECORDING"
        const val ACTION_STOP_RECORDING = "ACTION_STOP_RECORDING"
        const val ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE"
        const val COMMAND_SETUP_RECORDER = 0
        const val COMMAND_SETUP_MEDIA_PROJECTION = 1
        const val COMMAND_START_RECORDING = 2
        const val COMMAND_STOP_RECORDING = 3
        const val COMMAND_STOP_SERVICE = 4
        const val ATTR_RECORD_PARAMS = "screen_record_params"
        const val ATTR_MEDIA_PROJECTION_PARAMS = "media_projection_params"
        const val ATTR_DESTROY_MEDIA_PROJECTION = "destroy_media_projection"
    }

    inner class ServiceBinder : Binder() {
        internal val service: ScreenRecordingService
            get() = this@ScreenRecordingService
    }

    private var notificationId: Int = 0
    private val binder = ServiceBinder()
    private var listener: ScreenRecordingServiceListener? = null

    private var recordingState: RecordingState = RecordingState.IDLE
    private var recordParams: ScreenRecordParams? = null
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

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun setListener(listener: ScreenRecordingServiceListener?) {
        this.listener = listener
    }

    override fun onCreate() {
        Log.d(LOG_TAG, "onCreate()")
        startAsForeground()
        projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    private fun startAsForeground() {
        notificationId = NotificationUtils.generateNotificationId()
        val startIntent = Intent(this, ScreenRecordingService::class.java)
        startIntent.action = ACTION_START_RECORDING
        val startPendingIntent = PendingIntent.getService(
            this, COMMAND_START_RECORDING, startIntent, 0
        )
        val closeIntent = Intent(this, ScreenRecordingService::class.java)
        closeIntent.action = ACTION_STOP_SERVICE
        val closePendingIntent = PendingIntent.getService(
            this, COMMAND_STOP_SERVICE, closeIntent, 0
        )
        startForeground(
            notificationId,
            createServiceNotificationBuilder(applicationContext)?.addAction(
                R.drawable.ic_stop,
                getString(R.string.stop_record),
                startPendingIntent
            )?.addAction(
                R.drawable.ic_close,
                getString(R.string.close),
                closePendingIntent
            )?.build()
        )
    }

    fun stopService() {
        val closeIntent = Intent(this, ScreenRecordingService::class.java)
        closeIntent.action = ACTION_STOP_SERVICE
        this.startActivity(closeIntent)
    }

    override fun stopService(name: Intent?): Boolean {
        stopRecording(true)
        closeServiceNotification(this)
        return super.stopService(name)
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (intent.action == null) {
            return START_STICKY
        }
        val action = intent.action
        Log.d(LOG_TAG, "onStartCommand() action:$action")
        when (action) {
            ACTION_SETUP_RECORDER -> {
                val params = intent.getSerializableExtra(ATTR_RECORD_PARAMS) as ScreenRecordParams
                setupRecorder(params)
            }
            ACTION_SETUP_MEDIA_PROJECTION -> {
                val params =
                    intent.getSerializableExtra(ATTR_MEDIA_PROJECTION_PARAMS) as MediaProjectionParams
                setupMediaProjection(params)
            }
            ACTION_START_RECORDING -> {
                startRecording()
            }
            ACTION_STOP_RECORDING -> {
                val destroyMediaProjection =
                    intent.getBooleanExtra(ATTR_DESTROY_MEDIA_PROJECTION, false)
                stopRecording(destroyMediaProjection)
            }
            ACTION_STOP_SERVICE -> {
                stopRecording(true)
                closeServiceNotification(this)
                stopForeground(true)
                stopSelfResult(startId)
            }
        }
        return START_STICKY
    }


    private fun createServiceNotificationBuilder(context: Context?): NotificationCompat.Builder? {
        context ?: return null
        val intent = Intent(context, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        val openAppIntent = PendingIntent.getActivity(
            context,
            Constants.REQUEST_OPEN_APP,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notificationBuilder =
            NotificationCompat.Builder(context, NotificationUtils.CHANNEL_DEFAULT_ID)
                .setContentTitle(getString(R.string.service_running))
                .setContentText(getString(R.string.start_record_or_close_service))
                .setContentIntent(openAppIntent)
                .setAutoCancel(true)
                .setColor(ContextCompat.getColor(context, R.color.portGore))
                .setSmallIcon(R.drawable.ic_notification)
                .setDefaults(NotificationCompat.DEFAULT_LIGHTS)
                .setVibrate(NotificationUtils.silenceVibrationPattern)
                .setSilent(true)
//                .setDefaults(Notification.DEFAULT_VIBRATE)
//                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
//                .setVibrate(NotificationUtils.defaultVibrationPattern)

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(NotificationUtils.getChannelDefault())
        }
        return notificationBuilder
    }

    private fun updateServiceNotification(
        context: Context
    ) {
        val startIntent = Intent(this, ScreenRecordingService::class.java)
        startIntent.action = ACTION_START_RECORDING
        val startPendingIntent = PendingIntent.getService(
            context, COMMAND_START_RECORDING, startIntent, 0
        )
        val stopIntent = Intent(this, ScreenRecordingService::class.java)
        stopIntent.action = ACTION_STOP_RECORDING
        val stopPendingIntent = PendingIntent.getService(
            context, COMMAND_STOP_RECORDING, stopIntent, 0
        )
        val closeIntent = Intent(this, ScreenRecordingService::class.java)
        closeIntent.action = ACTION_STOP_SERVICE
        val closePendingIntent = PendingIntent.getService(
            context, COMMAND_STOP_SERVICE, closeIntent, 0
        )
        when (recordingState) {
            RecordingState.IDLE -> {
                val contentTitle = getString(R.string.service_running)
                val contentText = getString(R.string.start_record_or_close_service)
                createServiceNotificationBuilder(context)?.let { notificationBuilder ->
                    notificationBuilder.setContentTitle(contentTitle)
                    notificationBuilder.setContentText(contentText)
                    val notificationManager =
                        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.notify(notificationId, notificationBuilder.build())

                    notificationBuilder.addAction(
                        R.drawable.ic_record,
                        getString(R.string.start_record),
                        startPendingIntent
                    )
                    notificationBuilder.addAction(
                        R.drawable.ic_close,
                        getString(R.string.close),
                        closePendingIntent
                    )
                }
            }
            RecordingState.PREPARED -> {
                val contentTitle = getString(R.string.service_running)
                val contentText = getString(R.string.start_record_or_close_service)
                createServiceNotificationBuilder(context)?.let { notificationBuilder ->
                    notificationBuilder.setContentTitle(contentTitle)
                    notificationBuilder.setContentText(contentText)
                    notificationBuilder.addAction(
                        R.drawable.ic_record,
                        getString(R.string.start_record),
                        startPendingIntent
                    )
                    notificationBuilder.addAction(
                        R.drawable.ic_close,
                        getString(R.string.close),
                        closePendingIntent
                    )
                    val notificationManager =
                        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.notify(notificationId, notificationBuilder.build())
                }
            }
            RecordingState.RECORDING -> {
                val contentTitle = getString(R.string.service_running)
                val contentText = getString(R.string.recording)
                createServiceNotificationBuilder(context)?.let { notificationBuilder ->
                    notificationBuilder.setContentTitle(contentTitle)
                    notificationBuilder.setContentText(contentText)
                    notificationBuilder.addAction(
                        R.drawable.ic_stop,
                        getString(R.string.stop_record),
                        stopPendingIntent
                    )
                    notificationBuilder.addAction(
                        R.drawable.ic_close,
                        getString(R.string.close),
                        closePendingIntent
                    )
                    val notificationManager =
                        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.notify(notificationId, notificationBuilder.build())
                }
            }
        }
    }

    private fun closeServiceNotification(context: Context) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)
    }

    override fun isRecorderConfigured(): Boolean {
        return mediaRecorder != null && recordParams != null
    }

    override fun setupRecorder(params: ScreenRecordParams) {
        this.recordParams = params
        Log.d(LOG_TAG, "setupRecorder() params: ${params}")
        try {
            destroyRecorder()

            val mediaRecorder = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                MediaRecorder()
            } else {
                MediaRecorder(this)
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

            if (CHECK_DEVICE_ENCODERS) {
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
                        LOG_TAG, "setupRecorder() Checking DeviceEncoders... " +
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
                            LOG_TAG, "setupRecorder() Could not respect encoders parameters. " +
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
                            LOG_TAG,
                            "setupRecorder() Got VideoException ${videoException.message}"
                        )
                        videoEncoderOffset++
                    } catch (audioException: DeviceEncoders.AudioException) {
                        Log.i(
                            LOG_TAG,
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
            recordingState = RecordingState.PREPARED
            updateServiceNotification(this)
        } catch (e: IllegalStateException) {
            Log.e(LOG_TAG, e.message, e)
            destroyRecorder()
        } catch (e: IOException) {
            Log.e(LOG_TAG, e.message, e)
            destroyRecorder()
        } catch (e: Exception) {
            Log.e(LOG_TAG, e.message, e)
            destroyRecorder()
        }
    }

    override fun isMediaProjectionConfigured(): Boolean {
        return mediaProjection != null
    }

    override fun setupMediaProjection(params: MediaProjectionParams) {
        mediaProjection = projectionManager?.getMediaProjection(params.resultCode, params.data)
        mediaProjection?.registerCallback(mediaProjectionCallback, null)
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

    override fun isRecording(): Boolean {
        return recordingState == RecordingState.RECORDING
    }

    override fun startRecording() {
        if (mediaProjection == null) {
            listener?.onNeedSetupMediaProjection()
            return
        }
        if (mediaRecorder == null) {
            val recordParams = recordParams
            if (recordParams != null) {
                setupRecorder(recordParams)
            } else {
                listener?.onNeedSetupMediaRecorder()
                return
            }
        }
        if (virtualDisplay == null) {
            createVirtualDisplay(recordParams)
        }
        mediaRecorder?.start()
        recordingState = RecordingState.RECORDING
        listener?.onRecordingStarted()
        updateServiceNotification(this)
    }

    override fun stopRecording() {
        stopRecording(false)
    }

    private fun stopRecording(destroyMediaProjection: Boolean = false) {
        Log.d(LOG_TAG, "stopSharing()")
        try {
            mediaRecorder?.stop()
            recordingState = RecordingState.IDLE
            val resultFile = File(recordParams?.videoFilePath ?: "")
            val mimeType = recordParams?.videoCodec?.type?.mimeType
            FileUtils.addVideoFileToPublic(
                context = this,
                file = resultFile,
                mimeType = mimeType
            )
            listener?.onRecordingStopped(recordParams?.videoFilePath)
            updateServiceNotification(this)
            destroyVirtualDisplay()
            destroyRecorder()
            if (destroyMediaProjection) {
                destroyMediaProjection()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun destroyMediaProjection() {
        Log.d(LOG_TAG, "destroyMediaProjection()")
        mediaProjection?.unregisterCallback(mediaProjectionCallback)
        mediaProjection?.stop()
        mediaProjection = null
    }

    private fun destroyVirtualDisplay() {
        virtualDisplay?.release()
        virtualDisplay = null
    }

    private fun destroyRecorder() {
        mediaRecorder?.release()
        mediaRecorder = null
    }
}