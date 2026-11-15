package com.github.jschbe.dadablubb

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import com.github.jschbe.dadablubb.core.*
import com.github.jschbe.dadablubb.data.DatasetStorage
import com.github.jschbe.dadablubb.data.SettingsStorage
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class SettingsStorageTest {
    @Test fun preferencesSurviveNewStoreAndDatasetResetKeepsThem() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testName = "test-settings-${UUID.randomUUID()}"
        val isolated = object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                context.getSharedPreferences(testName, mode)
        }
        val temporary = File(context.cacheDir, "test-dada-${UUID.randomUUID()}.json")
        try {
            assertEquals(GeneratorSettings(), SettingsStorage(isolated).load())
            val chosen = GeneratorSettings(50, Style.CHAOTIC, Repetition.HIGH, Connectors.FREQUENT)
            SettingsStorage(isolated).save(chosen)
            assertEquals(chosen, SettingsStorage(isolated).load())
            val dataset = DatasetStorage(temporary) { context.assets.open("dada.json") }
            dataset.replace(Dataset(listOf("Möter"), listOf(Connector("", 1))))
            assertEquals(listOf("Möter"), dataset.load().dataset.words)
            assertTrue(dataset.reset().words.size >= 200)
            assertFalse(temporary.exists())
            assertEquals(chosen, SettingsStorage(isolated).load())
            SettingsStorage(isolated).save(chosen.copy(wordCount = 1))
            assertEquals(1, SettingsStorage(isolated).load().wordCount)
        } finally {
            context.deleteSharedPreferences(testName)
            temporary.delete()
        }
    }
}
