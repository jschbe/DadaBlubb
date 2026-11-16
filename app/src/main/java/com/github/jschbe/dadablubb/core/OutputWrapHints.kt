package com.github.jschbe.dadablubb.core

import java.security.MessageDigest

/** Positions in the exact displayed canonical text, never reconstructed from a vocabulary. */
data class OutputWrapHints(val textHash: String, val boundaries: List<Int>) {
    fun matches(text: String): Boolean {
        if (text.isEmpty() || boundaries.size > 49 || textHash != hash(text)) return false
        var previous = 0
        for (offset in boundaries) {
            if (offset <= previous || offset >= text.length) return false
            if (Character.isHighSurrogate(text[offset - 1]) && Character.isLowSurrogate(text[offset])) return false
            // Imported stems start with a letter. Do not accept a break before an accent mark.
            if (Character.getType(text.codePointAt(offset)) in setOf(
                    Character.NON_SPACING_MARK.toInt(), Character.COMBINING_SPACING_MARK.toInt(),
                    Character.ENCLOSING_MARK.toInt())) return false
            previous = offset
        }
        return true
    }

    companion object {
        fun from(generation: Generation) = OutputWrapHints(hash(generation.text), generation.stemBoundaries)

        private fun hash(text: String): String = MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
