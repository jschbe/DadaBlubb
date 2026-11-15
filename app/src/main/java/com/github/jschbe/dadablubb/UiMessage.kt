package com.github.jschbe.dadablubb

import android.content.res.Resources
import com.github.jschbe.dadablubb.core.*

/** Keep messages independent of the locale active when an asynchronous operation finishes. */
data class UiMessage(val resource: Int, val arguments: List<Any> = emptyList(), val quantity: Int? = null)

fun pluralMessage(resource: Int, quantity: Int) = UiMessage(resource, listOf(quantity), quantity)

fun Resources.resolve(message: UiMessage): String {
    val arguments = message.arguments.map { argument -> when (argument) {
        is UiMessage -> resolve(argument)
        is DatasetText -> resolve(argument)
        else -> argument
    } }.toTypedArray()
    return message.quantity?.let { getQuantityString(message.resource, it, *arguments) }
        ?: getString(message.resource, *arguments)
}

internal fun Resources.resolve(message: DatasetText): String = getString(when (message.key) {
    DatasetTextKey.SIZE -> R.string.dataset_size
    DatasetTextKey.UTF8 -> R.string.dataset_utf8
    DatasetTextKey.ROOT_OBJECT -> R.string.dataset_root_object
    DatasetTextKey.MALFORMED -> R.string.dataset_malformed
    DatasetTextKey.SCHEMA -> R.string.dataset_schema
    DatasetTextKey.WORDS_ARRAY -> R.string.dataset_words_array
    DatasetTextKey.CONNECTORS_ARRAY -> R.string.dataset_connectors_array
    DatasetTextKey.CONNECTOR_OBJECT -> R.string.dataset_connector_object
    DatasetTextKey.EXPORT_SIZE -> R.string.dataset_export_size
    DatasetTextKey.WORD_COUNT -> R.string.dataset_word_count
    DatasetTextKey.CONNECTOR_COUNT -> R.string.dataset_connector_count
    DatasetTextKey.WORD_DUPLICATES -> R.string.dataset_word_duplicates
    DatasetTextKey.POSITIVE_WEIGHT -> R.string.dataset_positive_weight
    DatasetTextKey.CONNECTOR_DUPLICATES -> R.string.dataset_connector_duplicates
    DatasetTextKey.TOTAL_WEIGHT -> R.string.dataset_total_weight
    DatasetTextKey.TEXT_LENGTH -> R.string.dataset_text_length
    DatasetTextKey.LETTERS -> R.string.dataset_letters
    DatasetTextKey.STRING -> R.string.dataset_string
    DatasetTextKey.INTEGER -> R.string.dataset_integer
    DatasetTextKey.INTEGER_RANGE -> R.string.dataset_integer_range
    DatasetTextKey.FIELDS -> R.string.dataset_fields
    DatasetTextKey.OBJECT_KEY -> R.string.dataset_object_key
    DatasetTextKey.DUPLICATE_KEYS -> R.string.dataset_duplicate_keys
    DatasetTextKey.CONTROL -> R.string.dataset_control
    DatasetTextKey.NESTING -> R.string.dataset_nesting
    DatasetTextKey.BRACKET -> R.string.dataset_bracket
    DatasetTextKey.INCOMPLETE -> R.string.dataset_incomplete
    DatasetTextKey.FORMATTED_SIZE -> R.string.dataset_formatted_size
    DatasetTextKey.SAVED_OVERRIDE -> R.string.dataset_saved_override
    DatasetTextKey.LABEL_ROOT -> R.string.dataset_label_root
    DatasetTextKey.LABEL_SCHEMA -> R.string.dataset_label_schema
    DatasetTextKey.LABEL_WORD -> R.string.dataset_label_word
    DatasetTextKey.LABEL_CONNECTOR -> R.string.dataset_label_connector
    DatasetTextKey.LABEL_TEXT -> R.string.dataset_label_text
    DatasetTextKey.LABEL_WEIGHT -> R.string.dataset_label_weight
}, *message.arguments.map { if (it is DatasetText) resolve(it) else it }.toTypedArray())
