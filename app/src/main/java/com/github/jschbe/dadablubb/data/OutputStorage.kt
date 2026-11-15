package com.github.jschbe.dadablubb.data

import java.io.File
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.StandardCopyOption

interface OutputPersistence {
    /** null means never saved; an empty string is an intentionally empty output. */
    fun read(): String?
    fun write(text: String)
}

/** Bounded UTF-8 with same-directory atomic replacement. Never rewrites a failed read. */
class OutputStorage(private val file: File) : OutputPersistence {
    override fun read(): String? {
        val source = try { Files.newInputStream(file.toPath()) }
        catch (_: NoSuchFileException) { return null }
        val bytes = source.use { input ->
            val buffer = ByteArray(8192)
            val output = ByteArrayOutputStream()
            while (output.size() <= MAX_BYTES) {
                val count = input.read(buffer, 0, minOf(buffer.size, MAX_BYTES + 1 - output.size()))
                if (count == -1) break
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        if (bytes.size > MAX_BYTES) throw OutputTooLargeException()
        return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    }

    override fun write(text: String) {
        val bytes = encode(text)
        val temporary = Files.createTempFile(file.parentFile!!.toPath(), "output-", ".tmp")
        try {
            FileOutputStream(temporary.toFile()).use {
                it.write(bytes)
                it.fd.sync()
            }
            Files.move(temporary, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            runCatching { Files.deleteIfExists(temporary) }
        }
    }

    companion object {
        const val MAX_BYTES = 64 * 1024

        fun encode(text: String): ByteArray {
            if (text.length > MAX_BYTES) throw OutputTooLargeException()
            val buffer = Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(text))
            if (buffer.remaining() > MAX_BYTES) throw OutputTooLargeException()
            return ByteArray(buffer.remaining()).also { buffer.get(it) }
        }
    }
}

class OutputTooLargeException : IOException("Output exceeds 64 KiB of UTF-8 text.")
