package com.auracam.app.domain.model

import android.net.Uri

enum class CameraMode {
    PHOTO,
    HDR,
    PORTRAIT,
    NIGHT,
    VIDEO
}

enum class LensFacing {
    BACK,
    FRONT
}

enum class FlashMode {
    OFF,
    ON,
    AUTO
}

data class CameraUIState(
    val activeMode: CameraMode = CameraMode.PHOTO,
    val lensFacing: LensFacing = LensFacing.BACK,
    val flashMode: FlashMode = FlashMode.OFF,
    val zoomRatio: Float = 1.0f,
    val minZoom: Float = 1.0f,
    val maxZoom: Float = 8.0f,
    val isRecordingVideo: Boolean = false,
    val isProcessing: Boolean = false,
    val processingMessage: String = "",
    val error: String? = null
)

data class MediaItem(
    val uri: Uri,
    val displayName: String,
    val dateTaken: Long,
    val isVideo: Boolean
)
