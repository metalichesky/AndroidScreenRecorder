package com.metalichesky.screenrecorder.service

interface ScreenRecordingServiceListener {

    fun onRecordingStarted()
    fun onRecordingStopped(filePath: String?)
    fun onNeedSetupMediaProjection()
    fun onNeedSetupMediaRecorder()
    fun onServiceClosed()
}