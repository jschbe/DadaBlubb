package com.github.jschbe.dadablubb

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.pressBack
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MilestoneNavigationUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun englishMenuOrderAboutDismissalVectorCenterAndBothBackPaths() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val info = target.packageManager.getPackageInfo(target.packageName, 0)
        lateinit var model: AppViewModel
        compose.runOnIdle { model = AppViewModel(target.applicationContext as Application) }
        compose.setContent {
            EnglishTestUi { DadaBlubbApp(model, AppVersion(info.versionName, info.longVersionCode)) }
        }
        compose.waitUntil(10_000) { model.ready }
        val more = "More options"
        val about = "About App"
        val settings = "Settings"
        val back = "Back"
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription(more).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription(more).performClick()
        val items = listOf(about, settings, "Import JSON", "Export JSON", "Reset to Defaults")
        val positions = items.map { compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot.top }
        assertTrue("Menu order: $positions", positions.zipWithNext().all { (a, b) -> a < b })
        compose.onNodeWithTag("about-entry").performClick()
        compose.onNode(hasText(about) and hasAnyAncestor(isDialog())).assertExists()
        assertEquals("1.2.1", info.versionName)
        assertEquals(4L, info.longVersionCode)
        compose.onNodeWithText("Version ${info.versionName} (code ${info.longVersionCode})")
            .performScrollTo().assertExists()
        compose.onNodeWithText("Copyright © 2026 Jochen Schmitt").performScrollTo().assertExists()
        compose.onNodeWithText("GNU General Public License v3.0 only (GPL-3.0-only)").performScrollTo().assertExists()
        compose.onNodeWithText("https://github.com/jschbe/DadaBlubb").performScrollTo().assertExists()
        pressBack() // About returns to the generator, without entering Settings
        compose.onNodeWithTag("output").assertExists()
        compose.onNodeWithContentDescription(back).assertDoesNotExist()
        compose.onNodeWithContentDescription(more).performClick()
        compose.onNodeWithText(settings).performClick()
        compose.onNodeWithTag("about-entry").assertDoesNotExist()
        compose.onNodeWithText(about).assertDoesNotExist()
        val button = compose.onNodeWithContentDescription(back).fetchSemanticsNode().boundsInRoot
        val iconNode = compose.onNodeWithTag("settings-back-icon", useUnmergedTree = true)
        iconNode.assertWidthIsEqualTo(24.dp).assertHeightIsEqualTo(24.dp)
        val icon = iconNode.fetchSemanticsNode().boundsInRoot
        assertEquals(button.center.x, icon.center.x, 1f)
        assertEquals(button.center.y, icon.center.y, 1f)
        compose.onNodeWithContentDescription(more).performClick()
        compose.onNodeWithTag("about-entry").performClick()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithContentDescription(back).assertExists()
        compose.onNodeWithContentDescription(back).performClick()
        compose.onNodeWithTag("output").assertExists()
        compose.onNodeWithContentDescription(more).performClick()
        compose.onNodeWithText(settings).performClick()
        pressBack()
        compose.onNodeWithTag("output").assertExists()
    }
}
