package com.metalichesky.screenrecorder.ui

import android.app.Activity.RESULT_OK
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
import com.metalichesky.screenrecorder.util.*
import kotlinx.coroutines.delay
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
    private var recordRequested: Boolean = false

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (PermissionUtils.isPermissionsGranted(result) && recordRequested) {
            tryStartRecording()
        }
    }

    private val recordScreenLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { activityResult ->
        if (activityResult.resultCode == RESULT_OK) {
            serviceController.setupMediaProjection(
                MediaProjectionParams(
                    resultCode = activityResult.resultCode,
                    data = activityResult.data ?: Intent()
                )
            )
            if (recordRequested) {
                tryStartRecording()
            }
        } else {
            Toast.makeText(
                this,
                "Screen Cast Permission Denied", Toast.LENGTH_SHORT
            ).show()
            binding.toggle.isChecked = false
        }
    }

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
        serviceController.listener = object : ScreenRecordingServiceListener {
            override fun onRecordingStarted() {
                binding.toggle.isChecked = true
            }

            override fun onRecordingStopped(filePath: String?) {
                binding.toggle.isChecked = false
                Log.d(LOG_TAG, "onRecordingStopped() saved to ${filePath}")
                // set next video record params
                prepareRecordParams()?.let {
                    serviceController.setupRecorder(it)
                }
            }

            override fun onNeedSetupMediaProjection() {
                recordScreenLauncher.launch(IntentUtils.getScreenCaptureIntent(this@MainActivity))
            }

            override fun onNeedSetupMediaRecorder() {
                prepareRecordParams()?.let {
                    serviceController.setupRecorder(it)
                }
            }

            override fun onServiceClosed() {

            }
        }
        lifecycleScope.launch {
            binding.toggle.isEnabled = serviceController.connected
            while (isActive && !serviceController.connected) {
                serviceController.startService()
            }
            binding.toggle.isEnabled = serviceController.connected
            if (serviceController.connected) {
                prepareRecordParams()?.let {
                    serviceController.setupRecorder(it)
                }
            }
        }
    }

    private fun prepareRecordParams(): ScreenRecordParams? {
        val permissionsGranted = PermissionUtils.isPermissionsGranted(
            this@MainActivity,
            PermissionUtils.READ_WRITE_PERMISSIONS
        )
        return if (permissionsGranted) {
            // read write external storage available
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
            val currentSysDate = DateTimeUtils.formatDate(
                DateTimeUtils.getCurrentDate(),
                FileUtils.FILE_TIMESTAMP_DATE_PATTERN
            )
            val videoFile = videoRepo.createVideoOutputFile(currentSysDate)
            ScreenRecordParams(
                screenSize = screenSize,
                screenDensity = screenDensity,
                videoSize = recordSize,
                videoFilePath = videoFile.absolutePath
            )
        } else {
            null
        }
    }

    override fun onResume() {
        super.onResume()
        maybeRequestPermissions()
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
        recordRequested = true
        lifecycleScope.launch {
            binding.toggle.isChecked = serviceController.connected
            while (isActive && !serviceController.connected) {
                serviceController.startService()
            }
            binding.toggle.isChecked = serviceController.connected
            if (!isActive) return@launch

            val permissions =
                PermissionUtils.RECORD_AUDIO_PERMISSIONS + PermissionUtils.READ_WRITE_PERMISSIONS
            val permissionsGranted =
                PermissionUtils.isPermissionsGranted(this@MainActivity, permissions)
            val mediaProjectionConfigured = serviceController.isMediaProjectionConfigured()
            val recorderConfigured = serviceController.isRecorderConfigured()
            if (permissionsGranted && !recorderConfigured) {
                prepareRecordParams()?.let {
                    serviceController.setupRecorder(it)
                }
            }
            if (permissionsGranted && mediaProjectionConfigured) {
                startRecording()
            } else if (!permissionsGranted) {
                maybeRequestPermissions()
            } else if (!mediaProjectionConfigured) {
                recordScreenLauncher.launch(IntentUtils.getScreenCaptureIntent(this@MainActivity))
            }
        }
    }

    private fun maybeRequestPermissions() {
        val permissions =
            PermissionUtils.RECORD_AUDIO_PERMISSIONS + PermissionUtils.READ_WRITE_PERMISSIONS
        val permissionsGranted =
            PermissionUtils.isPermissionsGranted(this@MainActivity, permissions)
        if (!permissionsGranted) {
            permissionsLauncher.launch(permissions)
        }
    }

    private fun startRecording() {
        serviceController.startRecording()
    }

    private fun stopRecording() {
        recordRequested = false
        serviceController.stopRecording()
    }
}
