package com.metalichesky.screenrecorder.util

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.ActivityCompat


object PermissionUtils {
    private const val DEFAULT_REQUEST_CODE = 21586
    val READ_PERMISSIONS = arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE
    )
    val READ_WRITE_PERMISSIONS = arrayOf(
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE
    )
    val CAMERA_PERMISSIONS = arrayOf(
        Manifest.permission.CAMERA
    )
    val RECORD_AUDIO_PERMISSIONS = arrayOf(
        Manifest.permission.RECORD_AUDIO
    )

    fun isPermissionsGranted(permissions: Map<String, Boolean>): Boolean {
        var granted = true
        for (permission in permissions) {
            granted = granted && permission.value
            if (!granted) break
        }
        return granted
    }

    fun isPermissionGranted(activity: Activity, permission: String): Boolean {
        val result = if (Build.VERSION.SDK_INT >= 23) {
            activity.checkSelfPermission(permission)
        } else {
            ActivityCompat.checkSelfPermission(activity, permission)
        }
        return result == PackageManager.PERMISSION_GRANTED
    }

    fun isPermissionsGranted(activity: Activity, permissions: Array<String>): Boolean {
        var permissionsGranted = true
        for (permission in permissions) {
            permissionsGranted = permissionsGranted && isPermissionGranted(activity, permission)
            if (!permissionsGranted) {
                break
            }
        }
        PackageManager.PERMISSION_DENIED
        return permissionsGranted
    }

    fun filterNonGrantedPermissions(activity: Activity, permissions: Array<String>): List<String> {
        val nonGrantedPermissions = mutableListOf<String>()
        for (permission in permissions) {
            val result = if (Build.VERSION.SDK_INT >= 23) {
                activity.checkSelfPermission(permission)
            } else {
                ActivityCompat.checkSelfPermission(activity, permission)
            }
            if (result != PackageManager.PERMISSION_GRANTED) {
                nonGrantedPermissions.add(permission)
            }
        }
        return nonGrantedPermissions
    }

    fun shouldShowRequestPermissionRationale(
        activity: Activity,
        permissions: Array<String>
    ): Boolean {
        var shouldShow = false
        for (permission in permissions) {
            val result = if (Build.VERSION.SDK_INT >= 23) {
                activity.shouldShowRequestPermissionRationale(permission)
            } else {
                ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
            }
            shouldShow = shouldShow || result
            if (result) {
                break
            }
        }
        return shouldShow
    }

    fun requestPermissions(
        activity: Activity,
        permissions: Array<String>,
        requestCode: Int = DEFAULT_REQUEST_CODE
    ) {
        if (Build.VERSION.SDK_INT >= 23) {
            activity.requestPermissions(permissions, requestCode)
        } else {
            return
        }
    }

    fun requestPermissions(
        launcher: ActivityResultLauncher<Array<out String>>,
        permissions: Array<String>
    ) {
        if (Build.VERSION.SDK_INT >= 23) {
            launcher.launch(permissions)
        } else {
            return
        }
    }
}