package com.github.jschbe.dadablubb

import com.github.jschbe.dadablubb.core.*
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class UiPreferencesTest {
    @Test fun defaultsAndUnknownStoredValuesAreSafe() {
        for (value in listOf(null, "", "future")) {
            assertEquals(Appearance.SYSTEM, Appearance.restore(value))
            assertEquals(AppLanguage.SYSTEM, AppLanguage.restore(value))
        }
        for (value in Appearance.entries) assertEquals(value, Appearance.restore(value.name))
        for (value in AppLanguage.entries) assertEquals(value, AppLanguage.restore(value.name))
    }

    @Test fun systemUsesFirstLanguageWithEnglishFallbackAndExplicitChoicesOverrideIt() {
        for (tag in listOf("de", "de-DE", "de-CH")) {
            assertEquals("de", AppLanguage.SYSTEM.effectiveTag(tag))
        }
        for (tag in listOf("en", "en-US", "fr-FR", "tr", "ja", "", "und")) {
            assertEquals("en", AppLanguage.SYSTEM.effectiveTag(tag))
        }
        for (tag in listOf("de-DE", "en-GB", "fr")) {
            assertEquals("de", AppLanguage.GERMAN.effectiveTag(tag))
            assertEquals("en", AppLanguage.ENGLISH.effectiveTag(tag))
        }
    }

    @Test fun everyEnglishResourceHasAGermanTranslationWithMatchingFormatArguments() {
        val en = strings("values")
        val de = strings("values-de")
        assertTrue(en.size > 100)
        assertEquals(en.keys, de.keys)
        val format = Regex("%(\\d+)\\$([ds])")
        for ((name, text) in en) {
            assertFalse("Blank German resource: $name", de.getValue(name).isBlank())
            assertEquals("Format arguments: $name", format.findAll(text).map { it.value }.toSet(),
                format.findAll(de.getValue(name)).map { it.value }.toSet())
        }
        for (key in DatasetTextKey.entries) {
            assertTrue("Missing diagnostic: $key", "dataset_${key.name.lowercase(java.util.Locale.ROOT)}" in en)
        }
        assertEquals("Dada Blubb", en["app_name"])
        assertEquals(en["app_name"], de["app_name"])
    }

    @Test fun structuredValidationKeepsUsefulEnglishJvmDiagnostics() {
        val error = assertThrows(DatasetException::class.java) {
            DatasetCodec.validate(Dataset(listOf("bad word"), listOf(Connector("", 1))))
        }
        assertEquals(DatasetTextKey.LETTERS, error.detail.key)
        assertTrue(error.message!!.startsWith("Word 1 must contain only letters"))
        assertEquals(DatasetTextKey.LABEL_WORD, (error.detail.arguments.first() as DatasetText).key)
    }

    private fun strings(directory: String): Map<String, String> {
        val file = File(checkNotNull(System.getProperty("app.resources")), "$directory/strings.xml")
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = document.getElementsByTagName("string")
        val result = (0 until nodes.length).associate { index ->
            val node = nodes.item(index)
            node.attributes.getNamedItem("name").nodeValue to node.textContent
        }.toMutableMap()
        val plurals = document.getElementsByTagName("plurals")
        for (index in 0 until plurals.length) {
            val plural = plurals.item(index) as org.w3c.dom.Element
            val items = plural.getElementsByTagName("item")
            val quantities = (0 until items.length).map { items.item(it) as org.w3c.dom.Element }
            assertEquals(setOf("one", "other"), quantities.map { it.getAttribute("quantity") }.toSet())
            for (item in quantities) {
                result[plural.getAttribute("name") + "." + item.getAttribute("quantity")] = item.textContent
            }
        }
        return result
    }
}
