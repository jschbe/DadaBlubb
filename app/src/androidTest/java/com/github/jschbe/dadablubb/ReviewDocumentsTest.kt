package com.github.jschbe.dadablubb

import android.Manifest
import android.content.ContentResolver
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.github.jschbe.dadablubb.core.*
import com.github.jschbe.dadablubb.data.*
import java.io.File
import java.io.IOException
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

/** Real Android ContentResolver/ParcelFileDescriptor IO through an in-process
 * DocumentsProvider. No picker, external provider, user vocabulary or preferences. */
@SdkSuppress(minSdkVersion = 29)
class ReviewDocumentsTest {
    private fun isolated(test: (ContentResolver, File) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "review-documents-${UUID.randomUUID()}").apply { mkdirs() }
        val provider = FixtureDocumentsProvider(directory)
        provider.attachInfo(context, ProviderInfo().apply {
            authority = AUTHORITY
            exported = true
            grantUriPermissions = true
            readPermission = Manifest.permission.MANAGE_DOCUMENTS
            writePermission = Manifest.permission.MANAGE_DOCUMENTS
        })
        try { test(ContentResolver.wrap(provider), directory) }
        finally { provider.shutdown(); directory.deleteRecursively() }
    }

    @Test fun exportImportCompareAndResetUseOnlyIsolatedFiles() = isolated { resolver, directory ->
        val defaults = Dataset(listOf("Default"), listOf(Connector("", 1)))
        val imported = Dataset(listOf("Möter", "Blubb"), listOf(Connector("di", 60), Connector("", 20)))
        val storage = DatasetStorage(File(directory, "active.json")) { DatasetCodec.encode(defaults).byteInputStream() }
        storage.replace(imported)
        val root = DocumentsContract.buildDocumentUri(AUTHORITY, "root")
        val uri = checkNotNull(DocumentsContract.createDocument(resolver, root, "application/json", "review.json"))
        DatasetDocuments.write(storage.load().dataset) { resolver.openOutputStream(uri, "wt") }
        val decoded = DatasetDocuments.read { resolver.openInputStream(uri) }
        assertEquals(imported, decoded)
        storage.replace(decoded)
        assertEquals(imported, storage.load().dataset)
        assertEquals(defaults, storage.reset())
        assertEquals(defaults, storage.load().dataset)
        // Reset affects the private fixture override, not the exported document.
        assertEquals(imported, DatasetDocuments.read { resolver.openInputStream(uri) })
        assertTrue(DocumentsContract.deleteDocument(resolver, uri))
    }

    @Test fun deniedMissingAndMalformedDocumentsKeepPrivateFixtureVocabulary() = isolated { resolver, directory ->
        val original = Dataset(listOf("Keep"), listOf(Connector("", 1)))
        val storage = DatasetStorage(File(directory, "active.json")) { DatasetCodec.encode(original).byteInputStream() }
        storage.replace(original)
        val before = File(directory, "active.json").readBytes()
        val missing = DocumentsContract.buildDocumentUri(AUTHORITY, "missing")
        val denied = DocumentsContract.buildDocumentUri(AUTHORITY, "denied")
        assertThrows(IOException::class.java) { DatasetDocuments.read { resolver.openInputStream(missing) } }
        assertThrows(SecurityException::class.java) { DatasetDocuments.write(original) { resolver.openOutputStream(denied, "wt") } }
        val uri = checkNotNull(DocumentsContract.createDocument(resolver,
            DocumentsContract.buildDocumentUri(AUTHORITY, "root"), "application/json", "invalid.json"))
        resolver.openOutputStream(uri, "wt")!!.use { it.write("{bad".toByteArray()) }
        assertThrows(DatasetException::class.java) { DatasetDocuments.read { resolver.openInputStream(uri) } }
        assertArrayEquals(before, File(directory, "active.json").readBytes())
        assertEquals(original, storage.load().dataset)
    }

    companion object { const val AUTHORITY = "com.github.jschbe.dadablubb.review.fixture" }
}

private class FixtureDocumentsProvider(private val directory: File) : DocumentsProvider() {
    private val documents = mutableSetOf<String>()
    override fun onCreate() = true
    override fun queryRoots(projection: Array<out String>?): Cursor = MatrixCursor(projection ?: arrayOf(DocumentsContract.Root.COLUMN_ROOT_ID))
    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor =
        MatrixCursor(projection ?: arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val columns = projection ?: arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_FLAGS, DocumentsContract.Document.COLUMN_SIZE)
        return MatrixCursor(columns).apply {
            addRow(columns.map { when (it) {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID -> documentId
                DocumentsContract.Document.COLUMN_DISPLAY_NAME -> "review.json"
                DocumentsContract.Document.COLUMN_MIME_TYPE -> "application/json"
                DocumentsContract.Document.COLUMN_FLAGS -> DocumentsContract.Document.FLAG_SUPPORTS_WRITE
                DocumentsContract.Document.COLUMN_SIZE -> File(directory, documentId).length()
                else -> null
            } })
        }
    }
    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        check(parentDocumentId == "root")
        return UUID.randomUUID().toString().also { documents.add(it); File(directory, it).createNewFile() }
    }
    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        if (documentId == "denied") throw SecurityException("Fixture denial")
        if (documentId !in documents) throw java.io.FileNotFoundException("Fixture document missing")
        return ParcelFileDescriptor.open(File(directory, documentId), ParcelFileDescriptor.parseMode(mode))
    }
    override fun deleteDocument(documentId: String) {
        check(documents.remove(documentId))
        check(File(directory, documentId).delete())
    }
}
