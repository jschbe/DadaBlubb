package com.github.jschbe.dummlaberer

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class OutputStateTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun editedOutputSurvivesActivityRecreationAndGenerateReplacesIt() {
        val edited = "My edited output\nWith a second line"
        compose.onNodeWithTag("output").performScrollTo().performTextReplacement(edited)
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("output").assertTextEquals(edited)
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Generate").fetchSemanticsNodes().any {
                !it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)
            }
        }
        compose.onNodeWithText("Generate").performScrollTo().performClick()
        compose.onNodeWithTag("output").assert(!hasText(edited)).assertIsNotFocused()
    }
}
