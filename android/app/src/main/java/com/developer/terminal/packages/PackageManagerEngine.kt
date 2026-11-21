package com.developer.terminal.packages

import android.content.Context
import android.os.Build
import com.developer.terminal.data.db.InstalledPackageEntity
import com.developer.terminal.data.db.KestrelDatabase
import com.developer.terminal.data.db.PackageLogEntity
import com.developer.terminal.data.db.PackageRepoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class AvailablePackage(
    val name: String, val version: String, val description: String, val architecture: String,
    val repository: String, val sizeBytes: Long, val dependencies: List<String>, val downloadUrl: String
)

data class PackageDownloadState(
    val packageName: String = "", val progress: Float = 0f, val stage: String = "", val active: Boolean = false
)

class PackageManagerEngine(private val context: Context, private val db: KestrelDatabase) {
    private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    private val prefixDir = File(context.filesDir, "usr").apply { mkdirs() }
    private val cacheDir = File(context.cacheDir, "pkg_cache").apply { mkdirs() }

    private val _catalog = MutableStateFlow<List<AvailablePackage>>(emptyList())
    val catalog: StateFlow<List<AvailablePackage>> = _catalog.asStateFlow()
    private val _downloadState = MutableStateFlow(PackageDownloadState())
    val downloadState: StateFlow<PackageDownloadState> = _downloadState.asStateFlow()
    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    fun observeInstalled(): Flow<List<InstalledPackageEntity>> = db.dao().observeInstalledPackages()
    fun observeRepos(): Flow<List<PackageRepoEntity>> = db.dao().observeRepositories()
    fun observeLogs(): Flow<List<PackageLogEntity>> = db.dao().observePackageLogs()

    suspend fun ensureDefaultReposAndNativeInventory() = withContext(Dispatchers.IO) {
        if (db.dao().getEnabledRepositories().isEmpty()) {
            db.dao().upsertRepository(PackageRepoEntity("alpine-main-aarch64", "Alpine Linux Main (aarch64)", "https://dl-cdn.alpinelinux.org/alpine/v3.20/main/aarch64", "https://dl-cdn.alpinelinux.org/alpine/v3.20/main/aarch64/APKINDEX.tar.gz", "aarch64", true))
            db.dao().upsertRepository(PackageRepoEntity("alpine-community-aarch64", "Alpine Linux Community (aarch64)", "https://dl-cdn.alpinelinux.org/alpine/v3.20/community/aarch64", "https://dl-cdn.alpinelinux.org/alpine/v3.20/community/aarch64/APKINDEX.tar.gz", "aarch64", true))
        }
        val nativeDir = context.applicationInfo.nativeLibraryDir
        db.dao().upsertPackage(InstalledPackageEntity("kestrel-coreutils", "1.0.0", "Bundled ARM64 native utilities (sysinfo, tree, wc, which) + POSIX PTY engine", Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a", "kestrel-bundled", File(nativeDir, "libkestrel_utils.so").length(), "", nativeDir, true, System.currentTimeMillis()))
        db.dao().upsertPackage(InstalledPackageEntity("android-sh-toybox", "API-${Build.VERSION.SDK_INT}", "System POSIX shell (/system/bin/sh) & Toybox CLI applets", Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a", "android-system", File("/system/bin/sh").length(), "", "/system/bin/sh", true, System.currentTimeMillis()))
    }

    suspend fun syncRepositories(): Result<Int> = withContext(Dispatchers.IO) {
        _syncing.value = true
        try {
            val parsed = mutableListOf<AvailablePackage>()
            for (repo in db.dao().getEnabledRepositories()) {
                client.newCall(Request.Builder().url(repo.indexUrl).build()).execute().use { resp ->
                    if (!resp.isSuccessful) { log(repo.name, "UPDATE", "ERROR", "HTTP ${resp.code}"); return@use }
                    val indexFile = File(cacheDir, "${repo.id}-APKINDEX.tar.gz")
                    resp.body?.byteStream()?.use { input -> FileOutputStream(indexFile).use { input.copyTo(it) } }
                    val pkgs = parseApkIndexTarGz(indexFile, repo)
                    parsed.addAll(pkgs)
                    log(repo.name, "UPDATE", "OK", "Indexed ${pkgs.size} aarch64 packages")
                }
            }
            _catalog.value = parsed.distinctBy { it.name }.sortedBy { it.name }
            Result.success(_catalog.value.size)
        } catch (t: Throwable) {
            log("repositories", "UPDATE", "ERROR", t.message ?: "Sync failed")
            Result.failure(t)
        } finally { _syncing.value = false }
    }

    private fun parseApkIndexTarGz(file: File, repo: PackageRepoEntity): List<AvailablePackage> {
        val list = mutableListOf<AvailablePackage>()
        GzipCompressorInputStream(BufferedInputStream(FileInputStream(file))).use { gz ->
            TarArchiveInputStream(gz).use { tar ->
                var entry = tar.nextEntry
                while (entry != null) {
                    if (entry.name == "APKINDEX") {
                        val content = tar.bufferedReader().readText()
                        for (block in content.split("\n\n")) {
                            if (block.isBlank()) continue
                            var name = ""; var ver = ""; var desc = ""; var arch = repo.arch; var size = 0L; var deps = emptyList<String>()
                            for (line in block.lines()) when {
                                line.startsWith("P:") -> name = line.drop(2).trim()
                                line.startsWith("V:") -> ver = line.drop(2).trim()
                                line.startsWith("T:") -> desc = line.drop(2).trim()
                                line.startsWith("A:") -> arch = line.drop(2).trim()
                                line.startsWith("S:") -> size = line.drop(2).trim().toLongOrNull() ?: 0L
                                line.startsWith("D:") -> deps = line.drop(2).trim().split(' ').filter { it.isNotBlank() && !it.startsWith("so:") && !it.startsWith("/bin/") }.map { it.substringBefore("=").substringBefore(">").substringBefore("<") }
                            }
                            if (name.isNotBlank() && ver.isNotBlank()) {
                                list.add(AvailablePackage(name, ver, desc.ifBlank { "Alpine aarch64 package" }, arch, repo.name, size, deps, "${repo.baseUrl.trimEnd('/')}/$name-$ver.apk"))
                            }
                        }
                        break
                    }
                    entry = tar.nextEntry
                }
            }
        }
        return list
    }

    suspend fun installPackage(pkg: AvailablePackage): Result<Unit> = withContext(Dispatchers.IO) {
        _downloadState.value = PackageDownloadState(pkg.name, 0.05f, "Resolving ${pkg.name}...", true)
        try {
            val catalogMap = _catalog.value.associateBy { it.name }
            val installedSet = db.dao().getInstalledPackages().map { it.name }.toMutableSet()
            for (dep in pkg.dependencies.take(5)) {
                if (dep !in installedSet) catalogMap[dep]?.let { downloadAndExtractSingle(it); installedSet.add(dep) }
            }
            downloadAndExtractSingle(pkg)
            Result.success(Unit)
        } catch (t: Throwable) {
            log(pkg.name, "INSTALL", "ERROR", t.message ?: "Failed")
            Result.failure(t)
        } finally { _downloadState.value = PackageDownloadState() }
    }

    private suspend fun downloadAndExtractSingle(pkg: AvailablePackage) {
        _downloadState.value = PackageDownloadState(pkg.name, 0.1f, "Downloading ${pkg.name} ${pkg.version}...", true)
        val archiveFile = File(cacheDir, "${pkg.name}-${pkg.version}.apk")
        client.newCall(Request.Builder().url(pkg.downloadUrl).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code} downloading ${pkg.downloadUrl}")
            val body = resp.body ?: throw IllegalStateException("Empty body")
            val total = body.contentLength().coerceAtLeast(pkg.sizeBytes).coerceAtLeast(1L)
            var downloaded = 0L
            body.byteStream().use { input ->
                FileOutputStream(archiveFile).use { output ->
                    val buf = ByteArray(16384)
                    while (true) {
                        val read = input.read(buf); if (read <= 0) break
                        output.write(buf, 0, read); downloaded += read
                        _downloadState.value = PackageDownloadState(pkg.name, (0.1f + 0.6f * downloaded / total).coerceIn(0.1f, 0.7f), "Downloading ${downloaded / 1024} KB...", true)
                    }
                }
            }
        }
        _downloadState.value = PackageDownloadState(pkg.name, 0.75f, "Extracting into \$PREFIX...", true)
        val pkgDir = File(prefixDir, "packages/${pkg.name}").apply { mkdirs() }
        var extractedFiles = 0
        GzipCompressorInputStream(BufferedInputStream(FileInputStream(archiveFile))).use { gz ->
            TarArchiveInputStream(gz).use { tar ->
                var entry = tar.nextEntry
                val canonicalRoot = pkgDir.canonicalFile
                while (entry != null) {
                    val cleanName = entry.name.trimStart('/')
                    if (cleanName.isNotEmpty() && !cleanName.startsWith(".")) {
                        val outFile = File(pkgDir, cleanName).canonicalFile
                        if (outFile.path.startsWith(canonicalRoot.path)) {
                            if (entry.isDirectory) outFile.mkdirs()
                            else if (!entry.isSymbolicLink) {
                                outFile.parentFile?.mkdirs()
                                FileOutputStream(outFile).use { tar.copyTo(it) }
                                if ((entry.mode and 0b001_000_000) != 0) outFile.setExecutable(true, false)
                                extractedFiles++
                            }
                        }
                    }
                    entry = tar.nextEntry
                }
            }
        }
        db.dao().upsertPackage(InstalledPackageEntity(pkg.name, pkg.version, pkg.description, pkg.architecture, pkg.repository, pkg.sizeBytes, pkg.dependencies.joinToString(","), pkgDir.absolutePath, false, System.currentTimeMillis()))
        log(pkg.name, "INSTALL", "OK", "Extracted $extractedFiles files to ${pkgDir.absolutePath}")
    }

    suspend fun removePackage(pkg: InstalledPackageEntity): Result<Unit> = withContext(Dispatchers.IO) {
        if (pkg.repository == "kestrel-bundled" || pkg.repository == "android-system") return@withContext Result.failure(IllegalStateException("Core package"))
        val dir = File(pkg.installPath)
        if (dir.exists() && dir.canonicalPath.startsWith(prefixDir.canonicalPath)) dir.deleteRecursively()
        db.dao().deletePackage(pkg.name)
        log(pkg.name, "REMOVE", "OK", "Removed ${pkg.name}")
        Result.success(Unit)
    }

    suspend fun upgradeAll(): Result<Int> = withContext(Dispatchers.IO) {
        val syncRes = syncRepositories()
        if (syncRes.isFailure) return@withContext Result.failure(syncRes.exceptionOrNull()!!)
        val catalogMap = _catalog.value.associateBy { it.name }
        var upgraded = 0
        for (inst in db.dao().getInstalledPackages()) {
            val latest = catalogMap[inst.name] ?: continue
            if (latest.version != inst.version && inst.repository != "kestrel-bundled" && inst.repository != "android-system") {
                if (installPackage(latest).isSuccess) upgraded++
            }
        }
        log("system", "UPGRADE", "OK", "Upgraded $upgraded package(s)")
        Result.success(upgraded)
    }

    suspend fun addRepository(name: String, baseUrl: String) = withContext(Dispatchers.IO) {
        val cleanBase = baseUrl.trimEnd('/')
        db.dao().upsertRepository(PackageRepoEntity("custom-${name.lowercase().replace(Regex("[^a-z0-9]"), "-")}", name.trim(), cleanBase, "$cleanBase/APKINDEX.tar.gz", "aarch64", true))
    }

    private suspend fun log(pkg: String, action: String, status: String, message: String) {
        db.dao().insertPackageLog(PackageLogEntity(packageName = pkg, action = action, status = status, message = message, timestamp = System.currentTimeMillis()))
    }
}
