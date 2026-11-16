package com.github.jschbe.dadablubb

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import com.github.jschbe.dadablubb.core.*
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class GeneratorLayoutUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun fiftyStemsScrollInsideEditorWhileActionsStayFixedAtSmallHeights() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dataset = context.assets.open("dada.json").use(DatasetCodec::read)
        val generation = Generator(Random(5)).generate(dataset, GeneratorSettings(50))
        var width by mutableIntStateOf(320)
        var height by mutableIntStateOf(640)
        var copied = ""
        var shared = ""
        compose.setContent {
            EnglishTestUi {
                val config = Configuration(LocalConfiguration.current).apply {
                    orientation = if (width > height) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
                }
                CompositionLocalProvider(LocalConfiguration provides config) {
                    Box(Modifier.requiredSize(width.dp, height.dp)) {
                        GeneratorScreen(GeneratorSettings(50), generation.text, true, {}, {},
                            { copied = it }, { shared = it }, {}, wrapHints = OutputWrapHints.from(generation))
                    }
                }
            }
        }
        compose.onNodeWithText("A little rhythm. A lot of nonsense.").assertDoesNotExist()
        compose.onNodeWithText("Number of stems combined into a fantasy word").assertExists()
        compose.onNodeWithText("Generate").assertIsDisplayed()
        for ((w, h) in listOf(320 to 640, 320 to 360, 240 to 360)) {
            compose.runOnIdle { width = w; height = h }
            val field = compose.onNodeWithTag("output")
            val copy = compose.onNodeWithText("Copy").assertIsDisplayed()
            val share = compose.onNodeWithText("Share").assertIsDisplayed()
            val copyBefore = copy.fetchSemanticsNode().boundsInRoot
            val fieldBefore = field.fetchSemanticsNode().boundsInRoot
            val imageBefore = field.captureToImage().asAndroidBitmap()
            field.performTouchInput { swipeUp() }
            val imageAfter = field.captureToImage().asAndroidBitmap()
            assertFalse("The editor content must scroll at $w x $h", imageBefore.sameAs(imageAfter))
            assertEquals(copyBefore, copy.fetchSemanticsNode().boundsInRoot)
            assertEquals(fieldBefore, field.fetchSemanticsNode().boundsInRoot)
            copy.performClick()
            share.performClick()
            compose.runOnIdle { assertEquals(generation.text, copied); assertEquals(generation.text, shared) }
            field.assert(SemanticsMatcher.expectValue(SemanticsProperties.InputText, AnnotatedString(generation.text)))
        }
    }

    @Test fun largeFontsNarrowWidthAndOpenImeHaveReachableActionsAndKeepEditorFocus() {
        var width by mutableIntStateOf(240)
        var height by mutableIntStateOf(640)
        var fontScale by mutableFloatStateOf(1.8f)
        var output by mutableStateOf("Möter".repeat(100))
        var imeBottom = 0
        var copied = ""
        var shared = ""
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                val ime = WindowInsets.ime.getBottom(LocalDensity.current)
                SideEffect { imeBottom = ime }
                EnglishTestUi {
                    val config = Configuration(LocalConfiguration.current).apply {
                        orientation = if (width > height) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
                    }
                    CompositionLocalProvider(LocalConfiguration provides config) {
                        Box(Modifier.requiredSize(width.dp, height.dp)) {
                            GeneratorScreen(GeneratorSettings(50), output, true, {}, {},
                                { copied = it }, { shared = it }, { output = it })
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Copy").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText("Share").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        val field = compose.onNodeWithTag("output")
        field.scrollIntoView().performClick()
        compose.waitUntil(10_000) { imeBottom > 0 }
        field.assertIsFocused()
        field.performTextInput("ä")
        field.assertIsFocused()
        compose.onNodeWithText("Copy").scrollIntoView().performClick()
        compose.onNodeWithText("Share").scrollIntoView().performClick()
        compose.runOnIdle { assertEquals(output, copied); assertEquals(output, shared) }
        closeSoftKeyboard()
        // Landscape-like height and even larger fonts exercise the explicit page fallback.
        compose.runOnIdle { width = 320; height = 220; fontScale = 2f }
        compose.onNodeWithText("Generate").scrollIntoView().assertIsDisplayed()
        field.scrollIntoView().assertIsDisplayed()
        compose.onNodeWithText("Copy").scrollIntoView().assertIsDisplayed().performClick()
        compose.onNodeWithText("Share").scrollIntoView().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(output, copied); assertEquals(output, shared) }
    }
}
