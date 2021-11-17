package com.metalichesky.screenrecorder.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import android.widget.ToggleButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.metalichesky.screenrecorder.databinding.ActivityMainBinding
import com.metalichesky.screenrecorder.model.MediaProjectionParams
import com.metalichesky.screenrecorder.model.ScreenRecordParams
import com.metalichesky.screenrecorder.repo.VideoRepo
import com.metalichesky.screenrecorder.service.ScreenRecordingServiceController
import com.metalichesky.screenrecorder.service.ScreenRecordingServiceListener
import com.metalichesky.screenrecorder.util.FileUtils
import com.metalichesky.screenrecorder.util.IntentUtils
import com.metalichesky.screenrecorder.util.PermissionUtils
import com.metalichesky.screenrecorder.util.Size
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    companion object {
        private const val LOG_TAG = "MainActivity"
    }

    private lateinit var binding: ActivityMainBinding

    private lateinit var serviceController: ScreenRecordingServiceController
    private lateinit var videoRepo: VideoRepo

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (PermissionUtils.isPermissionsGranted(result)) {
            tryStartRecording()
        }
    }

    private val recordScreenLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { activityResult ->
        if (activityResult.resultCode == RESULT_OK) {
            tryStartRecording(activityResult.resultCode, activityResult.data)
        } else {
            Toast.makeText(
                this,
                "Screen Cast Permission Denied", Toast.LENGTH_SHORT
            ).show()
            binding.toggle.isChecked = false
        }
    }

    private val currentSysDate: String
        get() = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater, null, false)
        setContentView(binding.root)

        setupViews()
        setupService()
    }

    private fun setupViews() {
        binding.toggle.setOnClickListener { v -> onToggleScreenShare(v) }
    }

    private fun setupService() {
        videoRepo = VideoRepo(applicationContext)
        serviceController = ScreenRecordingServiceController(applicationContext)
        serviceController.listener = object: ScreenRecordingServiceListener {
            override fun onRecordingStarted() {
                binding.toggle.isChecked = true
            }

            override fun onRecordingStopped(filePath: String?) {
                binding.toggle.isChecked = false
                Log.d(LOG_TAG, "onRecordingStopped() saved to ${filePath}")
            }

            override fun onNeedSetupMediaProjection() {
                recordScreenLauncher.launch(IntentUtils.getScreenCaptureIntent(this@MainActivity))
            }

            override fun onNeedSetupMediaRecorder() {
                serviceController.setupRecorder(prepareRecordParams())
            }

            override fun onServiceClosed() {

            }
        }
        lifecycleScope.launch {
            binding.toggle.isEnabled = false
            while(isActive && !serviceController.connected) {
                serviceController.startService()
            }
            binding.toggle.isEnabled = serviceController.connected
            serviceController.setupRecorder(prepareRecordParams())
        }
    }

    private fun prepareRecordParams(): ScreenRecordParams {
        val screenSize = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val windowMetrics = windowManager.currentWindowMetrics
            Size(windowMetrics.bounds.width(), windowMetrics.bounds.height())
        } else {
            val displayMetrics = resources.displayMetrics
            Size(displayMetrics.widthPixels, displayMetrics.heightPixels)
        }
        val screenDensity = resources.configuration.densityDpi
        val recordWidth = screenSize.width / 2
        val recordHeight = screenSize.height / 2
        val recordSize = Size(recordWidth, recordHeight)
        val videoFile = videoRepo.createVideoOutputFile(currentSysDate)
        return ScreenRecordParams(
            screenSize = screenSize,
            screenDensity = screenDensity,
            videoSize = recordSize,
            videoFilePath = videoFile.absolutePath
        )
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun onToggleScreenShare(view: View) {
        if ((view as ToggleButton).isChecked) {
            tryStartRecording()
        } else {
            stopRecording()
        }
    }

    private fun tryStartRecording() {
        val permissions = PermissionUtils.RECORD_AUDIO_PERMISSIONS + PermissionUtils.READ_WRITE_PERMISSIONS
        val permissionsGranted = PermissionUtils.isPermissionsGranted(this, permissions)
        val mediaProjectionConfigured = serviceController.isMediaProjectionConfigured()
        val recorderConfigured = serviceController.isRecorderConfigured()
        if (permissionsGranted && !recorderConfigured) {
            serviceController.setupRecorder(prepareRecordParams())
        }
        if (permissionsGranted && mediaProjectionConfigured) {
            startRecording()
        } else if (!permissionsGranted) {
            permissionsLauncher.launch(permissions)
        } else if (!mediaProjectionConfigured) {
            recordScreenLauncher.launch(IntentUtils.getScreenCaptureIntent(this))
        }
    }

    private fun tryStartRecording(resultCode: Int, data: Intent?) {
        serviceController.setupMediaProjection(MediaProjectionParams(
            resultCode = resultCode,
            data = data ?: Intent()
        ))
        tryStartRecording()
    }

    private fun startRecording() {
        serviceController.startRecording()
    }

    private fun stopRecording() {
        serviceController.stopRecording()
    }
}
