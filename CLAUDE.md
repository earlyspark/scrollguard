# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Global Instruction Override

**CRITICAL**: This project follows the global CLAUDE.md instructions. Most importantly:
- **Leave NO TODOs, placeholders, or missing pieces** in any code
- All code must be fully functional and complete
- No placeholder implementations allowed, even for development

## Project Overview

ScrollGuard is an Android accessibility service that uses local AI (llama.cpp) to intelligently filter unproductive content from social media apps in real-time. All AI processing happens on-device for complete privacy. The app targets social media platforms like Instagram, TikTok, Twitter, Reddit, YouTube, Facebook, Snapchat, and LinkedIn.

## Development Environment Setup

### Prerequisites
- Java JDK 17 or newer
- Android SDK with Command Line Tools
- CMake 3.22.1+ (for native library compilation)
- Android device or emulator with API 30+
- 4GB+ RAM for optimal LLM performance

### Environment Variables
```bash
export JAVA_HOME=/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=$HOME/Library/Android/sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator
```

### Local Development Setup
The project requires a `local.properties` file in the root directory:
```properties
sdk.dir=/Users/earlyspark/Library/Android/sdk
```

### Build Commands
```bash
# Build the app (debug) - VERIFIED WORKING
JAVA_HOME=/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew assembleDebug

# Build release version
JAVA_HOME=/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew assembleRelease

# Install to connected device/emulator
JAVA_HOME=/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew installDebug

# Clean build
./gradlew clean

# Generate Gradle wrapper (working version 8.9)
gradle wrapper --gradle-version 8.9
```

### Testing Commands
```bash
# Run unit tests
./gradlew test

# Run instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest

# Run specific test class
./gradlew test --tests="*YourTestClass*"

# Run tests with coverage
./gradlew testDebugUnitTestCoverage
```

### Native Library Development
```bash
# Build only native components
./gradlew externalNativeBuildDebug

# Clean native build
./gradlew cleanExternalNativeBuildDebug

# Debug native build issues
./gradlew externalNativeBuildDebug --info
```

## Architecture Overview

### Core Components

**AccessibilityService Layer** (`ContentFilterService.kt`)
- Main entry point that monitors target social media apps
- Extracts text content from UI elements using accessibility APIs
- Manages content processing pipeline and overlay display
- Handles service lifecycle and permission management

**LLM Inference Layer** (`service/llm/`)
- `LlamaInferenceManager.kt`: High-level interface for content classification
- `LlamaInference.kt`: JNI bridge to native llama.cpp implementation
- `ModelDownloadManager.kt`: Handles GGUF model downloading and management
- Uses custom C++ native library with optimized ARM builds

**Data Layer** (`data/`)
- Room database with encrypted storage for user preferences
- `ContentRepository.kt`: Manages content analysis caching and history
- `PreferencesRepository.kt`: User settings and learning adaptations
- Database entities: `ContentAnalysis`, `UserPreferences`, `FilterSession`, `DailySummary`

**UI Layer** (`ui/`)
- `LauncherActivity.kt`: Splash screen and initial setup
- `MainActivity.kt`: Main dashboard and service controls
- `OnboardingActivity.kt`: Accessibility permission setup wizard
- `SettingsActivity.kt`: Filter configuration and preferences
- `ContentOverlayManager.kt`: Real-time content overlay system

**Native Layer** (`src/main/cpp/`)
- JNI wrappers for llama.cpp integration
- CMake build system with ARM optimization
- Git submodule for llama.cpp at `app/src/main/cpp/llama.cpp`

### Key Design Patterns

**Privacy-First Architecture**: All content processing happens locally. No user content ever leaves the device. Analytics only collect aggregated, anonymized metrics.

**Adaptive Learning**: User feedback (swipes, manual overrides) feeds back into classification confidence weighting stored in encrypted local database.

**Multi-App Support**: Social media detection and content extraction works across major platforms using accessibility service APIs.

**Performance Optimization**: 
- Content caching to avoid re-analyzing identical posts
- Background processing with foreground service
- Memory management for continuous LLM inference
- Optimized native libraries for ARM processors

## Development Workflow

### Adding New Social Media Platform Support
1. Add package name to `SUPPORTED_PACKAGES` in `ContentFilterService.kt`
2. Update `accessibility_service_config.xml` with new package name
3. Implement platform-specific content extraction in `AccessibilityNodeHelper.kt`
4. Add platform detection logic in `SocialMediaDetector.kt`
5. Test extraction accuracy on target platform

### Modifying Content Classification
1. Update classification prompts in `LlamaInferenceManager.kt`
2. Adjust confidence thresholds in `ContentAnalysis.kt`
3. Test classification accuracy with various content types
4. Update user feedback integration if needed

### Native Library Changes
1. Modify C++ source files in `src/main/cpp/jni/`
2. Update CMakeLists.txt if adding new source files
3. Rebuild native library: `./gradlew externalNativeBuildDebug`
4. Test JNI bridge functionality with Kotlin code

### Database Schema Changes
1. Update entity models in `data/model/`
2. Create migration in `ScrollGuardDatabase.kt`
3. Update corresponding DAO interfaces
4. Test migration with existing user data

## Critical Architecture Notes

### llama.cpp Integration
- Native library built as git submodule at `app/src/main/cpp/llama.cpp`
- Requires 3B parameter GGUF model (~2GB) stored in app's internal storage
- JNI bridge provides Kotlin interface to C++ inference engine
- CMake configuration includes platform-specific ARM optimizations

### Accessibility Service Requirements
- Service declaration must include all target app package names
- Requires `BIND_ACCESSIBILITY_SERVICE` permission
- Must handle service interruption and recovery gracefully
- Google Play Store requires detailed privacy policy for accessibility services

### Privacy & Security
- All content analysis happens on-device using local LLM
- User preferences stored in encrypted SharedPreferences
- No network requests contain user content
- Analytics follow strict privacy guidelines (see ANALYTICS_REFERENCE.md)

### Performance Considerations
- Target <200ms classification time per content item
- Maximum 500MB memory usage during active filtering
- Battery impact <5% additional drain per day
- Concurrent processing limited to 3 simultaneous analyses

## File Structure Insights

### Package Organization
```
com.scrollguard.app/
├── data/           # Data layer (Room DB, repositories, models)
├── service/        # Background services (accessibility, LLM)
├── ui/            # User interface (activities, fragments, dialogs)
└── util/          # Utility classes (helpers, error handling)
```

### Resource Dependencies
- String resources in `res/values/strings.xml` (app name, UI text, accessibility descriptions)
- Drawable resources include notification icons and UI elements
- Layouts for activities, dialogs, and overlay system
- Accessibility service configuration in `res/xml/accessibility_service_config.xml`

### Native Build Dependencies
- Requires llama.cpp submodule initialization: `git submodule update --init --recursive`
- CMake 3.22.1+ for native library compilation
- NDK version 26.1.10909125 as specified in gradle.properties
- ARM architecture optimizations for arm64-v8a and armeabi-v7a

## ✅ BUILD SUCCESS STATUS

### Verified Working Build (August 2025)
The ScrollGuard app **successfully compiles** with the following confirmed working configuration:
- **Gradle Version**: 8.9 (required for Android Gradle Plugin 8.7.2)
- **Java Version**: OpenJDK 17 at `/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`
- **Android SDK**: Located at `$HOME/Library/Android/sdk`
- **Build Time**: ~32 seconds for full debug build
- **Output**: Generates functional APK with native libraries included

### Key Architecture Components Successfully Built
- **Room Database**: All TypeConverters and DAOs compile correctly
- **Accessibility Service**: Properly configured for target social media apps
- **Native Libraries**: llama.cpp JNI bindings build for ARM architectures
- **Material3 UI**: All themes, layouts, and components functional
- **Security Integration**: androidx.security-crypto for encrypted storage working
- **Firebase Analytics**: Optional analytics framework properly integrated

### Build Warnings (Non-blocking)
- Room schema export disabled (acceptable for development)
- Some deprecated Firebase Analytics APIs (functionality preserved)
- Deprecated accessibility methods (backward compatibility maintained)

## Troubleshooting Notes

### Compilation Issues RESOLVED ✅
- **Repository conflicts**: Fixed by removing duplicate repositories from `build.gradle.kts`
- **Missing TypeConverters**: Added Map<ContentType, Int> converter for Room database
- **Import issues**: Fixed MasterKey imports by updating androidx.security version
- **Progress indicator issues**: Replaced LinearProgressIndicator with standard ProgressBar
- **Suspend function calls**: Wrapped in proper coroutine scope in Application class

### Development Environment Requirements
- Java 17 at specific path: `/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`
- Android SDK at: `/Users/earlyspark/Library/Android/sdk`
- CMake 3.22.1+ for native library compilation
- NDK version 26.1.10909125 (auto-installed during build)

### Testing Environment
- Requires Android device/emulator with API 30+ for accessibility service testing
- Social media apps must be installed for content extraction testing
- Accessibility service requires manual enablement in Android Settings
- Model file (~2GB) must be available for LLM inference testing

### Privacy Compliance
- Follow analytics guidelines in ANALYTICS_REFERENCE.md strictly
- Never log actual user content in debug output
- Ensure all analytics data is aggregated and anonymized
- Test data sanitization thoroughly before any analytics implementation