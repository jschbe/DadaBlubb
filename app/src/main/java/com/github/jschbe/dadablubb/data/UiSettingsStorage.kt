package com.github.jschbe.dadablubb.data

import android.content.Context
import androidx.core.content.edit
import com.github.jschbe.dadablubb.core.*

/** Separate keys/store leave all existing generator preferences untouched. */
class UiSettingsStorage(context: Context) {
    private val preferences = context.getSharedPreferences("ui-settings", Context.MODE_PRIVATE)

    fun appearance() = Appearance.restore(preferences.getString("appearance", null))
    fun saveAppearance(value: Appearance) { preferences.edit { putString("appearance", value.name) } }
    fun legacyLanguage() = AppLanguage.restore(preferences.getString("language", null))
    // Synchronous: attachBaseContext on the immediately recreated activity must see the new choice.
    // Keep commit()'s Boolean: KTX edit(commit = true) returns Unit and would hide save failures.
    fun saveLegacyLanguage(value: AppLanguage): Boolean =
        preferences.edit().putString("language", value.name).commit()
}
