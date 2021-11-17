package com.metalichesky.screenrecorder.util

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings


object IntentUtils {
    const val PACKAGE_WEBVIEW = "com.google.android.webview"
    const val PACKAGE_CHROME = "com.android.chrome"

    fun isPackageExists(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (ex: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun isActivityExists(context: Context, intent: Intent): Boolean {
        return try {
            intent.resolveActivity(context.packageManager) != null
        } catch (ex: Exception) {
            ex.printStackTrace()
            false
        }
    }

    fun getIntentOpenBrowser(uri: Uri): Intent {
        val browserIntent = Intent(Intent.ACTION_VIEW)
        browserIntent.setData(uri)
        return browserIntent
    }

    fun getGalleryIntent(): Intent {
        val galleryIntent = Intent(Intent.ACTION_PICK)
        galleryIntent.type = "image/*"
        return galleryIntent
    }

    fun getCameraIntent(outputUri: Uri?): Intent {
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, outputUri)
        return cameraIntent
    }

    fun getScreenCaptureIntent(context: Context): Intent {
        val projectionManager = context.getSystemService(Service.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        return projectionManager.createScreenCaptureIntent()
    }

    fun getSecuritySettingsIntent(): Intent {
        return Intent(Settings.ACTION_SECURITY_SETTINGS)
    }

    fun getSettingsIntent(): Intent {
        return Intent(Settings.ACTION_SETTINGS)
    }
}