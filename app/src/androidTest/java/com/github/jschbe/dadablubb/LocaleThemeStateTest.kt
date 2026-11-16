package com.github.jschbe.dadablubb

import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import com.github.jschbe.dadablubb.core.AppLanguage
import com.github.jschbe.dadablubb.data.UiSettingsStorage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LocaleThemeStateTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun activityLocaleAndThemeChangesPreserveEditsNavigationAndGermanAbout() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val originalLanguage = AppLocales.selection(context)
        val originalAppearance = UiSettingsStorage(context).appearance()
        val edited = "Preserved Dada\nEdited output"
        try {
            val activity = compose.activity
            compose.runOnUiThread { assertTrue(AppLocales.apply(activity, AppLanguage.ENGLISH)) }
            waitForText("Generate")
            compose.onNodeWithTag("output").scrollIntoView().performTextReplacement(edited)
            closeSoftKeyboard()
            compose.onNodeWithContentDescription("More options").performClick()
            compose.onNodeWithText("Settings").performClick()
            compose.onNodeWithTag("appearance-LIGHT").performScrollTo().performClick().assertIsSelected()
            val light = compose.onRoot().captureToImage().toPixelMap()[0, 0].luminance()
            compose.onNodeWithTag("appearance-DARK").performScrollTo().performClick().assertIsSelected()
            val dark = compose.onRoot().captureToImage().toPixelMap()[0, 0].luminance()
            assertTrue("Theme background must change: light=$light dark=$dark", light > dark + 0.3f)

            compose.onNodeWithTag("language-GERMAN").performScrollTo().performClick()
            waitForText("Einstellungen")
            compose.onNodeWithTag("language-GERMAN").assertIsSelected()
            compose.onNodeWithTag("appearance-DARK").assertIsSelected()
            compose.onNodeWithContentDescription("Weitere Optionen").performClick()
            // Popups use their Android window's resources: check German in the real locale test.
            val menuPositions = listOf("Über die App", "Einstellungen", "JSON importieren",
                "JSON exportieren", "Standard wiederherstellen").map {
                compose.onNode(hasText(it) and hasAnyAncestor(isPopup())).fetchSemanticsNode().boundsInRoot.top
            }
            assertTrue(menuPositions.zipWithNext().all { (a, b) -> a < b })
            compose.onNodeWithTag("about-entry").performClick()
            compose.onNode(hasText("Über die App") and hasAnyAncestor(isDialog())).assertExists()
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            compose.onNodeWithText("Version ${info.versionName} (Code ${info.longVersionCode})")
                .performScrollTo().assertExists()
            compose.onNodeWithText("Entwickelt mit Unterstützung von OpenAI Codex.").performScrollTo().assertExists()
            compose.onNodeWithText("Schließen").performClick()
            compose.onNodeWithContentDescription("Zurück").performClick()
            compose.onNodeWithContentDescription("Wortanzahl verringern").assertExists()
            compose.onNodeWithText("Anzahl Wortstämme, verbunden zu Fantasiewort").assertExists()
            compose.onNodeWithText("Ein bisschen Rhythmus. Viel Unsinn.").assertDoesNotExist()
            compose.onNodeWithTag("output").assertTextEquals(edited)
        } finally {
            val activity = compose.activity
            compose.runOnUiThread {
                ViewModelProvider(activity)[AppViewModel::class.java].updateAppearance(originalAppearance)
                AppLocales.apply(activity, originalLanguage)
            }
            compose.waitForIdle()
        }
    }

    private fun waitForText(text: String) {
        compose.waitUntil(15_000) {
            runCatching { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
        }
    }
}
