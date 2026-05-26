package com.auracam.app.domain.usecase

import android.graphics.Bitmap
import android.net.Uri
import com.auracam.app.domain.repository.MediaRepository
import java.io.File

class SaveMediaUseCase(private val mediaRepository: MediaRepository) {
    fun savePhoto(bitmap: Bitmap, title: String): Uri? {
        return mediaRepository.saveBitmapToGallery(bitmap, title)
    }

    fun saveVideo(videoFile: File): Uri? {
        return mediaRepository.saveVideoToGallery(videoFile)
    }

    fun createTempFile(): File {
        return mediaRepository.getTempVideoFile()
    }
}
