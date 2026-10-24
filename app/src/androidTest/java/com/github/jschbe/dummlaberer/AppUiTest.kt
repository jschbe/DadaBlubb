package com.github.jschbe.dummlaberer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.github.jschbe.dummlaberer.core.*
import com.github.jschbe.dummlaberer.ui.theme.DummlabererTheme
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AppUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun exactCountControlsAndEmptyOutputActions() {
        var settings by mutableStateOf(GeneratorSettings())
        compose.setContent {
            DummlabererTheme {
                GeneratorScreen(settings, "", true, { settings = it }, {}, {}, {})
            }
        }
        compose.onNodeWithText("Copy").assertIsNotEnabled()
        compose.onNodeWithText("Share").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Word count: 3").assertExists()
        compose.onNodeWithContentDescription("Decrease word count").performClick().performClick()
        compose.onNodeWithContentDescription("Word count: 1").assertExists()
        compose.onNodeWithContentDescription("Decrease word count").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Increase word count").performClick()
        compose.runOnIdle { assertEquals(2, settings.wordCount); settings = settings.copy(wordCount = 49) }
        compose.onNodeWithContentDescription("Increase word count").performClick()
        compose.onNodeWithContentDescription("Word count: 50").assertExists()
        compose.onNodeWithContentDescription("Increase word count").assertIsNotEnabled()
    }

    @Test fun fiftyStemOutputIsCompleteSelectableAndCopyShareReceiveFullValue() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dataset = context.assets.open("dada.json").use(DatasetCodec::read)
        val settings = GeneratorSettings(50)
        val expected = Generator(Random(1)).generate(dataset, settings).text
        var output by mutableStateOf("")
        var copied = ""
        var shared = ""
        compose.setContent {
            DummlabererTheme {
                GeneratorScreen(settings, output, true, {},
                    { output = Generator(Random(1)).generate(dataset, settings).text },
                    { copied = output }, { shared = output })
            }
        }
        compose.onNodeWithText("Generate").performScrollTo().performClick()
        compose.onNodeWithText(expected).assertExists()
        compose.onNodeWithText("Copy").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText("Share").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(expected, copied); assertEquals(expected, shared) }
    }

    @Test fun settingsRadioControlsUpdateOneSettingsValue() {
        var settings by mutableStateOf(GeneratorSettings())
        compose.setContent { DummlabererTheme { SettingsScreen(settings) { settings = it } } }
        compose.onNodeWithText("Rhythmic").performScrollTo().performClick()
        compose.onNodeWithText("Rhythmic").assertIsSelected()
        compose.onNodeWithText("High").performScrollTo().performClick()
        compose.onNodeWithText("Frequent").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(GeneratorSettings(3, Style.RHYTHMIC, Repetition.HIGH, Connectors.FREQUENT), settings)
        }
    }

    @Test fun singleStemVocabularyLimitsControlsAndExplainsTheLimit() {
        val source = Dataset(listOf("Möter", "MÖTER"), listOf(Connector("di", 1)))
        val settings = GeneratorSettings(8).cappedTo(source)
        compose.setContent {
            DummlabererTheme {
                GeneratorScreen(settings, "", true, {}, {}, {}, {}, maxWordCount = source.maxWordCount)
            }
        }
        compose.onNodeWithContentDescription("Word count: 1").assertExists()
        compose.onNodeWithContentDescription("Decrease word count").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Increase word count").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Word Count").assertIsNotEnabled()
        compose.onNodeWithText("This dataset has 1 unique stem. Word Count is limited to 1.").assertExists()
    }
}
