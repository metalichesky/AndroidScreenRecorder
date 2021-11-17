package com.metalichesky.screenrecorder.service

import com.metalichesky.screenrecorder.model.MediaProjectionParams
import com.metalichesky.screenrecorder.model.ScreenRecordParams


interface ScreenRecordingServiceBridge {
    fun setListener(listener: ScreenRecordingServiceListener?)

    fun setupRecorder(params: ScreenRecordParams)
    fun setupMediaProjection(params: MediaProjectionParams)
    fun isMediaProjectionConfigured(): Boolean
    fun isRecorderConfigured(): Boolean

    fun startRecording()
    fun stopRecording()
    fun isRecording(): Boolean
}