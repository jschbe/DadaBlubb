package com.github.jschbe.dadablubb

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class OutputStateTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun editedOutputSurvivesActivityRecreationAndGenerateReplacesIt() {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("output").fetchSemanticsNodes().any {
                !it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)
            }
        }
        val edited = "My edited output\nWith a second line"
        compose.onNodeWithTag("output").performScrollTo().performTextReplacement(edited)
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("output").assertTextEquals(edited)
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(compose.activity.getString(R.string.generate)).fetchSemanticsNodes().any {
                !it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)
            }
        }
        compose.onNodeWithText(compose.activity.getString(R.string.generate)).performScrollTo().performClick()
        compose.onNodeWithTag("output").assert(!hasText(edited)).assertIsNotFocused()
    }
}
