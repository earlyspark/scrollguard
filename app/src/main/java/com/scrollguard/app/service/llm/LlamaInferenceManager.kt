package com.scrollguard.app.service.llm

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Manager class for LLama inference operations.
 * Handles model loading, content classification, and resource management.
 */
class LlamaInferenceManager(private val context: Context) {

    companion object {
        private const val MODEL_FILENAME = "scrollguard-model.gguf"
        private const val DEFAULT_N_CTX = 2048
        private const val DEFAULT_N_THREADS = 4
        private const val DEFAULT_TEMPERATURE = 0.1f
        
        // Classification prompts
        private val CLASSIFICATION_PROMPT = """
You are a content classifier for a productivity app. Analyze the following social media content and determine if it's productive or unproductive.

Productive content includes:
- Educational material, tutorials, how-to guides
- News and current events (factual)
- Professional networking and career advice
- Health and wellness information
- Scientific research and insights
- Creative and artistic content with substance
- Meaningful discussions and debates

Unproductive content includes:
- Clickbait headlines and sensationalized content
- Gossip, drama, and celebrity news
- Addictive short-form entertainment
- Inflammatory or divisive content
- Repetitive memes and viral content
- Time-wasting challenges and trends
- Excessive advertising and promotional content

Content to analyze: "{content}"

Respond with only: PRODUCTIVE or UNPRODUCTIVE
        """.trimIndent()
    }

    private val inferenceMutex = Mutex()
    private val resultCache = ConcurrentHashMap<String, ClassificationResult>()
    private val modelDownloadManager = ModelDownloadManager(context)
    
    private var isInitialized = false
    private var isModelLoaded = false
    private var modelPath: String? = null
    private var currentModel: ModelDownloadManager.ModelInfo? = null

    data class ClassificationResult(
        val isProductive: Boolean,
        val confidence: Float,
        val reason: String,
        val processingTimeMs: Int,
        val success: Boolean = true,
        val errorMessage: String? = null
    )

    /**
     * Initialize the LLama inference manager
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext true
        
        try {
            Timber.d("Initializing LLama inference manager")
            
            // Initialize native library
            val initResult = LlamaInference.nativeInit()
            if (!initResult) {
                Timber.e("Failed to initialize native LLama library")
                return@withContext false
            }
            
            isInitialized = true
            Timber.d("LLama inference manager initialized successfully")
            true
            
        } catch (e: Exception) {
            Timber.e(e, "Error initializing LLama inference manager")
            false
        }
    }

    /**
     * Load model from assets or external storage
     */
    suspend fun loadModel(customModelPath: String? = null): Boolean = withContext(Dispatchers.IO) {
        inferenceMutex.withLock {
            if (isModelLoaded) return@withLock true
            
            try {
                val modelFile = customModelPath?.let { File(it) } ?: getDefaultModelFile()
                
                if (!modelFile.exists()) {
                    Timber.w("Model file not found: ${modelFile.absolutePath}")
                    
                    // For development, create a placeholder
                    if (!modelFile.exists()) {
                        Timber.d("Creating placeholder model file for development")
                        modelFile.parentFile?.mkdirs()
                        modelFile.createNewFile()
                        // In production, this would download or extract the actual model
                    }
                }

                modelPath = modelFile.absolutePath
                
                Timber.d("Loading model from: $modelPath")
                
                val loadResult = LlamaInference.nativeLoadModel(
                    modelPath = modelPath!!,
                    nCtx = DEFAULT_N_CTX,
                    nThreads = DEFAULT_N_THREADS,
                    temperature = DEFAULT_TEMPERATURE
                )

                if (loadResult) {
                    isModelLoaded = true
                    
                    // Warm up the model
                    warmUpModel()
                    
                    Timber.d("Model loaded successfully")
                    true
                } else {
                    Timber.e("Failed to load model")
                    false
                }

            } catch (e: Exception) {
                Timber.e(e, "Error loading model")
                false
            }
        }
    }

    /**
     * Classify content for productivity
     */
    suspend fun classifyContent(content: String, context: String = ""): ClassificationResult = withContext(Dispatchers.Default) {
        if (content.isBlank()) {
            return@withContext ClassificationResult(
                isProductive = true,
                confidence = 0f,
                reason = "empty_content",
                processingTimeMs = 0,
                success = false,
                errorMessage = "Empty content"
            )
        }

        // Check cache first
        val contentHash = content.hashCode().toString()
        resultCache[contentHash]?.let { return@withContext it }

        if (!isInitialized || !isModelLoaded) {
            Timber.w("Model not loaded, using fallback classification")
            return@withContext fallbackClassification(content)
        }

        try {
            val startTime = System.currentTimeMillis()
            
            // Prepare prompt
            val prompt = CLASSIFICATION_PROMPT.replace("{content}", content.take(500))
            
            // Call native inference
            val resultJson = LlamaInference.nativeClassifyContent(prompt, context)
            val processingTime = (System.currentTimeMillis() - startTime).toInt()
            
            // Parse result
            val result = parseClassificationResult(resultJson, processingTime)
            
            // Cache result
            resultCache[contentHash] = result
            
            // Clean cache if it gets too large
            if (resultCache.size > 1000) {
                val oldestKeys = resultCache.keys.take(200)
                oldestKeys.forEach { resultCache.remove(it) }
            }
            
            result

        } catch (e: Exception) {
            Timber.e(e, "Error during content classification")
            fallbackClassification(content)
        }
    }

    /**
     * Check if manager is initialized
     */
    fun isInitialized(): Boolean = isInitialized

    /**
     * Check if model is loaded
     */
    fun isModelLoaded(): Boolean = isModelLoaded

    /**
     * Get current memory usage
     */
    suspend fun getMemoryUsage(): Long = withContext(Dispatchers.Default) {
        if (!isInitialized) return@withContext 0L
        
        try {
            LlamaInference.nativeGetMemoryUsage()
        } catch (e: Exception) {
            Timber.e(e, "Error getting memory usage")
            0L
        }
    }

    /**
     * Warm up the model with a test inference
     */
    private suspend fun warmUpModel() = withContext(Dispatchers.Default) {
        try {
            Timber.d("Warming up model")
            LlamaInference.nativeWarmUp()
            
            // Perform a test classification
            classifyContent("This is a test post for model warm-up")
            
        } catch (e: Exception) {
            Timber.e(e, "Error warming up model")
        }
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        try {
            resultCache.clear()
            
            if (isInitialized) {
                LlamaInference.nativeCleanup()
            }
            
            isModelLoaded = false
            isInitialized = false
            modelPath = null
            
            Timber.d("LLama inference manager cleaned up")
            
        } catch (e: Exception) {
            Timber.e(e, "Error during cleanup")
        }
    }

    /**
     * Get default model file location
     */
    private fun getDefaultModelFile(): File {
        val modelsDir = File(context.filesDir, "models")
        return File(modelsDir, MODEL_FILENAME)
    }

    /**
     * Parse classification result from JSON
     */
    private fun parseClassificationResult(json: String, processingTimeMs: Int): ClassificationResult {
        return try {
            // Simple JSON parsing for the native response
            val isSuccess = json.contains("\"success\":true")
            if (!isSuccess) {
                val errorMsg = extractJsonValue(json, "error") ?: "Unknown error"
                return ClassificationResult(
                    isProductive = true,
                    confidence = 0f,
                    reason = "error",
                    processingTimeMs = processingTimeMs,
                    success = false,
                    errorMessage = errorMsg
                )
            }

            val isProductive = json.contains("\"is_productive\":true")
            val confidence = extractJsonValue(json, "confidence")?.toFloatOrNull() ?: 0.5f
            val reason = extractJsonValue(json, "reason") ?: "llm_classification"

            ClassificationResult(
                isProductive = isProductive,
                confidence = confidence,
                reason = reason,
                processingTimeMs = processingTimeMs
            )

        } catch (e: Exception) {
            Timber.e(e, "Error parsing classification result")
            ClassificationResult(
                isProductive = true,
                confidence = 0f,
                reason = "parse_error",
                processingTimeMs = processingTimeMs,
                success = false,
                errorMessage = e.message
            )
        }
    }

    /**
     * Simple JSON value extraction (avoiding dependencies)
     */
    private fun extractJsonValue(json: String, key: String): String? {
        val pattern = "\"$key\"\\s*:\\s*\"?([^,}\"]+)\"?".toRegex()
        return pattern.find(json)?.groupValues?.get(1)
    }

    /**
     * Fallback classification using simple heuristics
     */
    private fun fallbackClassification(content: String): ClassificationResult {
        val startTime = System.currentTimeMillis()
        
        val lowerContent = content.lowercase()
        
        // Simple keyword-based classification
        val unproductiveKeywords = listOf(
            "trending", "viral", "shocking", "you won't believe",
            "clickbait", "drama", "gossip", "breaking news",
            "watch this", "must see", "insane", "crazy",
            "epic fail", "hilarious", "omg", "wtf"
        )
        
        val productiveKeywords = listOf(
            "learn", "tutorial", "education", "how to",
            "guide", "tips", "advice", "knowledge",
            "study", "research", "analysis", "insight",
            "explain", "understand", "science", "technology"
        )
        
        var productiveScore = 0
        var unproductiveScore = 0
        
        productiveKeywords.forEach { keyword ->
            if (lowerContent.contains(keyword)) productiveScore++
        }
        
        unproductiveKeywords.forEach { keyword ->
            if (lowerContent.contains(keyword)) unproductiveScore++
        }
        
        val isProductive = productiveScore > unproductiveScore
        val confidence = if (productiveScore + unproductiveScore > 0) {
            maxOf(productiveScore, unproductiveScore).toFloat() / (productiveScore + unproductiveScore)
        } else {
            0.5f // Neutral when no keywords match
        }
        
        val reason = when {
            productiveScore > 0 -> "educational_keywords"
            unproductiveScore > 0 -> "unproductive_keywords"
            else -> "neutral_content"
        }
        
        val processingTime = (System.currentTimeMillis() - startTime).toInt()
        
        return ClassificationResult(
            isProductive = isProductive,
            confidence = confidence,
            reason = reason,
            processingTimeMs = processingTime
        )
    }
}