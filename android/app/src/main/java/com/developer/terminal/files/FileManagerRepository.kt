package com.developer.terminal.files

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class FileNode(val name: String, val path: String, val isDirectory: Boolean, val sizeBytes: Long, val lastModified: Long, val canRead: Boolean, val canWrite: Boolean)

class FileManagerRepository(private val context: Context) {
    val homeDir: File = File(context.filesDir, "home").apply { mkdirs() }
    val prefixDir: File = File(context.filesDir, "usr").apply { mkdirs() }
    val externalRoot: File = Environment.getExternalStorageDirectory()

    suspend fun listDirectory(dir: File): Result<List<FileNode>> = withContext(Dispatchers.IO) {
        runCatching {
            val children = dir.listFiles() ?: throw SecurityException("Permission denied reading ${dir.absolutePath}")
            children.map { f ->
                FileNode(f.name, f.absolutePath, f.isDirectory, if (f.isFile) f.length() else (f.listFiles()?.size?.toLong() ?: 0L), f.lastModified(), f.canRead(), f.canWrite())
            }.sortedWith(compareByDescending<FileNode> { it.isDirectory }.thenBy { it.name.lowercase() })
        }
    }

    suspend fun createFolder(parent: File, name: String): Result<File> = withContext(Dispatchers.IO) {
        runCatching { File(parent, name.trim()).also { if (!it.mkdirs() && !it.exists()) error("Failed to create folder") } }
    }

    suspend fun rename(file: File, newName: String): Result<File> = withContext(Dispatchers.IO) {
        runCatching { File(file.parentFile, newName.trim()).also { if (!file.renameTo(it)) error("Rename failed") } }
    }

    suspend fun copy(source: File, destDir: File): Result<File> = withContext(Dispatchers.IO) {
        runCatching { File(destDir, source.name).also { source.copyRecursively(it, overwrite = true) } }
    }

    suspend fun move(source: File, destDir: File): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val target = File(destDir, source.name)
            if (source.renameTo(target)) return@runCatching target
            source.copyRecursively(target, overwrite = true); source.deleteRecursively(); target
        }
    }

    suspend fun delete(file: File): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { if (!file.deleteRecursively()) error("Failed to delete ${file.name}") }
    }

    suspend fun search(root: File, query: String): List<FileNode> = withContext(Dispatchers.IO) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return@withContext emptyList()
        val matches = mutableListOf<FileNode>()
        fun walk(dir: File, depth: Int) {
            if (depth > 6 || matches.size >= 150) return
            for (f in dir.listFiles() ?: return) {
                if (f.name.lowercase().contains(q)) matches += FileNode(f.name, f.absolutePath, f.isDirectory, if (f.isFile) f.length() else 0L, f.lastModified(), f.canRead(), f.canWrite())
                if (f.isDirectory && !f.name.startsWith(".git")) walk(f, depth + 1)
            }
        }
        walk(root, 0); matches
    }

    suspend fun readTextPreview(file: File, maxBytes: Int = 65536): Result<String> = withContext(Dispatchers.IO) {
        runCatching { FileInputStream(file).use { val buf = ByteArray(maxBytes); val n = it.read(buf); if (n <= 0) "" else String(buf, 0, n, Charsets.UTF_8) } }
    }

    suspend fun compressToZip(sources: List<File>, outputZip: File): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            ZipOutputStream(FileOutputStream(outputZip)).use { zos -> for (src in sources) zipWalk(src, src.name, zos) }
            outputZip
        }
    }

    private fun zipWalk(file: File, entryName: String, zos: ZipOutputStream) {
        if (file.isDirectory) {
            val dirEntry = if (entryName.endsWith("/")) entryName else "$entryName/"
            zos.putNextEntry(ZipEntry(dirEntry)); zos.closeEntry()
            file.listFiles()?.forEach { zipWalk(it, "$dirEntry${it.name}", zos) }
        } else {
            zos.putNextEntry(ZipEntry(entryName))
            FileInputStream(file).use { it.copyTo(zos) }
            zos.closeEntry()
        }
    }

    suspend fun extractZip(zipFile: File, destDir: File): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            destDir.mkdirs()
            val canonicalDest = destDir.canonicalFile
            var count = 0
            ZipInputStream(FileInputStream(zipFile)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val outFile = File(destDir, entry.name).canonicalFile
                    if (!outFile.path.startsWith(canonicalDest.path)) throw SecurityException("Blocked unsafe zip entry")
                    if (entry.isDirectory) outFile.mkdirs()
                    else { outFile.parentFile?.mkdirs(); FileOutputStream(outFile).use { zis.copyTo(it) }; count++ }
                    zis.closeEntry(); entry = zis.nextEntry
                }
            }
            count
        }
    }

    suspend fun importFromSafUri(treeUri: Uri, targetDir: File): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val docTree = DocumentFile.fromTreeUri(context, treeUri) ?: error("Unable to open folder")
            var imported = 0
            fun copyDoc(doc: DocumentFile, dest: File) {
                if (doc.isDirectory) { val sub = File(dest, doc.name ?: "folder").apply { mkdirs() }; doc.listFiles().forEach { copyDoc(it, sub) } }
                else context.contentResolver.openInputStream(doc.uri)?.use { input ->
                    FileOutputStream(File(dest, doc.name ?: "file_${System.currentTimeMillis()}")).use { input.copyTo(it) }
                    imported++
                }
            }
            copyDoc(docTree, targetDir); imported
        }
    }

    suspend fun exportFileToSafUri(sourceFile: File, destUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(destUri)?.use { output -> FileInputStream(sourceFile).use { it.copyTo(output) } } ?: error("Failed to open destination URI")
        }
    }
}
