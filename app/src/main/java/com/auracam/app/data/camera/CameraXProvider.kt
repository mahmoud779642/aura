package com.auracam.app.data.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.auracam.app.domain.model.CameraMode
import com.auracam.app.domain.model.CameraUIState
import com.auracam.app.domain.model.LensFacing
import com.auracam.app.domain.repository.CameraRepository
import com.auracam.app.domain.repository.MediaRepository
import com.auracam.app.domain.repository.ThermalRepository
import com.auracam.app.domain.repository.ThermalThrottlingLevel
import com.auracam.app.processing.ImageProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraXProvider(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val thermalRepository: ThermalRepository,
    private val mediaRepository: MediaRepository,
    private val imageProcessor: ImageProcessor
) : CameraRepository {

    private val _uiState = MutableStateFlow(CameraUIState())
    override val uiState: StateFlow<CameraUIState> = _uiState.asStateFlow()

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var currentRecording: Recording? = null

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var analysisThread: HandlerThread? = null
    private var analysisHandler: Handler? = null

    private val maxFrameBufferCapacity = 5
    private val yuvFrameRingBuffer = java.util.Collections.synchronizedList(mutableListOf<ImageProxy>())

    init {
        setupExecutorThreads()
        initializeCameraX()
    }

    private fun setupExecutorThreads() {
        analysisThread = HandlerThread("AuraAnalysisThread").apply { start() }
        analysisHandler = Handler(analysisThread!!.looper)
    }

    private fun initializeCameraX() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
            } catch (e: Exception) {
                Log.e("CameraXProvider", "CameraX initialization failed", e)
                _uiState.update { it.copy(error = "Unable to initialize camera system.") }
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private var activePreviewView: PreviewView? = null

    override fun bindViewfinder(previewView: Any) {
        if (previewView is PreviewView) {
            activePreviewView = previewView
            bindCameraUseCases()
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: return
        val currentLens = if (_uiState.value.lensFacing == LensFacing.BACK) {
            CameraSelector.LENS_FACING_BACK
        } else {
            CameraSelector.LENS_FACING_FRONT
        }

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(currentLens)
            .build()

        preview = Preview.Builder()
            .setTargetResolution(Size(1920, 1080))
            .build()

        activePreviewView?.let {
            preview?.setSurfaceProvider(it.surfaceProvider)
        }

        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.FHD))
            .build()
        videoCapture = VideoCapture.withOutput(recorder)

        imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(1920, 1080))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()

        imageAnalysis?.setAnalyzer(cameraExecutor) { imageProxy ->
            synchronized(yuvFrameRingBuffer) {
                if (yuvFrameRingBuffer.size >= maxFrameBufferCapacity) {
                    yuvFrameRingBuffer.removeAt(0).close()
                }
                yuvFrameRingBuffer.add(imageProxy)
            }
        }

        try {
            cameraProvider.unbindAll()

            camera = if (_uiState.value.activeMode == CameraMode.VIDEO) {
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    videoCapture
                )
            } else {
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
            }

            observeCameraControlInfo()
        } catch (e: Exception) {
            Log.e("CameraXProvider", "Binding use cases failed", e)
            _uiState.update { it.copy(error = "Camera bounds failure on One UI.") }
        }
    }

    private fun observeCameraControlInfo() {
        camera?.cameraInfo?.let { info ->
            info.zoomState.observe(lifecycleOwner) { zoom ->
                _uiState.update {
                    it.copy(
                        zoomRatio = zoom.zoomRatio,
                        minZoom = zoom.minZoomRatio,
                        maxZoom = zoom.maxZoomRatio
                    )
                }
            }
        }
    }

    override fun setZoom(ratio: Float) {
        camera?.cameraControl?.setZoomRatio(ratio)
    }

    override fun setCameraMode(mode: CameraMode) {
        if (_uiState.value.activeMode == mode) return
        _uiState.update { it.copy(activeMode = mode) }
        bindCameraUseCases()
    }

    override fun toggleLens() {
        val newLens = if (_uiState.value.lensFacing == LensFacing.BACK) {
            LensFacing.FRONT
        } else {
            LensFacing.BACK
        }
        _uiState.update { it.copy(lensFacing = newLens, zoomRatio = 1.0f) }
        bindCameraUseCases()
    }

    override fun capturePhoto(onPhotoSaved: (Uri?) -> Unit) {
        if (_uiState.value.isProcessing) return

        _uiState.update { it.copy(isProcessing = true, processingMessage = "Capturing burst...") }

        val targetFrameCount = when (thermalRepository.throttlingLevel.value) {
            ThermalThrottlingLevel.NORMAL -> when (_uiState.value.activeMode) {
                CameraMode.HDR -> 5
                CameraMode.NIGHT -> 5
                CameraMode.PORTRAIT -> 3
                else -> 3
            }
            ThermalThrottlingLevel.MODERATE -> 3
            ThermalThrottlingLevel.SEVERE -> 1
            ThermalThrottlingLevel.CRITICAL -> 1
        }

        CoroutineScope(Dispatchers.Default).launch {
            val framesToProcess = mutableListOf<ImageProxy>()
            synchronized(yuvFrameRingBuffer) {
                val available = yuvFrameRingBuffer.takeLast(targetFrameCount)
                for (frame in available) {
                    framesToProcess.add(frame)
                }
                yuvFrameRingBuffer.clear()
            }

            if (framesToProcess.isEmpty()) {
                Log.w("CameraXProvider", "Circular buffer empty. Bypassing processing.")
                _uiState.update { it.copy(isProcessing = false) }
                onPhotoSaved(null)
                return@launch
            }

            _uiState.update { it.copy(processingMessage = "Stacking & preserving texture details...") }

            val isNight = _uiState.value.activeMode == CameraMode.NIGHT
            val isPortrait = _uiState.value.activeMode == CameraMode.PORTRAIT

            try {
                val resultBitmap: Bitmap? = imageProcessor.processYuvBurst(
                    frames = framesToProcess,
                    isNightMode = isNight,
                    isPortraitMode = isPortrait
                )

                if (resultBitmap != null) {
                    _uiState.update { it.copy(processingMessage = "Preserving photo to gallery...") }
                    
                    val uri = mediaRepository.saveBitmapToGallery(
                        bitmap = resultBitmap,
                        title = when (_uiState.value.activeMode) {
                            CameraMode.HDR -> "AuraHDR"
                            CameraMode.NIGHT -> "AuraNight"
                            CameraMode.PORTRAIT -> "AuraPortrait"
                            else -> "AuraPhoto"
                        }
                    )
                    
                    resultBitmap.recycle()
                    onPhotoSaved(uri)
                } else {
                    onPhotoSaved(null)
                }
            } catch (e: Exception) {
                Log.e("CameraXProvider", "Error during burst stack processing", e)
            } finally {
                for (frame in framesToProcess) {
                    frame.close()
                }
                _uiState.update { it.copy(isProcessing = false) }
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun startVideoRecording(onVideoSaved: (Uri?) -> Unit) {
        val videoCapture = videoCapture ?: return
        val tempFile = mediaRepository.getTempVideoFile()
        val fileOutputOptions = FileOutputOptions.Builder(tempFile).build()

        _uiState.update { it.copy(isRecordingVideo = true) }

        currentRecording = videoCapture.output
            .prepareRecording(context, fileOutputOptions)
            .withAudioEnabled()
            .start(ContextCompat.getMainExecutor(context)) { event ->
                when (event) {
                    is VideoRecordEvent.Finalize -> {
                        _uiState.update { it.copy(isRecordingVideo = false) }
                        if (!event.hasError()) {
                            CoroutineScope(Dispatchers.IO).launch {
                                val uri = mediaRepository.saveVideoToGallery(tempFile)
                                tempFile.delete()
                                onVideoSaved(uri)
                            }
                        } else {
                            Log.e("CameraXProvider", "Video recording failed: ${event.error}")
                            tempFile.delete()
                            onVideoSaved(null)
                        }
                    }
                }
            }
    }

    override fun stopVideoRecording() {
        currentRecording?.stop()
        currentRecording = null
    }

    override fun shutdown() {
        cameraExecutor.shutdown()
        analysisThread?.quitSafely()
        synchronized(yuvFrameRingBuffer) {
            yuvFrameRingBuffer.forEach { it.close() }
            yuvFrameRingBuffer.clear()
        }
    }
}
