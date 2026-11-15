package com.github.jschbe.dadablubb

import com.github.jschbe.dadablubb.core.*
import com.github.jschbe.dadablubb.data.DatasetDocuments
import java.io.*
import org.junit.Assert.*
import org.junit.Test

class DatasetDocumentsTest {
    private val dataset = Dataset(listOf("Möter", "Blubb"), listOf(Connector("di", 60), Connector("", 20)))

    @Test fun invalidOrTooLargeExportNeverOpensDestination() {
        var opened = false
        val oversized = Dataset((0 until 5000).map { "𐐀".repeat(60) + it.toString().padStart(4, '0').map { c -> 'a' + (c - '0') }.joinToString("") },
            listOf(Connector("", 1)))
        for (invalid in listOf(dataset.copy(words = listOf("bad word")), oversized)) {
            assertThrows(DatasetException::class.java) {
                DatasetDocuments.write(invalid) { opened = true; ByteArrayOutputStream() }
            }
            assertFalse(opened)
        }
    }

    @Test fun exportRoundtripIsExactAndClosesBothStreams() {
        var outputClosed = false
        var inputClosed = false
        val bytes = object : ByteArrayOutputStream() { override fun close() { outputClosed = true } }
        DatasetDocuments.write(dataset) { bytes }
        assertTrue(outputClosed)
        val result = DatasetDocuments.read {
            object : ByteArrayInputStream(bytes.toByteArray()) { override fun close() { inputClosed = true } }
        }
        assertEquals(dataset, result)
        assertTrue(inputClosed)
    }

    @Test fun writeAndCloseFailuresPropagateAndAlwaysAttemptClose() {
        for (failOnClose in listOf(false, true)) {
            var closed = false
            val stream = object : OutputStream() {
                override fun write(value: Int) { if (!failOnClose) throw IOException("write failure") }
                override fun close() { closed = true; if (failOnClose) throw IOException("close failure") }
            }
            assertThrows(IOException::class.java) { DatasetDocuments.write(dataset) { stream } }
            assertTrue(closed)
        }
    }

    @Test fun nullDeniedMalformedAndFailedReadsAreReported() {
        assertThrows(IOException::class.java) { DatasetDocuments.write(dataset) { null } }
        assertThrows(SecurityException::class.java) { DatasetDocuments.write(dataset) { throw SecurityException() } }
        assertThrows(IOException::class.java) { DatasetDocuments.read { null } }
        assertThrows(DatasetException::class.java) { DatasetDocuments.read { "{bad".byteInputStream() } }
        assertThrows(IOException::class.java) {
            DatasetDocuments.read { object : InputStream() { override fun read(): Int = throw IOException() } }
        }
        assertThrows(IOException::class.java) {
            DatasetDocuments.read { object : ByteArrayInputStream(DatasetCodec.encode(dataset).toByteArray()) {
                override fun close() { throw IOException("close failure") }
            } }
        }
    }
}
