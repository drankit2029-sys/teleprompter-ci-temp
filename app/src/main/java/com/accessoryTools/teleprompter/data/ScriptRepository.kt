package com.accessoryTools.teleprompter.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ScriptRepository(
    context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val appContext = context.applicationContext
    private val contentResolver = appContext.contentResolver
    private val scriptsDir = File(appContext.filesDir, "scripts")
    private val trashDir = File(appContext.filesDir, "trash")

    private val _scripts = MutableStateFlow<List<Script>>(emptyList())
    val scripts: StateFlow<List<Script>> = _scripts.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init {
        scriptsDir.mkdirs()
        trashDir.mkdirs()
        refresh()
    }

    fun refresh() {
        scope.launch {
            _scripts.value = listScripts()
            _loading.value = false
        }
    }

    suspend fun listScripts(): List<Script> =
        withContext(Dispatchers.IO) {
            scriptsDir
                .listFiles { file -> file.isFile && file.extension == SCRIPT_EXTENSION }
                .orEmpty()
                .mapNotNull { file -> runCatching { readScript(file) }.getOrNull() }
                .sortedByDescending { script -> script.updatedAt }
        }

    suspend fun getScript(id: String): Script? =
        withContext(Dispatchers.IO) {
            val file = scriptFile(id)
            if (file.isFile) runCatching { readScript(file) }.getOrNull() else null
        }

    suspend fun createScript(
        title: String,
        content: String,
    ): Script =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val script =
                Script(
                    id = UUID.randomUUID().toString(),
                    title = cleanTitle(title),
                    content = content,
                    createdAt = now,
                    updatedAt = now,
                )
            writeScript(script)
            script
        }

    suspend fun saveScript(script: Script): Script =
        withContext(Dispatchers.IO) {
            val updated =
                script.copy(
                    title = cleanTitle(script.title),
                    updatedAt = System.currentTimeMillis(),
                    words = wordCount(script.content),
                    characters = script.content.length,
                )
            writeScript(updated)
            updated
        }

    suspend fun updateScript(
        id: String,
        title: String,
        content: String,
    ): Script? =
        withContext(Dispatchers.IO) {
            val existing = getScriptOnIo(id) ?: return@withContext null
            val updated =
                existing.copy(
                    title = cleanTitle(title),
                    content = content,
                    updatedAt = System.currentTimeMillis(),
                    words = wordCount(content),
                    characters = content.length,
                )
            writeScript(updated)
            updated
        }

    suspend fun setFavorite(
        id: String,
        favorite: Boolean,
    ): Script? =
        withContext(Dispatchers.IO) {
            val existing = getScriptOnIo(id) ?: return@withContext null
            val updated = existing.copy(favorite = favorite, updatedAt = System.currentTimeMillis())
            writeScript(updated)
            updated
        }

    suspend fun markOpened(id: String): Script? =
        withContext(Dispatchers.IO) {
            val existing = getScriptOnIo(id) ?: return@withContext null
            val updated = existing.copy(lastOpenedAt = System.currentTimeMillis())
            writeScript(updated)
            updated
        }

    suspend fun saveProgress(
        id: String,
        progress: Float,
    ) {
        withContext(Dispatchers.IO) {
            val existing = getScriptOnIo(id) ?: return@withContext
            val clamped = progress.coerceIn(0f, 1f)
            val updated =
                existing.copy(
                    lastPosition = clamped,
                    lastOpenedAt = System.currentTimeMillis(),
                )
            writeScript(updated)
        }
    }

    suspend fun duplicateScript(id: String): Script? =
        withContext(Dispatchers.IO) {
            val existing = getScriptOnIo(id) ?: return@withContext null
            val now = System.currentTimeMillis()
            val copy =
                existing.copy(
                    id = UUID.randomUUID().toString(),
                    title = "${existing.title} copy",
                    createdAt = now,
                    updatedAt = now,
                    favorite = false,
                    lastPosition = 0f,
                    lastOpenedAt = 0L,
                )
            writeScript(copy)
            copy
        }

    suspend fun deleteScript(id: String): Script? =
        withContext(Dispatchers.IO) {
            val existing = getScriptOnIo(id) ?: return@withContext null
            val file = scriptFile(id)
            val target = File(trashDir, file.name)
            if (file.isFile) {
                Files.move(
                    file.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
            _scripts.value = listScriptsOnIo()
            _loading.value = false
            existing
        }

    suspend fun restoreScript(id: String): Script? =
        withContext(Dispatchers.IO) {
            val trashed = File(trashDir, "$id.$SCRIPT_EXTENSION")
            if (!trashed.isFile) return@withContext null
            val restored = runCatching { readScript(trashed) }.getOrNull() ?: return@withContext null
            val target = scriptFile(id)
            Files.move(
                trashed.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
            writeScript(restored.copy(updatedAt = System.currentTimeMillis()))
            restored
        }

    suspend fun seedDemoOnce(
        title: String,
        content: String,
    ) {
        withContext(Dispatchers.IO) {
            val marker = File(appContext.filesDir, SEED_MARKER)
            if (marker.exists()) {
                return@withContext
            }
            if (listScripts().isEmpty()) {
                createScript(title, content)
            }
            runCatching { marker.createNewFile() }
        }
    }

    suspend fun writeBackup(uri: Uri): Int =
        withContext(Dispatchers.IO) {
            val scripts = listScripts()
            contentResolver.openOutputStream(uri)?.use { stream ->
                writeBackup(stream, scripts)
            } ?: error("Could not open the selected backup file for writing.")
            scripts.size
        }

    suspend fun restoreBackup(uri: Uri): Int =
        withContext(Dispatchers.IO) {
            val stream =
                contentResolver.openInputStream(uri)
                    ?: error("Could not open the selected backup file.")
            stream.use { readBackup(it) }
        }

    private fun writeBackup(
        output: OutputStream,
        scripts: List<Script>,
    ) {
        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            scripts.forEach { script ->
                val entry = ZipEntry("scripts/${script.id}.$SCRIPT_EXTENSION")
                zip.putNextEntry(entry)
                zip.write(scriptToJson(script).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
    }

    private fun readBackup(input: InputStream): Int {
        var restored = 0
        var uncompressed = 0L
        val existing = listScriptsOnIo().toMutableList()
        val usedTitles = existing.mapTo(mutableSetOf()) { it.title }
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            var entry = zip.nextEntry
            var entries = 0
            while (entry != null) {
                entries += 1
                if (entries > MAX_BACKUP_ENTRIES) {
                    error("Backup contains too many entries.")
                }
                if (!entry.isDirectory && entry.name.endsWith(".$SCRIPT_EXTENSION")) {
                    val bytes = zip.readLimited(MAX_SCRIPT_BYTES)
                    uncompressed += bytes.size
                    if (uncompressed > MAX_BACKUP_BYTES) {
                        error("Backup is too large.")
                    }
                    val script =
                        runCatching {
                            jsonToScript(JSONObject(bytes.toString(Charsets.UTF_8)))
                        }.getOrNull()
                    if (script != null) {
                        val existingById = existing.firstOrNull { it.id == script.id }
                        val exactCopy = existing.any { it.content == script.content }
                        if (!exactCopy) {
                            val targetId = if (existingById != null) UUID.randomUUID().toString() else script.id
                            val targetTitle =
                                if (existingById != null) {
                                    restoredTitle(script.title, usedTitles)
                                } else {
                                    script.title
                                }
                            val restoredScript =
                                script.copy(
                                    id = targetId,
                                    title = targetTitle,
                                    updatedAt = System.currentTimeMillis(),
                                )
                            writeScript(restoredScript)
                            existing += restoredScript
                            usedTitles += restoredScript.title
                            restored += 1
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return restored
    }

    private fun writeScript(script: Script) {
        scriptsDir.mkdirs()
        val target = scriptFile(script.id)
        val temp = File(scriptsDir, "${script.id}.tmp")
        FileOutputStream(temp).use { stream ->
            stream.write(scriptToJson(script).toByteArray(Charsets.UTF_8))
            stream.fd.sync()
        }
        runCatching {
            Files.move(
                temp.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        }.onFailure {
            Files.move(
                temp.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
        _scripts.value = listScriptsOnIo()
        _loading.value = false
    }

    private fun getScriptOnIo(id: String): Script? {
        val file = scriptFile(id)
        return if (file.isFile) runCatching { readScript(file) }.getOrNull() else null
    }

    private fun listScriptsOnIo(): List<Script> =
        scriptsDir
            .listFiles { file -> file.isFile && file.extension == SCRIPT_EXTENSION }
            .orEmpty()
            .mapNotNull { file -> runCatching { readScript(file) }.getOrNull() }
            .sortedByDescending { script -> script.updatedAt }

    private fun readScript(file: File): Script = jsonToScript(JSONObject(file.readText(Charsets.UTF_8)))

    private fun scriptToJson(script: Script): String =
        JSONObject()
            .put("version", 1)
            .put("id", script.id)
            .put("title", script.title)
            .put("content", script.content)
            .put("createdAt", script.createdAt)
            .put("updatedAt", script.updatedAt)
            .put("favorite", script.favorite)
            .put("lastPosition", script.lastPosition.toDouble())
            .put("lastOpenedAt", script.lastOpenedAt)
            .put("words", script.words)
            .put("characters", script.characters)
            .toString()

    private fun jsonToScript(json: JSONObject): Script {
        val content = json.optString("content")
        return Script(
            id = json.getString("id"),
            title = json.optString("title").ifBlank { "Untitled script" },
            content = content,
            createdAt = json.optLong("createdAt"),
            updatedAt = json.optLong("updatedAt"),
            favorite = json.optBoolean("favorite"),
            lastPosition = json.optDouble("lastPosition", 0.0).toFloat().coerceIn(0f, 1f),
            lastOpenedAt = json.optLong("lastOpenedAt"),
            words = json.optInt("words", wordCount(content)),
            characters = json.optInt("characters", content.length),
        )
    }

    private fun scriptFile(id: String): File = File(scriptsDir, "$id.$SCRIPT_EXTENSION")

    private fun cleanTitle(title: String): String = title.trim().take(MAX_TITLE_LENGTH).ifBlank { "Untitled script" }

    private fun restoredTitle(
        baseTitle: String,
        usedTitles: Set<String>,
    ): String {
        val base =
            cleanTitle(baseTitle)
                .take(MAX_TITLE_LENGTH - " (restored 100)".length)
                .trim()
                .ifBlank { "Untitled script" }
        val single = "$base (restored)"
        if (single !in usedTitles) {
            return single
        }
        var index = 2
        while (true) {
            val candidate = "$base (restored $index)"
            if (candidate !in usedTitles) {
                return candidate
            }
            index += 1
        }
    }

    private fun ZipInputStream.readLimited(limit: Int): ByteArray {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        val output = java.io.ByteArrayOutputStream()
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) {
                break
            }
            total += read
            if (total > limit) {
                error("Backup entry is too large.")
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    companion object {
        private const val SCRIPT_EXTENSION = "tpscript"
        private const val SEED_MARKER = ".teleprompter-seeded"
        private const val MAX_TITLE_LENGTH = 120
        private const val MAX_SCRIPT_BYTES = 5 * 1024 * 1024
        private const val MAX_BACKUP_BYTES = 50L * 1024 * 1024
        private const val MAX_BACKUP_ENTRIES = 2_000
    }
}
