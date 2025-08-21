#include <jni.h>
#include <android/log.h>
#include <string>
#include <memory>
#include "../include/llama_wrapper.h"

#define LOG_TAG "ScrollGuard-Native"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using namespace scrollguard;

// Global model instance
static std::unique_ptr<LlamaWrapper> g_llama_wrapper = nullptr;

extern "C" {

/**
 * Initialize the native llama wrapper
 */
JNIEXPORT jboolean JNICALL
Java_com_scrollguard_app_service_llm_LlamaInference_nativeInit(JNIEnv *env, jobject thiz) {
    LOGD("Initializing native LLama wrapper");
    
    try {
        if (!g_llama_wrapper) {
            g_llama_wrapper = std::make_unique<LlamaWrapper>();
        }
        return JNI_TRUE;
    } catch (const std::exception& e) {
        LOGE("Failed to initialize LLama wrapper: %s", e.what());
        return JNI_FALSE;
    }
}

/**
 * Load model from file path
 */
JNIEXPORT jboolean JNICALL
Java_com_scrollguard_app_service_llm_LlamaInference_nativeLoadModel(
    JNIEnv *env, 
    jobject thiz,
    jstring model_path,
    jint n_ctx,
    jint n_threads,
    jfloat temperature
) {
    if (!g_llama_wrapper) {
        LOGE("LLama wrapper not initialized");
        return JNI_FALSE;
    }

    const char* path_cstr = env->GetStringUTFChars(model_path, nullptr);
    if (!path_cstr) {
        LOGE("Failed to get model path string");
        return JNI_FALSE;
    }

    ModelConfig config;
    config.model_path = std::string(path_cstr);
    config.n_ctx = n_ctx;
    config.n_threads = n_threads;
    config.temperature = temperature;

    LOGD("Loading model: %s", config.model_path.c_str());
    
    bool success = g_llama_wrapper->load_model(config);
    
    env->ReleaseStringUTFChars(model_path, path_cstr);
    
    if (success) {
        LOGD("Model loaded successfully");
    } else {
        LOGE("Failed to load model");
    }
    
    return success ? JNI_TRUE : JNI_FALSE;
}

/**
 * Check if model is loaded
 */
JNIEXPORT jboolean JNICALL
Java_com_scrollguard_app_service_llm_LlamaInference_nativeIsModelLoaded(JNIEnv *env, jobject thiz) {
    if (!g_llama_wrapper) {
        return JNI_FALSE;
    }
    return g_llama_wrapper->is_model_loaded() ? JNI_TRUE : JNI_FALSE;
}

/**
 * Classify content and return result as JSON string
 */
JNIEXPORT jstring JNICALL
Java_com_scrollguard_app_service_llm_LlamaInference_nativeClassifyContent(
    JNIEnv *env,
    jobject thiz,
    jstring content,
    jstring context
) {
    if (!g_llama_wrapper || !g_llama_wrapper->is_model_loaded()) {
        LOGE("Model not loaded");
        return env->NewStringUTF("{\"success\":false,\"error\":\"Model not loaded\"}");
    }

    const char* content_cstr = env->GetStringUTFChars(content, nullptr);
    const char* context_cstr = context ? env->GetStringUTFChars(context, nullptr) : "";
    
    if (!content_cstr) {
        LOGE("Failed to get content string");
        return env->NewStringUTF("{\"success\":false,\"error\":\"Invalid content\"}");
    }

    std::string content_str(content_cstr);
    std::string context_str(context_cstr ? context_cstr : "");

    LOGD("Classifying content (length: %zu)", content_str.length());

    ClassificationResult result = g_llama_wrapper->classify_content(content_str, context_str);

    // Create JSON response
    std::string json_result = "{"
        "\"success\":" + std::string(result.success ? "true" : "false") + ","
        "\"is_productive\":" + std::string(result.is_productive ? "true" : "false") + ","
        "\"confidence\":" + std::to_string(result.confidence) + ","
        "\"reason\":\"" + result.reason + "\","
        "\"processing_time_ms\":" + std::to_string(result.processing_time_ms);
    
    if (!result.success) {
        json_result += ",\"error\":\"" + result.error_message + "\"";
    }
    
    json_result += "}";

    // Cleanup
    env->ReleaseStringUTFChars(content, content_cstr);
    if (context_cstr && context) {
        env->ReleaseStringUTFChars(context, context_cstr);
    }

    LOGD("Classification result: %s", json_result.c_str());
    return env->NewStringUTF(json_result.c_str());
}

/**
 * Get memory usage in bytes
 */
JNIEXPORT jlong JNICALL
Java_com_scrollguard_app_service_llm_LlamaInference_nativeGetMemoryUsage(JNIEnv *env, jobject thiz) {
    if (!g_llama_wrapper) {
        return 0;
    }
    return static_cast<jlong>(g_llama_wrapper->get_memory_usage());
}

/**
 * Warm up the model
 */
JNIEXPORT void JNICALL
Java_com_scrollguard_app_service_llm_LlamaInference_nativeWarmUp(JNIEnv *env, jobject thiz) {
    if (g_llama_wrapper && g_llama_wrapper->is_model_loaded()) {
        LOGD("Warming up model");
        g_llama_wrapper->warm_up();
    }
}

/**
 * Unload model and cleanup
 */
JNIEXPORT void JNICALL
Java_com_scrollguard_app_service_llm_LlamaInference_nativeCleanup(JNIEnv *env, jobject thiz) {
    LOGD("Cleaning up native resources");
    if (g_llama_wrapper) {
        g_llama_wrapper->unload_model();
        g_llama_wrapper.reset();
    }
}

} // extern "C"