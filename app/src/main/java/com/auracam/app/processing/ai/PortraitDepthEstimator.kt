package com.auracam.app.processing.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PortraitDepthEstimator(private val context: Context) {
    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    private var isModelLoaded = false

    init {
        tryLoadTFLiteModel()
    }

    private fun tryLoadTFLiteModel() {
        try {
            val modelBuffer = ModelLoader.loadModelFile(context, "portrait_segmenter.tflite")
            val options = Interpreter.Options().apply {
                try {
                    gpuDelegate = GpuDelegate()
                    addDelegate(gpuDelegate)
                    Log.i("PortraitDepthEstimator", "Mali GPU Delegate added successfully.")
                } catch (e: Exception) {
                    setNumThreads(2)
                    Log.w("PortraitDepthEstimator", "GPU delegate unsupported. Falling back to dual CPU threads.")
                }
            }
            interpreter = Interpreter(modelBuffer, options)
            isModelLoaded = true
            Log.i("PortraitDepthEstimator", "TFLite Selfie Segmenter loaded successfully.")
        } catch (e: Exception) {
            Log.e("PortraitDepthEstimator", "TFLite model asset missing or failed to initialize: ${e.message}")
            Log.w("PortraitDepthEstimator", "Activating high-fidelity HSV Skin & Central Procedural fallback.")
            isModelLoaded = false
        }
    }

    /**
     * Estimates a black-and-white mask of the portrait subject (white = person, black = background).
     */
    fun estimateDepthMask(srcBitmap: Bitmap): Bitmap? {
        if (isModelLoaded && interpreter != null) {
            return runTFLiteSegmenter(srcBitmap)
        }
        return runProceduralSegmenter(srcBitmap)
    }

    private fun runTFLiteSegmenter(srcBitmap: Bitmap): Bitmap? {
        val interpreter = interpreter ?: return null
        val width = srcBitmap.width
        val height = srcBitmap.height

        try {
            val modelInputSize = 256
            val scaledBitmap = Bitmap.createScaledBitmap(srcBitmap, modelInputSize, modelInputSize, true)

            val inputBuffer = ByteBuffer.allocateDirect(4 * modelInputSize * modelInputSize * 3).apply {
                order(ByteOrder.nativeOrder())
            }

            val intValues = IntArray(modelInputSize * modelInputSize)
            scaledBitmap.getPixels(intValues, 0, modelInputSize, 0, 0, modelInputSize, modelInputSize)

            inputBuffer.rewind()
            for (pixel in intValues) {
                val r = ((pixel >> 16) & 0xFF) / 255.0f
                val g = ((pixel >> 8) & 0xFF) / 255.0f
                val b = (pixel & 0xFF) / 255.0f

                inputBuffer.putFloat(r)
                inputBuffer.putFloat(g)
                inputBuffer.putFloat(b)
            }
            scaledBitmap.recycle()

            val outputBuffer = ByteBuffer.allocateDirect(4 * modelInputSize * modelInputSize).apply {
                order(ByteOrder.nativeOrder())
            }

            interpreter.run(inputBuffer, outputBuffer)

            val maskBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val maskPixels = IntArray(width * height)
            
            outputBuffer.rewind()
            val scoreMap = FloatArray(modelInputSize * modelInputSize)
            outputBuffer.asFloatBuffer().get(scoreMap)

            for (y in 0 until height) {
                val modelY = (y * modelInputSize) / height
                val rowOffset = modelY * modelInputSize
                val pixelOffset = y * width

                for (x in 0 until width) {
                    val modelX = (x * modelInputSize) / width
                    val confidence = scoreMap[rowOffset + modelX]
                    val value = if (confidence > 0.5f) 255 else 0
                    maskPixels[pixelOffset + x] = Color.rgb(value, value, value)
                }
            }

            maskBitmap.setPixels(maskPixels, 0, width, 0, 0, width, height)
            return maskBitmap
        } catch (e: Exception) {
            Log.e("PortraitDepthEstimator", "TFLite inference crashed, calling procedural fallback.", e)
            return runProceduralSegmenter(srcBitmap)
        }
    }

    private fun runProceduralSegmenter(srcBitmap: Bitmap): Bitmap {
        val w = srcBitmap.width
        val h = srcBitmap.height
        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        
        val pixels = IntArray(w * h)
        srcBitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        
        val maskPixels = IntArray(w * h)
        
        val centerX = w / 2.0f
        val centerY = h / 2.0f
        val maxDist = Math.sqrt((centerX * centerX + centerY * centerY).toDouble()).toFloat()
        
        val hsv = FloatArray(3)
        
        for (y in 0 until h) {
            val offset = y * w
            for (x in 0 until w) {
                val color = pixels[offset + x]
                val r = (color >> 16) & 0xFF
                val g = (color >> 8) & 0xFF
                val b = color & 0xFF
                
                Color.RGBToHSV(r, g, b, hsv)
                val hue = hsv[0]
                val sat = hsv[1]
                val valColor = hsv[2]
                
                // Human skin tone bounds in HSV
                val isSkinColor = (hue in 0.0f..38.0f || hue in 340.0f..360.0f) && 
                                  (sat in 0.15f..0.75f) && (valColor in 0.2f..0.98f)
                
                val dx = x - centerX
                val dy = y - centerY
                val distance = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                val radialWeight = 1.0f - (distance / maxDist)
                
                var score = 0.0f
                if (isSkinColor) score += 0.6f
                score += radialWeight * 0.4f
                
                val maskVal = if (score > 0.45f) 255 else 0
                maskPixels[offset + x] = Color.rgb(maskVal, maskVal, maskVal)
            }
        }
        
        output.setPixels(maskPixels, 0, w, 0, 0, w, h)
        return output
    }

    fun shutdown() {
        interpreter?.close()
        gpuDelegate?.close()
    }
}
