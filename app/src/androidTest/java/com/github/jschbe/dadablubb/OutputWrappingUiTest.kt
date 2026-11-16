package com.github.jschbe.dadablubb

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import com.github.jschbe.dadablubb.core.*
import com.github.jschbe.dadablubb.data.*
import java.io.File
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class OutputWrappingUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val field get() = compose.onNodeWithTag("output")

    private fun layout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        field.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(results)) }
        return results.single()
    }

    private fun assertCanonical(expected: String) {
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.InputText,
            androidx.compose.ui.text.AnnotatedString(expected)))
    }

    private fun assertSelection(start: Int, end: Int = start) {
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(start, end)))
    }

    @Test fun narrowLayoutsBreakAtStructuralBoundariesAndOversizedStemStillWraps() {
        var generation by mutableStateOf(Generation(listOf("Schnurpsel", "Knöter", "Bummel", "Didi"),
            listOf("di", "", "didel")))
        var width by mutableStateOf(240)
        compose.setContent {
            EnglishTestUi {
                Box(Modifier.width(width.dp)) {
                    GeneratorScreen(GeneratorSettings(), generation.text, true, {}, {}, {}, {}, {},
                        generation = generation)
                }
            }
        }
        fun assertBoundaryLines() {
            field.performScrollTo()
            val result = layout()
            val mapping = StemBoundaryTransformation(generation).filter(
                androidx.compose.ui.text.AnnotatedString(generation.text)).offsetMapping
            assertTrue(result.lineCount > 1)
            assertFalse(result.didOverflowWidth)
            for (line in 1 until result.lineCount) {
                val start = mapping.transformedToOriginal(result.getLineStart(line))
                assertTrue("Line $line starts inside a stem at $start", start in generation.stemBoundaries)
            }
            assertCanonical(generation.text)
        }
        assertBoundaryLines()
        compose.runOnIdle { width = 320 }
        assertBoundaryLines()
        compose.runOnIdle {
            width = 240
            val dataset = context.assets.open("dada.json").use(DatasetCodec::read)
            generation = Generator(Random(1)).generate(dataset, GeneratorSettings(50))
        }
        field.performScrollTo()
        assertTrue(layout().lineCount > 8)
        assertFalse(layout().didOverflowWidth)
        assertCanonical(generation.text)
        // Scroll the bounded editor to its end using the ordinary editor cursor/bring-into-view.
        field.performTextInputSelection(TextRange(generation.text.length))
        field.assertIsFocused()
        assertCanonical(generation.text)
        compose.runOnIdle { generation = Generation(listOf("Schnurpsel".repeat(6)), emptyList()) }
        assertTrue(layout().lineCount > 1)
        assertFalse(layout().didOverflowWidth)
        assertCanonical(generation.text)
    }

    @Test fun selectionClipboardCutPasteAndBoundaryEditsKeepCanonicalOffsetsAndDiskText() {
        val directory = File(context.cacheDir, "wrap-${UUID.randomUUID()}").apply { mkdirs() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val disk = OutputStorage(File(directory, "output.txt"))
        val repo = OutputRepository(disk, scope)
        val generation = Generation(listOf("Möter", "Didi", "Röngel"), listOf("di", ""))
        val original = generation.text
        var copied = ""
        var shared = ""
        try {
            compose.waitUntil(10_000) { repo.state.value.loaded }
            repo.update(generation)
            compose.setContent {
                val state by repo.state.collectAsState()
                EnglishTestUi {
                    GeneratorScreen(GeneratorSettings(), state.text, true, {}, { repo.update(generation) },
                        { copied = it }, { shared = it }, repo::update, generation = state.generation)
                }
            }
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            field.performScrollTo().performTextInputSelection(TextRange(0, original.length))
            field.performSemanticsAction(SemanticsActions.CopyText) { it() }
            compose.runOnIdle { assertEquals(original, clipboard.primaryClip!!.getItemAt(0).text.toString()) }
            assertNotNull(repo.state.value.generation) // selection/copy must not invalidate hints
            // Finish the keyboard/clipboard-overlay transition before accessibility-driven selection.
            closeSoftKeyboard()
            compose.waitForIdle()

            // Accessibility selection uses rendered offsets: both sides of a hint map to one boundary.
            val transformed = StemBoundaryTransformation(generation).filter(androidx.compose.ui.text.AnnotatedString(original))
            field.performSemanticsAction(SemanticsActions.SetSelection) {
                assertTrue(it(transformed.offsetMapping.originalToTransformed(3),
                    transformed.offsetMapping.originalToTransformed(9), false))
            }
            assertSelection(3, 9)
            field.performSemanticsAction(SemanticsActions.CutText) { it() }
            val cut = original.removeRange(3, 9)
            assertCanonical(cut)
            assertSelection(3)
            compose.runOnIdle {
                assertEquals(original.substring(3, 9), clipboard.primaryClip!!.getItemAt(0).text.toString())
                assertNull(repo.state.value.generation)
            }
            // Paste through the editor clipboard action, including multiline/supplementary Unicode.
            compose.runOnIdle { clipboard.setPrimaryClip(ClipData.newPlainText("test", "di\n😀")) }
            field.performSemanticsAction(SemanticsActions.PasteText) { it() }
            val pasted = cut.substring(0, 3) + "di\n😀" + cut.substring(3)
            assertCanonical(pasted)
            assertSelection(8)

            compose.runOnIdle { repo.update(generation) }
            val boundary = generation.stemBoundaries.first()
            field.performTextInputSelection(TextRange(boundary))
            field.performTextInput("X")
            assertCanonical(original.substring(0, boundary) + "X" + original.substring(boundary))
            assertSelection(boundary + 1)
            compose.runOnIdle { assertNull(repo.state.value.generation); repo.update(generation) }
            field.performTextInputSelection(TextRange(boundary))
            field.performKeyInput { pressKey(Key.Backspace) }
            assertCanonical(original.removeRange(boundary - 1, boundary))
            assertSelection(boundary - 1)
            compose.runOnIdle { repo.update(generation) }
            field.performTextInputSelection(TextRange(boundary))
            field.performKeyInput { pressKey(Key.Delete) }
            val deleted = original.removeRange(boundary, boundary + 1)
            assertCanonical(deleted)
            assertSelection(boundary)
            closeSoftKeyboard()
            compose.onNodeWithText("Copy").performScrollTo().performClick()
            compose.onNodeWithText("Share").performScrollTo().performClick()
            compose.runOnIdle { assertEquals(deleted, copied); assertEquals(deleted, shared) }
            compose.waitUntil(10_000) { !repo.state.value.saving }
            assertEquals(deleted, disk.read())
            val restored = OutputRepository(disk, scope)
            compose.waitUntil(10_000) { restored.state.value.loaded }
            assertEquals(deleted, restored.state.value.text)
            assertNull(restored.state.value.generation)

            compose.onNodeWithText("Generate").performScrollTo().performClick()
            compose.onNodeWithText("Copy").performScrollTo().performClick()
            compose.onNodeWithText("Share").performScrollTo().performClick()
            compose.runOnIdle { assertEquals(original, copied); assertEquals(original, shared) }
            compose.waitUntil(10_000) { !repo.state.value.saving }
            assertEquals(original, disk.read())
        } finally { scope.cancel(); directory.deleteRecursively() }
    }

    @Test fun realInputConnectionCompositionAndCommitNeverSeeDisplayHints() {
        val generation = Generation(listOf("Möter", "Röngel"), listOf("di"))
        var output by mutableStateOf(generation.text)
        var hints by mutableStateOf<Generation?>(generation)
        lateinit var view: android.view.View
        compose.setContent {
            view = LocalView.current
            EnglishTestUi {
                GeneratorScreen(GeneratorSettings(), output, true, {}, {}, {}, {},
                    { output = it; hints = null }, generation = hints)
            }
        }
        field.performScrollTo().performClick().performTextInputSelection(TextRange(5))
        compose.runOnIdle {
            val info = android.view.inputmethod.EditorInfo()
            val connection = view.onCreateInputConnection(info)!!
            assertEquals(generation.text.substring(0, 5), connection.getTextBeforeCursor(100, 0).toString())
            assertEquals(generation.text.substring(5), connection.getTextAfterCursor(100, 0).toString())
            connection.setComposingText("ä", 1)
        }
        assertCanonical("Möterädiröngel")
        compose.runOnIdle {
            val connection = view.onCreateInputConnection(android.view.inputmethod.EditorInfo())!!
            connection.commitText("ö", 1)
            connection.finishComposingText()
        }
        assertCanonical("Möterödiröngel")
        compose.runOnIdle { assertNull(hints) }
    }
}
