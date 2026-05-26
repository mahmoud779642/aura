package com.auracam.app.domain.repository

import android.graphics.Bitmap
import android.net.Uri
import java.io.File

interface MediaRepository {
    fun saveBitmapToGallery(bitmap: Bitmap, title: String): Uri?
    fun saveVideoToGallery(videoFile: File): Uri?
    fun getTempVideoFile(): File
}
