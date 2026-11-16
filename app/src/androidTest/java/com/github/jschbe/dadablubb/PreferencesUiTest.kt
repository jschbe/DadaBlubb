package com.github.jschbe.dadablubb

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.github.jschbe.dadablubb.core.*
import com.github.jschbe.dadablubb.data.SettingsStorage
import com.github.jschbe.dadablubb.data.UiSettingsStorage
import com.github.jschbe.dadablubb.ui.theme.DadaBlubbTheme
import java.util.UUID
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PreferencesUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun appearanceAndLanguageRadiosUpdateIndependentlyOfGenerationSettings() {
        var appearance by mutableStateOf(Appearance.SYSTEM)
        var language by mutableStateOf(AppLanguage.SYSTEM)
        val settings = GeneratorSettings(8, Style.CHAOTIC, Repetition.HIGH, Connectors.FREQUENT)
        compose.setContent {
            EnglishTestUi {
                SettingsScreen(settings, { fail("UI preferences must not change generation settings") },
                    appearance, { appearance = it }, language, { language = it })
            }
        }
        compose.onNodeWithTag("about-entry").assertDoesNotExist()
        compose.onNodeWithText("Appearance").assertIsDisplayed()
        compose.onNodeWithTag("appearance-LIGHT").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("language-GERMAN").performScrollTo().performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(Appearance.LIGHT, appearance); assertEquals(AppLanguage.GERMAN, language) }
        compose.onNodeWithTag("appearance-DARK").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("language-ENGLISH").performScrollTo().performClick().assertIsSelected()
    }

    @Test fun aboutUsesSuppliedVersionMetadata() {
        compose.setContent {
            EnglishTestUi { AboutDialog(AppVersion("test-version", 123), {}, {}) }
        }
        compose.onNodeWithText("About App").assertExists()
        compose.onNodeWithText("Dada Blubb").assertExists()
        compose.onNodeWithText("Version test-version (code 123)").performScrollTo().assertExists()
        compose.onNodeWithText("Developed with the assistance of OpenAI Codex.").performScrollTo().assertExists()
        compose.onNodeWithText("https://github.com/jschbe/DadaBlubb").performScrollTo().assertExists()
    }

    @Test fun configurationChangesUpdateSemanticsAndRadioLabelsWithTheSameContext() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val english = AppLocales.localizedContext(target, AppLanguage.ENGLISH).resources
        val german = AppLocales.localizedContext(target, AppLanguage.GERMAN).resources
        // Intentionally not snapshot state: the only invalidation is LocalConfiguration.
        var currentResources = english
        var configuration by mutableStateOf(Configuration(english.configuration))
        var settingsVisible by mutableStateOf(false)
        compose.setContent {
            val base = LocalContext.current
            val stableContext = remember(base) {
                object : ContextWrapper(base) {
                    override fun getResources() = currentResources
                }
            }
            CompositionLocalProvider(LocalContext provides stableContext, LocalConfiguration provides configuration) {
                DadaBlubbTheme {
                    if (settingsVisible) SettingsScreen(GeneratorSettings(), {})
                    else GeneratorScreen(GeneratorSettings(), "Edited\noutput", true, {}, {}, {}, {}, {})
                }
            }
        }
        compose.onNodeWithContentDescription("Decrease word count").assertExists()
        compose.runOnIdle {
            currentResources = german
            configuration = Configuration(german.configuration)
        }
        compose.onNodeWithContentDescription("Wortanzahl verringern").assertExists()
        compose.onNodeWithContentDescription("Word count: 3").assertDoesNotExist()
        compose.onNodeWithContentDescription("Wortanzahl: 3").assertExists()
        compose.onNodeWithTag("output").assertTextEquals("Edited\noutput")
        compose.runOnIdle { settingsVisible = true }
        compose.onNodeWithTag("appearance-LIGHT").assertTextContains("Hell")
        compose.runOnIdle {
            currentResources = english
            configuration = Configuration(english.configuration)
        }
        compose.onNodeWithTag("appearance-LIGHT").assertTextContains("Light")
        compose.onNodeWithText("Darstellung").assertDoesNotExist()
    }

    @Test fun importedFeedbackUsesIndependentWordAndConnectorPlurals() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (language in listOf(AppLanguage.ENGLISH, AppLanguage.GERMAN)) {
            val resources = AppLocales.localizedContext(context, language).resources
            for (words in 1..2) for (connectors in 1..2) {
                val feedback = UiMessage(R.string.imported, listOf(
                    pluralMessage(R.plurals.word_total, words), pluralMessage(R.plurals.connector_total, connectors)))
                val wordLabel = if (language == AppLanguage.GERMAN) {
                    if (words == 1) "Wort" else "Wörter"
                } else if (words == 1) "word" else "words"
                val connectorLabel = if (language == AppLanguage.GERMAN) {
                    if (connectors == 1) "Verbindung" else "Verbindungen"
                } else if (connectors == 1) "connector" else "connectors"
                val expected = if (language == AppLanguage.GERMAN)
                    "Datensatz importiert: $words $wordLabel und $connectors $connectorLabel."
                else "Dataset imported: $words $wordLabel and $connectors $connectorLabel."
                assertEquals(expected, resources.resolve(feedback))
            }
        }
    }

    @Test fun uiPreferencesPersistWithoutChangingExistingGeneratorKeys() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "test-ui-${UUID.randomUUID()}"
        val isolated = object : ContextWrapper(context) {
            override fun getSharedPreferences(requested: String, mode: Int): SharedPreferences =
                context.getSharedPreferences("$name-$requested", mode)
        }
        try {
            val chosen = GeneratorSettings(9, Style.RHYTHMIC, Repetition.HIGH, Connectors.FREQUENT)
            SettingsStorage(isolated).save(chosen)
            val storage = UiSettingsStorage(isolated)
            assertEquals(Appearance.SYSTEM, storage.appearance())
            assertEquals(AppLanguage.SYSTEM, storage.legacyLanguage())
            storage.saveAppearance(Appearance.DARK)
            storage.saveLegacyLanguage(AppLanguage.GERMAN)
            assertEquals(Appearance.DARK, UiSettingsStorage(isolated).appearance())
            assertEquals(AppLanguage.GERMAN, UiSettingsStorage(isolated).legacyLanguage())
            assertEquals(chosen, SettingsStorage(isolated).load())
            val error = assertThrows(DatasetException::class.java) {
                DatasetCodec.validate(Dataset(listOf("bad word"), listOf(Connector("", 1))))
            }
            val german = AppLocales.localizedContext(context, AppLanguage.GERMAN).resources.resolve(error.detail)
            assertTrue(german.startsWith("Wort 1"))
            assertFalse(german.contains("must contain"))
        } finally {
            context.deleteSharedPreferences("$name-ui-settings")
            context.deleteSharedPreferences("$name-generator-settings")
        }
    }
}
