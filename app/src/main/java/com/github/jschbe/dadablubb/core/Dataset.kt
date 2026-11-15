package com.github.jschbe.dadablubb.core

import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.text.Normalizer
import java.util.Locale
import com.github.jschbe.dadablubb.core.DatasetTextKey.*
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
        if (bytes.size > MAX_BYTES) invalid(SIZE)
        val text = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: java.nio.charset.CharacterCodingException) {
            invalid(UTF8)
        }
        return parse(text)
    }

    fun parse(text: String): Dataset {
        if (text.length > MAX_BYTES || text.toByteArray(Charsets.UTF_8).size > MAX_BYTES) {
            invalid(SIZE)
        }
        checkNesting(text)
        val root = try {
            json.parseToJsonElement(text) as? JsonObject
                ?: invalid(ROOT_OBJECT)
        } catch (_: SerializationException) {
            invalid(MALFORMED)
        } catch (_: IllegalArgumentException) {
            invalid(MALFORMED)
        }
        keys(root, setOf("schemaVersion", "words", "connectors"), datasetText(LABEL_ROOT))
        if (integer(root["schemaVersion"], datasetText(LABEL_SCHEMA)) != 1) {
            invalid(SCHEMA)
        }
        val words = (root["words"] as? JsonArray ?: invalid(WORDS_ARRAY))
            .mapIndexed { index, entry -> string(entry, datasetText(LABEL_WORD, index + 1)) }
        val connectors = (root["connectors"] as? JsonArray
            ?: invalid(CONNECTORS_ARRAY)).mapIndexed { index, entry ->
            val item = entry as? JsonObject ?: invalid(CONNECTOR_OBJECT, index + 1)
            keys(item, setOf("text", "weight"), datasetText(LABEL_CONNECTOR, index + 1))
            Connector(string(item["text"], datasetText(LABEL_TEXT)), integer(item["weight"], datasetText(LABEL_WEIGHT)))
        }
        return Dataset(words, connectors).also {
            validate(it)
            if (encode(it).toByteArray(Charsets.UTF_8).size > MAX_BYTES) {
                invalid(EXPORT_SIZE)
            }
        }
    }

    fun validate(dataset: Dataset) {
        if (dataset.words.size !in 1..MAX_WORDS) invalid(WORD_COUNT, MAX_WORDS)
        if (dataset.connectors.size !in 1..MAX_CONNECTORS) invalid(CONNECTOR_COUNT, MAX_CONNECTORS)
        dataset.words.forEachIndexed { index, word -> validateText(word, 64, false, datasetText(LABEL_WORD, index + 1)) }
        if (dataset.words.distinct().size != dataset.words.size) invalid(WORD_DUPLICATES)
        dataset.connectors.forEachIndexed { index, connector ->
            validateText(connector.text, 32, true, datasetText(LABEL_CONNECTOR, index + 1))
            if (connector.weight <= 0) invalid(POSITIVE_WEIGHT)
        }
        if (dataset.connectors.map { it.text }.distinct().size != dataset.connectors.size) {
            invalid(CONNECTOR_DUPLICATES)
        }
        val total = dataset.connectors.sumOf { it.weight.toLong() }
        if (total !in 1..Int.MAX_VALUE.toLong()) invalid(TOTAL_WEIGHT)
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

    private fun validateText(value: String, max: Int, allowEmpty: Boolean, label: DatasetText) {
        if ((!allowEmpty && value.isEmpty()) || value.codePointCount(0, value.length) > max) {
            invalid(TEXT_LENGTH, label, if (allowEmpty) 0 else 1, max)
        }
        // Letters and combining accents preserve Unicode, including supplementary letters.
        // Reject whitespace, controls, invisible formatting, punctuation and unpaired surrogates.
        if (value.codePoints().anyMatch {
                !Character.isLetter(it) && Character.getType(it) !in setOf(
                    Character.NON_SPACING_MARK.toInt(), Character.COMBINING_SPACING_MARK.toInt()
                )
            } || (value.isNotEmpty() && !Character.isLetter(value.codePointAt(0)))) {
            invalid(LETTERS, label)
        }
    }

    private fun string(value: JsonElement?, label: DatasetText): String =
        (value as? JsonPrimitive)?.takeIf { it.isString }?.content ?: invalid(STRING, label)

    private fun integer(value: JsonElement?, label: DatasetText): Int {
        val primitive = value as? JsonPrimitive ?: invalid(INTEGER, label)
        if (primitive.isString || !Regex("-?(0|[1-9][0-9]*)").matches(primitive.content)) {
            invalid(INTEGER, label)
        }
        return primitive.intOrNull ?: invalid(INTEGER_RANGE, label)
    }

    private fun keys(value: JsonObject, expected: Set<String>, label: DatasetText) {
        if (value.keys != expected) invalid(FIELDS, label, expected.joinToString())
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
                            invalid(OBJECT_KEY)
                        }
                        if (objectKeys.lastOrNull()?.add(key) == false) invalid(DUPLICATE_KEYS)
                    }
                }
                else if (char < ' ') invalid(CONTROL)
            } else when (char) {
                '"' -> { quoted = true; stringStart = index }
                '{', '[' -> {
                    if (++depth > 4) invalid(NESTING)
                    objectKeys.add(if (char == '{') mutableSetOf() else null)
                }
                '}', ']' -> {
                    if (--depth < 0) invalid(BRACKET)
                    objectKeys.removeAt(objectKeys.lastIndex)
                }
            }
        }
        if (quoted || depth != 0) invalid(INCOMPLETE)
    }

    private fun invalid(key: DatasetTextKey, vararg arguments: Any): Nothing =
        throw DatasetException(datasetText(key, *arguments))
}
