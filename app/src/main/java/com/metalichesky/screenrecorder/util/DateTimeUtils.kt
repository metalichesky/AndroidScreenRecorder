package com.metalichesky.screenrecorder.util

import org.threeten.bp.Instant
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit


object DateTimeUtils {
    const val DEFAULT_DATE_TIME_PATTERN = "dd-MM-yyyy HH:mm:ss"
    val DEFAULT_LOCALE = Locale.US

    fun getDateFormatter(pattern: String = DEFAULT_DATE_TIME_PATTERN, locale: Locale = DEFAULT_LOCALE): SimpleDateFormat {
        return SimpleDateFormat(pattern, locale).apply {
            this.timeZone = Calendar.getInstance().timeZone
        }
    }

    fun parseDate(date: String): Calendar {
        val instant = Instant.parse(date)
        return Calendar.getInstance().apply {
            this.timeInMillis = instant.toEpochMilli()
            this.timeZone = TimeZone.getDefault()
        }
    }

    fun formatDateISO8601(date: Calendar): String {
        val instant = Instant.ofEpochMilli(date.timeInMillis)
        return instant.toString()
    }

    fun formatDate(date: Calendar, pattern: String = DEFAULT_DATE_TIME_PATTERN, locale: Locale = DEFAULT_LOCALE): String {
        return getDateFormatter(pattern, locale).format(date.timeInMillis)
    }

    fun getCurrentDate(): Calendar {
        return Calendar.getInstance()
    }

    fun getDate(timeInMillis: Long): Calendar {
        return Calendar.getInstance().apply {
            this.timeInMillis = timeInMillis
            this.timeZone = TimeZone.getDefault()
        }
    }

    fun getTimezone(): Int {
        return TimeUnit.HOURS.convert(
            TimeZone.getDefault().rawOffset.toLong(), TimeUnit.MILLISECONDS
        ).toInt()
    }
}