# Security Policy

## Scope & Android Sandbox Model

Kestrel (`com.developer.terminal`) targets **Android 15 (API 35)** with `minSdk = 26` and adheres to Android's W^X (`targetSdk >= 29`) executable binary model:

1. **Native Binaries in Read-Only `nativeLibraryDir`:** Both `libterminal_pty.so` (POSIX PTY JNI bridge) and `libkestrel_utils.so` (PIE native CLI helper) are compiled by Android NDK CMake and extracted with `extractNativeLibs="true"` into the verified APK `nativeLibraryDir`, rather than executing untrusted binaries from writable storage.
2. **ZIP Path Traversal Protection (`ZipSlip`):** Both `PackageManagerEngine` and `FileManagerRepository` / `DeveloperToolsEngine` canonicalize every archive entry path and reject any entry escaping the target `$PREFIX` or `$HOME` canonical directory.
3. **Localhost Binding for Dev Server:** The embedded static HTTP server binds strictly to `127.0.0.1` so local workspace files are never exposed over public Wi-Fi interfaces unintentionally.
4. **No Hardcoded Secrets or Embedded Root Exploits:** Root checking (`su`) is read-only, strictly opt-in, and Kestrel never attempts privilege escalation or bypasses SELinux / Android sandboxing.

## Reporting a Vulnerability

If you discover a security issue (e.g. path traversal in archive extraction, shell injection in terminal helpers, or credential exposure):

- **Do not** open a public issue with active exploit payloads.
- Open a private **GitHub Security Advisory** via the repository's **Security -> Advisories** tab, or attach a minimal reproduction note for maintainers to review before public disclosure.

## Supported Versions

| Version | Supported | Notes |
| :--- | :--- | :--- |
| `1.0.x` | Active | Targets `arm64-v8a`, Android API 26–35 |
