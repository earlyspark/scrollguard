#include "../include/llama_wrapper.h"
#include <android/log.h>
#include <chrono>
#include <algorithm>
#include <regex>
#include <thread>
#include <fstream>
#include <cctype>

#define LOG_TAG "ScrollGuard-LLama"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace scrollguard {

/**
 * Private implementation for LlamaWrapper using PIMPL pattern
 * Supports both real llama.cpp and fallback heuristic modes
 */
class LlamaWrapper::Impl {
public:
    Impl() : model_loaded_(false), llama_available_(false) {
#ifdef LLAMA_CPP_AVAILABLE
        llama_available_ = true;
        LOGD("LlamaWrapper::Impl created with llama.cpp support");
#else
        LOGD("LlamaWrapper::Impl created in fallback mode (no llama.cpp)");
#endif
    }
    
    ~Impl() {
        LOGD("LlamaWrapper::Impl destroyed");
        unload_model();
    }

    bool load_model(const ModelConfig& config) {
        LOGD("Loading model from: %s", config.model_path.c_str());
        
        model_config_ = config;
        
#ifdef LLAMA_CPP_AVAILABLE
        if (llama_available_) {
            return load_llama_model(config);
        }
#endif
        
        // Fallback mode - just validate file exists
        std::ifstream file(config.model_path);
        if (!file.good()) {
            LOGE("Model file not found: %s", config.model_path.c_str());
            return false;
        }
        
        // Simulate loading time
        std::this_thread::sleep_for(std::chrono::milliseconds(100));
        
        model_loaded_ = true;
        LOGD("Model loaded successfully (fallback mode)");
        return true;
    }

    bool is_model_loaded() const {
        return model_loaded_;
    }

    void unload_model() {
        if (model_loaded_) {
            LOGD("Unloading model");
            
#ifdef LLAMA_CPP_AVAILABLE
            if (llama_available_ && ctx_) {
                llama_free(ctx_);
                ctx_ = nullptr;
            }
            if (llama_available_ && model_) {
                llama_model_free(model_);
                model_ = nullptr;
            }
#endif
            
            model_loaded_ = false;
        }
    }

    ClassificationResult classify_content(
        const std::string& content,
        const std::string& context
    ) {
        auto start_time = std::chrono::steady_clock::now();
        
        ClassificationResult result;
        result.success = false;
        
        if (!model_loaded_) {
            result.error_message = "Model not loaded";
            return result;
        }

        if (content.empty()) {
            result.error_message = "Empty content";
            return result;
        }

        LOGD("Classifying content: %.50s%s", 
             content.c_str(), 
             content.length() > 50 ? "..." : "");

        try {
#ifdef LLAMA_CPP_AVAILABLE
            if (llama_available_ && ctx_) {
                result = classify_with_llama(content, context);
            } else {
                result = content_utils::classify_with_heuristics(content);
            }
#else
            result = content_utils::classify_with_heuristics(content);
#endif
            
        } catch (const std::exception& e) {
            LOGE("Classification error: %s", e.what());
            result.error_message = e.what();
            result.success = false;
        }
        
        auto end_time = std::chrono::steady_clock::now();
        result.processing_time_ms = static_cast<int>(
            std::chrono::duration_cast<std::chrono::milliseconds>(
                end_time - start_time
            ).count()
        );
        
        LOGD("Classification completed: productive=%s, confidence=%.2f, time=%dms",
             result.is_productive ? "true" : "false",
             result.confidence,
             result.processing_time_ms);
        
        return result;
    }

    void warm_up() {
        if (!model_loaded_) return;
        
        LOGD("Warming up model");
        
        // TODO: Implement actual warm-up with llama.cpp
        // For now, simulate a quick inference
        classify_content("warm up test", "");
    }

    size_t get_memory_usage() const {
        if (!model_loaded_) return 0;
        
#ifdef LLAMA_CPP_AVAILABLE
        if (llama_available_ && ctx_) {
            return llama_state_get_size(ctx_);
        }
#endif
        
        // Fallback estimate
        return 1024 * 1024 * 200; // 200MB estimate for fallback mode
    }

    void clear_cache() {
        // TODO: Clear llama.cpp KV cache
        LOGD("Clearing model cache");
    }

    std::string get_model_info() const {
        if (!model_loaded_) {
            return "Model not loaded";
        }
        
#ifdef LLAMA_CPP_AVAILABLE
        if (llama_available_) {
            return "llama.cpp model loaded: " + model_config_.model_path;
        }
#endif
        
        return "Fallback mode: " + model_config_.model_path;
    }
    
    bool is_llama_cpp_available() const {
        return llama_available_;
    }

private:
    bool model_loaded_;
    bool llama_available_;
    ModelConfig model_config_;
    
#ifdef LLAMA_CPP_AVAILABLE
    llama_model* model_ = nullptr;
    llama_context* ctx_ = nullptr;
    
    bool load_llama_model(const ModelConfig& config) {
        try {
            // Initialize llama backend
            llama_backend_init();
            
            // Load model
            llama_model_params model_params = llama_model_default_params();
            model_params.use_mmap = config.use_mmap;
            model_params.use_mlock = config.use_mlock;
            model_params.n_gpu_layers = config.n_gpu_layers;
            
            model_ = llama_model_load_from_file(config.model_path.c_str(), model_params);
            if (!model_) {
                LOGE("Failed to load model from %s", config.model_path.c_str());
                return false;
            }
            
            // Create context
            llama_context_params ctx_params = llama_context_default_params();
            ctx_params.n_ctx = config.n_ctx;
            ctx_params.n_threads = config.n_threads;
            
            ctx_ = llama_init_from_model(model_, ctx_params);
            if (!ctx_) {
                LOGE("Failed to create context");
                llama_model_free(model_);
                model_ = nullptr;
                return false;
            }
            
            model_loaded_ = true;
            LOGD("llama.cpp model loaded successfully");
            return true;
            
        } catch (const std::exception& e) {
            LOGE("Exception loading llama model: %s", e.what());
            return false;
        }
    }
    
    ClassificationResult classify_with_llama(const std::string& content, const std::string& context) {
        auto start_time = std::chrono::steady_clock::now();
        
        ClassificationResult result;
        result.success = false;
        
        try {
            // Prepare prompt
            std::string prompt = content_utils::generate_classification_prompt(content);
            
            // For now, use a simplified approach since the API has changed significantly
            // In a real implementation, we would use the new batch API properly
            LOGD("Using simplified classification for content: %.100s", prompt.c_str());
            
            // Simple heuristic-based classification as fallback
            // This ensures we have working functionality while the full llama.cpp integration is developed
            std::string lower_prompt = prompt;
            std::transform(lower_prompt.begin(), lower_prompt.end(), lower_prompt.begin(), ::tolower);
            
            // Count productive vs unproductive keywords
            std::vector<std::string> productive_keywords = {
                "learn", "education", "research", "study", "analysis", "work", "project",
                "development", "programming", "science", "technology", "business"
            };
            
            std::vector<std::string> unproductive_keywords = {
                "funny", "meme", "viral", "trending", "celebrity", "gossip", "drama",
                "entertainment", "game", "fun", "party", "social"
            };
            
            int productive_score = 0;
            int unproductive_score = 0;
            
            for (const auto& keyword : productive_keywords) {
                if (lower_prompt.find(keyword) != std::string::npos) {
                    productive_score++;
                }
            }
            
            for (const auto& keyword : unproductive_keywords) {
                if (lower_prompt.find(keyword) != std::string::npos) {
                    unproductive_score++;
                }
            }
            
            result.is_productive = productive_score >= unproductive_score;
            float total_score = productive_score + unproductive_score;
            result.confidence = total_score > 0 ? std::abs(productive_score - unproductive_score) / total_score : 0.5f;
            result.reason = "llama_heuristic_fallback";
            result.success = true;
            
        } catch (const std::exception& e) {
            LOGE("Exception in llama classification: %s", e.what());
            result.error_message = e.what();
            result.success = false;
        }
        
        auto end_time = std::chrono::steady_clock::now();
        result.processing_time_ms = static_cast<int>(
            std::chrono::duration_cast<std::chrono::milliseconds>(
                end_time - start_time
            ).count()
        );
        
        return result;
    }
#endif
};

// LlamaWrapper implementation

LlamaWrapper::LlamaWrapper() : pimpl_(std::make_unique<Impl>()) {
}

LlamaWrapper::~LlamaWrapper() = default;

bool LlamaWrapper::load_model(const ModelConfig& config) {
    return pimpl_->load_model(config);
}

bool LlamaWrapper::is_model_loaded() const {
    return pimpl_->is_model_loaded();
}

void LlamaWrapper::unload_model() {
    pimpl_->unload_model();
}

ClassificationResult LlamaWrapper::classify_content(
    const std::string& content,
    const std::string& context
) {
    return pimpl_->classify_content(content, context);
}

void LlamaWrapper::warm_up() {
    pimpl_->warm_up();
}

size_t LlamaWrapper::get_memory_usage() const {
    return pimpl_->get_memory_usage();
}

void LlamaWrapper::clear_cache() {
    pimpl_->clear_cache();
}

std::string LlamaWrapper::get_model_info() const {
    return pimpl_->get_model_info();
}

bool LlamaWrapper::is_llama_cpp_available() const {
    return pimpl_->is_llama_cpp_available();
}

// Content utilities implementation
namespace content_utils {

ClassificationResult classify_with_heuristics(const std::string& content) {
    auto start_time = std::chrono::steady_clock::now();
    
    std::string lower_content = content;
    std::transform(lower_content.begin(), lower_content.end(), 
                  lower_content.begin(), ::tolower);
    
    // Remove extra whitespace and normalize
    std::regex whitespace_regex("\\s+");
    lower_content = std::regex_replace(lower_content, whitespace_regex, " ");
    
    // Check for unproductive content patterns
    bool is_productive = true;
    std::string reason = "neutral_content";
    float confidence = 0.6f;
    
    // Unproductive patterns (stronger indicators)
    std::vector<std::pair<std::string, float>> unproductive_patterns = {
        {"you won't believe", 0.9f},
        {"shocking", 0.8f},
        {"viral", 0.7f},
        {"trending", 0.7f},
        {"clickbait", 0.9f},
        {"drama", 0.7f},
        {"gossip", 0.8f},
        {"must see", 0.7f},
        {"watch this", 0.6f},
        {"epic fail", 0.8f},
        {"omg", 0.6f},
        {"wtf", 0.7f},
        {"insane", 0.7f},
        {"crazy", 0.6f}
    };
    
    // Productive patterns (stronger indicators)
    std::vector<std::pair<std::string, float>> productive_patterns = {
        {"how to", 0.9f},
        {"tutorial", 0.9f},
        {"learn", 0.8f},
        {"education", 0.9f},
        {"guide", 0.8f},
        {"research", 0.9f},
        {"analysis", 0.8f},
        {"study", 0.8f},
        {"insight", 0.8f},
        {"explanation", 0.8f},
        {"understand", 0.7f},
        {"science", 0.8f},
        {"technology", 0.7f},
        {"knowledge", 0.8f}
    };
    
    float max_unproductive_score = 0.0f;
    float max_productive_score = 0.0f;
    
    // Check unproductive patterns
    for (const auto& pattern : unproductive_patterns) {
        if (lower_content.find(pattern.first) != std::string::npos) {
            max_unproductive_score = std::max(max_unproductive_score, pattern.second);
        }
    }
    
    // Check productive patterns
    for (const auto& pattern : productive_patterns) {
        if (lower_content.find(pattern.first) != std::string::npos) {
            max_productive_score = std::max(max_productive_score, pattern.second);
        }
    }
    
    // Determine classification
    if (max_productive_score > max_unproductive_score) {
        is_productive = true;
        confidence = max_productive_score;
        reason = "educational_keywords";
    } else if (max_unproductive_score > max_productive_score) {
        is_productive = false;
        confidence = max_unproductive_score;
        reason = "unproductive_keywords";
    } else if (max_unproductive_score > 0 && max_productive_score > 0) {
        // Mixed content - lean towards productive
        is_productive = true;
        confidence = 0.5f;
        reason = "mixed_content";
    }
    
    // Additional heuristics
    
    // Check for excessive caps (indicates shouting/clickbait)
    int caps_count = 0;
    int letter_count = 0;
    for (char c : content) {
        if (std::isalpha(c)) {
            letter_count++;
            if (std::isupper(c)) {
                caps_count++;
            }
        }
    }
    
    if (letter_count > 0 && (float)caps_count / letter_count > 0.5f) {
        is_productive = false;
        confidence = std::max(confidence, 0.7f);
        reason = "excessive_caps";
    }
    
    // Check for excessive punctuation (!!!, ???)
    size_t exclamation_count = std::count(content.begin(), content.end(), '!');
    size_t question_count = std::count(content.begin(), content.end(), '?');
    
    if (exclamation_count > 3 || question_count > 3) {
        is_productive = false;
        confidence = std::max(confidence, 0.6f);
        reason = "excessive_punctuation";
    }
    
    auto end_time = std::chrono::steady_clock::now();
    int processing_time = static_cast<int>(
        std::chrono::duration_cast<std::chrono::milliseconds>(
            end_time - start_time
        ).count()
    );
    
    ClassificationResult result;
    result.is_productive = is_productive;
    result.confidence = confidence;
    result.reason = reason;
    result.processing_time_ms = processing_time;
    result.success = true;
    
    return result;
}

std::string prepare_content_for_analysis(const std::string& raw_content) {
    std::string content = raw_content;
    
    // Remove excessive whitespace
    std::regex whitespace_regex("\\s+");
    content = std::regex_replace(content, whitespace_regex, " ");
    
    // Trim
    content.erase(0, content.find_first_not_of(" \t\n\r"));
    content.erase(content.find_last_not_of(" \t\n\r") + 1);
    
    // Limit length for processing
    if (content.length() > 500) {
        content = content.substr(0, 500) + "...";
    }
    
    return content;
}

std::string generate_classification_prompt(const std::string& content) {
    std::string prompt = 
        "Classify this social media content as PRODUCTIVE or UNPRODUCTIVE.\n\n"
        "PRODUCTIVE content: educational, informative, constructive, helpful\n"
        "UNPRODUCTIVE content: clickbait, gossip, drama, time-wasting\n\n"
        "Content: \"" + prepare_content_for_analysis(content) + "\"\n\n"
        "Classification:";
    
    return prompt;
}

} // namespace content_utils

} // namespace scrollguard