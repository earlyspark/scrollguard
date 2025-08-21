package com.scrollguard.app.service.llm

import timber.log.Timber

/**
 * JNI interface for LLama native library.
 * Provides Kotlin bindings for the native C++ LLama implementation.
 */
object LlamaInference {

    init {
        try {
            System.loadLibrary("scrollguard-native")
            Timber.d("ScrollGuard native library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Timber.e(e, "Failed to load ScrollGuard native library")
            throw RuntimeException("Cannot load native library", e)
        }
    }

    /**
     * Initialize the native LLama wrapper
     * @return true if initialization was successful
     */
    external fun nativeInit(): Boolean

    /**
     * Load model from file path
     * @param modelPath Path to the GGUF model file
     * @param nCtx Context length for the model
     * @param nThreads Number of threads to use for inference
     * @param temperature Temperature for text generation (0.0-1.0)
     * @return true if model was loaded successfully
     */
    external fun nativeLoadModel(
        modelPath: String,
        nCtx: Int,
        nThreads: Int,
        temperature: Float
    ): Boolean

    /**
     * Check if model is currently loaded
     * @return true if model is loaded and ready for inference
     */
    external fun nativeIsModelLoaded(): Boolean

    /**
     * Classify content and return result as JSON string
     * @param content The text content to classify
     * @param context Additional context information (optional)
     * @return JSON string containing classification result
     */
    external fun nativeClassifyContent(
        content: String,
        context: String = ""
    ): String

    /**
     * Get current memory usage in bytes
     * @return Memory usage in bytes
     */
    external fun nativeGetMemoryUsage(): Long

    /**
     * Warm up the model with a test inference
     */
    external fun nativeWarmUp()

    /**
     * Cleanup native resources and unload model
     */
    external fun nativeCleanup()

    /**
     * Get model information as JSON string
     * @return JSON string containing model metadata
     */
    fun getModelInfo(): String {
        return if (nativeIsModelLoaded()) {
            """
            {
                "loaded": true,
                "memory_usage_mb": ${nativeGetMemoryUsage() / 1024 / 1024},
                "model_type": "llama",
                "quantization": "Q4_K_M"
            }
            """.trimIndent()
        } else {
            """
            {
                "loaded": false,
                "error": "Model not loaded"
            }
            """.trimIndent()
        }
    }

    /**
     * Test if native library is working correctly
     * @return true if basic functionality works
     */
    fun testNativeLibrary(): Boolean {
        return try {
            nativeInit()
        } catch (e: Exception) {
            Timber.e(e, "Native library test failed")
            false
        }
    }

    /**
     * Get inference statistics
     * @return Statistics about recent inference operations
     */
    fun getInferenceStats(): InferenceStats {
        return InferenceStats(
            modelLoaded = nativeIsModelLoaded(),
            memoryUsageMB = if (nativeIsModelLoaded()) nativeGetMemoryUsage() / 1024 / 1024 else 0,
            lastError = null
        )
    }

    /**
     * Data class for inference statistics
     */
    data class InferenceStats(
        val modelLoaded: Boolean,
        val memoryUsageMB: Long,
        val lastError: String?
    )
}