package com.github.jschbe.dadablubb

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element

class BackupPolicyTest {
    private val domains = setOf("root", "file", "database", "sharedpref", "external",
        "device_root", "device_file", "device_database", "device_sharedpref")
    private fun xml(name: String) = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        .parse(File(checkNotNull(System.getProperty("app.resources")), "xml/$name.xml"))
    private fun children(parent: Element, name: String): List<Element> {
        val nodes = parent.getElementsByTagName(name)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    @Test fun legacyApi28To30IncludesEveryDomainOnlyForDeviceTransfer() {
        val root = xml("backup_rules").documentElement
        assertEquals("full-backup-content", root.tagName)
        val includes = children(root, "include")
        assertEquals(domains, includes.map { it.getAttribute("domain") }.toSet())
        assertEquals(domains.size, includes.size)
        for (rule in includes) {
            assertEquals(".", rule.getAttribute("path"))
            assertEquals("deviceToDeviceTransfer", rule.getAttribute("requireFlags"))
        }
        assertTrue(children(root, "exclude").isEmpty())
    }

    @Test fun modernCloudExcludesAllDomainsWhileDeviceTransferIncludesThem() {
        val root = xml("data_extraction_rules").documentElement
        val cloud = children(root, "cloud-backup").single()
        val transfer = children(root, "device-transfer").single()
        assertTrue(children(cloud, "include").isEmpty())
        assertTrue(children(transfer, "exclude").isEmpty())
        for ((section, tag) in listOf(cloud to "exclude", transfer to "include")) {
            val rules = children(section, tag)
            assertEquals(domains, rules.map { it.getAttribute("domain") }.toSet())
            assertEquals(domains.size, rules.size)
            assertTrue(rules.all { it.getAttribute("path") == "." })
        }
        // Both current file payloads and all preference files are covered, not just one filename.
        for (domain in listOf("file", "sharedpref")) {
            assertTrue(children(cloud, "exclude").any { it.getAttribute("domain") == domain })
        }
    }

    @Test fun manifestActivatesBothFormatsWithoutDisablingDeviceTransfer() {
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(File(checkNotNull(System.getProperty("app.manifest"))))
        val app = doc.getElementsByTagName("application").item(0) as Element
        val ns = "http://schemas.android.com/apk/res/android"
        assertEquals("true", app.getAttributeNS(ns, "allowBackup"))
        assertEquals("@xml/backup_rules", app.getAttributeNS(ns, "fullBackupContent"))
        assertEquals("@xml/data_extraction_rules", app.getAttributeNS(ns, "dataExtractionRules"))
    }
}
