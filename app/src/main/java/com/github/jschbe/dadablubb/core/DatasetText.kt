package com.github.jschbe.dadablubb.core

import java.util.Locale

/** Structured validation diagnostics. Android translates these keys at display time. */
enum class DatasetTextKey(val english: String) {
    SIZE("JSON file must be at most 1 MiB."),
    UTF8("JSON file must contain valid UTF-8 text."),
    ROOT_OBJECT("JSON root must be an object."),
    MALFORMED("Malformed JSON. Check quotes, commas and brackets."),
    SCHEMA("Unsupported schemaVersion. Expected 1."),
    WORDS_ARRAY("words must be an array."),
    CONNECTORS_ARRAY("connectors must be an array."),
    CONNECTOR_OBJECT("Connector %1\$d must be an object."),
    EXPORT_SIZE("Dataset is too large when formatted for export; use fewer or shorter entries."),
    WORD_COUNT("Provide 1–%1\$d words."),
    CONNECTOR_COUNT("Provide 1–%1\$d connectors."),
    WORD_DUPLICATES("Words must be distinct; remove duplicates."),
    POSITIVE_WEIGHT("Every connector weight must be a positive integer."),
    CONNECTOR_DUPLICATES("Connector texts must be distinct; remove duplicates."),
    TOTAL_WEIGHT("Total connector weight must not exceed 2147483647."),
    TEXT_LENGTH("%1\$s must contain %2\$d–%3\$d characters."),
    LETTERS("%1\$s must contain only letters and accents, starting with a letter; no whitespace."),
    STRING("%1\$s must be a string."),
    INTEGER("%1\$s must be an integer."),
    INTEGER_RANGE("%1\$s is outside the supported integer range."),
    FIELDS("%1\$s must have exactly these fields: %2\$s."),
    OBJECT_KEY("Malformed JSON object key."),
    DUPLICATE_KEYS("Duplicate JSON fields are not allowed."),
    CONTROL("Malformed JSON: unescaped control character."),
    NESTING("JSON nesting is too deep for schema v1."),
    BRACKET("Malformed JSON: unmatched bracket."),
    INCOMPLETE("Malformed JSON: incomplete string or object."),
    FORMATTED_SIZE("Formatted dataset exceeds 1 MiB."),
    SAVED_OVERRIDE("The saved dataset could not be loaded. Bundled defaults are active. Import a valid file or reset to repair it."),
    LABEL_ROOT("Root"),
    LABEL_SCHEMA("schemaVersion"),
    LABEL_WORD("Word %1\$d"),
    LABEL_CONNECTOR("Connector %1\$d"),
    LABEL_TEXT("Connector text"),
    LABEL_WEIGHT("Connector weight"),
}

data class DatasetText(val key: DatasetTextKey, val arguments: List<Any> = emptyList()) {
    fun english(): String = String.format(Locale.ROOT, key.english,
        *arguments.map { if (it is DatasetText) it.english() else it }.toTypedArray())
}

fun datasetText(key: DatasetTextKey, vararg arguments: Any) = DatasetText(key, arguments.toList())

class DatasetException(val detail: DatasetText) : IllegalArgumentException(detail.english())
