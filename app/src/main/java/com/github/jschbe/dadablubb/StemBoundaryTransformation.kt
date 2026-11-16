package com.github.jschbe.dadablubb

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.github.jschbe.dadablubb.core.Generation

/** Soft break hints exist only in the layout. The editor/IME, selection clipboard, app actions
 * and persistence all use the original text. Every text edit discards the Generation upstream.
 * Android's normal line breaker still splits a segment too wide for the field. */
internal class StemBoundaryTransformation(generation: Generation) : VisualTransformation {
    private val original = generation.text
    private val boundaries = generation.stemBoundaries

    override fun filter(text: AnnotatedString): TransformedText {
        // Also guard against stale metadata at the rendering boundary; never reconstruct it.
        if (text.text != original || boundaries.isEmpty()) return VisualTransformation.None.filter(text)
        val rendered = AnnotatedString.Builder()
        var start = 0
        boundaries.forEach { end ->
            rendered.append(text.subSequence(start, end))
            rendered.append('\u200B')
            start = end
        }
        rendered.append(text.subSequence(start, text.length))
        val hintOffsets = boundaries.mapIndexed { index, offset -> offset + index }
        return TransformedText(rendered.toAnnotatedString(), object : OffsetMapping {
            // Both sides of a hint map to the same canonical cursor boundary.
            override fun originalToTransformed(offset: Int) = offset + boundaries.count { it <= offset }
            override fun transformedToOriginal(offset: Int) = offset - hintOffsets.count { it < offset }
        })
    }
}
