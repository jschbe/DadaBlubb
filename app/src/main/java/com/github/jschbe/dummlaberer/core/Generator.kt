package com.github.jschbe.dummlaberer.core

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
        val remaining = dataset.uniqueWords.toMutableList()
        require(settings.wordCount <= remaining.size) {
            "Word Count must not exceed ${remaining.size} unique stems in the active vocabulary."
        }
        val components = mutableListOf<String>()
        val connectors = mutableListOf<String>()
        repeat(settings.wordCount) {
            val word = nextWord(remaining, components, settings)
            components += word
            remaining.remove(word)
            if (it > 0) connectors += chooseConnector(dataset.connectors, settings.connectors)
        }
        return Generation(components, connectors)
    }

    private fun nextWord(words: List<String>, history: List<String>, settings: GeneratorSettings): String {
        if (history.isEmpty()) return words.random(random)
        // Repetition now echoes endings rather than selecting an already-used stem.
        val echoChance = when (settings.style) {
            Style.MIXED -> if (settings.repetition == Repetition.HIGH) 0.80 else 0.35
            Style.RHYTHMIC -> if (settings.repetition == Repetition.HIGH) 0.95 else 0.75
            Style.CHAOTIC -> if (settings.repetition == Repetition.HIGH) 0.65 else 0.05
        }
        if (random.nextDouble() < echoChance) {
            val target = history.random(random)
            val echoes = words.filter { rhyme(it) == rhyme(target) }
            if (echoes.isNotEmpty()) return echoes.random(random)
        }
        val previous = history.last()
        val candidates = when (settings.style) {
            Style.RHYTHMIC -> words.filter { firstLetter(it) == firstLetter(previous) }
            Style.CHAOTIC -> words.filter {
                rhyme(it) != rhyme(previous) && firstLetter(it) != firstLetter(previous)
            }
            Style.MIXED -> emptyList()
        }
        return candidates.ifEmpty { words }.random(random)
    }

    private fun firstLetter(word: String): Int = Character.toLowerCase(word.codePointAt(0))

    private fun rhyme(word: String): String {
        val lower = word.lowercase(Locale.GERMAN)
        val count = lower.codePointCount(0, lower.length)
        return lower.substring(lower.offsetByCodePoints(0, (count - 2).coerceAtLeast(0)))
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
