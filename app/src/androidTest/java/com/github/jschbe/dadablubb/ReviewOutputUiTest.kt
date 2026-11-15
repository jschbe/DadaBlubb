package com.github.jschbe.dadablubb

import android.app.Application
import android.content.SharedPreferences
import android.content.res.AssetManager
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.github.jschbe.dadablubb.core.GeneratorSettings
import com.github.jschbe.dadablubb.data.*
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Safe for a populated phone: standalone Compose and unique cache/preferences only. */
class ReviewOutputUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun editorPlaceholderEmptyRestoreGenerateAndActionPayloads() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "review-output-${UUID.randomUUID()}").apply { mkdirs() }
        val file = File(directory, "output.txt")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val repo = OutputRepository(OutputStorage(file), scope)
        var active by mutableStateOf(repo)
        var copied: String? = null
        var shared: String? = null
        try {
            compose.setContent {
                val state by active.state.collectAsState()
                EnglishTestUi {
                    GeneratorScreen(GeneratorSettings(), state.text, state.loaded, {},
                        { active.update("generated again") }, { copied = it }, { shared = it }, active::update,
                        showOutputPlaceholder = state.showPlaceholder, outputLoaded = state.loaded)
                }
            }
            compose.waitUntil(10_000) { repo.state.value.loaded }
            compose.onNodeWithText("Your next nonsense word is one tap away.").assertExists()
            val edited = "Review Möter\nSecond line 😀"
            compose.onNodeWithTag("output").performScrollTo().performTextReplacement(edited)
            compose.onNodeWithText("Copy").performScrollTo().performClick()
            compose.onNodeWithText("Share").performScrollTo().performClick()
            compose.runOnIdle { assertEquals(edited, copied); assertEquals(edited, shared) }
            compose.onNodeWithTag("output").performScrollTo().performTextClearance()
            compose.waitUntil(10_000) { !repo.state.value.saving }
            compose.onNodeWithText("Your next nonsense word is one tap away.").assertDoesNotExist()
            val restored = OutputRepository(OutputStorage(file), scope)
            compose.waitUntil(10_000) { restored.state.value.loaded }
            compose.runOnIdle { active = restored }
            compose.onNodeWithTag("output").assertTextEquals("")
            compose.onNodeWithText("Your next nonsense word is one tap away.").assertDoesNotExist()
            compose.onNodeWithText("Generate").performScrollTo().performClick()
            compose.waitUntil(10_000) { !restored.state.value.saving }
            compose.onNodeWithTag("output").assertTextEquals("generated again").assertIsNotFocused()
            assertEquals("generated again", OutputStorage(file).read())
        } finally { scope.cancel(); directory.deleteRecursively() }
    }

    @Test fun pendingOutputWriteOutlivesClearedViewModelStore() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = "review-lifecycle-${UUID.randomUUID()}"
        val directory = File(context.cacheDir, id).apply { mkdirs() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val disk = OutputStorage(File(directory, "output.txt"))
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val repo = OutputRepository(object : OutputPersistence {
            override fun read() = disk.read()
            override fun write(text: String) {
                started.countDown()
                check(release.await(10, TimeUnit.SECONDS))
                disk.write(text)
            }
        }, scope)
        val screen = ViewModelStore()
        val app = object : Application() {
            override fun getFilesDir() = directory
            override fun getAssets(): AssetManager = context.assets
            override fun getContentResolver() = context.contentResolver
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                context.getSharedPreferences("$id-$name", mode)
        }
        try {
            lateinit var model: AppViewModel
            compose.runOnUiThread {
                model = ViewModelProvider(screen, object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T = modelClass.cast(AppViewModel(app, repo))!!
                })[AppViewModel::class.java]
                model.editOutput("survives screen finish\n")
            }
            assertTrue(started.await(10, TimeUnit.SECONDS))
            compose.runOnUiThread { screen.clear() } // while the write is demonstrably pending
            release.countDown()
            runBlocking { withTimeout(10_000) { repo.state.first { it.loaded && !it.saving } } }
            assertEquals("survives screen finish\n", disk.read())
            val restarted = OutputRepository(disk, scope)
            runBlocking { withTimeout(10_000) { restarted.state.first { it.loaded } } }
            assertEquals("survives screen finish\n", restarted.state.value.text)
        } finally {
            release.countDown()
            compose.runOnUiThread { screen.clear() }
            scope.cancel()
            directory.deleteRecursively()
            context.deleteSharedPreferences("$id-generator-settings")
            context.deleteSharedPreferences("$id-ui-settings")
        }
    }
}
