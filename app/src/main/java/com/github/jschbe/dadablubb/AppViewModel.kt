package com.github.jschbe.dadablubb

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.jschbe.dadablubb.core.*
import com.github.jschbe.dadablubb.data.DatasetStorage
import com.github.jschbe.dadablubb.data.SettingsStorage
import com.github.jschbe.dadablubb.data.UiSettingsStorage
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
    private val uiPreferences = UiSettingsStorage(application)
    var appearance by mutableStateOf(uiPreferences.appearance())
        private set
    private val resolver = application.contentResolver
    private val generator = Generator()
    private var dataset: Dataset? = null
    var maxWordCount by mutableIntStateOf(50)
        private set
    var settings by mutableStateOf(preferences.load())
        private set
    var output by mutableStateOf("")
        private set
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<UiMessage?>(null)
        private set
    var pendingImport by mutableStateOf<Dataset?>(null)
        private set
    val ready get() = dataset != null && !busy

    init {
        perform(R.string.action_load) {
            val loaded = withContext(Dispatchers.IO) { storage.load() }
            activate(loaded.dataset)
            message = loaded.warning?.let { UiMessage(R.string.message_detail, listOf(it)) }
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

    fun importFrom(uri: Uri) = perform(R.string.action_import) {
        pendingImport = withContext(Dispatchers.IO) {
            (resolver.openInputStream(uri) ?: throw IOException("The document could not be opened."))
                .use(DatasetCodec::read)
        }
    }

    fun cancelImport() { pendingImport = null }

    fun confirmImport() {
        val candidate = pendingImport ?: return
        perform(R.string.action_replace) {
            withContext(Dispatchers.IO) { storage.replace(candidate) }
            activate(candidate)
            pendingImport = null
            notify(R.string.imported,
                pluralMessage(R.plurals.word_total, candidate.words.size),
                pluralMessage(R.plurals.connector_total, candidate.connectors.size))
        }
    }

    fun exportTo(uri: Uri) {
        val snapshot = dataset ?: return
        perform(R.string.action_export) {
            withContext(Dispatchers.IO) {
                (resolver.openOutputStream(uri, "wt") ?: throw IOException("The document could not be opened."))
                    .bufferedWriter(Charsets.UTF_8).use { it.write(DatasetCodec.encode(snapshot)) }
            }
            notify(R.string.exported)
        }
    }

    fun reset() = perform(R.string.action_reset) {
        activate(withContext(Dispatchers.IO) { storage.reset() })
        notify(R.string.defaults_restored)
    }

    fun updateAppearance(value: Appearance) {
        uiPreferences.saveAppearance(value)
        appearance = value
    }

    fun notify(resource: Int, vararg arguments: Any) { message = UiMessage(resource, arguments.toList()) }
    fun dismissMessage() { message = null }

    private fun perform(action: Int, block: suspend () -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: DatasetException) {
                notify(R.string.message_detail, error.detail)
            } catch (_: SecurityException) {
                notify(R.string.operation_denied, UiMessage(action))
            } catch (error: IOException) {
                notify(R.string.operation_io, UiMessage(action))
            } catch (_: Exception) {
                notify(R.string.operation_failed, UiMessage(action))
            } finally {
                busy = false
            }
        }
    }
}
