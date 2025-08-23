package com.scrollguard.app.service.llm

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Manages downloading and validation of GGUF models for ScrollGuard.
 * Handles progressive download with progress reporting and integrity validation.
 */
class ModelDownloadManager(private val context: Context) {

    companion object {
        private const val MODELS_DIR = "models"
        private const val DOWNLOAD_BUFFER_SIZE = 8192
        private const val CONNECTION_TIMEOUT = 30000 // 30 seconds
        private const val READ_TIMEOUT = 60000 // 60 seconds
        
        // Available models - using small models suitable for mobile
        val AVAILABLE_MODELS = listOf(
            ModelInfo(
                id = "qwen2-0.5b-q4",
                name = "Qwen2 0.5B (Recommended)",
                description = "Lightweight model optimized for mobile devices",
                filename = "qwen2-0_5b-instruct-q4_k_m.gguf",
                url = "https://huggingface.co/Qwen/Qwen2-0.5B-Instruct-GGUF/resolve/main/qwen2-0_5b-instruct-q4_k_m.gguf",
                sizeMB = 350, // ~350MB
                isRecommended = true
            ),
            ModelInfo(
                id = "phi-3.5-mini-q4",
                name = "Phi-3.5 Mini (Large)",
                description = "Microsoft's small model optimized for mobile devices",
                filename = "Phi-3.5-mini-instruct-Q4_K_M.gguf",
                url = "https://huggingface.co/microsoft/Phi-3.5-mini-instruct-gguf/resolve/main/Phi-3.5-mini-instruct-Q4_K_M.gguf",
                sizeMB = 2400, // ~2.4GB
                isRecommended = false
            ),
            ModelInfo(
                id = "qwen2-0.5b-q4",
                name = "Qwen2 0.5B (Ultra Light)",
                description = "Ultra-lightweight model for basic classification",
                filename = "qwen2-0_5b-instruct-q4_k_m.gguf",
                url = "https://huggingface.co/Qwen/Qwen2-0.5B-Instruct-GGUF/resolve/main/qwen2-0_5b-instruct-q4_k_m.gguf",
                sizeMB = 300, // ~300MB
                isRecommended = false
            )
        )
    }

    data class ModelInfo(
        val id: String,
        val name: String,
        val description: String,
        val filename: String,
        val url: String,
        val sizeMB: Int,
        val isRecommended: Boolean
    )

    data class DownloadProgress(
        val downloadedBytes: Long = 0,
        val totalBytes: Long = 0,
        val progressPercent: Int = 0,
        val status: DownloadStatus = DownloadStatus.PENDING,
        val message: String = "",
        val error: String? = null
    )

    enum class DownloadStatus {
        PENDING,
        DOWNLOADING,
        VALIDATING,
        COMPLETED,
        ERROR,
        CANCELLED
    }

    private val _downloadProgress = MutableStateFlow(DownloadProgress())
    val downloadProgress: StateFlow<DownloadProgress> = _downloadProgress

    /**
     * Get the directory where models are stored
     */
    fun getModelsDirectory(): File {
        val modelsDir = File(context.filesDir, MODELS_DIR)
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }
        return modelsDir
    }

    /**
     * Get the recommended model for download
     */
    fun getRecommendedModel(): ModelInfo {
        return AVAILABLE_MODELS.find { it.isRecommended } ?: AVAILABLE_MODELS.first()
    }

    /**
     * Check if a model is already downloaded
     */
    fun isModelDownloaded(modelInfo: ModelInfo): Boolean {
        val modelFile = File(getModelsDirectory(), modelInfo.filename)
        return modelFile.exists() && modelFile.length() > 0 && validateModelFile(modelFile)
    }

    /**
     * Get the path to a downloaded model
     */
    fun getModelPath(modelInfo: ModelInfo): String? {
        val modelFile = File(getModelsDirectory(), modelInfo.filename)
        return if (isModelDownloaded(modelInfo)) {
            modelFile.absolutePath
        } else null
    }

    /**
     * Download a model with progress reporting
     */
    suspend fun downloadModel(modelInfo: ModelInfo): Boolean = withContext(Dispatchers.IO) {
        Timber.d("Starting download of model: ${modelInfo.name}")
        
        try {
            _downloadProgress.value = DownloadProgress(
                status = DownloadStatus.DOWNLOADING,
                message = "Preparing download..."
            )

            val modelFile = File(getModelsDirectory(), modelInfo.filename)
            val tempFile = File(modelFile.absolutePath + ".tmp")

            // Clean up any existing temp file
            if (tempFile.exists()) {
                tempFile.delete()
            }

            // Download from URL
            val success = downloadFromUrl(
                url = modelInfo.url,
                targetFile = tempFile,
                expectedSizeBytes = modelInfo.sizeMB * 1024L * 1024L
            )
            
            if (success) {
                // Move temp file to final location
                if (tempFile.renameTo(modelFile)) {
                    _downloadProgress.value = DownloadProgress(
                        downloadedBytes = modelFile.length(),
                        totalBytes = modelFile.length(),
                        progressPercent = 100,
                        status = DownloadStatus.VALIDATING,
                        message = "Validating model..."
                    )
                    
                    // Validate the downloaded model
                    if (validateModelFile(modelFile)) {
                        _downloadProgress.value = DownloadProgress(
                            downloadedBytes = modelFile.length(),
                            totalBytes = modelFile.length(),
                            progressPercent = 100,
                            status = DownloadStatus.COMPLETED,
                            message = "Download completed successfully"
                        )
                        
                        Timber.d("Model downloaded and validated successfully: ${modelFile.absolutePath}")
                        true
                    } else {
                        _downloadProgress.value = DownloadProgress(
                            status = DownloadStatus.ERROR,
                            message = "Model validation failed",
                            error = "Downloaded file is not a valid GGUF model"
                        )
                        modelFile.delete()
                        false
                    }
                } else {
                    _downloadProgress.value = DownloadProgress(
                        status = DownloadStatus.ERROR,
                        message = "Failed to finalize download",
                        error = "Could not move temporary file"
                    )
                    tempFile.delete()
                    false
                }
            } else {
                _downloadProgress.value = DownloadProgress(
                    status = DownloadStatus.ERROR,
                    message = "Download failed",
                    error = "Failed to download from server"
                )
                tempFile.delete()
                false
            }

        } catch (e: Exception) {
            Timber.e(e, "Error downloading model: ${modelInfo.name}")
            _downloadProgress.value = DownloadProgress(
                status = DownloadStatus.ERROR,
                message = "Download failed",
                error = e.message
            )
            false
        }
    }

    /**
     * Download model from URL (real implementation)
     */
    private suspend fun downloadFromUrl(
        url: String,
        targetFile: File,
        expectedSizeBytes: Long
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECTION_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP ${connection.responseCode}: ${connection.responseMessage}")
            }

            val totalBytes = connection.contentLengthLong
            var downloadedBytes = 0L

            connection.inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
                    var bytesRead: Int

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        // Update progress
                        val progressPercent = if (totalBytes > 0) {
                            ((downloadedBytes * 100) / totalBytes).toInt()
                        } else {
                            0
                        }

                        _downloadProgress.value = DownloadProgress(
                            downloadedBytes = downloadedBytes,
                            totalBytes = totalBytes,
                            progressPercent = progressPercent,
                            status = DownloadStatus.DOWNLOADING,
                            message = "Downloading... ${formatFileSize(downloadedBytes)} / ${formatFileSize(totalBytes)}"
                        )
                    }
                }
            }

            true

        } catch (e: Exception) {
            Timber.e(e, "Error downloading from URL: $url")
            throw e
        }
    }

    /**
     * Create a placeholder model file for development/testing
     */
    fun createPlaceholderModel(targetFile: File): Boolean {
        return try {
            targetFile.parentFile?.mkdirs()
            
            targetFile.outputStream().use { output ->
                // Write GGUF magic number for validation
                output.write("GGUF".toByteArray())
                
                // Write some dummy data to simulate a real model
                val dummyData = ByteArray(1024) { 0 }
                repeat(100) { // Create ~100KB file
                    output.write(dummyData)
                }
            }

            Timber.d("Created placeholder model: ${targetFile.absolutePath}")
            true

        } catch (e: Exception) {
            Timber.e(e, "Error creating placeholder model")
            false
        }
    }
    
    /**
     * Create a placeholder model for the recommended model
     */
    suspend fun createPlaceholderRecommendedModel(): Boolean = withContext(Dispatchers.IO) {
        val recommendedModel = getRecommendedModel()
        val modelFile = File(getModelsDirectory(), recommendedModel.filename)
        
        if (modelFile.exists()) {
            Timber.d("Model already exists: ${modelFile.absolutePath}")
            return@withContext true
        }
        
        _downloadProgress.value = DownloadProgress(
            status = DownloadStatus.DOWNLOADING,
            progressPercent = 50,
            message = "Creating development model..."
        )
        
        val success = createPlaceholderModel(modelFile)
        
        if (success) {
            _downloadProgress.value = DownloadProgress(
                downloadedBytes = modelFile.length(),
                totalBytes = modelFile.length(),
                progressPercent = 100,
                status = DownloadStatus.COMPLETED,
                message = "Development model ready"
            )
        } else {
            _downloadProgress.value = DownloadProgress(
                status = DownloadStatus.ERROR,
                message = "Failed to create development model",
                error = "Could not create placeholder file"
            )
        }
        
        success
    }

    /**
     * Validate model file integrity
     */
    private fun validateModelFile(file: File): Boolean {
        return try {
            file.inputStream().use { input ->
                val magic = ByteArray(4)
                input.read(magic)
                String(magic) == "GGUF"
            }
        } catch (e: Exception) {
            Timber.e(e, "Error validating model file")
            false
        }
    }

    /**
     * Calculate file checksum (SHA-256)
     */
    private fun calculateChecksum(file: File): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Timber.e(e, "Error calculating checksum")
            ""
        }
    }

    /**
     * Delete a downloaded model
     */
    fun deleteModel(modelInfo: ModelInfo): Boolean {
        val modelFile = File(getModelsDirectory(), modelInfo.filename)
        return try {
            if (modelFile.exists()) {
                modelFile.delete()
            } else {
                true
            }
        } catch (e: Exception) {
            Timber.e(e, "Error deleting model: ${modelInfo.name}")
            false
        }
    }

    /**
     * Get total size of all downloaded models
     */
    fun getTotalModelsSize(): Long {
        return try {
            getModelsDirectory().listFiles()?.sumOf { it.length() } ?: 0L
        } catch (e: Exception) {
            Timber.e(e, "Error calculating models size")
            0L
        }
    }

    /**
     * Clear download cache
     */
    fun clearCache() {
        try {
            getModelsDirectory().listFiles()?.forEach { file ->
                if (file.name.endsWith(".tmp")) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error clearing cache")
        }
    }

    /**
     * Format file size for display
     */
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / 1024 / 1024} MB"
            else -> "${bytes / 1024 / 1024 / 1024} GB"
        }
    }
}