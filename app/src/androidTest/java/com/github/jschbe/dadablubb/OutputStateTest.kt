package com.github.jschbe.dadablubb

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.github.jschbe.dadablubb.core.GeneratorSettings
import com.github.jschbe.dadablubb.data.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import java.io.File
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class OutputStateTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun generatedFiftyStemHintsSurviveActivityAndColdRepositoryWithExactTextBytes() {
        val model = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        compose.waitUntil(10_000) { model.ready }
        compose.runOnUiThread { model.updateSettings(GeneratorSettings(50)) }
        compose.onNodeWithText(compose.activity.getString(R.string.generate)).scrollIntoView().performClick()
        compose.waitUntil(10_000) { !model.outputState.value.saving }
        val text = model.output
        val hints = model.outputState.value.wrapHints
        assertNotNull(hints)
        assertEquals(49, hints!!.boundaries.size)
        val file = File(compose.activity.filesDir, "output.txt")
        assertArrayEquals(text.toByteArray(Charsets.UTF_8), file.readBytes())
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("output").assert(SemanticsMatcher.expectValue(SemanticsProperties.InputText, AnnotatedString(text)))
        val recreated = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        assertEquals(hints, recreated.outputState.value.wrapHints)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val cold = OutputRepository(OutputStorage(file), scope)
            compose.waitUntil(10_000) { cold.state.value.loaded }
            assertEquals(text, cold.state.value.text)
            assertEquals(hints, cold.state.value.wrapHints)
            assertArrayEquals(text.toByteArray(Charsets.UTF_8), file.readBytes())
        } finally { scope.cancel() }
    }

    @Test fun editedOutputSurvivesActivityRecreationAndGenerateReplacesIt() {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("output").fetchSemanticsNodes().any {
                !it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)
            }
        }
        val edited = "My edited output\nWith a second line"
        compose.onNodeWithTag("output").scrollIntoView().performTextReplacement(edited)
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("output").assertTextEquals(edited)
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(compose.activity.getString(R.string.generate)).fetchSemanticsNodes().any {
                !it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)
            }
        }
        compose.onNodeWithText(compose.activity.getString(R.string.generate)).scrollIntoView().performClick()
        compose.onNodeWithTag("output").assert(!hasText(edited)).assertIsNotFocused()
    }
}
