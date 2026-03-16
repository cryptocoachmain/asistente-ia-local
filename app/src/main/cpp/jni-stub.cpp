#include <jni.h>
#include <android/log.h>

#define LOG_TAG "AILocal"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

// Stub implementations for JNI bridge
JNIEXPORT jstring JNICALL
Java_com_dnklabs_asistenteialocal_data_repository_LlamaCppRepository_nativeGetSystemInfo(JNIEnv *env, jobject thiz) {
    LOGI("Native getSystemInfo called (stub)");
    return env->NewStringUTF("STUB: System Info - No native library loaded");
}

JNIEXPORT jlong JNICALL
Java_com_dnklabs_asistenteialocal_data_repository_LlamaCppRepository_nativeLoadModel(
    JNIEnv *env, jobject thiz, jstring modelPath, jint nCtx, jint nThreads) {
    LOGI("Native loadModel called (stub)");
    return 0; // Return null handle
}

JNIEXPORT void JNICALL
Java_com_dnklabs_asistenteialocal_data_repository_LlamaCppRepository_nativeFreeModel(
    JNIEnv *env, jobject thiz, jlong modelHandle) {
    LOGI("Native freeModel called (stub)");
}

JNIEXPORT jlong JNICALL
Java_com_dnklabs_asistenteialocal_data_repository_LlamaCppRepository_nativeCreateContext(
    JNIEnv *env, jobject thiz, jlong modelHandle, jint nCtx, jint nThreads) {
    LOGI("Native createContext called (stub)");
    return 0;
}

JNIEXPORT void JNICALL
Java_com_dnklabs_asistenteialocal_data_repository_LlamaCppRepository_nativeFreeContext(
    JNIEnv *env, jobject thiz, jlong contextHandle) {
    LOGI("Native freeContext called (stub)");
}

JNIEXPORT jstring JNICALL
Java_com_dnklabs_asistenteialocal_data_repository_LlamaCppRepository_nativeGenerate(
    JNIEnv *env, jobject thiz, jlong contextHandle, jstring prompt, jint maxTokens, 
    jfloat temperature, jint topK, jfloat topP) {
    LOGI("Native generate called (stub)");
    return env->NewStringUTF("STUB: AI response - Native library not loaded");
}

JNIEXPORT void JNICALL
Java_com_dnklabs_asistenteialocal_data_repository_LlamaCppRepository_nativeStopGeneration(
    JNIEnv *env, jobject thiz) {
    LOGI("Native stopGeneration called (stub)");
}

JNIEXPORT jboolean JNICALL
Java_com_dnklabs_asistenteialocal_data_repository_LlamaCppRepository_nativeIsGenerating(
    JNIEnv *env, jobject thiz) {
    return JNI_FALSE;
}

} // extern "C"
