#include <jni.h>
#include <string>
#include <vector>
#include <cstdint>

namespace {
    inline uint8_t clamp8(int v) {
        if (v < 0) return 0;
        if (v > 255) return 255;
        return static_cast<uint8_t>(v);
    }
}

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
Java_com_example_image_1filter_1intention_MainActivity_applyGrayscaleYuv(
        JNIEnv* env,
        jobject /* this */,
        jbyteArray yPlane,
        jint width,
        jint height) {
    if (width <= 0 || height <= 0) {
        return env->NewByteArray(0);
    }

    const jsize y_length = env->GetArrayLength(yPlane);
    const jsize expected_y = static_cast<jsize>(width) * static_cast<jsize>(height);
    if (y_length != expected_y) {
        return env->NewByteArray(0);
    }

    jboolean is_copy = JNI_FALSE;
    jbyte* y_bytes = env->GetByteArrayElements(yPlane, &is_copy);
    if (y_bytes == nullptr) {
        return env->NewByteArray(0);
    }

    const size_t pixel_count = static_cast<size_t>(width) * static_cast<size_t>(height);
    std::vector<uint32_t> out_pixels(pixel_count);
    for (size_t i = 0; i < pixel_count; ++i) {
        const uint8_t y = static_cast<uint8_t>(y_bytes[i]);
        out_pixels[i] =
                (0xFFu << 24) |
                (static_cast<uint32_t>(y) << 16) |
                (static_cast<uint32_t>(y) << 8) |
                static_cast<uint32_t>(y);
    }

    env->ReleaseByteArrayElements(yPlane, y_bytes, JNI_ABORT);

    jbyteArray output = env->NewByteArray(expected_y * 4);
    if (output == nullptr) {
        return env->NewByteArray(0);
    }
    env->SetByteArrayRegion(
            output,
            0,
            expected_y * 4,
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

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_example_image_1filter_1intention_MainActivity_applyBloom(
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

    const size_t pixel_count = static_cast<size_t>(width) * static_cast<size_t>(height);
    const uint32_t* in_pixels = reinterpret_cast<const uint32_t*>(input_bytes);

    // bright pass
    std::vector<uint8_t> bright(pixel_count * 3);
    for (size_t i = 0; i < pixel_count; ++i) {
        uint32_t px = in_pixels[i];
        uint8_t r = static_cast<uint8_t>((px >> 16) & 0xFF);
        uint8_t g = static_cast<uint8_t>((px >> 8) & 0xFF);
        uint8_t b = static_cast<uint8_t>(px & 0xFF);
        int lum = static_cast<int>(0.299f * r + 0.587f * g + 0.114f * b);
        bool isBright = lum > 180; // threshold
        bright[i * 3 + 0] = isBright ? r : 0;
        bright[i * 3 + 1] = isBright ? g : 0;
        bright[i * 3 + 2] = isBright ? b : 0;
    }

    // separable blur (horizontal then vertical) with radius 2 (5-tap box)
    const int radius = 2;
    const int kernelSize = radius * 2 + 1;
    const int norm = kernelSize;

    std::vector<uint8_t> temp(pixel_count * 3, 0);
    // horizontal
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            int accR = 0, accG = 0, accB = 0;
            for (int k = -radius; k <= radius; ++k) {
                int sx = x + k;
                if (sx < 0) sx = 0;
                if (sx >= width) sx = width - 1;
                size_t idx = (static_cast<size_t>(y) * width + sx) * 3;
                accR += bright[idx + 0];
                accG += bright[idx + 1];
                accB += bright[idx + 2];
            }
            size_t outIdx = (static_cast<size_t>(y) * width + x) * 3;
            temp[outIdx + 0] = clamp8(accR / norm);
            temp[outIdx + 1] = clamp8(accG / norm);
            temp[outIdx + 2] = clamp8(accB / norm);
        }
    }

    std::vector<uint8_t> blurred(pixel_count * 3, 0);
    // vertical
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            int accR = 0, accG = 0, accB = 0;
            for (int k = -radius; k <= radius; ++k) {
                int sy = y + k;
                if (sy < 0) sy = 0;
                if (sy >= height) sy = height - 1;
                size_t idx = (static_cast<size_t>(sy) * width + x) * 3;
                accR += temp[idx + 0];
                accG += temp[idx + 1];
                accB += temp[idx + 2];
            }
            size_t outIdx = (static_cast<size_t>(y) * width + x) * 3;
            blurred[outIdx + 0] = clamp8(accR / norm);
            blurred[outIdx + 1] = clamp8(accG / norm);
            blurred[outIdx + 2] = clamp8(accB / norm);
        }
    }

    // combine: original + blurred bright
    std::vector<uint32_t> out_pixels(pixel_count);
    for (size_t i = 0; i < pixel_count; ++i) {
        uint32_t px = in_pixels[i];
        uint8_t a = static_cast<uint8_t>((px >> 24) & 0xFF);
        uint8_t r = static_cast<uint8_t>((px >> 16) & 0xFF);
        uint8_t g = static_cast<uint8_t>((px >> 8) & 0xFF);
        uint8_t b = static_cast<uint8_t>(px & 0xFF);
        uint8_t br = blurred[i * 3 + 0];
        uint8_t bg = blurred[i * 3 + 1];
        uint8_t bb = blurred[i * 3 + 2];
        uint8_t outR = clamp8(static_cast<int>(r) + static_cast<int>(br));
        uint8_t outG = clamp8(static_cast<int>(g) + static_cast<int>(bg));
        uint8_t outB = clamp8(static_cast<int>(b) + static_cast<int>(bb));
        out_pixels[i] =
                (static_cast<uint32_t>(a) << 24) |
                (static_cast<uint32_t>(outR) << 16) |
                (static_cast<uint32_t>(outG) << 8) |
                static_cast<uint32_t>(outB);
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