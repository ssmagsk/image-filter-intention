#include <jni.h>
#include <string>
#include <vector>
#include <cstdint>

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_example_image_1filter_1intention_MainActivity_applyGrayscale(
        JNIEnv* env,
        jobject /* this */,
        jbyteArray input,
        jint width,
        jint height) {
    if (width <= 0 || height <= 0) {
        return env->NewByteArray(0);
    }

    const jsize input_length = env->GetArrayLength(input);
    const jsize expected_length = static_cast<jsize>(width) * static_cast<jsize>(height) * 4;
    if (input_length != expected_length) {
        return env->NewByteArray(0);
    }

    jboolean is_copy = JNI_FALSE;
    jbyte* input_bytes = env->GetByteArrayElements(input, &is_copy);
    if (input_bytes == nullptr) {
        return env->NewByteArray(0);
    }

    const uint32_t* in_pixels = reinterpret_cast<const uint32_t*>(input_bytes);
    const size_t pixel_count = static_cast<size_t>(width) * static_cast<size_t>(height);

    std::vector<uint32_t> out_pixels(pixel_count);
    for (size_t i = 0; i < pixel_count; ++i) {
        const uint32_t px = in_pixels[i];
        const uint8_t a = static_cast<uint8_t>((px >> 24) & 0xFF);
        const uint8_t r = static_cast<uint8_t>((px >> 16) & 0xFF);
        const uint8_t g = static_cast<uint8_t>((px >> 8) & 0xFF);
        const uint8_t b = static_cast<uint8_t>(px & 0xFF);
        const uint8_t gray = static_cast<uint8_t>(0.299f * r + 0.587f * g + 0.114f * b);
        out_pixels[i] =
                (static_cast<uint32_t>(a) << 24) |
                (static_cast<uint32_t>(gray) << 16) |
                (static_cast<uint32_t>(gray) << 8) |
                static_cast<uint32_t>(gray);
    }

    env->ReleaseByteArrayElements(input, input_bytes, JNI_ABORT);

    jbyteArray output = env->NewByteArray(expected_length);
    if (output == nullptr) {
        return env->NewByteArray(0);
    }
    env->SetByteArrayRegion(
            output,
            0,
            expected_length,
            reinterpret_cast<const jbyte*>(out_pixels.data()));
    return output;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_example_image_1filter_1intention_MainActivity_applyNegative(
        JNIEnv* env,
        jobject /* this */,
        jbyteArray input,
        jint width,
        jint height) {
    if (width <= 0 || height <= 0) {
        return env->NewByteArray(0);
    }

    const jsize input_length = env->GetArrayLength(input);
    const jsize expected_length = static_cast<jsize>(width) * static_cast<jsize>(height) * 4;
    if (input_length != expected_length) {
        return env->NewByteArray(0);
    }

    jboolean is_copy = JNI_FALSE;
    jbyte* input_bytes = env->GetByteArrayElements(input, &is_copy);
    if (input_bytes == nullptr) {
        return env->NewByteArray(0);
    }

    const uint32_t* in_pixels = reinterpret_cast<const uint32_t*>(input_bytes);
    const size_t pixel_count = static_cast<size_t>(width) * static_cast<size_t>(height);

    std::vector<uint32_t> out_pixels(pixel_count);
    for (size_t i = 0; i < pixel_count; ++i) {
        const uint32_t px = in_pixels[i];
        const uint8_t a = static_cast<uint8_t>((px >> 24) & 0xFF);
        const uint8_t r = static_cast<uint8_t>((px >> 16) & 0xFF);
        const uint8_t g = static_cast<uint8_t>((px >> 8) & 0xFF);
        const uint8_t b = static_cast<uint8_t>(px & 0xFF);
        out_pixels[i] =
                (static_cast<uint32_t>(a) << 24) |
                (static_cast<uint32_t>(255 - r) << 16) |
                (static_cast<uint32_t>(255 - g) << 8) |
                static_cast<uint32_t>(255 - b);
    }

    env->ReleaseByteArrayElements(input, input_bytes, JNI_ABORT);

    jbyteArray output = env->NewByteArray(expected_length);
    if (output == nullptr) {
        return env->NewByteArray(0);
    }
    env->SetByteArrayRegion(
            output,
            0,
            expected_length,
            reinterpret_cast<const jbyte*>(out_pixels.data()));
    return output;
}