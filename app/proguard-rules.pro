# ProGuard configurations for AuraCam

# 1. Preserve JNI Native bindings and C++ classes
-keepclasseswithmembernames class * {
    native <methods>;
}

-keep class com.auracam.app.processing.NativeBridge {
    *;
}

# 2. Preserve TensorFlow Lite structures to prevent crashing during model runs
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.gpu.** { *; }

# 3. Preserve CameraX classes and configurations
-keep class androidx.camera.core.** { *; }
-dontwarn androidx.camera.core.**

# 4. General optimizations and warnings suppressions
-dontwarn okio.**
-keepattributes Signature,InnerClasses,EnclosingMethod,AnnotationDefault
