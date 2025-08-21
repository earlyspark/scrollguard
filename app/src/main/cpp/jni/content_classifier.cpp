#include "../include/llama_wrapper.h"
#include <android/log.h>
#include <string>
#include <vector>
#include <algorithm>

#define LOG_TAG "ScrollGuard-Classifier"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace scrollguard {

/**
 * Content classifier implementation
 * Provides specialized classification logic for different content types
 */
class ContentClassifier {
public:
    enum ContentCategory {
        NEWS,
        ENTERTAINMENT,
        EDUCATIONAL,
        SOCIAL,
        COMMERCIAL,
        UNKNOWN
    };
    
    struct ClassificationContext {
        std::string app_package;
        ContentCategory category;
        std::string language;
        int content_length;
    };
    
    static ClassificationResult classify_with_context(
        const std::string& content,
        const ClassificationContext& context
    ) {
        LOGD("Classifying content with context: app=%s, category=%d", 
             context.app_package.c_str(), static_cast<int>(context.category));
        
        // Use enhanced heuristics based on context
        auto result = content_utils::classify_with_heuristics(content);
        
        // Apply context-specific adjustments
        result = apply_context_adjustments(result, context);
        
        return result;
    }
    
    static ContentCategory determine_category(const std::string& content) {
        std::string lower_content = content;
        std::transform(lower_content.begin(), lower_content.end(), 
                      lower_content.begin(), ::tolower);
        
        // News indicators
        std::vector<std::string> news_keywords = {
            "breaking", "news", "report", "according to", "sources say",
            "announcement", "official", "statement", "update"
        };
        
        // Educational indicators
        std::vector<std::string> educational_keywords = {
            "learn", "tutorial", "how to", "guide", "explanation",
            "research", "study", "analysis", "science", "education"
        };
        
        // Entertainment indicators
        std::vector<std::string> entertainment_keywords = {
            "funny", "hilarious", "meme", "viral", "trending",
            "celebrity", "movie", "music", "game", "fun"
        };
        
        // Commercial indicators
        std::vector<std::string> commercial_keywords = {
            "buy", "sale", "discount", "offer", "deal", "price",
            "product", "review", "sponsored", "ad"
        };
        
        int news_score = count_keywords(lower_content, news_keywords);
        int educational_score = count_keywords(lower_content, educational_keywords);
        int entertainment_score = count_keywords(lower_content, entertainment_keywords);
        int commercial_score = count_keywords(lower_content, commercial_keywords);
        
        int max_score = std::max({news_score, educational_score, entertainment_score, commercial_score});
        
        if (max_score == 0) return UNKNOWN;
        if (max_score == news_score) return NEWS;
        if (max_score == educational_score) return EDUCATIONAL;
        if (max_score == entertainment_score) return ENTERTAINMENT;
        if (max_score == commercial_score) return COMMERCIAL;
        
        return SOCIAL; // Default fallback
    }
    
private:
    static int count_keywords(const std::string& content, const std::vector<std::string>& keywords) {
        int count = 0;
        for (const auto& keyword : keywords) {
            if (content.find(keyword) != std::string::npos) {
                count++;
            }
        }
        return count;
    }
    
    static ClassificationResult apply_context_adjustments(
        ClassificationResult result,
        const ClassificationContext& context
    ) {
        // Adjust confidence based on app context
        if (context.app_package.find("linkedin") != std::string::npos) {
            // LinkedIn content is more likely to be productive
            if (result.is_productive) {
                result.confidence = std::min(1.0f, result.confidence + 0.2f);
                result.reason += "_linkedin_boost";
            }
        } else if (context.app_package.find("tiktok") != std::string::npos) {
            // TikTok content is more likely to be unproductive
            if (!result.is_productive) {
                result.confidence = std::min(1.0f, result.confidence + 0.1f);
                result.reason += "_tiktok_penalty";
            }
        }
        
        // Adjust based on content category
        switch (context.category) {
            case EDUCATIONAL:
                if (result.is_productive) {
                    result.confidence = std::min(1.0f, result.confidence + 0.15f);
                    result.reason += "_educational_boost";
                }
                break;
            case ENTERTAINMENT:
                if (!result.is_productive) {
                    result.confidence = std::min(1.0f, result.confidence + 0.1f);
                    result.reason += "_entertainment_penalty";
                }
                break;
            case NEWS:
                // News can be productive or unproductive depending on content
                // No adjustment
                break;
            case COMMERCIAL:
                // Commercial content is generally unproductive
                if (!result.is_productive) {
                    result.confidence = std::min(1.0f, result.confidence + 0.2f);
                    result.reason += "_commercial_penalty";
                }
                break;
            default:
                break;
        }
        
        // Adjust based on content length
        if (context.content_length < 50) {
            // Very short content is less reliable
            result.confidence *= 0.8f;
            result.reason += "_short_content";
        } else if (context.content_length > 500) {
            // Longer content might be more substantial
            if (result.is_productive) {
                result.confidence = std::min(1.0f, result.confidence + 0.1f);
                result.reason += "_long_content_boost";
            }
        }
        
        return result;
    }
};

// Public interface functions
ClassificationResult classify_content_with_context(
    const std::string& content,
    const std::string& app_package,
    const std::string& context_info
) {
    ContentClassifier::ClassificationContext context;
    context.app_package = app_package;
    context.category = ContentClassifier::determine_category(content);
    context.content_length = static_cast<int>(content.length());
    context.language = "en"; // Default to English, could be detected
    
    return ContentClassifier::classify_with_context(content, context);
}

std::string get_content_category_name(const std::string& content) {
    auto category = ContentClassifier::determine_category(content);
    
    switch (category) {
        case ContentClassifier::NEWS: return "news";
        case ContentClassifier::EDUCATIONAL: return "educational";
        case ContentClassifier::ENTERTAINMENT: return "entertainment";
        case ContentClassifier::SOCIAL: return "social";
        case ContentClassifier::COMMERCIAL: return "commercial";
        default: return "unknown";
    }
}

} // namespace scrollguard