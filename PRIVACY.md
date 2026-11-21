# Privacy Policy — Kestrel Terminal

**Effective Date:** November 21, 2026  
**Application ID:** `com.developer.terminal`

Kestrel Terminal is designed as an offline-first, sandboxed Android terminal and developer environment. **Kestrel does not collect, sell, track, or transmit personal analytics, usage telemetry, crash beacons, or advertising identifiers.**

---

## 1. Local Storage & On-Device Data

All workspace artifacts stay exclusively inside your device's Android application sandbox (`filesDir`) unless you explicitly choose to export them:

- **Shell Environment (`$HOME`, `$PREFIX`):** Scripts, source code, downloaded packages, command history (`.kestrel_history`), and startup files (`.kestrelrc`).
- **Workspace Database (`kestrel_workspace.db`):** Room SQLite storage for installed package records, repository mirrors, package logs, environment variables, command aliases, terminal profiles, and scheduled automation tasks.
- **Preferences & Secrets (`SharedPreferences`):** UI appearance preferences (`kestrel_settings`) and optional user-entered credentials (`kestrel_secrets`), such as an AI API key or backup webhook URL, stored privately with `MODE_PRIVATE`.

---

## 2. Network Requests (User-Initiated Only)

Kestrel only initiates outbound network connections when you explicitly trigger a feature that requires networking:

| Feature | Destination | Purpose |
| :--- | :--- | :--- |
| **Package Manager (`Update` / `Install`)** | Configured Alpine Linux `aarch64` mirrors (`dl-cdn.alpinelinux.org` or custom repos) | Fetch `APKINDEX.tar.gz` and `.apk` archives |
| **HTTP Request Tester** | User-specified URL | Execute REST/HTTP requests via OkHttp |
| **Git & GitHub Client** | User-specified Git remotes or `api.github.com` | Clone repos or list repositories using optional PAT |
| **SSH Remote Exec** | User-specified SSH host & port | Execute remote shell commands over JSch SSH |
| **Optional Coding Assistant** | User-configured LLM endpoint (default Google Generative Language API) | Sends your prompt directly from device using your own API key |

No third-party analytics SDKs, ad networks, or background telemetry endpoints are bundled in the APK.

---

## 3. Android Permissions & Hardware Access

Kestrel requests only permissions directly tied to user-facing developer functionality:

- **`INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`**: Package downloads, HTTP/Git/SSH clients, and live network/Wi-Fi diagnostics on the Home dashboard and Network Status widget.
- **`FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` / `WAKE_LOCK`**: Keeps interactive PTY sessions and local HTTP server running reliably in the background when enabled.
- **`POST_NOTIFICATIONS` (Android 13+)**: Displays the ongoing foreground session status notification.
- **Storage (`SAF` / Optional `MANAGE_EXTERNAL_STORAGE`)**: Uses Android's Storage Access Framework (`OpenDocumentTree` / `CreateDocument`) by default so you grant access only to folders/files you pick. `MANAGE_EXTERNAL_STORAGE` is optional and never forced.
- **Root (`su`) Check**: Disabled by default. Only checks for `/system/bin/su` or similar binaries if you explicitly enable **Optional Root Binary Inspection** in **Settings**.

---

## 4. Data Deletion & Backups

- Deleting files in **Files** or uninstalling Kestrel permanently removes all sandboxed `$HOME`, `$PREFIX`, database records, and `SharedPreferences` from your device.
- Workspace backups created via **Settings -> Export ZIP Backup** are written only to the Storage Access Framework location you choose.

---

## 5. Contact

Questions or privacy disclosures can be filed in the repository issue tracker (`SECURITY.md` for security-sensitive reports).
