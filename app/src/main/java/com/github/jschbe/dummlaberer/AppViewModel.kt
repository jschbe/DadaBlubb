package com.github.jschbe.dummlaberer

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.jschbe.dummlaberer.core.*
import com.github.jschbe.dummlaberer.data.DatasetStorage
import com.github.jschbe.dummlaberer.data.SettingsStorage
import java.io.IOException
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val storage = DatasetStorage(File(application.filesDir, "active-dada.json")) {
        application.assets.open("dada.json")
    }
    private val preferences = SettingsStorage(application)
    private val resolver = application.contentResolver
    private val generator = Generator()
    private var dataset: Dataset? = null
    var maxWordCount by mutableStateOf(50)
        private set
    var settings by mutableStateOf(preferences.load())
        private set
    var output by mutableStateOf("")
        private set
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var pendingImport by mutableStateOf<Dataset?>(null)
        private set
    val ready get() = dataset != null && !busy

    init {
        perform("load the dataset") {
            val loaded = withContext(Dispatchers.IO) { storage.load() }
            activate(loaded.dataset)
            message = loaded.warning
        }
    }

    fun updateSettings(value: GeneratorSettings) {
        settings = dataset?.let(value::cappedTo) ?: value
        preferences.save(settings)
    }

    private fun activate(value: Dataset) {
        dataset = value
        maxWordCount = value.maxWordCount
        updateSettings(settings)
    }

    fun generate() {
        if (ready) output = generator.generate(checkNotNull(dataset), settings).text
    }

    fun editOutput(value: String) { output = value }

    fun importFrom(uri: Uri) = perform("import JSON") {
        pendingImport = withContext(Dispatchers.IO) {
            (resolver.openInputStream(uri) ?: throw IOException("The document could not be opened."))
                .use(DatasetCodec::read)
        }
    }

    fun cancelImport() { pendingImport = null }

    fun confirmImport() {
        val candidate = pendingImport ?: return
        perform("replace the dataset") {
            withContext(Dispatchers.IO) { storage.replace(candidate) }
            activate(candidate)
            pendingImport = null
            message = "Dataset imported: ${candidate.words.size} words and ${candidate.connectors.size} connectors."
        }
    }

    fun exportTo(uri: Uri) {
        val snapshot = dataset ?: return
        perform("export JSON") {
            withContext(Dispatchers.IO) {
                (resolver.openOutputStream(uri, "wt") ?: throw IOException("The document could not be opened."))
                    .bufferedWriter(Charsets.UTF_8).use { it.write(DatasetCodec.encode(snapshot)) }
            }
            message = "Active dataset exported."
        }
    }

    fun reset() = perform("reset the dataset") {
        activate(withContext(Dispatchers.IO) { storage.reset() })
        message = "Bundled defaults restored. Your settings were kept."
    }

    fun notify(message: String) { this.message = message }
    fun dismissMessage() { message = null }

    private fun perform(action: String, block: suspend () -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: DatasetException) {
                message = error.message ?: "Invalid dataset."
            } catch (_: SecurityException) {
                message = "Could not $action: document access was denied. Try choosing the file again."
            } catch (error: IOException) {
                message = "Could not $action. Check the file and available storage, then try again."
            } catch (_: Exception) {
                message = "Could not $action. Please try again."
            } finally {
                busy = false
            }
        }
    }
}
