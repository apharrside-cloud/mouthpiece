#include <jni.h>
#include <android/log.h>
#include <string>
#include "whisper.h"

#define TAG "WhisperJNI"

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_pharrlabs_voice_asr_WhisperNative_initContext(JNIEnv *env, jobject, jstring modelPath) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;
    whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(modelPath, path);
    if (ctx == nullptr) __android_log_print(ANDROID_LOG_ERROR, TAG, "failed to load model");
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jstring JNICALL
Java_com_pharrlabs_voice_asr_WhisperNative_fullTranscribe(JNIEnv *env, jobject, jlong ctxPtr,
                                                         jfloatArray pcm, jint threads) {
    auto *ctx = reinterpret_cast<whisper_context *>(ctxPtr);
    if (ctx == nullptr) return env->NewStringUTF("");
    jsize n = env->GetArrayLength(pcm);
    jfloat *data = env->GetFloatArrayElements(pcm, nullptr);

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads = threads;
    params.language = "en";
    params.translate = false;
    params.no_context = true;
    params.no_timestamps = true;
    // whisper encodes a fixed 30 s window (1500 frames = 50/s); shrink it to the clip length
    // (+2 s margin). Spike B: halves latency (small.en 11 s clip 5.8 s -> 2.9 s).
    // Only safe for small and below (audio state <= 768). Phone benchmark: medium and large-turbo decode
    // garbage/loops with a shrunk window (large-turbo 65% word errors vs 4% with the full window), so
    // they keep the full 30 s window.
    int frames = (int) ((n / 16000.0) * 50.0) + 100;
    bool canShrink = whisper_model_n_audio_state(ctx) <= 768;
    params.audio_ctx = (canShrink && frames < 1500) ? frames : 0;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_special = false;
    params.print_timestamps = false;

    std::string out;
    if (whisper_full(ctx, params, data, n) == 0) {
        int segs = whisper_full_n_segments(ctx);
        for (int i = 0; i < segs; i++) out += whisper_full_get_segment_text(ctx, i);
    } else {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "whisper_full failed");
    }
    env->ReleaseFloatArrayElements(pcm, data, JNI_ABORT);
    return env->NewStringUTF(out.c_str());
}

JNIEXPORT void JNICALL
Java_com_pharrlabs_voice_asr_WhisperNative_freeContext(JNIEnv *, jobject, jlong ctxPtr) {
    auto *ctx = reinterpret_cast<whisper_context *>(ctxPtr);
    if (ctx != nullptr) whisper_free(ctx);
}

JNIEXPORT jstring JNICALL
Java_com_pharrlabs_voice_asr_WhisperNative_systemInfo(JNIEnv *env, jobject) {
    return env->NewStringUTF(whisper_print_system_info());
}

}
