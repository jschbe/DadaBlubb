package com.github.jschbe.dadablubb.core

import java.text.Normalizer

/** Coarse German sound cues, not a pronunciation dictionary. Original stems stay untouched. */
internal data class SoundProfile(
    val onset: String,
    val nucleus: String,
    val rhyme: String,
    val syllables: Int
) {
    fun echoes(other: SoundProfile): Boolean = rhyme.isNotEmpty() && rhyme == other.rhyme

    fun clustersWith(other: SoundProfile): Boolean = echoes(other) ||
        (onset.isNotEmpty() && onset == other.onset && nucleus.isNotEmpty() && nucleus == other.nucleus)

    companion object {
        private val accents = Regex("\\p{M}+")
        private val doubles = Regex("(.)\\1+")
        private val vowels = Regex("[aeiouy]+")

        fun of(stem: String): SoundProfile {
            // Fold umlauts/accents broadly, common spellings, doubled letters and final devoicing.
            val key = Normalizer.normalize(stemIdentity(stem).replace("ä", "e"), Normalizer.Form.NFD)
                .replace(accents, "")
                .replace("ß", "s").replace("x", "ks")
                .replace("sch", "ʃ").replace("ch", "χ")
                .replace("ph", "f").replace("th", "t").replace("ck", "k")
                .replace("tz", "z").replace("z", "ts").replace("qu", "kv").replace("w", "v")
                .replace("ai", "ei").replace("ay", "ei").replace("ey", "ei").replace("ie", "i")
                .replace(doubles, "$1")
                .let { text -> when (text.lastOrNull()) {
                    'b' -> text.dropLast(1) + "p"
                    'd' -> text.dropLast(1) + "t"
                    'g' -> text.dropLast(1) + "k"
                    else -> text
                } }
            val nuclei = vowels.findAll(key).toList()
            return SoundProfile(
                onset = nuclei.firstOrNull()?.let { key.substring(0, it.range.first) }.orEmpty(),
                nucleus = nuclei.firstOrNull()?.value.orEmpty(),
                rhyme = nuclei.lastOrNull()?.let { key.substring(it.range.first) }.orEmpty(),
                syllables = nuclei.size.coerceAtLeast(1)
            )
        }
    }
}
