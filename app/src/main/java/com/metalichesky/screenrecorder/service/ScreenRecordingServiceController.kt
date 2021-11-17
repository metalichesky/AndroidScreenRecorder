package com.metalichesky.screenrecorder.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.metalichesky.screenrecorder.model.MediaProjectionParams
import com.metalichesky.screenrecorder.model.ScreenRecordParams
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeout

class ScreenRecordingServiceController(
    var context: Context
) {
    companion object {
        val LOG_TAG = ScreenRecordingServiceController.javaClass.simpleName
        const val SERVICE_STARTING_TIMEOUT_MS = 15_000L
    }

    private var service: ScreenRecordingServiceBridge? = null
    private var serviceConnection: ServiceConnection? = null

    val connected: Boolean
        get() {
            return service != null
        }
    var listener: ScreenRecordingServiceListener? = null
    private var serviceListener = object : ScreenRecordingServiceListener {
        override fun onNeedSetupMediaProjection() {
            listener?.onNeedSetupMediaProjection()
        }

        override fun onNeedSetupMediaRecorder() {
            listener?.onNeedSetupMediaRecorder()
        }

        override fun onRecordingStarted() {
            listener?.onRecordingStarted()
        }

        override fun onRecordingStopped(filePath: String?) {
            listener?.onRecordingStopped(filePath)
        }

        override fun onServiceClosed() {
            service = null
            listener?.onServiceClosed()
        }
    }

    val isRecording: Boolean
        get() = service?.isRecording() ?: false


    suspend fun startService(): Boolean {
        if (connected) return true
        val intent = Intent(context, ScreenRecordingService::class.java).apply {
            action = ScreenRecordingService.ACTION_START_SERVICE
            putExtra(
                ScreenRecordingService.EXTRA_COMMAND_KEY,
                ScreenRecordingService.COMMAND_START_SERVICE
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        try {
            val serviceConnection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    this@ScreenRecordingServiceController.service =
                        (service as? ScreenRecordingService.ServiceBinder)?.service
                    this@ScreenRecordingServiceController.service?.setListener(serviceListener)
                    Log.d(LOG_TAG, "onServiceConnected() name ${name?.className}")
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    this@ScreenRecordingServiceController.service = null
                    Log.d(LOG_TAG, "onServiceDisconnected() name ${name?.className}")
                }
            }
            this.serviceConnection = serviceConnection
            context.bindService(
                Intent(context, ScreenRecordingService::class.java),
                serviceConnection,
                Context.BIND_AUTO_CREATE
            )
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        return try {
            withTimeout(SERVICE_STARTING_TIMEOUT_MS) {
                while (isActive && !connected) {
                    delay(300L)
                }
                return@withTimeout connected
            }
        } catch (ex: Exception) {
            false
        }
    }

    fun stopService(): Boolean {
        if (!connected) return true
        try {
            serviceConnection?.let {
                context.unbindService(it)
            }
            serviceConnection = null
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        service = null
        context.stopService(Intent(context, ScreenRecordingService::class.java))
        return true
    }

    fun isRecorderConfigured(): Boolean {
        return service?.isRecorderConfigured() ?: false
    }

    fun setupRecorder(params: ScreenRecordParams) {
        service?.setupRecorder(params)
    }

    fun isMediaProjectionConfigured(): Boolean {
        return service?.isMediaProjectionConfigured() ?: false
    }

    fun setupMediaProjection(params: MediaProjectionParams) {
        service?.setupMediaProjection(params)
    }

    fun startRecording() {
        service?.startRecording()
    }

    fun stopRecording() {
        service?.stopRecording()
    }
}