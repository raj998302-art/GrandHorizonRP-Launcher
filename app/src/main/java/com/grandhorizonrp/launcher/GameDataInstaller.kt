package com.grandhorizonrp.launcher

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipInputStream

/**
 * Downloads the split game-data packages, verifies them and extracts them into
 * the launcher's external data folder (Android/data/com.grandhorizonrp.launcher/files).
 *
 * Features:
 *  - HTTP Range resume for interrupted downloads
 *  - SHA-256 integrity verification per package
 *  - Progress journal so an interrupted install resumes at the right package
 *  - Atomic per-file extraction with zip-path sanitisation
 */
class GameDataInstaller(private val context: Context) {

    class InstallCancelledException : Exception("Installation cancelled")
    class IntegrityException(message: String) : Exception(message)

    enum class Stage(val message: String) {
        PREPARING("Preparing download…"),
        DOWNLOADING("Downloading game data…"),
        VERIFYING("Verifying files…"),
        EXTRACTING("Installing game files…"),
        CLEANING("Cleaning up…"),
        DONE("Game data installed")
    }

    interface Listener {
        fun onStage(stage: Stage)
        fun onProgress(downloadedBytes: Long, totalBytes: Long, speedBps: Long)
    }

    @Volatile
    private var cancelled = false

    fun cancel() {
        cancelled = true
    }

    fun dataDir(): File = context.getExternalFilesDir(null) ?: context.filesDir

    fun versionMarker(): File = File(dataDir(), ".ghrp_version")

    fun progressFile(): File = File(dataDir(), ".ghrp_progress")

    fun installedVersion(): String? =
        versionMarker().takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.isNotEmpty() }

    fun isInstalled(expectedVersion: String): Boolean {
        if (installedVersion() != expectedVersion) return false
        val dir = dataDir()
        return File(dir, "common.bpc").exists() && File(dir, "textures").isDirectory
    }

    fun usedSpaceBytes(): Long {
        val dir = dataDir()
        return dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
    }

    suspend fun install(config: LauncherConfig, listener: Listener): Result<Unit> =
        withContext(Dispatchers.IO) {
            cancelled = false
            listener.onStage(Stage.PREPARING)
            val dir = dataDir()
            val tmpDir = File(dir, ".downloads")
            tmpDir.mkdirs()
            try {
                val version = config.gameData.version
                val completedParts = readCompletedParts(version)
                var completedBytes = config.gameData.parts
                    .filter { it.name in completedParts }
                    .sumOf { it.size }

                for (part in config.gameData.parts) {
                    if (cancelled) throw InstallCancelledException()
                    if (part.name in completedParts) continue

                    val target = File(tmpDir, part.name)
                    completedBytes = downloadPart(
                        part, target, completedBytes, config.gameData.totalBytes, listener
                    )

                    listener.onStage(Stage.VERIFYING)
                    if (!part.sha256.isNullOrEmpty()) {
                        val actual = sha256(target)
                        if (!actual.equals(part.sha256, ignoreCase = true)) {
                            target.delete()
                            throw IntegrityException(
                                "Integrity check failed for ${part.name}. Please retry the download."
                            )
                        }
                    }

                    listener.onStage(Stage.EXTRACTING)
                    extractZip(target, dir)

                    listener.onStage(Stage.CLEANING)
                    target.delete()

                    completedParts.add(part.name)
                    writeCompletedParts(version, completedParts)
                }

                tmpDir.deleteRecursively()
                progressFile().delete()
                versionMarker().writeText(version)
                listener.onStage(Stage.DONE)
                Result.success(Unit)
            } catch (e: InstallCancelledException) {
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun readCompletedParts(version: String): MutableSet<String> {
        val file = progressFile()
        if (!file.exists()) return mutableSetOf()
        val prefix = "$version:"
        return file.readLines()
            .filter { it.startsWith(prefix) }
            .map { it.removePrefix(prefix) }
            .toMutableSet()
    }

    private fun writeCompletedParts(version: String, parts: Set<String>) {
        progressFile().writeText(parts.joinToString("\n") { "$version:$it" })
    }

    private fun downloadPart(
        part: GameDataPart,
        target: File,
        baseBytes: Long,
        totalBytes: Long,
        listener: Listener
    ): Long {
        var base = baseBytes
        listener.onStage(Stage.DOWNLOADING)

        // Resume a partially downloaded file from a previous attempt
        var resumeFrom = 0L
        if (target.exists()) {
            if (part.size > 0 && target.length() >= part.size) {
                return base + part.size
            }
            resumeFrom = target.length()
        }

        var attempt = 0
        while (true) {
            attempt++
            try {
                if (attempt > 1 && target.exists()) {
                    resumeFrom = target.length()
                    if (part.size > 0 && resumeFrom >= part.size) return base + part.size
                }

                val conn = (URL(part.url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20000
                    readTimeout = 45000
                    useCaches = false
                    if (resumeFrom > 0) setRequestProperty("Range", "bytes=$resumeFrom-")
                }
                val code = conn.responseCode
                val appending: Boolean = when {
                    code == 206 && resumeFrom > 0 && target.exists() -> true

                    code == 200 -> {
                        target.delete()
                        resumeFrom = 0
                        false
                    }

                    code == 206 -> {
                        target.delete()
                        resumeFrom = 0
                        false
                    }

                    code == 416 -> {
                        if (part.size > 0 && target.exists() && target.length() == part.size) {
                            conn.disconnect()
                            return base + part.size
                        }
                        target.delete()
                        resumeFrom = 0
                        conn.disconnect()
                        throw IOException("Range not satisfiable for ${part.name}, restarting")
                    }

                    else -> throw IOException("HTTP $code while downloading ${part.name}")
                }

                val expectedSize =
                    if (part.size > 0) part.size else (resumeFrom + conn.contentLengthLong)
                var position = if (appending) resumeFrom else 0L
                var windowBytes = 0L
                var windowStart = System.currentTimeMillis()
                var lastReport = windowStart

                BufferedOutputStream(FileOutputStream(target, appending), BUFFER_SIZE).use { out ->
                    conn.inputStream.use { raw ->
                        val buf = ByteArray(BUFFER_SIZE)
                        while (true) {
                            if (cancelled) throw InstallCancelledException()
                            val read = raw.read(buf)
                            if (read < 0) break
                            out.write(buf, 0, read)
                            position += read
                            windowBytes += read
                            val now = System.currentTimeMillis()
                            if (now - lastReport >= 400) {
                                val elapsed = (now - windowStart).coerceAtLeast(1)
                                val speed = windowBytes * 1000L / elapsed
                                listener.onProgress(base + position, totalBytes, speed)
                                windowBytes = 0
                                windowStart = now
                                lastReport = now
                            }
                        }
                        out.flush()
                    }
                }
                conn.disconnect()

                if (expectedSize > 0 && position != expectedSize) {
                    resumeFrom = position
                    throw IOException(
                        "Incomplete download of ${part.name} ($position/$expectedSize bytes)"
                    )
                }

                listener.onProgress(base + position, totalBytes, 0L)
                base += position
                return base
            } catch (e: InstallCancelledException) {
                throw e
            } catch (e: Exception) {
                if (attempt >= 5) {
                    throw IOException("Could not download ${part.name}: ${e.message}", e)
                }
                if (target.exists()) resumeFrom = target.length()
                Thread.sleep((2000L * attempt).coerceAtMost(10000L))
            }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buf = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = fis.read(buf)
                if (read < 0) break
                digest.update(buf, 0, read)
            }
        }
        return digest.digest().joinToString("") { b -> String.format(Locale.US, "%02x", b) }
    }

    private fun extractZip(zipFile: File, targetDir: File) {
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile), BUFFER_SIZE)).use { zis ->
            while (true) {
                if (cancelled) throw InstallCancelledException()
                val entry = zis.nextEntry ?: break
                val out = sanitize(targetDir, entry.name)
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    val tmp = File(out.parentFile, out.name + ".tmp")
                    FileOutputStream(tmp).use { fos -> zis.copyTo(fos, BUFFER_SIZE) }
                    if (!tmp.renameTo(out)) {
                        out.delete()
                        if (!tmp.renameTo(out)) {
                            tmp.copyTo(out, overwrite = true)
                            tmp.delete()
                        }
                    }
                }
                zis.closeEntry()
            }
        }
    }

    private fun sanitize(root: File, rawName: String): File {
        var name = rawName.replace('\\', '/')
        if (name.startsWith("files/")) name = name.removePrefix("files/")
        if (name.isBlank() || name == "files") return root
        val f = File(root, name)
        val rootCanonical = root.canonicalPath
        val targetCanonical = f.canonicalPath
        require(
            targetCanonical == rootCanonical ||
                targetCanonical.startsWith(rootCanonical + File.separator)
        ) { "Blocked suspicious path in package: $rawName" }
        return f
    }

    companion object {
        private const val BUFFER_SIZE = 256 * 1024
    }
}
