package com.github.jschbe.dummlaberer.data

import com.github.jschbe.dummlaberer.core.Dataset
import com.github.jschbe.dummlaberer.core.DatasetCodec
import com.github.jschbe.dummlaberer.core.DatasetException
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** JVM-testable file persistence. The caller runs all methods on Dispatchers.IO. */
class DatasetStorage(private val file: File, private val bundled: () -> InputStream) {
    data class Loaded(val dataset: Dataset, val warning: String? = null)

    fun load(): Loaded {
        val defaults = defaults()
        if (!file.exists()) return Loaded(defaults)
        return try {
            Loaded(file.inputStream().use(DatasetCodec::read))
        } catch (_: Exception) {
            Loaded(defaults, "The saved dataset could not be loaded. Bundled defaults are active. Import a valid file or reset to repair it.")
        }
    }

    fun replace(dataset: Dataset) {
        val bytes = DatasetCodec.encode(dataset).toByteArray(Charsets.UTF_8)
        if (bytes.size > DatasetCodec.MAX_BYTES) throw DatasetException("Formatted dataset exceeds 1 MiB.")
        // Same-directory rename is atomic on Android's private filesystem. If unsupported
        // or unsuccessful, fail rather than replacing the previous file non-atomically.
        val temporary = Files.createTempFile(file.parentFile!!.toPath(), "dada-", ".tmp")
        try {
            FileOutputStream(temporary.toFile()).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            Files.move(temporary, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            // Cleanup must not mask a write failure or turn a successful replacement into an error.
            runCatching { Files.deleteIfExists(temporary) }
        }
    }

    fun reset(): Dataset {
        val defaults = defaults()
        // Load defaults first; failed parsing/deletion leaves the active import unchanged.
        Files.deleteIfExists(file.toPath())
        return defaults
    }

    private fun defaults() = bundled().use(DatasetCodec::read)
}
