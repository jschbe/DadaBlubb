package com.github.jschbe.dadablubb

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import com.github.jschbe.dadablubb.core.AppLanguage
import com.github.jschbe.dadablubb.data.UiSettingsStorage
import java.util.Locale

/** Framework app locales on API 33+; a persisted configuration context on API 28–32. */
object AppLocales {
    fun selection(context: Context): AppLanguage = if (Build.VERSION.SDK_INT >= 33) {
        val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
        if (locales.isEmpty) AppLanguage.SYSTEM else AppLanguage.fromTag(locales[0].toLanguageTag())
    } else UiSettingsStorage(context).legacyLanguage()

    fun localizedContext(context: Context, choice: AppLanguage = selection(context)): Context {
        val system = if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).systemLocales
        } else Resources.getSystem().configuration.locales
        val tag = choice.effectiveTag(if (system.isEmpty) "" else system[0].toLanguageTag())
        val locale = Locale.forLanguageTag(tag)
        return context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocales(LocaleList(locale))
            setLayoutDirection(locale)
        })
    }

    fun apply(activity: Activity, choice: AppLanguage): Boolean {
        if (selection(activity) == choice) return true
        if (Build.VERSION.SDK_INT >= 33) {
            // An empty list really means follow system, including later system-language changes.
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                LocaleList.forLanguageTags(choice.tag)
        } else {
            if (!UiSettingsStorage(activity).saveLegacyLanguage(choice)) return false
            activity.recreate()
        }
        return true
    }
}
