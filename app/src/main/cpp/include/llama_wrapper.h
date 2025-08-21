#ifndef SCROLLGUARD_LLAMA_WRAPPER_H
#define SCROLLGUARD_LLAMA_WRAPPER_H

#include <jni.h>
#include <string>
#include <memory>

// Check if we have actual llama.cpp available
#ifdef LLAMA_CPP_AVAILABLE
#include "llama.h"
#include "ggml.h"
#endif

/**
 * C++ wrapper for llama.cpp functionality specifically for ScrollGuard.
 * This provides a clean interface between Java/Kotlin and the native llama.cpp library.
 * Supports both real llama.cpp integration and fallback placeholder mode.
 */

namespace scrollguard {

/**
 * Configuration for the LLM model
 */
struct ModelConfig {
    std::string model_path;
    int n_ctx = 2048;          // Context length
    int n_threads = 4;         // Number of threads
    float temperature = 0.1f;  // Low temperature for consistent classification
    int top_k = 1;            // Focus on most likely token
    float top_p = 0.1f;       // Low top_p for deterministic results
    bool use_mmap = true;     // Use memory mapping for efficiency
    bool use_mlock = false;   // Don't lock model in memory (mobile consideration)
    int n_gpu_layers = 0;     // CPU only on mobile
};

/**
 * Result of content classification
 */
struct ClassificationResult {
    bool is_productive;
    float confidence;
    std::string reason;
    int processing_time_ms;
    bool success;
    std::string error_message;
};

/**
 * Main wrapper class for llama.cpp functionality
 */
class LlamaWrapper {
public:
    LlamaWrapper();
    ~LlamaWrapper();

    // Model management
    bool load_model(const ModelConfig& config);
    bool is_model_loaded() const;
    void unload_model();
    
    // Content classification
    ClassificationResult classify_content(
        const std::string& content,
        const std::string& context = ""
    );
    
    // Performance utilities
    void warm_up();
    size_t get_memory_usage() const;
    void clear_cache();
    
    // Model information
    std::string get_model_info() const;
    bool is_llama_cpp_available() const;

private:
    class Impl;
    std::unique_ptr<Impl> pimpl_;
};

// Utility functions for content analysis
namespace content_utils {
    /**
     * Simple heuristic-based content classification fallback
     */
    ClassificationResult classify_with_heuristics(const std::string& content);
    
    /**
     * Extract and sanitize content for classification
     */
    std::string prepare_content_for_analysis(const std::string& raw_content);
    
    /**
     * Generate classification prompt for llama.cpp
     */
    std::string generate_classification_prompt(const std::string& content);
}

} // namespace scrollguard

#endif // SCROLLGUARD_LLAMA_WRAPPER_H