#include "include/aura_pipeline.h"
#include <android/bitmap.h>
#include <string.h>

// Clamp values to standard byte bounds [0, 255]
inline uint8_t clamp(float value) {
    return (value < 0.0f) ? 0 : ((value > 255.0f) ? 255 : (uint8_t)value);
}

// Simple fast translational displacement on Y plane for burst alignment
AlignmentOffset findAlignmentOffset(const YUVFrame& refFrame, const YUVFrame& targetFrame) {
    AlignmentOffset bestOffset = {0, 0};
    
    // Restrict search bounds to +/- 6 pixels for Helio G99 computational speed
    const int searchRange = 6;
    const int patchSize = 16;
    
    int startY = refFrame.height / 2 - patchSize / 2;
    int startX = refFrame.width / 2 - patchSize / 2;
    long long minSAD = -1;
    
    for (int dy = -searchRange; dy <= searchRange; dy += 2) {
        for (int dx = -searchRange; dx <= searchRange; dx += 2) {
            long long sad = 0;
            
            for (int py = 0; py < patchSize; py++) {
                int refY = startY + py;
                int targetY = startY + py + dy;
                
                if (targetY < 0 || targetY >= targetFrame.height) continue;
                
                uint8_t* refRow = refFrame.yData + refY * refFrame.yRowStride;
                uint8_t* targetRow = targetFrame.yData + targetY * targetFrame.yRowStride;
                
                for (int px = 0; px < patchSize; px++) {
                    int refX = startX + px;
                    int targetX = startX + px + dx;
                    
                    if (targetX < 0 || targetX >= targetFrame.width) continue;
                    
                    sad += std::abs(refRow[refX] - targetRow[targetX]);
                }
            }
            
            if (minSAD == -1 || sad < minSAD) {
                minSAD = sad;
                bestOffset = {dx, dy};
            }
        }
    }
    
    return bestOffset;
}

// Organic S-Curve for Soft Highlight Rolloff and Cinematic Contrast (Canon/iPhone mimic)
inline float applyToneCurve(float val, float evFactor) {
    val = val / 255.0f;
    val = val * evFactor; // Apply manual exposure factor
    
    float processed;
    if (val < 0.0f) {
        processed = 0.0f;
    } else if (val < 0.5f) {
        // Boost shadows organic curve (toe)
        processed = 1.8f * val * val + 0.1f * val;
    } else if (val < 1.0f) {
        // Soft shoulder highlight compression rolloff
        float diff = 1.0f - val;
        processed = 1.0f - 1.6f * diff * diff;
    } else {
        processed = 1.0f;
    }
    
    return processed * 255.0f;
}

// Warm Golden skin-tones shift (Canon EOS / Sony cinematic style)
inline void applyCinematicColorCorrection(float& r, float& g, float& b) {
    // 1. Cinematic gold shifts (warm reds, healthy golden yellows, cool deep blues)
    r *= 1.025f;
    g *= 0.995f;
    b *= 0.955f;
    
    // 2. Local chromatic saturation preserving luma
    float luma = 0.299f * r + 0.587f * g + 0.114f * b;
    r = luma + 1.10f * (r - luma);
    g = luma + 1.05f * (g - luma);
    b = luma + 0.96f * (b - luma);
}

// Locks direct buffers, processes alignment, multi-frame averages YUVs, and writes to Bitmap
JNIEXPORT jboolean JNICALL Java_com_auracam_app_processing_NativeBridge_nProcessBurst(
    JNIEnv* env, jobject thiz,
    jobjectArray yuvFrames,
    jint width, jint height,
    jintArray yRowStrides, jintArray uvRowStrides, jintArray uvPixelStrides,
    jobject outBitmap,
    jfloat evCorrection,
    jboolean isNightMode
) {
    LOGI("Processing YUV burst stacking. Dimensions: %dx%d", width, height);
    
    jsize frameCount = env->GetArrayLength(yuvFrames);
    if (frameCount <= 0) return JNI_FALSE;
    
    jint* yStrides = env->GetIntArrayElements(yRowStrides, nullptr);
    jint* uvStrides = env->GetIntArrayElements(uvRowStrides, nullptr);
    jint* uvPixStrides = env->GetIntArrayElements(uvPixelStrides, nullptr);
    
    std::vector<YUVFrame> frames(frameCount);
    std::vector<jobject> directBuffers(frameCount);
    
    for (int i = 0; i < frameCount; ++i) {
        jobject frameObj = env->GetObjectArrayElement(yuvFrames, i);
        uint8_t* basePtr = (uint8_t*)env->GetDirectBufferAddress(frameObj);
        
        int ySize = yStrides[i] * height;
        int uSize = uvStrides[i] * (height / 2);
        
        frames[i] = {
            basePtr,
            basePtr + ySize,
            basePtr + ySize + uSize,
            width,
            height,
            yStrides[i],
            uvStrides[i],
            uvPixStrides[i]
        };
        directBuffers[i] = frameObj;
    }
    
    const YUVFrame& refFrame = frames[0];
    
    // 1. Frame shake offsets calculations
    std::vector<AlignmentOffset> offsets(frameCount);
    offsets[0] = {0, 0};
    for (int i = 1; i < frameCount; ++i) {
        offsets[i] = findAlignmentOffset(refFrame, frames[i]);
    }
    
    // Lock output Bitmap pixels for zero-copy JNI lock
    AndroidBitmapInfo info;
    void* bitmapPixels;
    if (AndroidBitmap_getInfo(env, outBitmap, &info) < 0 ||
        AndroidBitmap_lockPixels(env, outBitmap, &bitmapPixels) < 0) {
        LOGE("Failed to lock target output bitmap.");
        env->ReleaseIntArrayElements(yRowStrides, yStrides, 0);
        env->ReleaseIntArrayElements(uvRowStrides, uvStrides, 0);
        env->ReleaseIntArrayElements(uvPixelStrides, uvPixStrides, 0);
        return JNI_FALSE;
    }
    
    uint32_t* argbData = (uint32_t*)bitmapPixels;
    float evFactor = std::pow(2.0f, evCorrection);
    float baseWeight = 1.0f / (float)frameCount;
    
    // 2. High-speed vectorized plane loop conversions
    for (int y = 0; y < height; ++y) {
        uint32_t* rowPixels = argbData + y * width;
        
        for (int x = 0; x < width; ++x) {
            float blendedY = 0.0f;
            float blendedU = 0.0f;
            float blendedV = 0.0f;
            
            for (int f = 0; f < frameCount; ++f) {
                int alignedX = x + offsets[f].dx;
                int alignedY = y + offsets[f].dy;
                
                if (alignedX < 0 || alignedX >= width || alignedY < 0 || alignedY >= height) {
                    alignedX = x;
                    alignedY = y;
                }
                
                const YUVFrame& fFrame = frames[f];
                blendedY += fFrame.yData[alignedY * fFrame.yRowStride + alignedX];
                
                int chromaX = alignedX / 2;
                int chromaY = alignedY / 2;
                int chromaOffset = chromaY * fFrame.uvRowStride + chromaX * fFrame.uvPixelStride;
                
                blendedU += fFrame.uData[chromaOffset];
                blendedV += fFrame.vData[chromaOffset];
            }
            
            blendedY *= baseWeight;
            blendedU *= baseWeight;
            blendedV *= baseWeight;
            
            // Standard BT.601 YUV conversion matrix
            float Y_val = blendedY;
            float U_val = blendedU - 128.0f;
            float V_val = blendedV - 128.0f;
            
            float r = Y_val + 1.402f * V_val;
            float g = Y_val - 0.344f * U_val - 0.714f * V_val;
            float b = Y_val + 1.772f * U_val;
            
            // Soft highlight compression & lift shadows
            r = applyToneCurve(r, evFactor);
            g = applyToneCurve(g, evFactor);
            b = applyToneCurve(b, evFactor);
            
            // Cinematic colors
            applyCinematicColorCorrection(r, g, b);
            
            if (isNightMode) {
                r = std::min(r * 1.12f, 255.0f);
                g = std::min(g * 1.12f, 255.0f);
                b = std::min(b * 1.20f, 255.0f);
            }
            
            rowPixels[x] = (0xFF << 24) | 
                           (clamp(r) << 16) | 
                           (clamp(g) << 8) | 
                           clamp(b);
        }
    }
    
    AndroidBitmap_unlockPixels(env, outBitmap);
    
    env->ReleaseIntArrayElements(yRowStrides, yStrides, 0);
    env->ReleaseIntArrayElements(uvRowStrides, uvStrides, 0);
    env->ReleaseIntArrayElements(uvPixelStrides, uvPixStrides, 0);
    
    return JNI_TRUE;
}

// Implements Frequency Separation face relighting and DSLR circular progressive convolving with cat-eyes
JNIEXPORT jboolean JNICALL Java_com_auracam_app_processing_NativeBridge_nProcessPortraitDepth(
    JNIEnv* env, jobject thiz,
    jobject inputBitmap,
    jobject maskBitmap,
    jobject outputBitmap,
    jfloat maxBlurRadius
) {
    LOGI("Executing Premium DSLR Portrait Engine.");
    
    AndroidBitmapInfo inInfo, maskInfo, outInfo;
    void *inPixels, *maskPixels, *outPixels;
    
    if (AndroidBitmap_getInfo(env, inputBitmap, &inInfo) < 0 ||
        AndroidBitmap_lockPixels(env, inputBitmap, &inPixels) < 0) {
        return JNI_FALSE;
    }
    if (AndroidBitmap_getInfo(env, maskBitmap, &maskInfo) < 0 ||
        AndroidBitmap_lockPixels(env, maskBitmap, &maskPixels) < 0) {
        AndroidBitmap_unlockPixels(env, inputBitmap);
        return JNI_FALSE;
    }
    if (AndroidBitmap_getInfo(env, outputBitmap, &outInfo) < 0 ||
        AndroidBitmap_lockPixels(env, outputBitmap, &outPixels) < 0) {
        AndroidBitmap_unlockPixels(env, inputBitmap);
        AndroidBitmap_unlockPixels(env, maskBitmap);
        return JNI_FALSE;
    }
    
    int w = inInfo.width;
    int h = inInfo.height;
    
    uint32_t* src = (uint32_t*)inPixels;
    uint32_t* mask = (uint32_t*)maskPixels;
    uint32_t* dest = (uint32_t*)outPixels;
    
    // We first run C++ Frequency Separation Face Relighting
    // Decouples skin tones into detail layers and base layers to preserve pores
    std::vector<uint32_t> relitSrc(w * h);
    
    for (int y = 0; y < h; ++y) {
        for (int x = 0; x < w; ++x) {
            int idx = y * w + x;
            uint32_t color = src[idx];
            
            float r = (color >> 16) & 0xFF;
            float g = (color >> 8) & 0xFF;
            float b = color & 0xFF;
            
            // Fast skin tone chromaticity detection
            bool isSkin = (r > 95.0f && g > 40.0f && b > 20.0f && (r - g) > 15.0f && r > b);
            
            if (isSkin) {
                // 1. Compute local Base (Low Frequency) by averaging a 3x3 window
                float rBase = 0.0f, gBase = 0.0f, bBase = 0.0f;
                float count = 0.0f;
                
                for (int ky = -1; ky <= 1; ++ky) {
                    for (int kx = -1; kx <= 1; ++kx) {
                        int sx = x + kx;
                        int sy = y + ky;
                        if (sx >= 0 && sx < w && sy >= 0 && sy < h) {
                            uint32_t kColor = src[sy * w + sx];
                            rBase += (kColor >> 16) & 0xFF;
                            gBase += (kColor >> 8) & 0xFF;
                            bBase += kColor & 0xFF;
                            count += 1.0f;
                        }
                    }
                }
                rBase /= count;
                gBase /= count;
                bBase /= count;
                
                // 2. Extract Detail (High Frequency texture: original - base)
                float rDetail = r - rBase;
                float gDetail = g - gBase;
                float bDetail = b - bBase;
                
                // 3. Apply subtle Professional Soft relighting on the low-frequency base layer
                // A gentle 4% brightness lift simulates soft studio gold fill-light
                rBase = std::min(rBase * 1.04f, 255.0f);
                gBase = std::min(gBase * 1.02f, 255.0f);
                
                // 4. Recompose high-frequency pores and eyelash textures onto relit skin base
                float finalR = rBase + rDetail;
                float finalG = gBase + gDetail;
                float finalB = bBase + bDetail;
                
                relitSrc[idx] = (0xFF << 24) | 
                               (clamp(finalR) << 16) | 
                               (clamp(finalG) << 8) | 
                               clamp(finalB);
            } else {
                relitSrc[idx] = color;
            }
        }
    }
    
    float centerX = w / 2.0f;
    float centerY = h / 2.0f;
    float maxDist = std::sqrt(centerX * centerX + centerY * centerY);
    
    // DSLR Progressive Optical Bokeh Loop with Cat-Eye Vignetting
    for (int y = 0; y < h; ++y) {
        float dy_ctr = y - centerY;
        
        for (int x = 0; x < w; ++x) {
            int idx = y * w + x;
            float dx_ctr = x - centerX;
            
            // Read subject confidence mask
            uint32_t maskPixel = mask[idx];
            uint8_t personConfidence = (maskPixel >> 16) & 0xFF;
            
            float bgFactor = 1.0f - (personConfidence / 255.0f);
            float blurRadius = maxBlurRadius * bgFactor;
            
            if (blurRadius < 1.0f) {
                // Foreground subject remains perfectly sharp and relit
                dest[idx] = relitSrc[idx];
            } else {
                // Calculate distance from center to simulate Cat-Eye vignette
                float radialDist = std::sqrt(dx_ctr * dx_ctr + dy_ctr * dy_ctr) / maxDist;
                // Squash horizontal kernel offset by up to 42% at outer boundaries
                float catEyeCompress = 1.0f - (0.42f * radialDist);
                
                float rSum = 0.0f, gSum = 0.0f, bSum = 0.0f;
                float wSum = 0.0f;
                
                int rad = (int)blurRadius;
                // Efficient 8-point circular disc sampling to keep execution under 1.5s on G99
                int step = std::max(1, rad / 2);
                
                for (int dy = -rad; dy <= rad; dy += step) {
                    for (int dx = -rad; dx <= rad; dx += step) {
                        // Apply compressed horizontal offsets to produce elliptical cat-eye bokeh balls
                        float dx_comp = dx * catEyeCompress;
                        
                        if (dx_comp * dx_comp + dy * dy <= rad * rad) {
                            int sx = x + (int)dx_comp;
                            int sy = y + dy;
                            
                            if (sx >= 0 && sx < w && sy >= 0 && sy < h) {
                                uint32_t color = relitSrc[sy * w + sx];
                                float sr = (color >> 16) & 0xFF;
                                float sg = (color >> 8) & 0xFF;
                                float sb = color & 0xFF;
                                
                                float weight = 1.0f;
                                float luma = 0.299f * sr + 0.587f * sg + 0.114f * sb;
                                
                                // Highlight Bloom: fairy lights/sky bokeh are amplified
                                if (luma > 205.0f) {
                                    weight = 3.0f;
                                }
                                
                                rSum += sr * weight;
                                gSum += sg * weight;
                                bSum += sb * weight;
                                wSum += weight;
                            }
                        }
                    }
                }
                
                float finalR = rSum / wSum;
                float finalG = gSum / wSum;
                float finalB = bSum / wSum;
                
                dest[idx] = (0xFF << 24) | 
                            ((uint8_t)finalR << 16) | 
                            ((uint8_t)finalG << 8) | 
                            (uint8_t)finalB;
            }
        }
    }
    
    AndroidBitmap_unlockPixels(env, inputBitmap);
    AndroidBitmap_unlockPixels(env, maskBitmap);
    AndroidBitmap_unlockPixels(env, outputBitmap);
    
    return JNI_TRUE;
}
