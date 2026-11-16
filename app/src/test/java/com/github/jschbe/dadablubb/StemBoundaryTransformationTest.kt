package com.github.jschbe.dadablubb

import androidx.compose.ui.text.AnnotatedString
import com.github.jschbe.dadablubb.core.*
import java.io.File
import java.util.Locale
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class StemBoundaryTransformationTest {
    @Test fun boundariesComeFromComponentsWithConnectorsAttachedToFollowingStem() {
        val generation = Generation(listOf("Didi", "Möter", "İ𐐀", "Fin"), listOf("di", "", "eldi"))
        assertEquals("Dididimöteri̇𐐨eldifin", generation.text)
        assertEquals(listOf(4, 11, 15), generation.stemBoundaries)
        assertEquals("Didi\u200Bdimöter\u200Bi̇𐐨\u200Beldifin",
            StemBoundaryTransformation(generation).filter(AnnotatedString(generation.text)).text.text)
        assertFalse(generation.text.contains('\u200B'))
    }

    @Test fun seededCountsAndUnicodeHaveMonotonicRoundTripOffsetsAndExactCanonicalText() {
        val dataset = File(checkNotNull(System.getProperty("dada.asset"))).inputStream().use(DatasetCodec::read)
        val generations = (1..50).map {
            Generator(Random(it)).generate(dataset, GeneratorSettings(it, connectors = Connectors.FREQUENT))
        } + Generation(listOf("𐐨İ", "É", "Ος"), listOf("İ", ""))
        for (generation in generations) {
            val original = generation.text
            val transformed = StemBoundaryTransformation(generation).filter(AnnotatedString(original))
            val mapping = transformed.offsetMapping
            assertEquals(generation.components.size - 1, generation.stemBoundaries.size)
            assertEquals(original, transformed.text.text.replace("\u200B", ""))
            val segments = (listOf(0) + generation.stemBoundaries + original.length).zipWithNext { start, end ->
                original.substring(start, end).lowercase(Locale.GERMAN)
            }
            generation.components.forEachIndexed { index, stem ->
                val expected = (if (index == 0) "" else generation.connectors[index - 1]) + stem
                assertEquals(expected.lowercase(Locale.GERMAN), segments[index])
            }
            val forward = (0..original.length).map(mapping::originalToTransformed)
            val backward = (0..transformed.text.length).map(mapping::transformedToOriginal)
            assertTrue(forward.zipWithNext().all { (a, b) -> a <= b })
            assertTrue(backward.zipWithNext().all { (a, b) -> a <= b })
            assertTrue(backward.all { it in 0..original.length })
            (0..original.length).forEach { assertEquals(it, mapping.transformedToOriginal(forward[it])) }
            generation.stemBoundaries.forEachIndexed { index, boundary ->
                assertEquals(boundary, mapping.transformedToOriginal(boundary + index))
                assertEquals(boundary, mapping.transformedToOriginal(boundary + index + 1))
            }
        }
    }

    @Test fun mismatchedOrSingleStemTextHasIdentityTransformation() {
        for (generation in listOf(Generation(listOf("Möter"), emptyList()),
            Generation(listOf("Möter", "Röngel"), listOf("di")))) {
            val edited = AnnotatedString("Edited di text\n😀")
            val result = StemBoundaryTransformation(generation).filter(edited)
            assertEquals(edited, result.text)
            (0..edited.length).forEach { assertEquals(it, result.offsetMapping.originalToTransformed(it)) }
        }
    }
}
