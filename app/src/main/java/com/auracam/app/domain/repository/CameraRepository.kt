package com.auracam.app.domain.repository

import android.net.Uri
import com.auracam.app.domain.model.CameraMode
import com.auracam.app.domain.model.CameraUIState
import kotlinx.coroutines.flow.StateFlow

interface CameraRepository {
    val uiState: StateFlow<CameraUIState>

    fun bindViewfinder(previewView: Any)
    fun setZoom(ratio: Float)
    fun setCameraMode(mode: CameraMode)
    fun toggleLens()
    fun capturePhoto(onPhotoSaved: (Uri?) -> Unit)
    fun startVideoRecording(onVideoSaved: (Uri?) -> Unit)
    fun stopVideoRecording()
    fun shutdown()
}
