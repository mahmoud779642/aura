package com.auracam.app.domain.usecase

import android.net.Uri
import com.auracam.app.domain.repository.CameraRepository

class CapturePhotoUseCase(private val cameraRepository: CameraRepository) {
    operator fun invoke(onPhotoSaved: (Uri?) -> Unit) {
        cameraRepository.capturePhoto(onPhotoSaved)
    }
}
