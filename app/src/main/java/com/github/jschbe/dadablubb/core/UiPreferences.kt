package com.github.jschbe.dadablubb.core

enum class Appearance { SYSTEM, LIGHT, DARK;
    companion object {
        fun restore(value: String?) = entries.find { it.name == value } ?: SYSTEM
    }
}

enum class AppLanguage(val tag: String) { SYSTEM(""), GERMAN("de"), ENGLISH("en");
    fun effectiveTag(systemTag: String): String = when (this) {
        SYSTEM -> if (java.util.Locale.forLanguageTag(systemTag).language == "de") "de" else "en"
        else -> tag
    }

    companion object {
        fun restore(value: String?) = entries.find { it.name == value } ?: SYSTEM
        fun fromTag(tag: String) = when (java.util.Locale.forLanguageTag(tag).language) {
            "de" -> GERMAN
            "en" -> ENGLISH
            else -> SYSTEM
        }
    }
}
