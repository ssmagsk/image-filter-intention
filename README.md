# Image Filter Intention

Android proof-of-concept that captures camera frames with CameraX, applies simple filters on-device (NDK + OpenGL ES), and shows the processed result alongside the live preview.

## What it does
- Jetpack Compose UI hosts a `PreviewView`, filter picker, and lens flip control; CameraX starts automatically after camera permission is granted.
- Live frames are converted from YUV to RGBA (`CPUImageConverter`) and displayed beneath the preview.
- Captured/preview bitmaps can be run through several filters: negative, grayscale, bloom, GPU bloom, and a Y-plane grayscale path.
- Native filters (`native-lib.cpp`) handle the hot path for negative/grayscale/bloom; GPU bloom uses an offscreen GLES2 shader (`gpu/GPUBloom.kt`).
- An experimental GLES2 YUV->RGBA path exists in `converter/GPUImageConverter.kt` (currently marked TODO; the app defaults to the CPU converter).

## Tech stack
- Kotlin + Jetpack Compose for UI (`MainActivity.kt`) and app logic
- CameraX (Preview + ImageCapture) for capture and preview
- JNI/NDK C++ via CMake for pixel filters and Y-plane grayscale
- OpenGL ES 2.0 + EGL for GPU bloom and experimental YUV->RGBA conversion
- Gradle build (Android plugin), uses ByteBuffer interop between Kotlin and native code

## Key sources
- `imagefilterintention/app/src/main/java/com/example/image_filter_intention/MainActivity.kt` — Compose screen, CameraX setup, filter routing
- `imagefilterintention/app/src/main/java/com/example/image_filter_intention/CameraXManager.kt` — Camera lifecycle and image delivery
- `imagefilterintention/app/src/main/java/com/example/image_filter_intention/converter/CPUImageConverter.kt` — YUV_420_888 to RGBA conversion
- `imagefilterintention/app/src/main/java/com/example/image_filter_intention/converter/GPUImageConverter.kt` — experimental GL YUV->RGBA converter
- `imagefilterintention/app/src/main/java/com/example/image_filter_intention/gpu/GPUBloom.kt` — GLES2 bloom shader pipeline
- `imagefilterintention/app/src/main/cpp/native-lib.cpp` — NDK filters (grayscale, negative, bloom, Y-plane grayscale)

## Running
- Open the project in Android Studio or run `./gradlew :imagefilterintention:app:installDebug` on a device/emulator with camera access.
- Grant the camera permission at startup; use the on-screen controls to flip lenses and switch filters.***

