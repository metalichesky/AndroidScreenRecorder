package com.metalichesky.screenrecorder.util

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import android.webkit.MimeTypeMap
import java.io.*
import java.util.*

internal object FileUtils {
    const val EXTENSION_MP4 = "mp4"
    const val EXTENSION_3GP = "3gp"
    const val EXTENSION_WEBM = "webm"
    const val EXTENSION_JPG = "jpg"
    const val EXTENSION_PNG = "png"
    const val EXTENSION_DNG = "png"

    const val MIME_TYPE_MP4 = "video/mp4"
    const val MIME_TYPE_3GP = "video/3gpp"
    const val MIME_TYPE_WEBM = "video/webm"
    const val MIME_TYPE_JPG = "image/jpeg"
    const val MIME_TYPE_PNG = "image/png"
    const val MIME_TYPE_DNG = "image/DNG"

    val DEFAULT_VIDEO_MIME_TYPES = listOf(
        MIME_TYPE_MP4, MIME_TYPE_3GP, MIME_TYPE_WEBM
    )
    const val DEFAULT_COPY_BUFFER_SIZE = 4 * 1024

    const val FILE_TIMESTAMP_DATE_PATTERN = "yyyyMMdd_HHmmss_SS"

    fun isVideoSupportedDefault(mimeType: String): Boolean {
        return DEFAULT_VIDEO_MIME_TYPES.contains(mimeType)
    }

    fun getExtension(path: String?): String? {
        return if (path != null) {
            MimeTypeMap.getFileExtensionFromUrl(path)?.lowercase()?.replace(".", "")
        } else {
            null
        }
    }

    fun getExtensionForMimeType(mimeType: String): String? {
        return MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)?.replace(".", "")
    }

    fun getMimeType(path: String?): String? {
        val extension = getExtension(path)
        return if (extension != null) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        } else {
            null
        }
    }

    fun getMimeType(context: Context, uri: Uri?): String? {
        uri ?: return null
        return if (ContentResolver.SCHEME_CONTENT == uri.scheme) {
            context.contentResolver.getType(uri)
        } else {
            getMimeType(uri.toString())
        }
    }

    fun getMimeType(file: File?): String? {
        file ?: return null
        return getMimeType(file.absolutePath)
    }

    fun getLastModified(file: File?): Long {
        file ?: return 0L
        return file.lastModified()
    }

    @SuppressLint("Range")
    fun getLastModified(context: Context, uri: Uri?): Long {
        uri ?: return 0L
        var lastModified = 0L
        val projection = arrayOf(MediaStore.MediaColumns.DATE_MODIFIED)
        try {
            context.contentResolver.query(uri, projection, null, null, null, null)?.use { cursor ->
                lastModified = cursor.getLong(cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED))
            }
        } catch (ex: Exception) {

        }
        return lastModified
    }

    fun addImageFileToPublic(
        context: Context,
        file: File,
        title: String? = null,
        description: String? = null,
        mimeType: String? = null
    ): Uri? {
        val values = ContentValues()
        values.put(MediaStore.Images.Media.TITLE, title)
        values.put(MediaStore.Images.Media.DESCRIPTION, description)
        if (Build.VERSION.SDK_INT >= 29) {
            values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
        }
        values.put(MediaStore.Images.Media.MIME_TYPE, mimeType)
        values.put(MediaStore.MediaColumns.DATA, file.absolutePath)
        return context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    }

    fun addVideoFileToPublic(
        context: Context,
        file: File,
        title: String? = null,
        description: String? = null,
        mimeType: String? = null
    ): Uri? {
        val values = ContentValues()
        values.put(MediaStore.Video.Media.TITLE, title)
        values.put(MediaStore.Video.Media.DESCRIPTION, description)
        if (Build.VERSION.SDK_INT >= 29) {
            values.put(MediaStore.Video.Media.DATE_TAKEN, file.lastModified())
        }
        values.put(MediaStore.Video.Media.MIME_TYPE, mimeType)
        values.put(MediaStore.MediaColumns.DATA, file.absolutePath)
        return context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
    }

    fun addUriToPublic(
        context: Context,
        uri: Uri,
        title: String? = null,
        description: String? = null,
        mimeType: String? = null
    ): Uri? {
        val values = ContentValues()
        values.put(MediaStore.Images.Media.TITLE, title)
        values.put(MediaStore.Images.Media.DESCRIPTION, description)
        if (Build.VERSION.SDK_INT >= 29) {
            values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
        }
        values.put(MediaStore.Images.Media.MIME_TYPE, mimeType)
        return context.contentResolver.insert(uri, values)
    }

    fun getVideoRotation(filePath: String?): Int? {
        filePath ?: return null
        var mediaMetadataRetriever: MediaMetadataRetriever? = null
        return try {
            mediaMetadataRetriever = MediaMetadataRetriever()
            mediaMetadataRetriever.setDataSource(filePath)
            val orientation =
                mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                    ?: return null
            Integer.valueOf(orientation)
        } catch (e: IllegalArgumentException) {
            0
        } catch (e: RuntimeException) {
            0
        } catch (e: Exception) {
            0
        } finally {
            try {
                mediaMetadataRetriever?.release()
            } catch (e: RuntimeException) {
                e
            }
        }
    }

    fun getVideoDuration(filePath: String?): Long? {
        filePath ?: return null
        var mediaMetadataRetriever: MediaMetadataRetriever? = null
        return try {
            mediaMetadataRetriever = MediaMetadataRetriever()
            mediaMetadataRetriever.setDataSource(filePath)
            val duration =
                mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?: return null
            duration.toLong()
        } catch (e: IllegalArgumentException) {
            0
        } catch (e: RuntimeException) {
            0
        } catch (e: Exception) {
            0
        } finally {
            try {
                mediaMetadataRetriever?.release()
            } catch (e: RuntimeException) {
                e
            }
        }
    }

    fun getVideoResolution(filePath: String?): Size? {
        filePath ?: return null
        var retriever: MediaMetadataRetriever? = null
        return try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(filePath)
            val rawWidth =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val rawHeight =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            if (rawWidth == null || rawHeight == null) {
                return null
            }
            val width = rawWidth.toInt()
            val height = rawHeight.toInt()
            Size(width, height)
        } catch (e: IllegalArgumentException) {
            null
        } catch (e: RuntimeException) {
            null
        } catch (e: Exception) {
            null
        } finally {
            try {
                retriever?.release()
            } catch (e: RuntimeException) {
                e
            }
        }
    }

    fun isFileExistsAndNotEmpty(fileInputStream: InputStream?): Boolean {
        val notEmpty = fileInputStream?.available() != null && fileInputStream.available() > 0
        return notEmpty
    }

    fun isFileExistsAndNotEmpty(file: File?): Boolean {
        var existsNotEmpty = false
        if (file?.exists() == true) {
            file.inputStream().use {
                existsNotEmpty = isFileExistsAndNotEmpty(it)
            }
        }
        return existsNotEmpty
    }

    fun isFileExistsAndNotEmpty(context: Context?, uri: Uri?): Boolean {
        var existsNotEmpty = false
        openInputStream(context, uri)?.use {
            existsNotEmpty = isFileExistsAndNotEmpty(it)
        }
        return existsNotEmpty
    }

    fun openInputStream(context: Context?, fileUri: Uri?): InputStream? {
        return try {
            fileUri ?: return null
            context?.contentResolver?.openInputStream(fileUri)
        } catch (ex: Exception) {
            null
        }
    }

    fun openInputStream(filePath: String?): InputStream? {
        return try {
            filePath ?: return null
            FileInputStream(File(filePath))
        } catch (ex: Exception) {
            null
        }
    }

    fun openOutputStream(context: Context?, fileUri: Uri?): OutputStream? {
        return try {
            fileUri ?: return null
            context?.contentResolver?.openOutputStream(fileUri)
        } catch (ex: Exception) {
            null
        }
    }

    fun openOutputStream(filePath: String?): OutputStream? {
        return try {
            filePath ?: return null
            FileOutputStream(File(filePath))
        } catch (ex: Exception) {
            null
        }
    }

    fun closeStream(stream: InputStream): Boolean {
        return try {
            stream.close()
            true
        } catch (ex: Exception) {
            ex.printStackTrace()
            false
        }
    }

    fun closeStream(stream: OutputStream): Boolean {
        return try {
            stream.close()
            true
        } catch (ex: Exception) {
            ex.printStackTrace()
            false
        }
    }

    fun deleteDir(file: File?) {
        file ?: return
        if (file.exists() && file.isDirectory) {
            try {
                file.deleteRecursively()
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    fun deleteFile(file: File?) {
        file ?: return
        if (file.exists() && file.isFile) {
            try {
                file.delete()
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    fun deleteFile(context: Context, fileUri: Uri?) {
        val imagePath = fileUri?.path ?: return
        val file = File(imagePath)
        if (file.exists() && file.isFile) {
            try {
                file.delete()
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        } else {
            try {
                val contentResolver: ContentResolver = context.contentResolver
                contentResolver.delete(fileUri, null, null)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    fun deleteFile(path: String?) {
        path ?: return
        val file = File(path)
        deleteFile(file)
    }

    fun copyByteArrayToStream(
        inputByteArray: ByteArray,
        outputStream: OutputStream,
        listener: CopyStreamListener? = null
    ) {
        outputStream.use { output ->
            val byteCount = inputByteArray.size
            output.write(inputByteArray, 0, byteCount)
            listener?.onDataCopied(byteCount.toLong(), byteCount.toLong())
            output.flush()
        }
    }


    fun copyStreamToStream(
        inputStream: InputStream,
        outputStream: OutputStream,
        bufferSize: Int = DEFAULT_COPY_BUFFER_SIZE,
        listener: CopyStreamListener? = null
    ) {
        inputStream.use { input ->
            outputStream.use { output ->
                val buffer = ByteArray(bufferSize)
                var allSize = inputStream.available().toLong()
                var copiedSize = 0L
                while (true) {
                    val byteCount = input.read(buffer)
                    if (byteCount < 0) break
                    output.write(buffer, 0, byteCount)
                    copiedSize += byteCount
                    if (copiedSize > allSize) {
                        // when input stream returns incorrect file size, we should correct it
                        allSize = copiedSize
                    }
                    listener?.onDataCopied(copiedSize, allSize)
                }
                output.flush()
            }
        }
    }

    interface CopyStreamListener {
        fun onDataCopied(copiedBytes: Long, allBytes: Long)
    }
}

fun String.addExtension(extension: String): String {
    return "${this.trimEnd('.')}.${extension}"
}