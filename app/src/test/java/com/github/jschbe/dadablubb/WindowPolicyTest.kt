package com.github.jschbe.dadablubb

import android.content.pm.ActivityInfo
import org.junit.Assert.*
import org.junit.Test

class WindowPolicyTest {
    @Test fun maximumWindowThresholdIsRotationIndependentAndIncludes600Dp() {
        for ((width, height, density, tablet) in listOf(
            Quadruple(1080, 2400, 2.625f, false),
            Quadruple(1600, 2560, 2f, true),
            Quadruple(1198, 2000, 2f, false),
            Quadruple(1200, 2000, 2f, true))) {
            assertEquals(tablet, WindowPolicy.isTablet(width, height, density))
            assertEquals(tablet, WindowPolicy.isTablet(height, width, density))
        }
        assertFalse(WindowPolicy.isTablet(0, 0, 0f))
    }

    @Test fun onlySmallDisplaysRequestPortraitAndLargeDisplaysRespectUserRotation() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, WindowPolicy.requestedOrientation(false))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_USER, WindowPolicy.requestedOrientation(true))
    }

    @Test fun portraitIsBoundedTabletLandscapeMayScrollAndHeightFontExceptionsRemain() {
        assertFalse(WindowPolicy.scrollPage(false, false, 700f, 1f))
        assertFalse(WindowPolicy.scrollPage(true, false, 1100f, 1f))
        assertTrue(WindowPolicy.scrollPage(true, true, 700f, 1f))
        assertTrue(WindowPolicy.scrollPage(true, true, 400f, 2f))
        for (tablet in listOf(false, true)) {
            assertTrue(WindowPolicy.scrollPage(tablet, false, 220f, 1f))
            assertTrue(WindowPolicy.scrollPage(tablet, false, 400f, 2f))
            assertFalse(WindowPolicy.scrollPage(tablet, false, 500f, 2f))
        }
    }

    private data class Quadruple(val width: Int, val height: Int, val density: Float, val tablet: Boolean)
}
