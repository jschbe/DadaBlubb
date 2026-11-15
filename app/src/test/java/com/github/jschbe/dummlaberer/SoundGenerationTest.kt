package com.github.jschbe.dummlaberer

import com.github.jschbe.dummlaberer.core.*
import java.io.File
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class SoundGenerationTest {
    @Test fun commonGermanSpellingsEchoBeyondLiteralLastTwoLetters() {
        for ((a, b) in listOf("Fex" to "Pheks", "Mai" to "Bey", "Badd" to "Fatt", "Möter" to "Möter")) {
            assertTrue("$a / $b", SoundProfile.of(a).echoes(SoundProfile.of(b)))
        }
        assertFalse(SoundProfile.of("Mau").echoes(SoundProfile.of("Mei")))
        assertTrue(SoundProfile.of("Schnurpsel").clustersWith(SoundProfile.of("Schnuff")))
        assertFalse(SoundProfile.of("Schnurpsel").echoes(SoundProfile.of("Schnuff")))
        assertFalse(SoundProfile.of("Schnurpsel").clustersWith(SoundProfile.of("Knatter")))
    }

    @Test fun seededClustersPreferRelatedSoundsButKeepBroadChoices() {
        val source = Dataset(listOf("Schnurpsel", "Schnuff", "Schnurk", "Schnudel", "Schnup",
            "Knatter", "Knall", "Knack", "Knappel", "Knarr", "Plörr", "Plopp", "Plock", "Plommel", "Plorf"),
            listOf(Connector("", 1)))
        fun clusters(style: Style): Int = (0 until 500).count { seed ->
            val (a, b) = Generator(Random(seed)).generate(source, GeneratorSettings(2, style)).components
            SoundProfile.of(a).clustersWith(SoundProfile.of(b))
        }
        val rhythmic = clusters(Style.RHYTHMIC)
        val mixed = clusters(Style.MIXED)
        val chaotic = clusters(Style.CHAOTIC)
        assertTrue("$rhythmic / $mixed / $chaotic", rhythmic > mixed + 70 && mixed > chaotic + 70)
        assertTrue("Broad draws must remain possible", rhythmic < 490)
        assertTrue((0 until 50).map { Generator(Random(it)).generate(source, GeneratorSettings(8)).text }
            .distinct().size > 40)
    }

    @Test fun highRepetitionIncreasesDistantEchoesForCountsFiveToTen() {
        val source = File(checkNotNull(System.getProperty("dada.asset"))).inputStream().use(DatasetCodec::read)
        for (style in Style.entries) {
            fun distantEchoes(repetition: Repetition): Int = (0 until 160).sumOf { seed ->
                val sounds = Generator(Random(seed)).generate(source,
                    GeneratorSettings(5 + seed % 6, style, repetition)).components.map(SoundProfile::of)
                // An echo that skips the neighbor, with a contrasting sound immediately before it.
                sounds.indices.drop(2).count { i ->
                    !sounds[i].echoes(sounds[i - 1]) && sounds.take(i - 1).any { sounds[i].echoes(it) }
                }
            }
            val normal = distantEchoes(Repetition.NORMAL)
            val high = distantEchoes(Repetition.HIGH)
            assertTrue("$style distant echoes: high=$high normal=$normal", high > normal + 15)
        }
    }

    @Test fun rhythmicStyleFavorsShortLongPulsesWithoutForcingAMeter() {
        val source = Dataset(listOf("Baff", "Daff", "Faff", "Kaff", "Laff", "Maff", "Naff", "Paff",
            "Bebel", "Dedel", "Fefel", "Kekel", "Lelel", "Memel", "Nener", "Peper"), listOf(Connector("di", 1)))
        fun pulses(style: Style): Int = (0 until 200).sumOf { seed ->
            Generator(Random(seed)).generate(source, GeneratorSettings(8, style)).components
                .map { SoundProfile.of(it).syllables }.zipWithNext().count { (a, b) -> a != b }
        }
        val rhythmic = pulses(Style.RHYTHMIC)
        val chaotic = pulses(Style.CHAOTIC)
        assertTrue("Pulses: rhythmic=$rhythmic chaotic=$chaotic", rhythmic > chaotic + 80)
        assertTrue("Meter must remain flexible", rhythmic < 200 * 7)
    }

    @Test fun exhaustedClustersVowellessAndUnicodeImportsAlwaysFinishWithoutDuplicates() {
        val source = Dataset(listOf("Brr", "Psst", "𐐨", "Ö", "Ä", "Schnurpsel", "Pheks", "Fex"),
            listOf(Connector("", 1)))
        for (style in Style.entries) for (repetition in Repetition.entries) for (count in 1..source.words.size) {
            val result = Generator(Random(count)).generate(source, GeneratorSettings(count, style, repetition))
            assertEquals(count, result.components.distinctBy(::stemIdentity).size)
            assertEquals(count - 1, result.connectors.size)
        }
        assertFalse(SoundProfile.of("Brr").echoes(SoundProfile.of("Psst")))
    }
}
