package com.github.jschbe.dadablubb.core

import java.util.Locale
import kotlin.random.Random

enum class Style(val label: String) { MIXED("Mixed"), RHYTHMIC("Rhythmic"), CHAOTIC("Chaotic") }
enum class Repetition(val label: String) { NORMAL("Normal"), HIGH("High") }
enum class Connectors(val label: String) { NORMAL("Normal"), FREQUENT("Frequent") }

data class GeneratorSettings(
    val wordCount: Int = 3,
    val style: Style = Style.MIXED,
    val repetition: Repetition = Repetition.NORMAL,
    val connectors: Connectors = Connectors.NORMAL
) {
    init { require(wordCount in 1..50) }

    fun cappedTo(dataset: Dataset): GeneratorSettings = copy(wordCount = wordCount.coerceAtMost(dataset.maxWordCount))

    companion object {
        fun restore(count: Int, style: String?, repetition: String?, connectors: String?) = GeneratorSettings(
            wordCount = count.coerceIn(1, 50),
            style = Style.entries.find { it.name == style } ?: Style.MIXED,
            repetition = Repetition.entries.find { it.name == repetition } ?: Repetition.NORMAL,
            connectors = Connectors.entries.find { it.name == connectors } ?: Connectors.NORMAL
        )
    }
}

/** Exactly one component per slot and exactly one connector (possibly empty) per adjacency. */
data class Generation(val components: List<String>, val connectors: List<String>) {
    init {
        require(components.isNotEmpty())
        require(components.distinctBy(::stemIdentity).size == components.size) { "Selected stems must be unique." }
        require(connectors.size == components.size - 1)
    }

    val text: String get() {
        val raw = buildString {
            components.forEachIndexed { index, component ->
                if (index > 0) append(connectors[index - 1])
                append(component)
            }
        }.lowercase(Locale.GERMAN)
        val first = raw.codePointAt(0)
        return StringBuilder().appendCodePoint(Character.toTitleCase(first))
            .append(raw.substring(Character.charCount(first))).toString()
    }
}

class Generator(private val random: Random = Random.Default) {
    fun generate(dataset: Dataset, settings: GeneratorSettings): Generation {
        DatasetCodec.validate(dataset)
        val remaining = dataset.uniqueWords.map { Stem(it, SoundProfile.of(it)) }.toMutableList()
        require(settings.wordCount <= remaining.size) {
            "Word Count must not exceed ${remaining.size} unique stems in the active vocabulary."
        }
        val selected = mutableListOf<Stem>()
        val connectors = mutableListOf<String>()
        repeat(settings.wordCount) {
            val word = nextWord(remaining, selected, settings)
            selected += word
            remaining.remove(word)
            if (it > 0) connectors += chooseConnector(dataset.connectors, settings.connectors)
        }
        return Generation(selected.map { it.text }, connectors)
    }

    private data class Stem(val text: String, val sound: SoundProfile)

    private fun nextWord(words: List<Stem>, history: List<Stem>, settings: GeneratorSettings): Stem {
        if (history.isEmpty()) return words.random(random)
        val echoChance = when (settings.style) {
            Style.MIXED -> if (settings.repetition == Repetition.HIGH) 0.80 else 0.35
            Style.RHYTHMIC -> if (settings.repetition == Repetition.HIGH) 0.92 else 0.75
            Style.CHAOTIC -> if (settings.repetition == Repetition.HIGH) 0.65 else 0.05
        }
        val previous = history.last().sound
        val rhythmChance = when (settings.style) {
            Style.MIXED -> 0.45
            Style.RHYTHMIC -> 0.85
            Style.CHAOTIC -> 0.0
        }
        var available = words
        if (random.nextDouble() < rhythmChance) {
            // Choose a loose pulse before sound matching, so an echo cannot lock the cadence.
            val preferredLength = if (previous.syllables == 1) 2 else 1
            available = words.filter { it.sound.syllables == preferredLength }.ifEmpty {
                words.filter { it.sound.syllables <= 2 }.ifEmpty { words }
            }
        }
        var candidates = emptyList<Stem>()
        val echoRun = history.size > 1 && previous.echoes(history[history.lastIndex - 1].sound)
        if (random.nextDouble() < echoChance * if (echoRun) 0.45 else 1.0) {
            // Often reach past the neighbor, but never impose an ABAB or other fixed rhyme scheme.
            val availableRhymes = available.map { it.sound.rhyme }.filter { it.isNotEmpty() }.toSet()
            val eligible = history.filter { it.sound.rhyme in availableRhymes }
            val earlier = eligible.filter { it != history.last() }
            val distantChance = if (settings.repetition == Repetition.HIGH) 0.85 else 0.60
            val targets = if (earlier.isNotEmpty() && random.nextDouble() < distantChance) {
                earlier.filter { !it.sound.echoes(previous) }.ifEmpty { earlier }
            } else eligible
            if (targets.isNotEmpty()) {
                val target = targets.random(random).sound
                candidates = available.filter { it.sound.echoes(target) }
            }
        }
        if (candidates.isEmpty()) {
            val clusterChance = when (settings.style) {
                Style.MIXED -> 0.45
                Style.RHYTHMIC -> 0.85
                Style.CHAOTIC -> 0.0
            }
            candidates = when {
                settings.style == Style.CHAOTIC -> available.filter {
                    !it.sound.clustersWith(previous) && it.sound.onset != previous.onset
                }
                random.nextDouble() < clusterChance -> available.filter {
                    it.sound.clustersWith(previous) && !it.sound.echoes(previous)
                }
                else -> emptyList()
            }
        }
        return candidates.ifEmpty { available }.random(random)
    }

    /** Frequent triples nonempty weights, retaining their relative distribution. Long avoids overflow. */
    internal fun chooseConnector(connectors: List<Connector>, mode: Connectors): String {
        fun weight(connector: Connector): Long = connector.weight.toLong() *
            if (mode == Connectors.FREQUENT && connector.text.isNotEmpty()) 3L else 1L
        val total = connectors.sumOf(::weight)
        var ticket = random.nextLong(total)
        for (connector in connectors) {
            ticket -= weight(connector)
            if (ticket < 0) return connector.text
        }
        error("Connector weights must have a positive total.")
    }
}
