package com.metalichesky.screenrecorder.util.record

import com.metalichesky.screenrecorder.model.RecordState

interface ScreenRecordListener {
    fun onRecordStarted()
    fun onRecordStopped(filePath: String?)
    fun onRecordStateChanged(state: RecordState)
    fun onNeedSetupMediaProjection()
    fun onNeedSetupMediaRecorder()
}