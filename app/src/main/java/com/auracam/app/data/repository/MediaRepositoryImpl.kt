package com.auracam.app.data.repository

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.auracam.app.domain.repository.MediaRepository
import java.io.File
import java.io.OutputStream

class MediaRepositoryImpl(private val context: Context) : MediaRepository {

    override fun saveBitmapToGallery(bitmap: Bitmap, title: String): Uri? {
        val filename = "${title}_${System.currentTimeMillis()}.jpg"
        var outputStream: OutputStream? = null
        var uri: Uri? = null

        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/Camera")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val contentResolver = context.contentResolver
            val imageCollection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            uri = contentResolver.insert(imageCollection, contentValues)
            if (uri == null) throw Exception("Failed to create MediaStore entry.")

            outputStream = contentResolver.openOutputStream(uri)
            if (outputStream == null) throw Exception("Failed to open output stream.")

            // Mirrorless-grade 98% compression to prevent jpeg macro-blocking artifacts
            bitmap.compress(Bitmap.CompressFormat.JPEG, 98, outputStream)

            contentValues.clear()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            contentResolver.update(uri, contentValues, null, null)
            
            Log.i("MediaRepositoryImpl", "Successfully saved: $filename")
        } catch (e: Exception) {
            Log.e("MediaRepositoryImpl", "Failed saving photo: ${e.message}", e)
            uri?.let { context.contentResolver.delete(it, null, null) }
            uri = null
        } finally {
            outputStream?.close()
        }

        return uri
    }

    override fun saveVideoToGallery(videoFile: File): Uri? {
        val filename = "AuraVideo_${System.currentTimeMillis()}.mp4"
        var uri: Uri? = null
        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/Camera")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val contentResolver = context.contentResolver
            val videoCollection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

            uri = contentResolver.insert(videoCollection, contentValues)
            if (uri != null) {
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    videoFile.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                contentResolver.update(uri, contentValues, null, null)
                Log.i("MediaRepositoryImpl", "Successfully saved video to MediaStore.")
            }
        } catch (e: Exception) {
            Log.e("MediaRepositoryImpl", "Failed saving video: ${e.message}", e)
            uri = null
        }
        return uri
    }

    override fun getTempVideoFile(): File {
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        return File.createTempFile("AuraVideo_${System.currentTimeMillis()}", ".mp4", storageDir)
    }
}
