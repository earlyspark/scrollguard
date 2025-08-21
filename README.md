# ScrollGuard

**AI-powered social media content filter for productivity**

ScrollGuard is an Android accessibility service that uses local AI to intelligently filter unproductive content from social media apps in real-time. All AI processing happens on-device for complete privacy.

![Platform](https://img.shields.io/badge/platform-Android-green.svg)
![API Level](https://img.shields.io/badge/API-30%2B-blue.svg)
![Kotlin](https://img.shields.io/badge/language-Kotlin-orange.svg)
![License](https://img.shields.io/badge/license-MIT-blue.svg)

## Features

### 🛡️ Privacy-First Design
- **100% Local Processing**: All content analysis happens on your device
- **No Data Collection**: Your social media content never leaves your phone
- **Encrypted Storage**: User preferences stored with Android's security-crypto

### 🤖 AI-Powered Filtering
- **Local LLM Integration**: Uses llama.cpp for on-device inference
- **Real-Time Analysis**: Analyzes content as you scroll
- **Adaptive Learning**: Improves accuracy based on your feedback
- **Custom Sensitivity**: Adjustable filtering thresholds

### 📱 Multi-Platform Support
- Instagram
- TikTok
- Twitter/X
- Reddit
- YouTube
- Facebook
- Snapchat
- LinkedIn

### 📊 Progress Tracking
- Daily filtering statistics
- Time saved metrics
- Weekly progress summaries
- Filtering accuracy reports

## Screenshots

| Dashboard | Settings | Content Overlay |
|-----------|----------|-----------------|
| ![Dashboard](screenshots/dashboard.png) | ![Settings](screenshots/settings.png) | ![Overlay](screenshots/overlay.png) |

## Installation

### Prerequisites
- Android 11 (API 30) or higher
- 4GB+ RAM for optimal performance
- ~500MB free storage (for app + AI model)

### Build from Source

1. **Clone the repository**
   ```bash
   git clone https://github.com/earlyspark/scrollguard.git
   cd scrollguard
   ```

2. **Initialize submodules**
   ```bash
   git submodule update --init --recursive
   ```

3. **Set up development environment**
   ```bash
   # Set Java and Android SDK paths
   export JAVA_HOME=/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
   export ANDROID_HOME=$HOME/Library/Android/sdk
   ```

4. **Create local.properties**
   ```properties
   sdk.dir=/path/to/your/Android/sdk
   ```

5. **Build the app**
   ```bash
   ./gradlew assembleDebug
   ```

6. **Install on device**
   ```bash
   ./gradlew installDebug
   ```

## Setup & Usage

### 1. Enable Accessibility Service
1. Open ScrollGuard app
2. Follow the onboarding flow
3. Grant accessibility permission in Android Settings
4. Enable "ScrollGuard Content Filter" service

### 2. Download AI Model
- The app will prompt you to download the ~250MB AI model
- WiFi connection recommended for download
- Model is stored locally and never uploaded

### 3. Configure Filtering
- Choose which social media apps to monitor
- Adjust sensitivity levels (Low/Medium/High)
- Set confidence thresholds for filtering

### 4. Start Filtering
- Toggle filtering on from the main dashboard
- ScrollGuard runs in the background
- Filtered content appears with overlay options

## Technology Stack

### Android Framework
- **Kotlin** - Primary language
- **Material Design 3** - UI framework
- **AndroidX** - Modern Android libraries
- **Room Database** - Local data persistence
- **Work Manager** - Background processing

### AI & ML
- **llama.cpp** - Local LLM inference engine
- **GGUF Models** - Optimized model format
- **JNI Bridge** - Native integration
- **ARM Optimization** - Mobile-specific optimizations

### Privacy & Security
- **androidx.security-crypto** - Encrypted preferences
- **Local-only processing** - No network requests for content
- **Accessibility APIs** - System-level content access

## Architecture

```
ScrollGuard App Architecture
│
├── UI Layer (Activities, Fragments)
│   ├── MainActivity - Dashboard & controls
│   ├── OnboardingActivity - Setup wizard
│   ├── SettingsActivity - Configuration
│   └── ContentOverlayManager - Real-time overlays
│
├── Service Layer
│   ├── ContentFilterService - Accessibility service
│   ├── LLMInferenceService - Background AI processing
│   └── AnalyticsManager - Privacy-safe metrics
│
├── Data Layer
│   ├── Room Database - Encrypted local storage
│   ├── ContentRepository - Analysis caching
│   └── PreferencesRepository - User settings
│
└── Native Layer (C++)
    ├── llama.cpp integration
    ├── JNI bridges
    └── ARM-optimized inference
```

## Development

### Building Native Components
```bash
# Build only native libraries
./gradlew externalNativeBuildDebug

# Clean native build
./gradlew cleanExternalNativeBuildDebug
```

### Running Tests
```bash
# Unit tests
./gradlew test

# Instrumented tests (requires device)
./gradlew connectedAndroidTest

# Test coverage
./gradlew testDebugUnitTestCoverage
```

### Adding New Platform Support
1. Add package name to `SUPPORTED_PACKAGES` in `ContentFilterService.kt`
2. Update `accessibility_service_config.xml`
3. Implement content extraction in `AccessibilityNodeHelper.kt`
4. Test on target platform

## Privacy & Security

ScrollGuard is designed with privacy as the top priority:

- **Local Processing Only**: Content never leaves your device
- **No Analytics by Default**: Optional, anonymized usage metrics only
- **Encrypted Storage**: All user data encrypted at rest
- **Open Source**: Transparent, auditable codebase
- **Minimal Permissions**: Only accessibility service required

## Performance

- **Target Response Time**: <200ms per content analysis
- **Memory Usage**: <500MB during active filtering
- **Battery Impact**: <5% additional drain per day
- **Model Size**: ~250MB GGUF format

## Contributing

We welcome contributions! Please see our [Contributing Guidelines](CONTRIBUTING.md) for details.

### Development Setup
1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Follow the build instructions above
4. Make your changes and test thoroughly
5. Commit using conventional commits (`feat:`, `fix:`, `docs:`, etc.)
6. Push to your branch and create a Pull Request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- [llama.cpp](https://github.com/ggml-org/llama.cpp) - Local LLM inference
- [Material Design](https://material.io/) - UI design system
- [Android Accessibility](https://developer.android.com/guide/topics/ui/accessibility) - Content access APIs

## Support

- 📧 Email: support@scrollguard.app
- 🐛 Bug Reports: [GitHub Issues](https://github.com/earlyspark/scrollguard/issues)
- 💬 Discussions: [GitHub Discussions](https://github.com/earlyspark/scrollguard/discussions)

---

**Made with ❤️ for digital wellbeing and productivity**