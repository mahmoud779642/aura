#ifndef AURA_PIPELINE_H
#define AURA_PIPELINE_H

#include <jni.h>
#include <android/log.h>
#include <vector>
#include <cmath>
#include <algorithm>

#define LOG_TAG "AuraPipelineNDK"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Contiguous reference structure mapping YUV planes directly in native buffer memory
struct YUVFrame {
    uint8_t* yData;
    uint8_t* uData;
    uint8_t* vData;
    int width;
    int height;
    int yRowStride;
    int uvRowStride;
    int uvPixelStride;
};

// Offset values representing minor hand shakes for burst captures
struct AlignmentOffset {
    int dx;
    int dy;
};

// Primary C++ NDK photographic interfaces (supporting Android Clean package structures)
extern "C" {
    JNIEXPORT jboolean JNICALL Java_com_auracam_app_processing_NativeBridge_nProcessBurst(
        JNIEnv* env, jobject thiz,
        jobjectArray yuvFrames,
        jint width, jint height,
        jintArray yRowStrides, jintArray uvRowStrides, jintArray uvPixelStrides,
        jobject outBitmap,
        jfloat evCorrection,
        jboolean isNightMode
    );

    JNIEXPORT jboolean JNICALL Java_com_auracam_app_processing_NativeBridge_nProcessPortraitDepth(
        JNIEnv* env, jobject thiz,
        jobject inputBitmap,
        jobject maskBitmap,
        jobject outputBitmap,
        jfloat maxBlurRadius
    );
}

#endif // AURA_PIPELINE_H
