package com.github.jschbe.dadablubb.data

import com.github.jschbe.dadablubb.core.OutputWrapHints
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.security.MessageDigest

/** Private, bounded binary sidecar. It is unrelated to the vocabulary JSON schema. */
internal object OutputWrapMetadata {
    const val MAX_BYTES = 1024
    private const val MAGIC = 0x44425752 // DBWR
    private const val VERSION = 1

    fun encode(hints: OutputWrapHints, identity: String): ByteArray = ByteArrayOutputStream().also { bytes ->
        DataOutputStream(bytes).use {
            it.writeInt(MAGIC)
            it.writeInt(VERSION)
            it.writeUTF(identity)
            it.writeUTF(hints.textHash)
            it.writeInt(hints.boundaries.size)
            hints.boundaries.forEach(it::writeInt)
        }
    }.toByteArray().let { it + checksum(it) }.also { require(it.size <= MAX_BYTES) }

    fun decode(bytes: ByteArray, text: String, identity: String): OutputWrapHints? {
        if (bytes.size !in 32..MAX_BYTES) return null
        val payload = bytes.copyOf(bytes.size - 32)
        if (!MessageDigest.isEqual(checksum(payload), bytes.copyOfRange(payload.size, bytes.size))) return null
        return try {
            DataInputStream(ByteArrayInputStream(payload)).use {
                if (it.readInt() != MAGIC || it.readInt() != VERSION || it.readUTF() != identity) return null
                val hash = it.readUTF()
                val count = it.readInt()
                if (count !in 0..49) return null
                val hints = OutputWrapHints(hash, List(count) { _ -> it.readInt() })
                hints.takeIf { _ -> it.read() == -1 && hints.matches(text) }
            }
        } catch (_: IOException) { null }
    }

    private fun checksum(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
}
