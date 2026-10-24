package com.github.jschbe.dummlaberer

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.ViewModelProvider
import com.github.jschbe.dummlaberer.core.*
import com.github.jschbe.dummlaberer.ui.theme.DummlabererTheme
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val model = ViewModelProvider(this)[AppViewModel::class.java]
        val version = packageManager.getPackageInfo(packageName, 0).versionName ?: "Unknown"
        setContent { DummlabererTheme { DummlabererApp(model, version) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DummlabererApp(model: AppViewModel, version: String) {
    val context = LocalContext.current
    var settingsVisible by rememberSaveable { mutableStateOf(false) }
    var menuVisible by remember { mutableStateOf(false) }
    var aboutVisible by rememberSaveable { mutableStateOf(false) }
    var resetVisible by rememberSaveable { mutableStateOf(false) }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(model::importFrom)
    }
    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(model::exportTo)
    }
    BackHandler(enabled = settingsVisible) { settingsVisible = false }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (settingsVisible) "Settings" else "Dummlaberer") },
                navigationIcon = {
                    if (settingsVisible) IconButton(
                        onClick = { settingsVisible = false },
                        modifier = Modifier.semantics { contentDescription = "Back" }
                    ) { Text("←", fontSize = 26.sp) }
                },
                actions = {
                    Box {
                        IconButton(
                            onClick = { menuVisible = true },
                            modifier = Modifier.semantics { contentDescription = "More options" }
                        ) { Text("⋮", fontSize = 28.sp) }
                        DropdownMenu(expanded = menuVisible, onDismissRequest = { menuVisible = false }) {
                            DropdownMenuItem(text = { Text("Settings") }, onClick = {
                                menuVisible = false
                                settingsVisible = true
                            })
                            DropdownMenuItem(text = { Text("Import JSON") }, enabled = !model.busy, onClick = {
                                menuVisible = false
                                launchDocumentAction(model) { importFile.launch(arrayOf("*/*")) }
                            })
                            DropdownMenuItem(text = { Text("Export JSON") }, enabled = model.ready, onClick = {
                                menuVisible = false
                                launchDocumentAction(model) { exportFile.launch("dummlaberer.json") }
                            })
                            DropdownMenuItem(text = { Text("Reset to Defaults") }, enabled = !model.busy, onClick = {
                                menuVisible = false
                                resetVisible = true
                            })
                            DropdownMenuItem(text = { Text("About") }, onClick = {
                                menuVisible = false
                                aboutVisible = true
                            })
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (model.busy) LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Working" })
            if (settingsVisible) SettingsScreen(model.settings, model::updateSettings)
            else GeneratorScreen(
                settings = model.settings,
                output = model.output,
                enabled = model.ready,
                maxWordCount = model.maxWordCount,
                onSettings = model::updateSettings,
                onGenerate = model::generate,
                onCopy = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Dummlaberer", model.output))
                    model.notify("Copied to clipboard.")
                },
                onShare = {
                    try {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, model.output)
                        }, "Share generated text"))
                    } catch (_: ActivityNotFoundException) {
                        model.notify("No app is available to share text.")
                    } catch (_: SecurityException) {
                        model.notify("The selected app could not receive the text.")
                    }
                }
            )
        }
    }

    model.pendingImport?.let { candidate ->
        AlertDialog(
            onDismissRequest = { if (!model.busy) model.cancelImport() },
            title = { Text("Replace active dataset?") },
            text = { Text("Import ${candidate.words.size} words and ${candidate.connectors.size} connectors? This replaces the entire active dataset. Bundled defaults and your settings are kept.") },
            confirmButton = { TextButton(onClick = model::confirmImport, enabled = !model.busy) { Text("Replace") } },
            dismissButton = { TextButton(onClick = model::cancelImport, enabled = !model.busy) { Text("Cancel") } }
        )
    }
    if (resetVisible) AlertDialog(
        onDismissRequest = { resetVisible = false },
        title = { Text("Reset to Defaults?") },
        text = { Text("Discard the imported dataset and restore bundled words and connectors? Your settings will be kept.") },
        confirmButton = { TextButton(onClick = { resetVisible = false; model.reset() }) { Text("Reset") } },
        dismissButton = { TextButton(onClick = { resetVisible = false }) { Text("Cancel") } }
    )
    if (aboutVisible) AlertDialog(
        onDismissRequest = { aboutVisible = false },
        title = { Text("Dummlaberer") },
        text = {
            Text("Version $version\n\nAn offline playground for absurd, rhythmic, German-sounding fantasy words. Choose a stem count, generate, and share a little dada.\n\nAll generation and vocabulary processing happen on your device.")
        },
        confirmButton = { TextButton(onClick = { aboutVisible = false }) { Text("Close") } }
    )
    model.message?.let { message ->
        AlertDialog(
            onDismissRequest = model::dismissMessage,
            title = { Text("Dummlaberer") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = model::dismissMessage) { Text("OK") } }
        )
    }
}

private fun launchDocumentAction(model: AppViewModel, launch: () -> Unit) {
    try {
        launch()
    } catch (_: ActivityNotFoundException) {
        model.notify("No document picker is available on this device.")
    } catch (_: SecurityException) {
        model.notify("The document picker could not be opened.")
    }
}

@Composable
internal fun GeneratorScreen(
    settings: GeneratorSettings,
    output: String,
    enabled: Boolean,
    onSettings: (GeneratorSettings) -> Unit,
    onGenerate: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    maxWordCount: Int = 50
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("A little rhythm. A lot of nonsense.", style = MaterialTheme.typography.bodyLarge)
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Word Count", style = MaterialTheme.typography.titleMedium)
                Text("The number of stems, joined into one fantasy string.", style = MaterialTheme.typography.bodyMedium)
                if (maxWordCount < 50) {
                    Text(
                        "This dataset has $maxWordCount unique ${if (maxWordCount == 1) "stem" else "stems"}. Word Count is limited to $maxWordCount.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                Row(
                    Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(
                        onClick = { onSettings(settings.copy(wordCount = settings.wordCount - 1)) },
                        enabled = settings.wordCount > 1,
                        modifier = Modifier.size(48.dp).semantics { contentDescription = "Decrease word count" }
                    ) { Text("−", fontSize = 24.sp) }
                    Text("${settings.wordCount}", style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(min = 40.dp).semantics { contentDescription = "Word count: ${settings.wordCount}" })
                    FilledTonalIconButton(
                        onClick = { onSettings(settings.copy(wordCount = settings.wordCount + 1)) },
                        enabled = settings.wordCount < maxWordCount,
                        modifier = Modifier.size(48.dp).semantics { contentDescription = "Increase word count" }
                    ) { Text("+", fontSize = 24.sp) }
                }
                Spacer(Modifier.height(24.dp))
                Slider(
                    value = settings.wordCount.toFloat(),
                    onValueChange = { onSettings(settings.copy(wordCount = it.roundToInt().coerceIn(1, maxWordCount))) },
                    valueRange = 1f..maxWordCount.toFloat(),
                    steps = (maxWordCount - 2).coerceAtLeast(0),
                    enabled = maxWordCount > 1,
                    modifier = Modifier.semantics { contentDescription = "Word Count" }
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("1", style = MaterialTheme.typography.labelMedium)
                    Text("$maxWordCount", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Button(onClick = onGenerate, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text("Generate", style = MaterialTheme.typography.titleMedium)
        }
        Text("Generated text", style = MaterialTheme.typography.titleMedium)
        OutlinedCard(Modifier.fillMaxWidth().heightIn(min = 160.dp)) {
            SelectionContainer {
                Text(
                    output.ifEmpty { "Your next nonsense word is one tap away." },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    style = if (output.isEmpty()) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.headlineSmall,
                    color = if (output.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    softWrap = true
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCopy, enabled = output.isNotEmpty(), modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Copy") }
            OutlinedButton(onClick = onShare, enabled = output.isNotEmpty(), modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Share") }
        }
    }
}

@Composable
internal fun SettingsScreen(settings: GeneratorSettings, onChange: (GeneratorSettings) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Saved automatically. Changes apply to the next generation.", style = MaterialTheme.typography.bodyMedium)
        SettingGroup("Style", "Mixed balances surprises and echoes. Rhythmic favors matching sounds. Chaotic favors different starts and endings. Stems stay unique.",
            Style.entries, settings.style, { it.label }) { onChange(settings.copy(style = it)) }
        SettingGroup("Repetition", "High echoes earlier endings more often using different stems. Every selected stem stays unique.",
            Repetition.entries, settings.repetition, { it.label }) { onChange(settings.copy(repetition = it)) }
        SettingGroup("Connectors", "Frequent triples nonempty connector weights. Connectors only appear between stems.",
            Connectors.entries, settings.connectors, { it.label }) { onChange(settings.copy(connectors = it)) }
    }
}

@Composable
private fun <T> SettingGroup(title: String, description: String, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
            Column(Modifier.selectableGroup()) {
                options.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .selectable(selected = option == selected, role = Role.RadioButton, onClick = { onSelect(option) })
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Text(label(option), modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        }
    }
}
