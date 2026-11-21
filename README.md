# Kestrel Terminal

**Native Android Terminal, ARM64 Linux Workspace, Code Editor & Developer Suite**

## Build and CI

GitHub Actions builds the debug APK, release APK, and Android App Bundle (AAB) on pushes and pull requests to `main` / `master`.

The workflow uses the Android SDK already installed on GitHub-hosted Ubuntu runners, installs the pinned SDK/NDK/CMake components, runs unit tests, builds the Android artifacts, verifies APK metadata, and uploads the results as `kestrel-android-artifacts`.

### Release signing (recommended for distributable updates)

The workflow intentionally does not generate a new release key on every run and does not contain a hard-coded keystore password.

To enable signed release APK/AAB builds, add these repository Actions secrets:

- `ANDROID_KEYSTORE_BASE64`: Base64-encoded contents of your private `.jks` or `.keystore` file.
- `ANDROID_KEYSTORE_PASSWORD`: keystore password.
- `ANDROID_KEY_ALIAS`: key alias.
- `ANDROID_KEY_PASSWORD`: private-key password.

Keep the keystore and passwords private. Never commit them to the repository. If the secrets are not configured, CI still builds the debug APK and unsigned release APK/AAB; the unsigned release cannot be installed as a normal signed app or used for Play Store updates until signed with your private release key.

### Create a release key locally

Run this command and enter a strong, unique password when prompted:

```bash
mkdir -p android/keystore
keytool -genkeypair -keystore android/keystore/kestrel-release.jks \
  -alias kestrel -keyalg RSA -keysize 2048 -validity 10000
```

Create `android/keystore/keystore.properties` locally with the following keys and your own values:

```properties
storeFile=keystore/kestrel-release.jks
storePassword=YOUR_PRIVATE_STORE_PASSWORD
keyAlias=kestrel
keyPassword=YOUR_PRIVATE_KEY_PASSWORD
```

The properties file and keystore are ignored by Git. Do not paste their contents into issues, pull requests, or chat.

### Download build outputs

Open the repository's **Actions** tab, select the latest successful **Build Kestrel Android APK & AAB** run, and download the `kestrel-android-artifacts` artifact.

