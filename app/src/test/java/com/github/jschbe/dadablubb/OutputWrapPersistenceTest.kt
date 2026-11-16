package com.github.jschbe.dadablubb

import com.github.jschbe.dadablubb.core.*
import com.github.jschbe.dadablubb.data.*
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OutputWrapPersistenceTest {
    @get:Rule val folder = TemporaryFolder()
    private val generation = Generation(listOf("Möter", "Didi", "𐐀İ"), listOf("di", ""))
    private val text = generation.text
    private val hints = OutputWrapHints.from(generation)
    private suspend fun settled(repo: OutputRepository) = withTimeout(5_000) {
        repo.state.first { it.loaded && !it.saving }
    }

    @Test fun sidecarRestoresHintsWithoutChangingExactUtf8AndLegacyEmptyFilesWork() {
        val file = File(folder.root, "output.txt")
        val store = OutputStorage(file)
        assertNull(store.read())
        store.write(text)
        assertNull(store.readWrapHints(text)) // legacy output
        val bytes = file.readBytes()
        store.writeWrapHints(text, hints)
        assertEquals(hints, OutputStorage(file).readWrapHints(text))
        assertArrayEquals(bytes, file.readBytes())
        assertTrue(File(folder.root, "output.txt.wrap").length() <= 1024)
        store.invalidateWrapHints()
        assertNull(store.readWrapHints(text))
        store.write("")
        assertEquals("", store.read())
        assertNull(store.readWrapHints(""))
    }

    @Test fun codecRejectsUnknownVersionsWrongTextAndIdentityAndMalformedRecords() {
        val bytes = OutputWrapMetadata.encode(hints, "file-instance")
        assertEquals(hints, OutputWrapMetadata.decode(bytes, text, "file-instance"))
        assertNull(OutputWrapMetadata.decode(bytes, text + "x", "file-instance"))
        assertNull(OutputWrapMetadata.decode(bytes, text, "older-file"))
        assertNull(OutputWrapMetadata.decode(ByteArray(1025), text, "file-instance"))
        assertNull(OutputWrapMetadata.decode(bytes + 0.toByte(), text, "file-instance"))
        for (size in bytes.indices) assertNull(OutputWrapMetadata.decode(bytes.copyOf(size), text, "file-instance"))
        for (offset in bytes.indices) {
            val corrupt = bytes.copyOf()
            corrupt[offset] = (corrupt[offset].toInt() xor 1).toByte()
            assertNull(OutputWrapMetadata.decode(corrupt, text, "file-instance"))
        }
        for (position in listOf(0, 4)) {
            val invalid = bytes.copyOf()
            ByteBuffer.wrap(invalid).putInt(position, 99)
            assertNull(OutputWrapMetadata.decode(invalid, text, "file-instance"))
        }
        val badHashes = listOf("", "a".repeat(64), hints.textHash.uppercase())
        for (hash in badHashes) assertNull(OutputWrapMetadata.decode(
            OutputWrapMetadata.encode(hints.copy(textHash = hash), "file-instance"), text, "file-instance"))
    }

    @Test fun invalidBoundariesIncludingSurrogatesAndCombiningMarksNeverActivate() {
        val unsafe = listOf(listOf(0), listOf(-1), listOf(text.length), listOf(text.length + 1),
            listOf(5, 5), listOf(11, 5), List(50) { 1 }, listOf(text.indexOf("𐐨") + 1))
        for (boundaries in unsafe) {
            val record = OutputWrapMetadata.encode(hints.copy(boundaries = boundaries), "file")
            assertNull(OutputWrapMetadata.decode(record, text, "file"))
        }
        val accented = Generation(listOf("Möter", "Blubb"), listOf(""))
        val accentHints = OutputWrapHints.from(accented)
        assertTrue(accentHints.matches(accented.text))
        assertFalse(accentHints.copy(boundaries = listOf(2)).matches(accented.text))
    }

    @Test fun corruptSidecarLeavesPlainTextAndStaleSidecarCannotReturnAfterIdenticalRewrite() {
        val file = File(folder.root, "output.txt")
        val store = OutputStorage(file)
        store.write(text)
        store.writeWrapHints(text, hints)
        val metadata = File(folder.root, "output.txt.wrap")
        val old = metadata.readBytes()
        metadata.writeBytes(byteArrayOf(1, 2, 3))
        assertNull(store.readWrapHints(text))
        assertEquals(text, store.read())
        metadata.writeBytes(old)
        // Simulate interrupted storage after text replacement but before sidecar replacement.
        store.write(text)
        assertNull(store.readWrapHints(text))
        store.write(text + "edited")
        store.write(text) // Undo to identical characters must not reactivate the old file's hints.
        assertNull(store.readWrapHints(text))
    }

    @Test fun metadataFailuresNeverPreventTextSavingOrReactivateHintsAfterUndo() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = File(folder.root, "output.txt")
        val disk = OutputStorage(file)
        disk.write(text)
        disk.writeWrapHints(text, hints)
        val failing = object : OutputPersistence by disk {
            override fun invalidateWrapHints() { throw IOException("cannot remove metadata") }
            override fun writeWrapHints(text: String, hints: OutputWrapHints) { throw IOException("metadata full") }
        }
        try {
            val repo = OutputRepository(failing, scope)
            assertEquals(hints, settled(repo).wrapHints)
            repo.update(text + "x")
            assertNull(settled(repo).error)
            repo.update(text)
            assertNull(settled(repo).wrapHints)
            val restored = OutputRepository(disk, scope)
            assertEquals(text, settled(restored).text)
            assertNull(restored.state.value.wrapHints)
            repo.update(generation)
            assertNull(settled(repo).error)
            assertEquals(text, disk.read())
            assertNull(settled(OutputRepository(disk, scope)).wrapHints)
        } finally { scope.cancel() }
    }

    @Test fun slowOldMetadataWriteAndConflatedGenerateEditUndoFinishInOrder() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val disk = OutputStorage(File(folder.root, "output.txt"))
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val blocked = object : OutputPersistence by disk {
            override fun writeWrapHints(text: String, hints: OutputWrapHints) {
                started.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                disk.writeWrapHints(text, hints)
            }
        }
        try {
            val repo = OutputRepository(blocked, scope)
            settled(repo)
            repo.update(generation)
            assertTrue(started.await(5, TimeUnit.SECONDS))
            repeat(100) { repo.update(generation); repo.update("edit $it") }
            repo.update(text) // manual Undo is a text-only revision
            release.countDown()
            assertNull(settled(repo).wrapHints)
            assertEquals(text, disk.read())
            assertNull(settled(OutputRepository(disk, scope)).wrapHints)
        } finally { release.countDown(); scope.cancel() }
    }

    @Test fun failedTextWriteKeepsPriorTextAndReadErrorsInOptionalMetadataAreHarmless() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val disk = OutputStorage(File(folder.root, "output.txt"))
        disk.write(text)
        disk.writeWrapHints(text, hints)
        val failing = object : OutputPersistence by disk {
            override fun readWrapHints(text: String): OutputWrapHints? = throw IOException("bad metadata")
            override fun write(text: String) { throw IOException("text full") }
        }
        try {
            val repo = OutputRepository(failing, scope)
            assertEquals(text, settled(repo).text)
            assertNull(repo.state.value.error)
            assertNull(repo.state.value.wrapHints)
            repo.update("unsaved edit")
            assertEquals(OutputError.WRITE, settled(repo).error)
            assertEquals("unsaved edit", repo.state.value.text)
            assertEquals(text, disk.read())
            assertNull(settled(OutputRepository(disk, scope)).wrapHints)
        } finally { scope.cancel() }
    }
}
