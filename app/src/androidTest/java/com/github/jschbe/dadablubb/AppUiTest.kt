package com.github.jschbe.dadablubb

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import com.github.jschbe.dadablubb.core.*
import com.github.jschbe.dadablubb.ui.theme.DadaBlubbTheme
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class AppUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun exactCountControlsAndEmptyOutputActions() {
        var settings by mutableStateOf(GeneratorSettings())
        compose.setContent {
            EnglishTestUi {
                GeneratorScreen(settings, "", true, { settings = it }, {}, {}, {}, {})
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
            EnglishTestUi {
                GeneratorScreen(settings, output, true, {},
                    { output = Generator(Random(1)).generate(dataset, settings).text },
                    { copied = it }, { shared = it }, { output = it })
            }
        }
        compose.onNodeWithText("Generate").scrollIntoView().performClick()
        compose.onNodeWithText(expected).assertExists()
        compose.onNodeWithText("Copy").scrollIntoView().assertIsEnabled().performClick()
        compose.onNodeWithText("Share").scrollIntoView().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(expected, copied); assertEquals(expected, shared) }
    }

    @Test fun editedMultilineOutputFeedsActionsAndGenerateReplacesItWithoutFocusing() {
        var output by mutableStateOf("")
        var copied = ""
        var shared = ""
        val generated = "Möterdiröngel"
        val edited = "First edited line\n" + "Schnurpsel ".repeat(180) + "\nLast line"
        compose.setContent {
            EnglishTestUi {
                GeneratorScreen(GeneratorSettings(), output, true, {}, { output = generated },
                    { copied = it }, { shared = it }, { output = it })
            }
        }
        compose.onNodeWithTag("output").assertIsNotFocused()
        compose.onNodeWithText("Generate").performClick()
        compose.onNodeWithTag("output").assertTextEquals(generated).assertIsNotFocused()
        compose.onNodeWithTag("output").scrollIntoView().performTextReplacement(edited)
        compose.onNodeWithTag("output").assertTextEquals(edited)
        // Finish the system IME transition before scrolling to action hit targets.
        closeSoftKeyboard()
        compose.waitForIdle()
        compose.onNodeWithText("Copy").scrollIntoView().performClick()
        compose.runOnIdle { assertEquals("Copy must use the complete edited field", edited, copied) }
        compose.onNodeWithText("Share").scrollIntoView().performClick()
        compose.runOnIdle { assertEquals("Share must use the complete edited field", edited, shared) }
        compose.onNodeWithText("Generate").scrollIntoView().performClick()
        compose.onNodeWithTag("output").assertTextEquals(generated).assertIsNotFocused()
        compose.onNodeWithTag("output").scrollIntoView().performTextClearance()
        compose.onNodeWithText("Copy").assertIsNotEnabled()
        compose.onNodeWithText("Share").assertIsNotEnabled()
    }

    @Test fun appCopiesEditedTextAndBuildsSharesheetIntentWithoutLaunchingAnotherApp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        lateinit var model: AppViewModel
        var launched: Intent? = null
        var denyShare = false
        compose.runOnIdle { model = AppViewModel(context.applicationContext as Application) }
        compose.setContent {
            val activityContext = LocalContext.current
            val capturingContext = remember(activityContext) {
                object : ContextWrapper(activityContext) {
                    override fun startActivity(intent: Intent) {
                        if (denyShare) throw SecurityException("Test denied share")
                        launched = intent
                    }
                }
            }
            CompositionLocalProvider(LocalContext provides capturingContext) {
                EnglishTestUi { DadaBlubbApp(model, AppVersion("test", 42)) }
            }
        }
        compose.waitUntil(10_000) { model.ready }
        val edited = "Edited Möter\nSecond line 😀\n "
        compose.onNodeWithTag("output").scrollIntoView().performTextReplacement(edited)
        closeSoftKeyboard()
        compose.waitForIdle()
        assertCopyToast("Copied") {
            compose.onNodeWithText("Copy").scrollIntoView().performClick()
        }
        compose.runOnIdle {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            assertEquals(edited, clipboard.primaryClip!!.getItemAt(0).text.toString())
            assertNull(model.message)
        }
        compose.onAllNodes(isDialog()).assertCountEquals(0)
        closeSoftKeyboard()
        compose.waitForIdle()
        compose.onNodeWithText("Share").scrollIntoView().performClick()
        compose.runOnIdle {
            assertEquals(Intent.ACTION_CHOOSER, launched!!.action)
            @Suppress("DEPRECATION")
            val send = launched!!.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
            assertEquals(Intent.ACTION_SEND, send.action)
            assertEquals("text/plain", send.type)
            assertEquals(edited, send.getStringExtra(Intent.EXTRA_TEXT))
        }
        compose.onNodeWithText("Generate").scrollIntoView().performClick()
        val generated = model.output
        compose.runOnIdle { org.junit.Assert.assertNotNull(model.outputState.value.wrapHints) }
        compose.onNodeWithText("Copy").scrollIntoView().performClick()
        compose.runOnIdle {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            assertEquals(generated, clipboard.primaryClip!!.getItemAt(0).text.toString())
            assertNull(model.message)
        }
        compose.onAllNodes(isDialog()).assertCountEquals(0)
        compose.onNodeWithText("Share").scrollIntoView().performClick()
        compose.runOnIdle {
            @Suppress("DEPRECATION")
            val send = launched!!.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
            assertEquals(generated, send.getStringExtra(Intent.EXTRA_TEXT))
        }
        // Copy no longer sets a modal message; existing errors and other notices still do.
        compose.runOnIdle { denyShare = true }
        compose.onNodeWithText("Share").scrollIntoView().performClick()
        compose.onNode(hasText("The selected app could not receive the text.") and hasAnyAncestor(isDialog()))
            .assertIsDisplayed()
        compose.onNodeWithText("OK").performClick()
        compose.runOnIdle { model.notify(R.string.exported) }
        compose.onNode(hasText("Active dataset exported.") and hasAnyAncestor(isDialog())).assertIsDisplayed()
        compose.onNodeWithText("OK").performClick()
    }

    @Test fun settingsRadioControlsUpdateOneSettingsValue() {
        var settings by mutableStateOf(GeneratorSettings())
        compose.setContent { EnglishTestUi { SettingsScreen(settings, { settings = it }) } }
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
            EnglishTestUi {
                GeneratorScreen(settings, "", true, {}, {}, {}, {}, {}, maxWordCount = source.maxWordCount)
            }
        }
        compose.onNodeWithContentDescription("Word count: 1").assertExists()
        compose.onNodeWithContentDescription("Decrease word count").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Increase word count").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Word Count").assertIsNotEnabled()
        compose.onNodeWithText("This dataset has 1 unique stem. Word Count is limited to 1.").assertExists()
    }
}

/** Observe the system toast even when Android renders it outside the app's Compose roots. */
internal fun assertCopyToast(expected: String, copy: () -> Unit) {
    val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    val event = automation.executeAndWaitForEvent({ copy() }, { event ->
        event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED &&
            event.className?.toString() == "android.widget.Toast" &&
            event.text.any { it.toString() == expected }
    }, 5_000)
    @Suppress("DEPRECATION")
    event.recycle()
}

/** Fixtures stay deterministic without changing the phone's system or application locale. */
@Composable
internal fun EnglishTestUi(content: @Composable () -> Unit) {
    val base = LocalContext.current
    val localized = remember(base) {
        val localizedResources = AppLocales.localizedContext(base, AppLanguage.ENGLISH).resources
        object : ContextWrapper(base) {
            override fun getResources() = localizedResources
        }
    }
    CompositionLocalProvider(LocalContext provides localized,
        LocalConfiguration provides localized.resources.configuration) {
        DadaBlubbTheme(content = content)
    }
}

/** Bounded main-screen children need no ancestor scroller; the extreme-height fallback does. */
internal fun SemanticsNodeInteraction.scrollIntoView(): SemanticsNodeInteraction = apply {
    if (!isDisplayed()) performScrollTo()
}
