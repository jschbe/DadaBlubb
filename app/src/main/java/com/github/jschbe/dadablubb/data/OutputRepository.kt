package com.github.jschbe.dadablubb.data

import com.github.jschbe.dadablubb.core.Generation
import com.github.jschbe.dadablubb.core.OutputWrapHints
import java.io.IOException
import java.nio.charset.CharacterCodingException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class OutputError { READ, WRITE, TOO_LARGE, INVALID_TEXT }

data class OutputState(
    val text: String = "",
    val loaded: Boolean = false,
    val hasOutput: Boolean = false,
    val saving: Boolean = false,
    val error: OutputError? = null,
    val wrapHints: OutputWrapHints? = null
) {
    val showPlaceholder get() = loaded && !hasOutput
}

/** Process-owned writer, independent of Activity/ViewModel cancellation.
 * Conflation bounds pending work; one consumer keeps writes in order.
 * Only a missing file is a first start, never a failed read. */
class OutputRepository(
    private val storage: OutputPersistence,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private data class Change(val revision: Long, val text: String, val hints: OutputWrapHints?)
    private val changes = Channel<Change>(Channel.CONFLATED)
    private val mutableState = MutableStateFlow(OutputState())
    val state = mutableState.asStateFlow()
    private var revision = 0L

    init {
        scope.launch {
            val initial = try {
                val text = storage.read()
                val hints = text?.let { optionalHints { storage.readWrapHints(it) } }
                OutputState(text = text.orEmpty(), loaded = true, hasOutput = text != null, wrapHints = hints)
            } catch (_: IOException) {
                OutputState(loaded = true, hasOutput = true, error = OutputError.READ)
            } catch (_: SecurityException) {
                OutputState(loaded = true, hasOutput = true, error = OutputError.READ)
            }
            synchronized(this@OutputRepository) {
                if (revision == 0L) mutableState.value = initial
            }
            for (change in changes) {
                val error = try {
                    // One writer owns all three phases. A crash loses at most optional hints;
                    // the hash/file-instance guard rejects an old sidecar after a text commit.
                    optionalHints { storage.invalidateWrapHints() }
                    storage.write(change.text)
                    change.hints?.let { hints -> optionalHints { storage.writeWrapHints(change.text, hints) } }
                    null
                } catch (_: IOException) {
                    OutputError.WRITE
                } catch (_: SecurityException) {
                    OutputError.WRITE
                }
                synchronized(this@OutputRepository) {
                    if (revision == change.revision) {
                        mutableState.value = mutableState.value.copy(saving = false,
                            error = error ?: mutableState.value.error?.takeUnless { it == OutputError.WRITE })
                    }
                }
            }
        }
    }

    fun update(text: String) = update(text, null)

    fun update(generation: Generation) = update(generation.text, OutputWrapHints.from(generation))

    @Synchronized
    private fun update(text: String, hints: OutputWrapHints?) {
        val error = try {
            OutputStorage.encode(text)
            null
        } catch (_: OutputTooLargeException) {
            OutputError.TOO_LARGE
        } catch (_: CharacterCodingException) {
            OutputError.INVALID_TEXT
        }
        if (error != null) {
            mutableState.value = mutableState.value.copy(error = error)
            return
        }
        val change = Change(++revision, text, hints)
        mutableState.value = OutputState(text, loaded = true, hasOutput = true, saving = true,
            wrapHints = hints)
        check(changes.trySend(change).isSuccess)
    }

    private fun <T> optionalHints(block: () -> T): T? = try { block() }
        catch (_: IOException) { null }
        catch (_: SecurityException) { null }
}
