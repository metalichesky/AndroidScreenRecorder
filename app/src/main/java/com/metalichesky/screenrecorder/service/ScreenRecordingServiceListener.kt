package com.metalichesky.screenrecorder.service

interface ScreenRecordingServiceListener {

    fun onRecordingStarted()
    fun onRecordingStopped()
    fun onNeedSetupMediaProjection()
    fun onNeedSetupMediaRecorder()
    fun onServiceClosed()
}