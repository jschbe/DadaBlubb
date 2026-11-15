package com.github.jschbe.dummlaberer

import com.github.jschbe.dummlaberer.core.*
import java.util.Locale
import java.io.File
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class GeneratorTest {
    private val dataset = Dataset(
        listOf("Ba", "Be", "Bo", "Bu", "Da", "De", "Do", "Du", "Fa", "Fe", "Fo", "Fu")
            .flatMap { start -> listOf("bbel", "mpel", "tter", "gg", "chs").map { start + it } },
        listOf(Connector("di", 60), Connector("de", 10), Connector("", 30)))

    @Test fun exactComponentsAndOnlyInternalConnectors() {
        for (count in listOf(1, 2, 8, 50)) {
            for (style in Style.entries) for (repetition in Repetition.entries) for (connectors in Connectors.entries) {
                val result = Generator(Random(31)).generate(dataset, GeneratorSettings(count, style, repetition, connectors))
                assertEquals(count, result.components.size)
                assertEquals(count, result.components.distinctBy(::stemIdentity).size)
                assertEquals(count - 1, result.connectors.size)
                assertTrue(result.components.all { it in dataset.words })
                assertTrue(result.connectors.all { it in dataset.connectors.map(Connector::text) })
                val reconstructed = buildString {
                    result.components.forEachIndexed { i, word ->
                        if (i > 0) append(result.connectors[i - 1])
                        append(word)
                    }
                }.lowercase(Locale.GERMAN).replaceFirstChar { it.titlecase(Locale.GERMAN) }
                assertEquals(reconstructed, result.text)
            }
        }
    }

    @Test fun oneStemNeverUsesConnectorEvenWhenFrequent() {
        val single = Dataset(listOf("Möter"), listOf(Connector("didel", 1)))
        val result = Generator(Random(0)).generate(single, GeneratorSettings(1, connectors = Connectors.FREQUENT))
        assertEquals(listOf("Möter"), result.components)
        assertTrue(result.connectors.isEmpty())
        assertEquals("Möter", result.text)
    }

    @Test fun emptyConnectorConcatenatesWithoutSeparators() {
        val source = Dataset(listOf("Möter", "Wachs"), listOf(Connector("", 1)))
        val result = Generator(Random(0)).generate(source, GeneratorSettings(2))
        assertEquals(listOf(""), result.connectors)
        assertEquals(result.components.joinToString("").lowercase(Locale.GERMAN)
            .replaceFirstChar { it.titlecase(Locale.GERMAN) }, result.text)
    }

    @Test fun connectorsCanRepeatWhileAllFiftyStemsStayUnique() {
        val source = dataset.copy(connectors = listOf(Connector("di", 1)))
        val result = Generator(Random(3)).generate(source, GeneratorSettings(50, repetition = Repetition.HIGH))
        assertEquals(50, result.components.size)
        assertEquals(50, result.components.distinctBy(::stemIdentity).size)
        assertEquals(List(49) { "di" }, result.connectors)
        assertEquals(result.components.sumOf { it.length } + 49 * 2, result.text.length)
        assertTrue(result.text.startsWith(result.components.first(), ignoreCase = true))
        assertTrue(result.text.endsWith(result.components.last(), ignoreCase = true))
    }

    @Test fun revisedBundleGeneratesEveryAllowedCountWithUniqueStemsAndInternalConnectors() {
        val source = bundledDataset()
        for (count in 1..50) {
            for (style in Style.entries) for (repetition in Repetition.entries) for (connectors in Connectors.entries) {
                val settings = GeneratorSettings(count, style, repetition, connectors)
                val result = Generator(Random(927 + count)).generate(source, settings)
                assertEquals(count, result.components.size)
                assertEquals(count, result.components.distinctBy(::stemIdentity).size)
                assertTrue(result.components.all { it in source.words })
                assertEquals(count - 1, result.connectors.size)
                assertTrue(result.connectors.all { text -> source.connectors.any { it.text == text } })
                val assembled = buildString {
                    result.components.forEachIndexed { index, stem ->
                        if (index > 0) append(result.connectors[index - 1])
                        append(stem)
                    }
                }.lowercase(Locale.GERMAN).replaceFirstChar { it.titlecase(Locale.GERMAN) }
                assertEquals(assembled, result.text)
            }
        }
    }

    @Test fun revisedBundleWeightsDriveNormalAndFrequentConnectorSelection() {
        val connectors = bundledDataset().connectors
        val normal = listOf(0L to "di", 59L to "di", 60L to "de", 64L to "de",
            65L to "da", 69L to "da", 70L to "didel", 74L to "didel",
            75L to "eldi", 79L to "eldi", 80L to "", 99L to "")
        for ((ticket, text) in normal) {
            assertEquals(text, Generator(TicketRandom(ticket, 100)).chooseConnector(connectors, Connectors.NORMAL))
        }
        val frequent = listOf(0L to "di", 179L to "di", 180L to "de", 194L to "de",
            195L to "da", 209L to "da", 210L to "didel", 224L to "didel",
            225L to "eldi", 239L to "eldi", 240L to "", 259L to "")
        for ((ticket, text) in frequent) {
            assertEquals(text, Generator(TicketRandom(ticket, 260)).chooseConnector(connectors, Connectors.FREQUENT))
        }
    }

    @Test fun noCharacterCroppingForFiftyLongUniqueStems() {
        val words = List(50) { "Schnurpsel".repeat(6) + ('a' + it / 26) + ('a' + it % 26) }
        val source = Dataset(words, listOf(Connector("didel", 1)))
        val result = Generator(Random(1)).generate(source, GeneratorSettings(50))
        assertEquals(words.sumOf { it.length } + 49 * 5, result.text.length)
    }

    @Test fun weightedSelectionHitsExactTicketBoundaries() {
        val connectors = listOf(Connector("di", 6), Connector("de", 2), Connector("", 2))
        for ((ticket, expected) in listOf(0L to "di", 5L to "di", 6L to "de", 7L to "de", 8L to "", 9L to "")) {
            assertEquals(expected, Generator(TicketRandom(ticket, 10)).chooseConnector(connectors, Connectors.NORMAL))
        }
        // Nonempty weights 18:6; empty weight stays 2, so total is 26.
        for ((ticket, expected) in listOf(17L to "di", 18L to "de", 23L to "de", 24L to "", 25L to "")) {
            assertEquals(expected, Generator(TicketRandom(ticket, 26)).chooseConnector(connectors, Connectors.FREQUENT))
        }
    }

    @Test fun frequentWeightsDoNotOverflow() {
        val connectors = listOf(Connector("di", Int.MAX_VALUE - 1), Connector("", 1))
        val total = (Int.MAX_VALUE - 1).toLong() * 3 + 1
        assertEquals("", Generator(TicketRandom(total - 1, total)).chooseConnector(connectors, Connectors.FREQUENT))
    }

    @Test fun weightedDistributionAndFrequentEffectAreObservable() {
        val normal = Generator(Random(45))
        val frequent = Generator(Random(45))
        val n = List(20_000) { normal.chooseConnector(dataset.connectors, Connectors.NORMAL) }
        val f = List(20_000) { frequent.chooseConnector(dataset.connectors, Connectors.FREQUENT) }
        assertTrue(n.count { it == "di" } in 11_500..12_500)
        assertTrue(n.count { it.isEmpty() } in 5_500..6_500)
        assertTrue(f.count { it.isEmpty() } < n.count { it.isEmpty() } / 2)
        val nonemptyRatio = f.count { it == "di" }.toDouble() / f.count { it == "de" }
        assertTrue(nonemptyRatio in 5.5..6.5)
    }

    @Test fun rhythmicMixedAndChaoticHaveDifferentActualPatterns() {
        val rhythmic = rhymingPairs(Style.RHYTHMIC, Repetition.NORMAL)
        val mixed = rhymingPairs(Style.MIXED, Repetition.NORMAL)
        val chaotic = rhymingPairs(Style.CHAOTIC, Repetition.NORMAL)
        assertTrue("Rhythmic: $rhythmic, mixed: $mixed", rhythmic > mixed + 400)
        assertTrue("Mixed: $mixed, chaotic: $chaotic", mixed > chaotic + 400)
    }

    @Test fun highRepetitionEchoesSoundsMoreOftenInEveryStyleWithoutRepeatingStems() {
        for (style in Style.entries) {
            val high = rhymingPairs(style, Repetition.HIGH)
            val normal = rhymingPairs(style, Repetition.NORMAL)
            assertTrue("$style high: $high, normal: $normal", high > normal + 200)
        }
    }

    @Test fun deterministicRngProducesIdenticalStructuredResults() {
        val settings = GeneratorSettings(50, Style.RHYTHMIC, Repetition.HIGH, Connectors.FREQUENT)
        assertEquals(Generator(Random(27)).generate(dataset, settings), Generator(Random(27)).generate(dataset, settings))
    }

    @Test fun tinyDatasetsCapDisplayedSettingsAndWorkInEveryMode() {
        val single = Dataset(listOf("Ö"), listOf(Connector("di", 1)))
        for (style in Style.entries) for (repetition in Repetition.entries) for (connectors in Connectors.entries) {
            val settings = GeneratorSettings(50, style, repetition, connectors).cappedTo(single)
            val result = Generator(Random(2)).generate(single, settings)
            assertEquals(1, settings.wordCount)
            assertEquals(style, settings.style)
            assertEquals(repetition, settings.repetition)
            assertEquals(connectors, settings.connectors)
            assertEquals(listOf("Ö"), result.components)
            assertTrue(result.connectors.isEmpty())
        }
    }

    @Test fun normalizedVariantsAreOneChoiceAndCannotBypassCapacity() {
        val variants = Dataset(listOf("Möter", "MÖTER", "Möter", "Wachs"), listOf(Connector("di", 1)))
        assertEquals(2, variants.maxWordCount)
        val settings = GeneratorSettings(50).cappedTo(variants)
        assertEquals(2, settings.wordCount)
        repeat(20) { seed ->
            val result = Generator(Random(seed)).generate(variants, settings)
            assertEquals(setOf("möter", "wachs"), result.components.map(::stemIdentity).toSet())
            assertEquals(2, result.components.size)
        }
        val error = assertThrows(IllegalArgumentException::class.java) {
            Generator(Random(0)).generate(variants, GeneratorSettings(3))
        }
        assertTrue(error.message!!.contains("2 unique stems"))
    }

    @Test fun identityIgnoresCaseSurroundingWhitespaceAndCanonicalAccentEncoding() {
        assertEquals(stemIdentity("Möter"), stemIdentity("  MÖTER  "))
        assertEquals(stemIdentity("Möter"), stemIdentity("Möter"))
        assertEquals(stemIdentity("𐐀"), stemIdentity("𐐨"))
    }

    @Test fun capacityNeverExceedsFiftyAndSettingsNeverSilentlyGenerateShorterOutput() {
        assertEquals(50, dataset.maxWordCount)
        assertEquals(GeneratorSettings(8), GeneratorSettings(8).cappedTo(dataset))
        val small = dataset.copy(words = dataset.words.take(8))
        assertEquals(8, GeneratorSettings(50).cappedTo(small).wordCount)
        assertThrows(IllegalArgumentException::class.java) {
            Generator(Random(0)).generate(small, GeneratorSettings(50))
        }
        assertThrows(IllegalArgumentException::class.java) {
            Generation(listOf("Möter", "MÖTER"), listOf("di"))
        }
    }

    @Test fun supplementaryUnicodeIsPreserved() {
        val result = Generation(listOf("𐐨ö", "Ä"), listOf("di"))
        assertEquals("𐐀ödiä", result.text)
        assertEquals(5, result.text.codePointCount(0, result.text.length))
    }

    @Test fun defaultAndRestoredSettings() {
        assertEquals(GeneratorSettings(), GeneratorSettings.restore(3, null, null, null))
        val custom = GeneratorSettings(50, Style.CHAOTIC, Repetition.HIGH, Connectors.FREQUENT)
        assertEquals(custom, GeneratorSettings.restore(50, custom.style.name, custom.repetition.name, custom.connectors.name))
        assertEquals(1, GeneratorSettings.restore(-1, "unknown", "unknown", "unknown").wordCount)
        assertEquals(50, GeneratorSettings.restore(60, null, null, null).wordCount)
        for (invalid in listOf(0, 51)) assertThrows(IllegalArgumentException::class.java) { GeneratorSettings(invalid) }
    }

    private fun rhymingPairs(style: Style, repetition: Repetition): Int {
        val words = listOf("Ba", "Be", "Bo", "Bu", "Da", "De", "Do", "Du", "Fa", "Fe", "Fo", "Fu")
            // A consonant separates vowel groups: all stems have two syllables, isolating echo
            // preference from the independent short/long rhythm preference.
            .flatMap { start -> listOf("bel", "ter", "igg", "achs", "atz", "und", "opf", "anz", "irk", "usk").map { start + "m" + it } }
        val source = Dataset(words, listOf(Connector("", 1)))
        val generator = Generator(Random(927))
        var rhymes = 0
        repeat(2_000) {
            val components = generator.generate(source, GeneratorSettings(2, style, repetition)).components
            assertEquals(2, components.distinctBy(::stemIdentity).size)
            if (components[0].takeLast(2) == components[1].takeLast(2)) rhymes++
        }
        return rhymes
    }

    private fun bundledDataset(): Dataset = File(checkNotNull(System.getProperty("dada.asset")))
        .inputStream().use(DatasetCodec::read)

    private class TicketRandom(private val ticket: Long, private val expectedBound: Long) : Random() {
        override fun nextBits(bitCount: Int): Int = error("Unexpected RNG call")
        override fun nextLong(until: Long): Long { assertEquals(expectedBound, until); return ticket }
    }
}
