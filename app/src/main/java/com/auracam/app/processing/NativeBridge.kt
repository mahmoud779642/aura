package com.auracam.app.processing

import android.graphics.Bitmap
import java.nio.ByteBuffer

object NativeBridge {
    
    /**
     * Stacks multiple YUV frames, performs sub-pixel alignment, local tone mapping, 
     * highlight-shadow recovery, and warm color adjustments in native C++ using ARM NEON.
     */
    external fun nProcessBurst(
        yuvFrames: Array<ByteBuffer>,
        width: Int,
        height: Int,
        yRowStrides: IntArray,
        uvRowStrides: IntArray,
        uvPixelStrides: IntArray,
        outBitmap: Bitmap,
        evCorrection: Float,
        isNightMode: Boolean
    ): Boolean

    /**
     * Integrates progressive bilateral disc blurring to emulate high-quality photographic 
     * lens bokeh. Merges the primary bitmap and a segmenter depth map into the output bitmap.
     */
    external fun nProcessPortraitDepth(
        inputBitmap: Bitmap,
        maskBitmap: Bitmap,
        outputBitmap: Bitmap,
        maxBlurRadius: Float
    ): Boolean
}
