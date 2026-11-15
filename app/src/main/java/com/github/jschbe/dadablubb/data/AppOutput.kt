package com.github.jschbe.dadablubb.data

import android.content.Context
import java.io.File

/** Only the application file/store survives; no Activity or ViewModel is retained. */
object AppOutput {
    private var repository: OutputRepository? = null

    @Synchronized
    fun get(context: Context): OutputRepository = repository ?: OutputRepository(
        OutputStorage(File(context.applicationContext.filesDir, "output.txt"))
    ).also { repository = it }
}
