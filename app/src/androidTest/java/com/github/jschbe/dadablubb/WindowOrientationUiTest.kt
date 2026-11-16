package com.github.jschbe.dadablubb

import android.app.UiAutomation
import android.content.res.Configuration
import android.view.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import com.github.jschbe.dadablubb.core.GeneratorSettings
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Run on both requested AVDs; rotation is performed through Android, not a fake density. */
class WindowOrientationUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun formFactorRotationLongOutputAndImeKeepControlsReachable() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val tablet = WindowPolicy.isTablet(compose.activity)
        val model = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        compose.waitUntil(10_000) { model.ready }
        compose.runOnUiThread { model.updateSettings(GeneratorSettings(50)); model.generate() }
        compose.waitUntil(10_000) { !model.outputState.value.saving }
        val generated = model.output
        val hints = model.outputState.value.wrapHints
        assertNotNull(hints)
        val orientations = mutableSetOf<Int>()
        try {
            for (rotation in listOf(UiAutomation.ROTATION_FREEZE_0, UiAutomation.ROTATION_FREEZE_90)) {
                rotate(automation, rotation, tablet)
                val orientation = compose.activity.resources.configuration.orientation
                orientations += orientation
                assertEquals(WindowPolicy.requestedOrientation(tablet), compose.activity.requestedOrientation)
                if (!tablet) assertEquals(Configuration.ORIENTATION_PORTRAIT, orientation)
                compose.onNodeWithTag("output").assert(SemanticsMatcher.expectValue(
                    SemanticsProperties.InputText, AnnotatedString(generated)))
                assertEquals(hints, model.outputState.value.wrapHints)
                val page = compose.onNodeWithTag("generator-layout")
                if (tablet && orientation == Configuration.ORIENTATION_LANDSCAPE) page.assert(hasScrollAction())
                else page.assert(!hasScrollAction())
            }
            if (tablet) assertEquals(setOf(Configuration.ORIENTATION_PORTRAIT, Configuration.ORIENTATION_LANDSCAPE), orientations)

            // Both devices in portrait: long text scrolls internally; the footer does not move.
            rotate(automation, if (tablet) UiAutomation.ROTATION_FREEZE_90 else UiAutomation.ROTATION_FREEZE_0, tablet)
            assertEquals(Configuration.ORIENTATION_PORTRAIT, compose.activity.resources.configuration.orientation)
            val longText = (1..500).joinToString("") { "MöterdiBlubb$it" }
            val field = compose.onNodeWithTag("output")
            field.scrollIntoView().performTextReplacement(longText)
            closeSoftKeyboard()
            val copyLabel = compose.activity.getString(R.string.copy)
            val shareLabel = compose.activity.getString(R.string.share)
            val copy = compose.onNodeWithText(copyLabel).assertIsDisplayed()
            compose.onNodeWithText(shareLabel).assertIsDisplayed()
            val before = copy.fetchSemanticsNode().boundsInRoot
            field.performTouchInput { swipeDown() }
            val image = field.captureToImage().asAndroidBitmap()
            field.performTouchInput { swipeUp() }
            assertFalse(image.sameAs(field.captureToImage().asAndroidBitmap()))
            assertEquals(before, copy.fetchSemanticsNode().boundsInRoot)
            field.assert(SemanticsMatcher.expectValue(SemanticsProperties.InputText, AnnotatedString(longText)))
            compose.onNodeWithTag("generator-layout").assert(!hasScrollAction())

            // Tablet landscape and portrait with the real keyboard; phone remains portrait.
            for (rotation in listOf(UiAutomation.ROTATION_FREEZE_0, UiAutomation.ROTATION_FREEZE_90)) {
                rotate(automation, rotation, tablet)
                field.scrollIntoView().performSemanticsAction(SemanticsActions.RequestFocus) { it() }
                field.assertIsFocused()
                val activity = compose.activity
                compose.runOnUiThread {
                    WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                        .show(WindowInsetsCompat.Type.ime())
                }
                compose.waitUntil(10_000) { imeInsets().isVisible(WindowInsetsCompat.Type.ime()) }
                field.assertIsFocused().performTextInput("ä")
                field.assertIsFocused()
                val expected = model.output
                compose.onNodeWithText(copyLabel).scrollIntoView().assertIsDisplayed().assertIsEnabled()
                compose.onNodeWithText(shareLabel).scrollIntoView().assertIsDisplayed().assertIsEnabled()
                val imeTop = compose.onRoot().fetchSemanticsNode().boundsInRoot.bottom - imeInsets().getInsets(WindowInsetsCompat.Type.ime()).bottom
                assertTrue("Actions must be above the docked IME", compose.onNodeWithText(shareLabel).fetchSemanticsNode().boundsInRoot.bottom <= imeTop + 1f)
                field.assert(SemanticsMatcher.expectValue(SemanticsProperties.InputText, AnnotatedString(expected)))
                closeSoftKeyboard()
            }
        } finally {
            automation.setRotation(UiAutomation.ROTATION_UNFREEZE)
        }
    }

    private fun imeInsets(): WindowInsetsCompat = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)!!

    private fun rotate(automation: UiAutomation, rotation: Int, tablet: Boolean) {
        assertTrue(automation.setRotation(rotation))
        if (tablet) {
            // The explicitly requested Medium Tablet has a natural landscape display.
            val expected = if (rotation == UiAutomation.ROTATION_FREEZE_0)
                Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
            compose.waitUntil(15_000) { compose.activity.resources.configuration.orientation == expected }
            val expectedRotation = if (rotation == UiAutomation.ROTATION_FREEZE_0) Surface.ROTATION_0 else Surface.ROTATION_90
            compose.waitUntil(15_000) { compose.activity.window.decorView.display.rotation == expectedRotation }
        } else {
            compose.waitForIdle()
            assertEquals(Configuration.ORIENTATION_PORTRAIT, compose.activity.resources.configuration.orientation)
        }
        compose.waitForIdle()
    }
}
