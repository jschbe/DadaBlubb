package com.github.jschbe.dadablubb

import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AboutLinksUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun privacyAndSourceLinksUseActionViewWithoutAppContent() {
        val launches = mutableListOf<Intent>()
        showAbout({ launches += Intent(it) }, { fail("Unexpected link error") })
        for ((label, url) in listOf(
            "Privacy Policy" to "https://jschbe.github.io/DadaBlubb/privacy.html",
            "Source code" to "https://github.com/jschbe/DadaBlubb"
        )) {
            compose.onNodeWithText(label).performScrollTo().assertIsDisplayed().performClick()
            compose.runOnIdle {
                val intent = launches.last()
                assertEquals(Intent.ACTION_VIEW, intent.action)
                assertEquals(url, intent.dataString)
                assertNull(intent.extras)
                assertNull(intent.component)
                assertNull(intent.`package`)
            }
        }
        compose.runOnIdle { assertEquals(2, launches.size) }
        compose.onNodeWithText("Close").assertIsDisplayed()
    }

    @Test fun missingBrowserUsesExistingErrorCallbackAndKeepsDialogOpen() {
        assertLaunchFailure { throw ActivityNotFoundException("No handler") }
    }

    @Test fun deniedLaunchUsesExistingErrorCallbackAndKeepsDialogOpen() {
        assertLaunchFailure { throw SecurityException("Denied") }
    }

    private fun assertLaunchFailure(launch: (Intent) -> Unit) {
        var errors = 0
        showAbout(launch) { errors++ }
        for ((index, label) in listOf("Privacy Policy", "Source code").withIndex()) {
            compose.onNodeWithText(label).performScrollTo().performClick()
            compose.runOnIdle { assertEquals(index + 1, errors) }
            compose.onNodeWithText("Close").assertIsDisplayed()
        }
    }

    private fun showAbout(launch: (Intent) -> Unit, onLinkError: () -> Unit) {
        compose.setContent {
            EnglishTestUi {
                val base = LocalContext.current
                val capturingContext = remember(base) {
                    object : ContextWrapper(base) {
                        override fun startActivity(intent: Intent) = launch(intent)
                    }
                }
                CompositionLocalProvider(LocalContext provides capturingContext) {
                    AboutDialog(AppVersion("test-version", 123), {}, onLinkError)
                }
            }
        }
    }
}
