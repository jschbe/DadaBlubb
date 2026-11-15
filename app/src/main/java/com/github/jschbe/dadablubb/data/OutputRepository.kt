package com.github.jschbe.dadablubb.data

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
    val error: OutputError? = null
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
    private data class Change(val revision: Long, val text: String)
    private val changes = Channel<Change>(Channel.CONFLATED)
    private val mutableState = MutableStateFlow(OutputState())
    val state = mutableState.asStateFlow()
    private var revision = 0L

    init {
        scope.launch {
            val initial = try {
                val text = storage.read()
                OutputState(text = text.orEmpty(), loaded = true, hasOutput = text != null)
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
                    storage.write(change.text)
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

    @Synchronized
    fun update(text: String) {
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
        val change = Change(++revision, text)
        mutableState.value = OutputState(text, loaded = true, hasOutput = true, saving = true)
        check(changes.trySend(change).isSuccess)
    }
}
