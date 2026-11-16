package com.github.jschbe.dadablubb

import java.io.File
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element

class PrivacyPolicyTest {
    private fun document(file: File) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
    }.newDocumentBuilder().parse(file)

    private fun policy() = document(File(checkNotNull(System.getProperty("privacy.policy"))))

    @Test fun policyContainsBothLanguagesContactAndDataHandlingDisclosures() {
        val doc = policy()
        val sections = doc.getElementsByTagName("section")
        assertEquals(2, sections.length)
        for ((index, language) in listOf("de", "en").withIndex()) {
            val section = sections.item(index) as Element
            assertEquals(language, section.getAttribute("lang"))
            assertEquals(8, section.getElementsByTagName("h2").length)
            val text = section.textContent
            for (required in listOf("Dada Blubb", "Jochen Schmitt", "android@jochen-schmitt.com",
                "Android", "JSON", "GitHub Pages", "GitHub", "2026")) {
                assertTrue("Missing $required in $language", text.contains(required))
            }
            val contact = section.getElementsByTagName("a").item(0) as Element
            assertEquals("mailto:android@jochen-schmitt.com", contact.getAttribute("href"))
        }
        val text = doc.documentElement.textContent
        for (required in listOf("Datenschutzerklärung", "Privacy Policy", "Cloud-Backups",
            "cloud backups", "gesamte aktuelle Ausgabetext", "entire current output text",
            "Android 13", "16. November 2026", "November 16, 2026")) {
            assertTrue("Missing disclosure: $required", text.contains(required))
        }
    }

    @Test fun policyIsStaticAndSelfContainedWithoutEditableOrActiveContent() {
        val doc = policy()
        for (tag in listOf("script", "link", "iframe", "object", "embed", "img", "audio",
            "video", "form", "input", "textarea", "button")) {
            assertEquals("Unexpected $tag", 0, doc.getElementsByTagName(tag).length)
        }
        val elements = doc.getElementsByTagName("*")
        for (index in 0 until elements.length) {
            val element = elements.item(index) as Element
            for (attributeIndex in 0 until element.attributes.length) {
                val attribute = element.attributes.item(attributeIndex)
                val name = attribute.nodeName.lowercase(java.util.Locale.ROOT)
                assertFalse("Active attribute: $name", name.startsWith("on"))
                assertFalse("Editable or external content: $name",
                    name in setOf("contenteditable", "src", "srcset", "action"))
                if (name == "href") assertTrue("External resource: ${attribute.nodeValue}",
                    attribute.nodeValue.startsWith("#") || attribute.nodeValue == "mailto:android@jochen-schmitt.com")
            }
        }
        val css = doc.getElementsByTagName("style").item(0).textContent.lowercase(java.util.Locale.ROOT)
        assertFalse(css.contains("@import"))
        assertFalse(css.contains("url("))
        val meta = doc.getElementsByTagName("meta")
        assertTrue((0 until meta.length).map { meta.item(it) as Element }
            .any { it.getAttribute("charset").equals("utf-8", ignoreCase = true) })
        assertFalse((0 until meta.length).map { meta.item(it) as Element }
            .any { it.getAttribute("http-equiv").equals("refresh", ignoreCase = true) })
    }

    @Test fun aboutHasLocalizedLabelsAndTheSameHttpsPolicyAddress() {
        val resources = File(checkNotNull(System.getProperty("app.resources")))
        for ((directory, label) in listOf("values" to "Privacy Policy", "values-de" to "Datenschutzerklärung")) {
            val nodes = document(File(resources, "$directory/strings.xml")).getElementsByTagName("string")
            val strings = (0 until nodes.length).associate {
                val node = nodes.item(it) as Element
                node.getAttribute("name") to node.textContent
            }
            assertEquals(label, strings["privacy_policy"])
            assertEquals("https://jschbe.github.io/DadaBlubb/privacy.html", strings["privacy_url"])
            assertEquals("https://github.com/jschbe/DadaBlubb", strings["source_url"])
        }
    }

    @Test fun manifestDoesNotRequestInternetOrBroadStorageAccess() {
        val doc = document(File(checkNotNull(System.getProperty("app.manifest"))))
        for (tag in listOf("uses-permission", "uses-permission-sdk-23")) {
            assertEquals("Review privacy policy if permissions are added", 0, doc.getElementsByTagName(tag).length)
        }
    }
}
