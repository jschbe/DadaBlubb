package com.github.jschbe.dadablubb

import com.github.jschbe.dadablubb.core.*
import com.github.jschbe.dadablubb.data.DatasetStorage
import java.io.File
import java.io.IOException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DatasetStorageTest {
    @get:Rule val folder = TemporaryFolder()
    private val defaults = Dataset(listOf("Möter"), listOf(Connector("di", 60), Connector("", 15)))
    private val imported = Dataset(listOf("Schnurpsel", "Wachs"), listOf(Connector("de", 5)))

    private fun storage(file: File) = DatasetStorage(file) { DatasetCodec.encode(defaults).byteInputStream(Charsets.UTF_8) }

    @Test fun firstLoadUsesBundledDefaults() {
        val loaded = storage(File(folder.root, "active.json")).load()
        assertEquals(defaults, loaded.dataset)
        assertNull(loaded.warning)
    }

    @Test fun revisedBundleLoadsFreshRetainsUserOverrideAndReturnsAfterExplicitReset() {
        val asset = File(checkNotNull(System.getProperty("dada.asset")))
        val assetBefore = asset.readBytes()
        val bundled = asset.inputStream().use(DatasetCodec::read)
        val file = File(folder.root, "active.json")
        val storage = DatasetStorage(file) { asset.inputStream() }
        assertEquals(bundled, storage.load().dataset)
        assertFalse(file.exists())
        storage.replace(imported)
        val overrideBefore = file.readBytes()
        assertEquals(imported, DatasetStorage(file) { asset.inputStream() }.load().dataset)
        assertArrayEquals(overrideBefore, file.readBytes())
        assertEquals(bundled, storage.reset())
        assertFalse(file.exists())
        assertEquals(bundled, storage.load().dataset)
        assertArrayEquals(assetBefore, asset.readBytes())
    }

    @Test fun validReplacementIsImmediateAndSurvivesReload() {
        val file = File(folder.root, "active.json")
        storage(file).replace(imported)
        assertEquals(imported, storage(file).load().dataset)
        assertEquals(imported, DatasetCodec.parse(file.readText(Charsets.UTF_8)))
        assertEquals(listOf("active.json"), folder.root.list()!!.toList())
    }

    @Test fun invalidImportAndFailedValidationRetainPreviousActiveDataset() {
        val file = File(folder.root, "active.json")
        val storage = storage(file)
        storage.replace(imported)
        val before = file.readBytes()
        assertThrows(DatasetException::class.java) { storage.replace(DatasetCodec.parse("{bad")) }
        assertThrows(DatasetException::class.java) { storage.replace(Dataset(listOf("Bad word"), listOf(Connector("", 1)))) }
        assertArrayEquals(before, file.readBytes())
        assertEquals(imported, storage.load().dataset)
    }

    @Test fun failedAtomicMoveCleansTemporaryFileAndRetainsExistingTarget() {
        val target = folder.newFolder("active.json")
        val sentinel = File(target, "sentinel").apply { writeText("keep") }
        assertThrows(IOException::class.java) { storage(target).replace(imported) }
        assertEquals("keep", sentinel.readText())
        assertEquals(listOf("active.json"), folder.root.list()!!.toList())
    }

    @Test fun resetDiscardsOverrideAndReloadsDefaults() {
        val file = File(folder.root, "active.json")
        val storage = storage(file)
        storage.replace(imported)
        assertEquals(defaults, storage.reset())
        assertFalse(file.exists())
        assertEquals(defaults, storage.load().dataset)
        assertEquals(defaults, storage.reset())
    }

    @Test fun corruptedOverrideFallsBackWithVisibleWarningAndCanBeReset() {
        val file = folder.newFile("active.json").apply { writeText("corrupt") }
        val storage = storage(file)
        assertEquals(defaults, storage.load().dataset)
        assertNotNull(storage.load().warning)
        storage.reset()
        assertNull(storage.load().warning)
    }

    @Test fun failedResetDoesNotDiscardAnImport() {
        val file = File(folder.root, "active.json")
        storage(file).replace(imported)
        val brokenDefaults = DatasetStorage(file) { "invalid".byteInputStream() }
        assertThrows(DatasetException::class.java) { brokenDefaults.reset() }
        assertEquals(imported, storage(file).load().dataset)
    }
}
