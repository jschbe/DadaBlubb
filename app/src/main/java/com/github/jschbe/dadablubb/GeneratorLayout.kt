package com.github.jschbe.dadablubb

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

/** Portrait keeps the editor and actions bounded. Tablet landscape and extremely short/large-
 * font windows allow one page scroller with a bounded editor. The children
 * stay at the same composition sites across IME/resizing, preserving selection and composition. */
@Composable
internal fun GeneratorLayout(
    controls: @Composable () -> Unit,
    editor: @Composable () -> Unit,
    actions: @Composable () -> Unit
) {
    val pageScroll = rememberScrollState()
    val controlsScroll = rememberScrollState()
    val density = LocalDensity.current
    val tablet = WindowPolicy.isTablet(LocalContext.current)
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    BoxWithConstraints(Modifier.fillMaxSize().imePadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
        val scrollPage = WindowPolicy.scrollPage(tablet, landscape, maxHeight.value, density.fontScale)
        val viewportHeight = with(density) { maxHeight.roundToPx() }
        val editorMinimum = with(density) { (104 * fontScale).dp.roundToPx() }
        val fallbackEditor = with(density) { (180 * fontScale).dp.roundToPx() }
        Layout(
            content = {
                Box(Modifier.fillMaxWidth().testTag("generator-controls").then(
                    if (scrollPage) Modifier else Modifier.verticalScroll(controlsScroll)
                )) { controls() }
                Box(Modifier.fillMaxWidth()) { editor() }
                Box(Modifier.fillMaxWidth()) { actions() }
            },
            modifier = Modifier.fillMaxWidth().testTag("generator-layout").then(
                if (scrollPage) Modifier.verticalScroll(pageScroll) else Modifier.fillMaxHeight()
            )
        ) { children, constraints ->
            val gap = 8.dp.roundToPx()
            val free = Constraints(minWidth = constraints.maxWidth, maxWidth = constraints.maxWidth)
            val footer = children[2].measure(free)
            val controlLimit = if (scrollPage) Constraints.Infinity else
                (constraints.maxHeight - footer.height - editorMinimum - gap * 2).coerceAtLeast(0)
            val header = children[0].measure(free.copy(maxHeight = controlLimit))
            val editorHeight = if (scrollPage)
                maxOf(fallbackEditor, viewportHeight - header.height - footer.height - gap * 2) else
                (constraints.maxHeight - header.height - footer.height - gap * 2).coerceAtLeast(0)
            val output = children[1].measure(free.copy(minHeight = editorHeight, maxHeight = editorHeight))
            val height = if (scrollPage) header.height + output.height + footer.height + gap * 2 else constraints.maxHeight
            layout(constraints.maxWidth, height) {
                header.placeRelative(0, 0)
                output.placeRelative(0, header.height + gap)
                footer.placeRelative(0, height - footer.height)
            }
        }
    }
}
