package com.github.jschbe.dummlaberer.core

import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.text.Normalizer
import java.util.Locale
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*

data class Connector(val text: String, val weight: Int)
data class Dataset(val words: List<String>, val connectors: List<Connector>) {
    // Keep imported spelling and entries intact for export; selection uses one representative.
    val uniqueWords: List<String> get() = words.distinctBy(::stemIdentity)
    val maxWordCount: Int get() = minOf(50, uniqueWords.size)
}

fun stemIdentity(stem: String): String =
    Normalizer.normalize(stem.trim().lowercase(Locale.ROOT), Normalizer.Form.NFC)

class DatasetException(message: String) : IllegalArgumentException(message)

/** Strict schema v1 JSON, with bounded input and nesting before parsing untrusted files. */
object DatasetCodec {
    const val MAX_BYTES = 1_048_576
    const val MAX_WORDS = 5_000
    const val MAX_CONNECTORS = 128
    private val json = Json { prettyPrint = true }

    fun read(input: InputStream): Dataset {
        val buffer = ByteArray(8192)
        val output = ByteArrayOutputStream()
        while (output.size() <= MAX_BYTES) {
            val count = input.read(buffer, 0, minOf(buffer.size, MAX_BYTES + 1 - output.size()))
            if (count == -1) break
            output.write(buffer, 0, count)
        }
        val bytes = output.toByteArray()
        if (bytes.size > MAX_BYTES) invalid("JSON file must be at most 1 MiB.")
        val text = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: java.nio.charset.CharacterCodingException) {
            invalid("JSON file must contain valid UTF-8 text.")
        }
        return parse(text)
    }

    fun parse(text: String): Dataset {
        if (text.length > MAX_BYTES || text.toByteArray(Charsets.UTF_8).size > MAX_BYTES) {
            invalid("JSON file must be at most 1 MiB.")
        }
        checkNesting(text)
        val root = try {
            json.parseToJsonElement(text) as? JsonObject
                ?: invalid("JSON root must be an object.")
        } catch (_: SerializationException) {
            invalid("Malformed JSON. Check quotes, commas and brackets.")
        } catch (_: IllegalArgumentException) {
            invalid("Malformed JSON. Check quotes, commas and brackets.")
        }
        keys(root, setOf("schemaVersion", "words", "connectors"), "Root")
        if (integer(root["schemaVersion"], "schemaVersion") != 1) {
            invalid("Unsupported schemaVersion. Expected 1.")
        }
        val words = (root["words"] as? JsonArray ?: invalid("words must be an array."))
            .mapIndexed { index, entry -> string(entry, "Word ${index + 1}") }
        val connectors = (root["connectors"] as? JsonArray
            ?: invalid("connectors must be an array.")).mapIndexed { index, entry ->
            val item = entry as? JsonObject ?: invalid("Connector ${index + 1} must be an object.")
            keys(item, setOf("text", "weight"), "Connector ${index + 1}")
            Connector(string(item["text"], "Connector text"), integer(item["weight"], "Connector weight"))
        }
        return Dataset(words, connectors).also {
            validate(it)
            if (encode(it).toByteArray(Charsets.UTF_8).size > MAX_BYTES) {
                invalid("Dataset is too large when formatted for export; use fewer or shorter entries.")
            }
        }
    }

    fun validate(dataset: Dataset) {
        if (dataset.words.size !in 1..MAX_WORDS) invalid("Provide 1–$MAX_WORDS words.")
        if (dataset.connectors.size !in 1..MAX_CONNECTORS) invalid("Provide 1–$MAX_CONNECTORS connectors.")
        dataset.words.forEachIndexed { index, word -> validateText(word, 64, false, "Word ${index + 1}") }
        if (dataset.words.distinct().size != dataset.words.size) invalid("Words must be distinct; remove duplicates.")
        dataset.connectors.forEachIndexed { index, connector ->
            validateText(connector.text, 32, true, "Connector ${index + 1}")
            if (connector.weight <= 0) invalid("Every connector weight must be a positive integer.")
        }
        if (dataset.connectors.map { it.text }.distinct().size != dataset.connectors.size) {
            invalid("Connector texts must be distinct; remove duplicates.")
        }
        val total = dataset.connectors.sumOf { it.weight.toLong() }
        if (total !in 1..Int.MAX_VALUE.toLong()) invalid("Total connector weight must not exceed 2147483647.")
    }

    fun encode(dataset: Dataset): String {
        validate(dataset)
        val root = buildJsonObject {
            put("schemaVersion", 1)
            put("words", buildJsonArray { dataset.words.forEach { add(it) } })
            put("connectors", buildJsonArray {
                dataset.connectors.forEach { connector ->
                    add(buildJsonObject {
                        put("text", connector.text)
                        put("weight", connector.weight)
                    })
                }
            })
        }
        return json.encodeToString(JsonElement.serializer(), root) + "\n"
    }

    private fun validateText(value: String, max: Int, allowEmpty: Boolean, label: String) {
        if ((!allowEmpty && value.isEmpty()) || value.codePointCount(0, value.length) > max) {
            invalid("$label must contain ${if (allowEmpty) "0" else "1"}–$max characters.")
        }
        // Letters and combining accents preserve Unicode, including supplementary letters.
        // Reject whitespace, controls, invisible formatting, punctuation and unpaired surrogates.
        if (value.codePoints().anyMatch {
                !Character.isLetter(it) && Character.getType(it) !in setOf(
                    Character.NON_SPACING_MARK.toInt(), Character.COMBINING_SPACING_MARK.toInt()
                )
            } || (value.isNotEmpty() && !Character.isLetter(value.codePointAt(0)))) {
            invalid("$label must contain only letters and accents, starting with a letter; no whitespace.")
        }
    }

    private fun string(value: JsonElement?, label: String): String =
        (value as? JsonPrimitive)?.takeIf { it.isString }?.content ?: invalid("$label must be a string.")

    private fun integer(value: JsonElement?, label: String): Int {
        val primitive = value as? JsonPrimitive ?: invalid("$label must be an integer.")
        if (primitive.isString || !Regex("-?(0|[1-9][0-9]*)").matches(primitive.content)) {
            invalid("$label must be an integer.")
        }
        return primitive.intOrNull ?: invalid("$label is outside the supported integer range.")
    }

    private fun keys(value: JsonObject, expected: Set<String>, label: String) {
        if (value.keys != expected) invalid("$label must have exactly these fields: ${expected.joinToString()}.")
    }

    private fun checkNesting(text: String) {
        var depth = 0
        var quoted = false
        var escaped = false
        var stringStart = 0
        val objectKeys = mutableListOf<MutableSet<String>?>()
        for ((index, char) in text.withIndex()) {
            if (quoted) {
                if (escaped) escaped = false
                else if (char == '\\') escaped = true
                else if (char == '"') {
                    quoted = false
                    var next = index + 1
                    while (next < text.length && text[next].isWhitespace()) next++
                    if (next < text.length && text[next] == ':') {
                        val key = try {
                            (json.parseToJsonElement(text.substring(stringStart, index + 1)) as JsonPrimitive).content
                        } catch (_: Exception) {
                            invalid("Malformed JSON object key.")
                        }
                        if (objectKeys.lastOrNull()?.add(key) == false) invalid("Duplicate JSON fields are not allowed.")
                    }
                }
                else if (char < ' ') invalid("Malformed JSON: unescaped control character.")
            } else when (char) {
                '"' -> { quoted = true; stringStart = index }
                '{', '[' -> {
                    if (++depth > 4) invalid("JSON nesting is too deep for schema v1.")
                    objectKeys.add(if (char == '{') mutableSetOf() else null)
                }
                '}', ']' -> {
                    if (--depth < 0) invalid("Malformed JSON: unmatched bracket.")
                    objectKeys.removeAt(objectKeys.lastIndex)
                }
            }
        }
        if (quoted || depth != 0) invalid("Malformed JSON: incomplete string or object.")
    }

    private fun invalid(message: String): Nothing = throw DatasetException(message)
}
