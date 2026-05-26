package com.auracam.app.processing

import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageProxy
import com.auracam.app.core.util.ImageUtils
import com.auracam.app.processing.ai.PortraitDepthEstimator

class ImageProcessor(private val depthEstimator: PortraitDepthEstimator) {

    /**
     * Entry point for computational capture pipelines.
     * Stacks multiple aligned frames, recovers dynamic range, and applies progressive bokeh depth filters.
     */
    fun processYuvBurst(
        frames: List<ImageProxy>,
        isNightMode: Boolean,
        isPortraitMode: Boolean
    ): Bitmap? {
        if (frames.isEmpty()) return null

        val refFrame = frames[0]
        val width = refFrame.width
        val height = refFrame.height

        val yRowStrides = IntArray(frames.size)
        val uvRowStrides = IntArray(frames.size)
        val uvPixelStrides = IntArray(frames.size)
        
        val byteBuffers = Array(frames.size) { i ->
            val frame = frames[i]
            val planes = frame.planes
            yRowStrides[i] = planes[0].rowStride
            uvRowStrides[i] = planes[1].rowStride
            uvPixelStrides[i] = planes[1].pixelStride
            ImageUtils.packYuvPlanes(frame)
        }

        // Allocate the target ARGB bitmap
        val stackedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        // Dispatch frame averaging, alignment and color matrices to NDK
        val success = NativeBridge.nProcessBurst(
            yuvFrames = byteBuffers,
            width = width,
            height = height,
            yRowStrides = yRowStrides,
            uvRowStrides = uvRowStrides,
            uvPixelStrides = uvPixelStrides,
            outBitmap = stackedBitmap,
            evCorrection = if (isNightMode) 0.5f else 0.0f,
            isNightMode = isNightMode
        )

        if (!success) {
            Log.e("ImageProcessor", "Native burst stacking failed.")
            stackedBitmap.recycle()
            return null
        }

        // Run progressive bokeh depth filters in C++ guided by AI segmentation map
        if (isPortraitMode) {
            Log.i("ImageProcessor", "Running portrait depth estimations...")
            val maskBitmap = depthEstimator.estimateDepthMask(stackedBitmap)
            if (maskBitmap != null) {
                val blurredBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                
                val bokehSuccess = NativeBridge.nProcessPortraitDepth(
                    inputBitmap = stackedBitmap,
                    maskBitmap = maskBitmap,
                    outputBitmap = blurredBitmap,
                    maxBlurRadius = 14.0f
                )
                
                stackedBitmap.recycle()
                maskBitmap.recycle()
                
                if (bokehSuccess) {
                    return blurredBitmap
                } else {
                    Log.e("ImageProcessor", "Portrait bokeh rendering failed.")
                    blurredBitmap.recycle()
                    return null
                }
            } else {
                Log.w("ImageProcessor", "Depth mask estimation failed. Returning clean stacked picture.")
            }
        }

        return stackedBitmap
    }
}
