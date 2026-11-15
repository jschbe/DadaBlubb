package com.github.jschbe.dadablubb

import com.github.jschbe.dadablubb.data.*
import java.io.File
import java.io.IOException
import java.nio.charset.CharacterCodingException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OutputStorageTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun absentEmptyAndMultilineUnicodeSurviveNewStorageInstances() {
        val file = File(folder.root, "output.txt")
        assertNull(OutputStorage(file).read())
        val text = "Möter\n  Blubb 😀\n"
        OutputStorage(file).write(text)
        assertEquals(text, OutputStorage(file).read())
        OutputStorage(file).write("")
        assertTrue(file.exists())
        assertEquals("", OutputStorage(file).read())
        assertEquals(listOf("output.txt"), folder.root.list()!!.toList())
    }

    @Test fun utf8ByteLimitIsInclusiveAndRejectedWritesKeepOldText() {
        val file = File(folder.root, "output.txt")
        val store = OutputStorage(file)
        val atLimit = "ä".repeat(OutputStorage.MAX_BYTES / 2)
        store.write(atLimit)
        assertEquals(atLimit, store.read())
        for (text in listOf(atLimit + "a", "a".repeat(OutputStorage.MAX_BYTES + 1))) {
            assertThrows(OutputTooLargeException::class.java) { store.write(text) }
            assertEquals(atLimit, store.read())
        }
    }

    @Test fun invalidOrOversizedFilesAreNotOverwrittenByReading() {
        val file = File(folder.root, "output.txt")
        for (bytes in listOf(byteArrayOf(0xc3.toByte()), ByteArray(OutputStorage.MAX_BYTES + 1))) {
            file.writeBytes(bytes)
            assertThrows(IOException::class.java) { OutputStorage(file).read() }
            assertArrayEquals(bytes, file.readBytes())
        }
    }

    @Test fun invalidUnicodeIsRejectedWithoutReplacingSavedOutput() {
        val file = File(folder.root, "output.txt")
        val store = OutputStorage(file)
        store.write("keep")
        assertThrows(CharacterCodingException::class.java) { store.write("\uD800") }
        assertEquals("keep", store.read())
    }

    @Test fun failedAtomicMoveKeepsTargetAndCleansTemporaryFile() {
        val target = folder.newFolder("output.txt")
        File(target, "sentinel").writeText("keep")
        assertThrows(IOException::class.java) { OutputStorage(target).write("new") }
        assertEquals("keep", File(target, "sentinel").readText())
        assertEquals(listOf("output.txt"), folder.root.list()!!.toList())
    }
}
