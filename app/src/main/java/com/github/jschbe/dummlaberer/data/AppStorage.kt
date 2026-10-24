package com.github.jschbe.dummlaberer.data

import android.content.Context
import androidx.core.content.edit
import com.github.jschbe.dummlaberer.core.GeneratorSettings

/** Small local preferences need no database or additional persistence dependency. */
class SettingsStorage(context: Context) {
    private val preferences = context.getSharedPreferences("generator-settings", Context.MODE_PRIVATE)

    fun load(): GeneratorSettings = GeneratorSettings.restore(
        preferences.getInt("wordCount", 3),
        preferences.getString("style", null),
        preferences.getString("repetition", null),
        preferences.getString("connectors", null)
    )

    fun save(settings: GeneratorSettings) {
        preferences.edit {
            putInt("wordCount", settings.wordCount)
            putString("style", settings.style.name)
            putString("repetition", settings.repetition.name)
            putString("connectors", settings.connectors.name)
        }
    }
}
