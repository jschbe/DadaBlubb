package com.github.jschbe.dadablubb

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlin.math.min

/** Orientation eligibility uses the maximum display window, never the keyboard-reduced area.
 * The layout separately observes the current window's orientation and usable constraints. */
internal object WindowPolicy {
    fun isTablet(widthPixels: Int, heightPixels: Int, density: Float): Boolean =
        density > 0 && min(widthPixels, heightPixels) / density >= 600f

    @Suppress("DEPRECATION") // Real display metrics are the API 28–29 fallback only.
    fun isTablet(context: Context): Boolean {
        val manager = context.getSystemService(WindowManager::class.java)
            ?: return context.resources.configuration.smallestScreenWidthDp >= 600
        return if (Build.VERSION.SDK_INT >= 30) {
            val bounds = manager.maximumWindowMetrics.bounds
            isTablet(bounds.width(), bounds.height(), context.resources.displayMetrics.density)
        } else {
            val metrics = DisplayMetrics()
            manager.defaultDisplay.getRealMetrics(metrics)
            isTablet(metrics.widthPixels, metrics.heightPixels, metrics.density)
        }
    }

    fun requestedOrientation(tablet: Boolean): Int = if (tablet)
        ActivityInfo.SCREEN_ORIENTATION_FULL_USER else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

    fun apply(activity: Activity) {
        val orientation = requestedOrientation(isTablet(activity))
        // No global manifest lock or large-screen opt-out. Android may override requests in
        // large/freeform windows; the same responsive layout remains usable in either shape.
        if (activity.requestedOrientation != orientation) activity.requestedOrientation = orientation
    }

    fun scrollPage(tablet: Boolean, landscape: Boolean, heightDp: Float, fontScale: Float): Boolean =
        (tablet && landscape) || heightDp < 232 * fontScale
}
