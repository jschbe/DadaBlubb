package com.github.jschbe.dadablubb

import com.github.jschbe.dadablubb.core.*
import java.io.ByteArrayInputStream
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class DatasetCodecTest {
    private val valid = """{"schemaVersion":1,"words":["Möter","Röngel"],"connectors":[{"text":"di","weight":60},{"text":"","weight":15}]}"""

    @Test fun validUtf8RoundTripRetainsAllValuesAndWeights() {
        val dataset = DatasetCodec.read(ByteArrayInputStream(valid.toByteArray(Charsets.UTF_8)))
        assertEquals(listOf("Möter", "Röngel"), dataset.words)
        assertEquals(listOf(Connector("di", 60), Connector("", 15)), dataset.connectors)
        val exported = DatasetCodec.encode(dataset)
        assertTrue(exported.contains('\n'))
        assertTrue(exported.contains("Möter"))
        assertEquals(dataset, DatasetCodec.parse(exported))
    }

    @Test fun bundledVocabularyIsCuratedValidAndRoundTrips() {
        val dataset = File(checkNotNull(System.getProperty("dada.asset"))).inputStream().use(DatasetCodec::read)
        assertEquals(449, dataset.words.size)
        assertTrue(dataset.words.containsAll(listOf("Möter", "Röngel", "Döngel", "Rubbel", "Nuddel", "Wachs", "Blogg", "Schnurpsel", "Knöter", "Bummel", "Pömpel")))
        assertTrue(dataset.words.all { it.isNotBlank() && it == it.trim() })
        assertEquals(449, dataset.words.distinctBy(::stemIdentity).size)
        assertEquals(50, dataset.maxWordCount)
        assertEquals(6, dataset.connectors.size)
        assertEquals(
            mapOf("di" to 60, "de" to 5, "da" to 5, "didel" to 5, "eldi" to 5, "" to 20),
            dataset.connectors.associate { it.text to it.weight }
        )
        assertEquals(100, dataset.connectors.sumOf { it.weight })
        assertEquals(dataset, DatasetCodec.parse(DatasetCodec.encode(dataset)))
    }

    @Test fun schemaAndFieldValidation() {
        for (input in listOf(
            valid.replace("\"schemaVersion\":1", "\"schemaVersion\":2"),
            valid.replace("\"schemaVersion\":1", "\"schemaVersion\":\"1\""),
            valid.replace("\"schemaVersion\":1", "\"schemaVersion\":1.0"),
            valid.replace("\"schemaVersion\":1", "\"schemaVersion\":true"),
            valid.replace("\"schemaVersion\":1,", ""),
            valid.replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"extra\":0"),
            valid.replace("\"words\":[\"Möter\",\"Röngel\"]", "\"words\":null"),
            valid.replace("\"words\":[\"Möter\",\"Röngel\"]", "\"words\":{}"),
            valid.replace("\"text\":\"di\"", "\"text\":\"di\",\"extra\":0"),
            valid.replace("\"text\":\"di\"", "\"text\":3"),
            valid.replace("\"weight\":60", "\"weight\":null"),
            "[]", "null", "{}"
        )) rejects(input)
    }

    @Test fun emptyDuplicateWhitespaceAndUnsafeWordsAreRejectedWithoutTrimming() {
        for (words in listOf("[]", "[\"\"]", "[\"Möter\",\"Möter\"]", "[\" Möter\"]", "[\"Möter \" ]",
            "[\"Mö ter\"]", "[\"Möter\\n\"]", "[\"Möter\\u200B\"]", "[\"Möter\\u0000\"]", "[\"\\uD800\"]",
            "[42]", "[null]", "[\"<script>\"]", "[\"́Möter\"]", "[\"${"a".repeat(65)}\"]")) {
            rejects("""{"schemaVersion":1,"words":$words,"connectors":[{"text":"","weight":1}]}""")
        }
    }

    @Test fun validAccentsAndSupplementaryLettersAreNotMutated() {
        val source = Dataset(listOf("Möter", "Möter", "𐐨"), listOf(Connector("", 1), Connector("ö", 2)))
        assertEquals(source, DatasetCodec.parse(DatasetCodec.encode(source)))
    }

    @Test fun smallImportsAndNormalizedDuplicatesStayCompatibleAndExportUnchanged() {
        val source = Dataset(listOf("Möter", "MÖTER", "Möter", "Wachs"), listOf(Connector("", 1)))
        val imported = DatasetCodec.parse(DatasetCodec.encode(source))
        assertEquals(source, imported)
        assertEquals(2, imported.maxWordCount)
        assertEquals(listOf("Möter", "Wachs"), imported.uniqueWords)
        val settings = GeneratorSettings(8).cappedTo(imported)
        assertEquals(2, settings.wordCount)
        assertEquals(2, Generator(kotlin.random.Random(1)).generate(imported, settings).components.size)
        // Existing schema rules still reject exact duplicates and padded/invalid entries.
        assertThrows(DatasetException::class.java) {
            DatasetCodec.encode(source.copy(words = listOf("Möter", "Möter")))
        }
        assertThrows(DatasetException::class.java) {
            DatasetCodec.encode(source.copy(words = listOf(" Möter ")))
        }
    }

    @Test fun invalidConnectorsAndWeightsAreRejected() {
        for (connectors in listOf("[]", "[null]", "[\"di\"]", "[{}]",
            """[{"text":"di","weight":0}]""", """[{"text":"di","weight":-1}]""",
            """[{"text":"di","weight":1.5}]""", """[{"text":"di","weight":"5"}]""",
            """[{"text":"di","weight":1e2}]""", """[{"text":"di","weight":2147483648}]""",
            """[{"text":"di","weight":2147483647},{"text":"","weight":1}]""",
            """[{"text":"di","weight":1},{"text":"di","weight":2}]""",
            """[{"text":" di","weight":1}]""", """[{"text":"d i","weight":1}]""",
            """[{"text":"${"d".repeat(33)}","weight":1}]""")) {
            rejects("""{"schemaVersion":1,"words":["Möter"],"connectors":$connectors}""")
        }
    }

    @Test fun maximumTotalWeightAndEmptyOnlyConnectorAreValid() {
        val source = Dataset(listOf("Möter"), listOf(Connector("", Int.MAX_VALUE)))
        assertEquals(source, DatasetCodec.parse(DatasetCodec.encode(source)))
    }

    @Test fun malformedJsonIsRejected() {
        for (input in listOf("", "{", valid.dropLast(1), "$valid trailing", "$valid$valid",
            valid.replace("\"words\"", "words"), valid.replace("\"Möter\"", "'Möter'"),
            valid.replace("\"Möter\",", "\"Möter\",,"), valid.replace("\"Röngel\"]", "\"Röngel\",]"),
            valid.replace("\"Möter\"", "\"Möter\n\""), valid.replace("\"Möter\"", "\"\\q\""),
            valid.replace("\"schemaVersion\":1", "/* comment */\"schemaVersion\":1"),
            valid.replace("\"schemaVersion\":1", "\"schemaVersion\":01"))) rejects(input)
    }

    @Test fun duplicateObjectFieldsIncludingEscapesAreRejected() {
        rejects(valid.replace("\"schemaVersion\":1", "\"schemaVersion\":2,\"schemaVersion\":1"))
        rejects(valid.replace("\"schemaVersion\":1", "\"schemaVersion\":2,\"schema\\u0056ersion\":1"))
        rejects(valid.replace("\"weight\":60", "\"weight\":0,\"weight\":60"))
    }

    @Test fun fileAndEntryLimits() {
        rejects(" ".repeat(DatasetCodec.MAX_BYTES + 1))
        rejects("ö".repeat(DatasetCodec.MAX_BYTES / 2 + 1))
        assertThrows(DatasetException::class.java) {
            DatasetCodec.read(ByteArrayInputStream(ByteArray(DatasetCodec.MAX_BYTES + 1)))
        }
        val words = List(DatasetCodec.MAX_WORDS + 1) { "Word" + alphabetic(it) }
        assertThrows(DatasetException::class.java) { DatasetCodec.validate(Dataset(words, listOf(Connector("", 1)))) }
        val connectors = List(DatasetCodec.MAX_CONNECTORS + 1) { Connector(alphabetic(it), 1) }
        assertThrows(DatasetException::class.java) { DatasetCodec.validate(Dataset(listOf("Möter"), connectors)) }
    }

    @Test fun excessiveNestingRejectedBeforeJsonParser() {
        rejects("[".repeat(10_000) + "]".repeat(10_000))
    }

    @Test fun malformedUtf8IsRejected() {
        assertThrows(DatasetException::class.java) { DatasetCodec.read(ByteArrayInputStream(byteArrayOf(0xC3.toByte(), 0x28))) }
    }

    @Test fun errorsAreActionableEnglishMessages() {
        val error = assertThrows(DatasetException::class.java) { DatasetCodec.parse(valid.replace("\"weight\":60", "\"weight\":0")) }
        assertTrue(error.message!!.contains("positive integer"))
    }

    private fun rejects(text: String) { assertThrows("Expected rejection: ${text.take(140)}", DatasetException::class.java) { DatasetCodec.parse(text) } }
    private fun alphabetic(number: Int): String = "${('a'.code + number / 676).toChar()}${('a'.code + number / 26 % 26).toChar()}${('a'.code + number % 26).toChar()}"
}
