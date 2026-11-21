# Contributing to Kestrel Terminal

Contributions across the Android Kotlin/Compose app, C/C++ NDK PTY engine, Glance widgets, and CI workflows are welcome.

## Development Prerequisites

- **JDK 17** (Temurin / OpenJDK 17)
- **Android SDK Platform 35** & **Build Tools 35.0.0**
- **Android NDK 27.2.12479018** & **CMake 3.30.5**
- Physical ARM64 Android device (`arm64-v8a`, API 26+) or ARM64 emulator for runtime verification

## Workflow

1. Fork the repository and create a topic branch (`git checkout -b feat/my-feature`).
2. Keep machine-specific paths out of Git (`android/local.properties` and `android/keystore/*.jks` are ignored by `.gitignore`).
3. Verify formatting, unit tests, and APK builds before opening a pull request:
   ```bash
   cd android
   ./gradlew testDebugUnitTest assembleDebug
   ```
4. Ensure all pull requests pass the [GitHub Actions Android CI workflow](.github/workflows/build-android.yml).

## Code Style

- Follow standard Kotlin official coding conventions (`kotlin.code.style=official`).
- Keep C/C++ PTY and CLI code warning-free under `-Wall` and PIE-compiled (`-fPIE -pie`).
- Respect Android scoped storage and avoid requesting unnecessary permissions.
