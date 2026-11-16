package com.github.jschbe.dadablubb

import com.github.jschbe.dadablubb.data.*
import com.github.jschbe.dadablubb.core.Generation
import com.github.jschbe.dadablubb.core.OutputWrapHints
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OutputRepositoryTest {
    @get:Rule val folder = TemporaryFolder()
    private suspend fun settled(repo: OutputRepository): OutputState =
        withTimeout(5_000) { repo.state.first { it.loaded && !it.saving } }

    @Test fun structuralHintsSurviveColdRestorationAndEveryAcceptedEditDiscardsThem() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val disk = OutputStorage(File(folder.root, "output.txt"))
        val generated = Generation(listOf("Didi", "Möter", "Röngel"), listOf("di", ""))
        try {
            val repo = OutputRepository(disk, scope)
            settled(repo)
            repo.update(generated)
            assertEquals(OutputWrapHints.from(generated), settled(repo).wrapHints)
            assertEquals(generated.text, disk.read())
            val restarted = OutputRepository(disk, scope)
            assertEquals(generated.text, settled(restarted).text)
            assertEquals(OutputWrapHints.from(generated), restarted.state.value.wrapHints)
            repo.update(generated.text + "di")
            assertNull(settled(repo).wrapHints)
            assertEquals(generated.text + "di", disk.read())
            repo.update(generated)
            settled(repo)
            repo.update(generated.text) // even replacement with identical characters cannot infer structure
            assertNull(settled(repo).wrapHints)
        } finally { scope.cancel() }
    }

    @Test fun firstStartSavedEmptyAndGenerateReplacementHaveDifferentStates() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = File(folder.root, "output.txt")
        try {
            val repo = OutputRepository(OutputStorage(file), scope)
            assertTrue(settled(repo).showPlaceholder)
            repo.update("generated")
            assertEquals("generated", settled(repo).text)
            repo.update("")
            assertFalse(settled(repo).showPlaceholder)
            val restarted = OutputRepository(OutputStorage(file), scope)
            assertEquals("", settled(restarted).text)
            assertTrue(restarted.state.value.hasOutput)
            assertFalse(restarted.state.value.showPlaceholder)
            restarted.update("generated again")
            settled(restarted)
            assertEquals("generated again", OutputStorage(file).read())
        } finally { scope.cancel() }
    }

    @Test fun lateInitialReadNeverOverwritesANewerEdit() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = File(folder.root, "output.txt")
        val disk = OutputStorage(file)
        val storage = object : OutputPersistence {
            override fun read(): String? {
                started.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                return "old disk text"
            }
            override fun write(text: String) = disk.write(text)
        }
        try {
            val repo = OutputRepository(storage, scope)
            assertTrue(started.await(5, TimeUnit.SECONDS))
            repo.update("new edit")
            assertEquals("new edit", repo.state.value.text)
            release.countDown()
            assertEquals("new edit", settled(repo).text)
            assertEquals("new edit", disk.read())
        } finally { release.countDown(); scope.cancel() }
    }

    @Test fun slowOlderWriteCannotOvertakeRapidNewerEdits() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val disk = OutputStorage(File(folder.root, "output.txt"))
        val storage = object : OutputPersistence {
            override fun read(): String? = null
            override fun write(text: String) {
                if (text == "first") {
                    started.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                }
                disk.write(text)
            }
        }
        try {
            val repo = OutputRepository(storage, scope)
            settled(repo)
            repo.update("first")
            assertTrue(started.await(5, TimeUnit.SECONDS))
            repeat(500) { repo.update("edit $it") }
            repo.update("last\n😀")
            assertEquals("last\n😀", repo.state.value.text)
            release.countDown()
            settled(repo)
            assertEquals("last\n😀", disk.read())
            assertEquals("last\n😀", settled(OutputRepository(disk, scope)).text)
        } finally { release.countDown(); scope.cancel() }
    }

    @Test fun failedReadSuppressesFirstStartAndDoesNotWriteUntilExplicitEdit() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var written: String? = null
        val storage = object : OutputPersistence {
            override fun read(): String? = throw IOException("read failed")
            override fun write(text: String) { written = text }
        }
        try {
            val repo = OutputRepository(storage, scope)
            assertEquals(OutputError.READ, settled(repo).error)
            assertFalse(repo.state.value.showPlaceholder)
            assertNull(written)
            repo.update("")
            assertNull(settled(repo).error)
            assertEquals("", written)
        } finally { scope.cancel() }
    }

    @Test fun writeFailureKeepsEditorAndPreviousDiskTextAndNextEditRetries() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var fail = true
        var disk = "previous"
        val storage = object : OutputPersistence {
            override fun read() = disk
            override fun write(text: String) {
                if (fail) throw IOException("full")
                disk = text
            }
        }
        try {
            val repo = OutputRepository(storage, scope)
            settled(repo)
            repo.update("unsaved")
            assertEquals(OutputError.WRITE, settled(repo).error)
            assertEquals("unsaved", repo.state.value.text)
            assertEquals("previous", disk)
            fail = false
            repo.update("retry")
            assertNull(settled(repo).error)
            assertEquals("retry", disk)
        } finally { scope.cancel() }
    }

    @Test fun oversizedAndInvalidEditsLeaveAcceptedStateAndDiskUntouched() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val disk = OutputStorage(File(folder.root, "output.txt"))
        try {
            val repo = OutputRepository(disk, scope)
            settled(repo)
            repo.update("keep")
            settled(repo)
            repo.update("ä".repeat(OutputStorage.MAX_BYTES))
            assertEquals(OutputError.TOO_LARGE, repo.state.value.error)
            assertEquals("keep", repo.state.value.text)
            repo.update("\uD800")
            assertEquals(OutputError.INVALID_TEXT, repo.state.value.error)
            assertEquals("keep", disk.read())
        } finally { scope.cancel() }
    }
}
