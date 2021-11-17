package com.metalichesky.screenrecorder.repo

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.metalichesky.screenrecorder.util.Constants
import com.metalichesky.screenrecorder.util.FileUtils
import java.io.File


class VideoRepo constructor(
    private val context: Context
) {
    companion object {
        const val DEFAULT_VIDEO_MIME_TYPE = FileUtils.MIME_TYPE_MP4
        const val DEFAULT_VIDEO_EXTENSION = FileUtils.EXTENSION_MP4
    }

    fun deleteVideoFile(videoUri: Uri?) {
        FileUtils.deleteFile(context, videoUri)
    }

    fun deleteVideoFile(videoFile: File?) {
        FileUtils.deleteFile(videoFile)
    }

    fun isVideoFileExists(videoUri: Uri?): Boolean {
        return if (videoUri != null) {
            try {
                context.contentResolver.openInputStream(videoUri)
                true
            } catch (e: Exception) {
                false
            }
        } else {
            false
        }
    }

    private fun getTempVideoDir(): File? {
        val cacheDir = context.externalCacheDir ?: return null
        val resultDir = File(cacheDir, Environment.DIRECTORY_MOVIES)
        if (!resultDir.exists()) {
            resultDir.mkdirs()
        }
        return resultDir
    }

    private fun getVideoDir(): File? {
        val resultDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: return null
        if (!resultDir.exists()) {
            resultDir.mkdirs()
        }
        return resultDir
    }

    fun createTempVideoOutputUri(name: String, mimeType: String = DEFAULT_VIDEO_MIME_TYPE): Uri? {
        val extension = FileUtils.getExtensionForMimeType(mimeType) ?: DEFAULT_VIDEO_EXTENSION
        val fileName = "${name}.${extension}"
        val resultFile = File(getTempVideoDir(), fileName)
        val uri =
            FileProvider.getUriForFile(
                context,
                Constants.FILE_PROVIDER_AUTHORITY,
                resultFile
            )
        return uri
    }

    fun createVideoOutputUri(
        name: String,
        mimeType: String = DEFAULT_VIDEO_MIME_TYPE,
        description: String? = null,
        isPublic: Boolean = false
    ): Uri? {
        val extension = FileUtils.getExtensionForMimeType(mimeType) ?: DEFAULT_VIDEO_EXTENSION
        val fileName = "${name}.${extension}"
        val resultFile = File(getVideoDir(), fileName)
        if (isPublic) {
            FileUtils.addVideoFileToPublic(context, resultFile, name, description, mimeType)
        }
        return FileProvider.getUriForFile(
            context,
            Constants.FILE_PROVIDER_AUTHORITY,
            resultFile
        )
    }

    /**
     * Creates Uri to external public directory
     */
    fun createPublicVideoOutputUri(
        name: String,
        mimeType: String = DEFAULT_VIDEO_MIME_TYPE,
        description: String? = null
    ): Uri? {
        val relativePath = Environment.DIRECTORY_MOVIES
        val uri = if (Build.VERSION.SDK_INT >= 29) {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.TITLE, name)
                put(MediaStore.Images.Media.DESCRIPTION, description)
                put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            }
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        } else {
            val path = Environment.getExternalStoragePublicDirectory(relativePath)
            val extension =
                FileUtils.getExtensionForMimeType(mimeType) ?: VideoRepo.DEFAULT_VIDEO_EXTENSION
            val fileName = "${name}.${extension}"
            val resultFile = File(path, fileName)
            FileUtils.addVideoFileToPublic(context, resultFile, name, mimeType)
            FileProvider.getUriForFile(
                context,
                Constants.FILE_PROVIDER_AUTHORITY,
                resultFile
            )
        }
        return uri
    }

    fun createTempVideoOutputFile(name: String, mimeType: String = DEFAULT_VIDEO_MIME_TYPE): File {
        val dir = getTempVideoDir()
        val extension = FileUtils.getExtensionForMimeType(mimeType) ?: DEFAULT_VIDEO_EXTENSION
        val fileName = "${name}.${extension}"
        val file = File(dir, fileName)
        if (!file.exists()) {
            file.createNewFile()
        }
        return file
    }

    fun createVideoOutputFile(
        name: String,
        mimeType: String = DEFAULT_VIDEO_MIME_TYPE,
        description: String? = null,
        isPublic: Boolean = false
    ): File {
        val dir = getVideoDir()
        val extension = FileUtils.getExtensionForMimeType(mimeType) ?: DEFAULT_VIDEO_EXTENSION
        val fileName = "${name}.${extension}"
        val file = File(dir, fileName)
        if (!file.exists()) {
            file.createNewFile()
        }
        if (isPublic) {
            FileUtils.addVideoFileToPublic(context, file, name, description, mimeType)
        }
        return file
    }

    fun getUriForFile(file: File?): Uri? {
        return file?.let {
            FileProvider.getUriForFile(context, Constants.FILE_PROVIDER_AUTHORITY, it)
        }
    }

    fun copyVideoToUri(
        fromUri: Uri?,
        name: String,
        toUri: Uri? = null,
        toTemp: Boolean = false
    ): Uri? {
        fromUri ?: return null
        return try {
            val mimeType = FileUtils.getMimeType(context, fromUri) ?: DEFAULT_VIDEO_MIME_TYPE
            val inputStream = context.contentResolver.openInputStream(fromUri) ?: return null
            val outputUri = if (toUri != null) {
                toUri
            } else if (toTemp) {
                createTempVideoOutputUri(name, mimeType) ?: return null
            } else {
                createVideoOutputUri(name, mimeType) ?: return null
            }
            val outputStream =
                context.contentResolver.openOutputStream(outputUri) ?: return null
            FileUtils.copyStreamToStream(inputStream, outputStream)
            outputUri
        } catch (ex: java.lang.Exception) {
            ex.printStackTrace()
            null
        }
    }

    fun copyVideoToUri(
        fromFile: File?,
        name: String,
        toUri: Uri? = null,
        toTemp: Boolean = false
    ): Uri? {
        fromFile ?: return null
        return try {
            val mimeType = FileUtils.getMimeType(fromFile) ?: DEFAULT_VIDEO_MIME_TYPE
            val inputStream = fromFile.inputStream()
            val outputUri = if (toUri != null) {
                toUri
            } else if (toTemp) {
                createTempVideoOutputUri(name, mimeType) ?: return null
            } else {
                createVideoOutputUri(name, mimeType) ?: return null
            }
            val outputStream =
                context.contentResolver.openOutputStream(outputUri) ?: return null
            FileUtils.copyStreamToStream(inputStream, outputStream)
            outputUri
        } catch (ex: java.lang.Exception) {
            ex.printStackTrace()
            null
        }
    }

    fun copyVideoToFile(
        fromFile: File?,
        name: String,
        toFile: File? = null,
        toTemp: Boolean = false
    ): File? {
        fromFile ?: return null
        return try {
            val mimeType = FileUtils.getMimeType(fromFile) ?: DEFAULT_VIDEO_MIME_TYPE
            val inputStream = fromFile.inputStream()
            val outputFile = if (toFile != null) {
                toFile
            } else if (toTemp) {
                createTempVideoOutputFile(name, mimeType) ?: return null
            } else {
                createVideoOutputFile(name, mimeType) ?: return null
            }
            val outputStream = outputFile.outputStream()
            FileUtils.copyStreamToStream(inputStream, outputStream)
            outputFile
        } catch (ex: java.lang.Exception) {
            ex.printStackTrace()
            null
        }
    }

    fun copyVideoToFile(
        fromUri: Uri?,
        name: String,
        toFile: File? = null,
        toTemp: Boolean = false
    ): File? {
        fromUri ?: return null
        return try {
            val mimeType = FileUtils.getMimeType(context, fromUri) ?: DEFAULT_VIDEO_MIME_TYPE
            val imageInputStream = context.contentResolver.openInputStream(fromUri) ?: return null
            val outputFile = if (toFile != null) {
                toFile
            } else if (toTemp) {
                createTempVideoOutputFile(name, mimeType) ?: return null
            } else {
                createVideoOutputFile(name, mimeType) ?: return null
            }
            val imageOutputStream = outputFile.outputStream()
            FileUtils.copyStreamToStream(imageInputStream, imageOutputStream)
            outputFile
        } catch (ex: java.lang.Exception) {
            ex.printStackTrace()
            null
        }
    }


}