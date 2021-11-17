package com.metalichesky.screenrecorder.util

import android.annotation.TargetApi
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.graphics.Color
import android.os.Build
import androidx.annotation.RequiresApi
import java.lang.reflect.InvocationTargetException

object NotificationUtils {
    private const val ID_MAX = Int.MAX_VALUE
    const val CHANNEL_DEFAULT_ID = "channel_id_default"
    const val CHANNEL_DEFAULT_NAME = "ScreenRecorder"
    const val CHANNEL_DEFAULT_DESCRIPTION = "Default notification channel"
    val defaultVibrationPattern = longArrayOf(300L, 300L, 300L)
    val silenceVibrationPattern = longArrayOf(0L)
    const val defaultVibrationDuration = 300L
    const val silenceVibrationDuration = 300L
    const val DEFAULTS_NONE = 0

    @RequiresApi(Build.VERSION_CODES.O)
    fun getChannelDefault(): NotificationChannel {
        val channel = NotificationChannel(
            CHANNEL_DEFAULT_ID,
            CHANNEL_DEFAULT_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        )
        channel.description = CHANNEL_DEFAULT_DESCRIPTION
        channel.setShowBadge(true)
        channel.canShowBadge()
        channel.enableLights(true)
        channel.lightColor = Color.BLUE
        channel.enableVibration(true)
        channel.vibrationPattern = defaultVibrationPattern
        return channel
    }

    fun generateNotificationId(): Int {
        return System.currentTimeMillis().rem(ID_MAX).toInt()
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private fun jbNotificationExtras(
        priority: Int,
        nbuilder: Notification.Builder
    ) {
        try {
            if (priority != 0) {
                val setpriority = nbuilder.javaClass.getMethod(
                    "setPriority",
                    Int::class.javaPrimitiveType
                )
                setpriority.invoke(nbuilder, priority)
                val setUsesChronometer = nbuilder.javaClass.getMethod(
                    "setUsesChronometer",
                    Boolean::class.javaPrimitiveType
                )
                setUsesChronometer.invoke(nbuilder, true)
            }
        } catch (e: NoSuchMethodException) {
            //ignore exception
        } catch (e: IllegalArgumentException) {
        } catch (e: InvocationTargetException) {
        } catch (e: IllegalAccessException) {
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun lpNotificationExtras(
        nbuilder: Notification.Builder,
        category: String
    ) {
        nbuilder.setCategory(category)
        nbuilder.setLocalOnly(true)
    }
}