package com.metalichesky.screenrecorder.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.MediaCodecInfo.*
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.metalichesky.screenrecorder.R
import com.metalichesky.screenrecorder.model.MediaProjectionParams
import com.metalichesky.screenrecorder.model.RecordState
import com.metalichesky.screenrecorder.model.ScreenRecordParams
import com.metalichesky.screenrecorder.ui.MainActivity
import com.metalichesky.screenrecorder.util.*
import com.metalichesky.screenrecorder.util.FileUtils
import com.metalichesky.screenrecorder.util.record.ScreenRecordListener
import com.metalichesky.screenrecorder.util.record.ScreenRecordManager
import java.io.File


class ScreenRecordingService : Service(), ScreenRecordingServiceBridge {
    companion object {
        val LOG_TAG = ScreenRecordingService.javaClass.simpleName
        const val CHECK_DEVICE_ENCODERS = true

        const val EXTRA_COMMAND_KEY = "COMMAND_KEY"
        const val ACTION_START_SERVICE = "ACTION_START_SERVICE"
        const val ACTION_SETUP_RECORDER = "ACTION_SETUP_RECORDER"
        const val ACTION_SETUP_MEDIA_PROJECTION = "ACTION_SETUP_MEDIA_PROJECTION"
        const val ACTION_START_RECORDING = "ACTION_START_RECORDING"
        const val ACTION_STOP_RECORDING = "ACTION_STOP_RECORDING"
        const val ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE"
        const val COMMAND_SETUP_RECORDER = 0
        const val COMMAND_SETUP_MEDIA_PROJECTION = 1
        const val COMMAND_START_RECORDING = 2
        const val COMMAND_STOP_RECORDING = 3
        const val COMMAND_START_SERVICE = 4
        const val COMMAND_STOP_SERVICE = 5
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

    private lateinit var recorder: ScreenRecordManager

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun setListener(listener: ScreenRecordingServiceListener?) {
        this.listener = listener
    }

    override fun onCreate() {
        Log.d(LOG_TAG, "onCreate()")
        recorder = ScreenRecordManager(this, object: ScreenRecordListener {
            override fun onRecordStarted() {
                this@ScreenRecordingService.listener?.onRecordingStarted()
            }

            override fun onRecordStopped(filePath: String?) {
                this@ScreenRecordingService.listener?.onRecordingStopped(filePath)
            }

            override fun onRecordStateChanged(state: RecordState) {
                updateServiceNotification(this@ScreenRecordingService)
            }

            override fun onNeedSetupMediaProjection() {
                this@ScreenRecordingService.listener?.onNeedSetupMediaProjection()
            }

            override fun onNeedSetupMediaRecorder() {
                this@ScreenRecordingService.listener?.onNeedSetupMediaRecorder()
            }
        })
    }

    private fun startService(startId: Int) {
        Log.d(LOG_TAG, "startService() startId = $startId")
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
        updateServiceNotification(applicationContext)
    }

    fun stopService() {
        val closeIntent = Intent(this, ScreenRecordingService::class.java)
        closeIntent.action = ACTION_STOP_SERVICE
        this.startActivity(closeIntent)
    }

    override fun stopService(name: Intent?): Boolean {
        recorder.stopRecording(true)
        closeServiceNotification(this)
        listener?.onServiceClosed()
        return super.stopService(name)
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (intent.action == null) {
            return START_STICKY
        }
        val action = intent.action
        Log.d(LOG_TAG, "onStartCommand() action:$action")
        when (action) {
            ACTION_START_SERVICE -> {
                startService(startId)
            }
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
                recorder.stopRecording(destroyMediaProjection)
            }
            ACTION_STOP_SERVICE -> {
                recorder.stopRecording(true)
                closeServiceNotification(this)
                stopForeground(true)
                stopSelfResult(startId)
                listener?.onServiceClosed()
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
        when (recorder.recordState) {
            RecordState.IDLE -> {
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
            RecordState.PREPARED -> {
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
            RecordState.RECORDING -> {
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
        return recorder.isConfigured()
    }

    override fun setupRecorder(params: ScreenRecordParams) {
        recorder.setup(params)
    }

    override fun isMediaProjectionConfigured(): Boolean {
        return recorder.isMediaProjectionConfigured()
    }

    override fun setupMediaProjection(params: MediaProjectionParams) {
        recorder.setupMediaProjection(params)
    }

    override fun isRecording(): Boolean {
        return recorder.isRecording()
    }

    override fun startRecording() {
        recorder.startRecording()
    }

    override fun stopRecording() {
        recorder.stopRecording()
    }
}