package com.auracam.app.core.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.Image
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer

object ImageUtils {

    /**
     * Packs separate Y, U, and V plane byte buffers from an Android [Image] 
     * into a single contiguous Direct ByteBuffer for fast, zero-copy native JNI access.
     */
    fun packYuvPlanes(image: Image): ByteBuffer {
        val planes = image.planes
        val yBuf = planes[0].buffer
        val uBuf = planes[1].buffer
        val vBuf = planes[2].buffer

        // Rewind to ensure we read full plane contents
        yBuf.rewind()
        uBuf.rewind()
        vBuf.rewind()

        val ySize = yBuf.remaining()
        val uSize = uBuf.remaining()
        val vSize = vBuf.remaining()

        val packedBuffer = ByteBuffer.allocateDirect(ySize + uSize + vSize)
        packedBuffer.put(yBuf)
        packedBuffer.put(uBuf)
        packedBuffer.put(vBuf)
        packedBuffer.rewind()

        return packedBuffer
    }

    /**
     * Saves a processed [Bitmap] into the device's public DCIM/Camera directory 
     * using the modern MediaStore Scoped Storage API to support Android 10+ (API 29+).
     */
    fun saveBitmapToGallery(context: Context, bitmap: Bitmap, title: String): Uri? {
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

            // Write JPEG directly at flagship-quality 98% compression
            bitmap.compress(Bitmap.CompressFormat.JPEG, 98, outputStream)

            contentValues.clear()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            contentResolver.update(uri, contentValues, null, null)
            
            Log.i("ImageUtils", "Successfully saved photo to Gallery: $filename")
        } catch (e: Exception) {
            Log.e("ImageUtils", "Error saving photo: ${e.message}", e)
            // Cleanup on failure
            uri?.let { context.contentResolver.delete(it, null, null) }
            uri = null
        } finally {
            outputStream?.close()
        }

        return uri
    }

    /**
     * Helper to retrieve a temporary file path for direct CameraX Video Capture.
     */
    fun getTempVideoFile(context: Context): File {
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        return File.createTempFile("AuraVideo_${System.currentTimeMillis()}", ".mp4", storageDir)
    }

    /**
     * Saves a recorded video file path to the system gallery.
     */
    fun saveVideoToGallery(context: Context, file: File): Uri? {
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
                    file.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                contentResolver.update(uri, contentValues, null, null)
                Log.i("ImageUtils", "Successfully saved video to Gallery.")
            }
        } catch (e: Exception) {
            Log.e("ImageUtils", "Error saving video: ${e.message}", e)
            uri = null
        }
        return uri
    }
}
