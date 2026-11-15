package com.github.jschbe.dadablubb

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.ViewModelProvider
import androidx.core.net.toUri
import com.github.jschbe.dadablubb.core.*
import com.github.jschbe.dadablubb.ui.theme.DadaBlubbTheme
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocales.localizedContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val model = ViewModelProvider(this)[AppViewModel::class.java]
        val info = packageManager.getPackageInfo(packageName, 0)
        val version = AppVersion(info.versionName, info.longVersionCode)
        val language = AppLocales.selection(this)
        setContent {
            val dark = when (model.appearance) {
                Appearance.SYSTEM -> isSystemInDarkTheme()
                Appearance.LIGHT -> false
                Appearance.DARK -> true
            }
            // Keep system bar contrast in step with the selected Compose theme.
            SideEffect { enableEdgeToEdge(
                statusBarStyle = androidx.activity.SystemBarStyle.auto(0, 0) { dark },
                navigationBarStyle = androidx.activity.SystemBarStyle.auto(0, 0) { dark }
            ) }
            DadaBlubbTheme(darkTheme = dark) {
                DadaBlubbApp(model, version, language) {
                    if (!AppLocales.apply(this, it)) model.notify(R.string.language_save_failed)
                }
            }
        }
    }
}

internal data class AppVersion(val name: String?, val code: Long)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DadaBlubbApp(
    model: AppViewModel,
    version: AppVersion,
    language: AppLanguage = AppLanguage.SYSTEM,
    onLanguage: (AppLanguage) -> Unit = {}
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    // Read resources in composition, including labels later used by callbacks/semantics.
    val backLabel = stringResource(R.string.back)
    val moreOptionsLabel = stringResource(R.string.more_options)
    val workingLabel = stringResource(R.string.working)
    val appName = stringResource(R.string.app_name)
    val shareTitle = stringResource(R.string.share_title)
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
                title = { Text(stringResource(if (settingsVisible) R.string.settings else R.string.app_name)) },
                navigationIcon = {
                    if (settingsVisible) IconButton(
                        onClick = { settingsVisible = false },
                        modifier = Modifier.semantics { contentDescription = backLabel }
                    ) { Text(stringResource(R.string.back_symbol), fontSize = 26.sp) }
                },
                actions = {
                    Box {
                        IconButton(
                            onClick = { menuVisible = true },
                            modifier = Modifier.semantics { contentDescription = moreOptionsLabel }
                        ) { Text(stringResource(R.string.menu_symbol), fontSize = 28.sp) }
                        DropdownMenu(expanded = menuVisible, onDismissRequest = { menuVisible = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.settings)) }, onClick = {
                                menuVisible = false
                                settingsVisible = true
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.import_json)) }, enabled = !model.busy, onClick = {
                                menuVisible = false
                                launchDocumentAction(model) { importFile.launch(arrayOf("*/*")) }
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.export_json)) }, enabled = model.ready, onClick = {
                                menuVisible = false
                                launchDocumentAction(model) { exportFile.launch("dada-blubb.json") }
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.reset_defaults)) }, enabled = !model.busy, onClick = {
                                menuVisible = false
                                resetVisible = true
                            })
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (model.busy) LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = workingLabel })
            if (settingsVisible) SettingsScreen(
                model.settings, model::updateSettings,
                model.appearance, model::updateAppearance, language, onLanguage,
                onAbout = { aboutVisible = true }
            )
            else GeneratorScreen(
                settings = model.settings,
                output = model.output,
                enabled = model.ready,
                maxWordCount = model.maxWordCount,
                onSettings = model::updateSettings,
                onGenerate = model::generate,
                onOutputChange = model::editOutput,
                onCopy = { text ->
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(appName, text))
                    model.notify(R.string.copied)
                },
                onShare = { text ->
                    try {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        }, shareTitle))
                    } catch (_: ActivityNotFoundException) {
                        model.notify(R.string.no_share_app)
                    } catch (_: SecurityException) {
                        model.notify(R.string.share_denied)
                    }
                }
            )
        }
    }

    model.pendingImport?.let { candidate ->
        AlertDialog(
            onDismissRequest = { if (!model.busy) model.cancelImport() },
            title = { Text(stringResource(R.string.replace_title)) },
            text = { Text(stringResource(R.string.replace_help,
                pluralStringResource(R.plurals.word_total, candidate.words.size, candidate.words.size),
                pluralStringResource(R.plurals.connector_total, candidate.connectors.size, candidate.connectors.size))) },
            confirmButton = { TextButton(onClick = model::confirmImport, enabled = !model.busy) { Text(stringResource(R.string.replace)) } },
            dismissButton = { TextButton(onClick = model::cancelImport, enabled = !model.busy) { Text(stringResource(R.string.cancel)) } }
        )
    }
    if (resetVisible) AlertDialog(
        onDismissRequest = { resetVisible = false },
        title = { Text(stringResource(R.string.reset_title)) },
        text = { Text(stringResource(R.string.reset_help)) },
        confirmButton = { TextButton(onClick = { resetVisible = false; model.reset() }) { Text(stringResource(R.string.reset)) } },
        dismissButton = { TextButton(onClick = { resetVisible = false }) { Text(stringResource(R.string.cancel)) } }
    )
    if (aboutVisible) AboutDialog(version, onDismiss = { aboutVisible = false }) {
        model.notify(R.string.no_browser)
    }
    model.message?.let { message ->
        AlertDialog(
            onDismissRequest = model::dismissMessage,
            title = { Text(stringResource(R.string.app_name)) },
            text = { Text(resources.resolve(message)) },
            confirmButton = { TextButton(onClick = model::dismissMessage) { Text(stringResource(R.string.ok)) } }
        )
    }
}

private fun launchDocumentAction(model: AppViewModel, launch: () -> Unit) {
    try {
        launch()
    } catch (_: ActivityNotFoundException) {
        model.notify(R.string.no_document_picker)
    } catch (_: SecurityException) {
        model.notify(R.string.document_picker_denied)
    }
}

@Composable
internal fun GeneratorScreen(
    settings: GeneratorSettings,
    output: String,
    enabled: Boolean,
    onSettings: (GeneratorSettings) -> Unit,
    onGenerate: () -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onOutputChange: (String) -> Unit,
    maxWordCount: Int = 50
) {
    val focus = LocalFocusManager.current
    val decreaseLabel = stringResource(R.string.decrease_count)
    val currentCountLabel = stringResource(R.string.current_count, settings.wordCount)
    val increaseLabel = stringResource(R.string.increase_count)
    val wordCountLabel = stringResource(R.string.word_count)
    val outputLabel = stringResource(R.string.generated_text)
    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(R.string.home_tagline), style = MaterialTheme.typography.bodyLarge)
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.word_count), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.word_count_help), style = MaterialTheme.typography.bodyMedium)
                if (maxWordCount < 50) {
                    Text(
                        pluralStringResource(R.plurals.count_limit, maxWordCount, maxWordCount),
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
                        modifier = Modifier.size(48.dp).semantics { contentDescription = decreaseLabel }
                    ) { Text(stringResource(R.string.minus_symbol), fontSize = 24.sp) }
                    Text("${settings.wordCount}", style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(min = 40.dp).semantics { contentDescription = currentCountLabel })
                    FilledTonalIconButton(
                        onClick = { onSettings(settings.copy(wordCount = settings.wordCount + 1)) },
                        enabled = settings.wordCount < maxWordCount,
                        modifier = Modifier.size(48.dp).semantics { contentDescription = increaseLabel }
                    ) { Text(stringResource(R.string.plus_symbol), fontSize = 24.sp) }
                }
                Spacer(Modifier.height(24.dp))
                Slider(
                    value = settings.wordCount.toFloat(),
                    onValueChange = { onSettings(settings.copy(wordCount = it.roundToInt().coerceIn(1, maxWordCount))) },
                    valueRange = 1f..maxWordCount.toFloat(),
                    steps = (maxWordCount - 2).coerceAtLeast(0),
                    enabled = maxWordCount > 1,
                    modifier = Modifier.semantics { contentDescription = wordCountLabel }
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("1", style = MaterialTheme.typography.labelMedium)
                    Text("$maxWordCount", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Button(onClick = { focus.clearFocus(); onGenerate() }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(R.string.generate), style = MaterialTheme.typography.titleMedium)
        }
        Text(stringResource(R.string.generated_text), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = output,
            onValueChange = onOutputChange,
            modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp).testTag("output")
                .semantics { contentDescription = outputLabel },
            placeholder = { Text(stringResource(R.string.output_placeholder)) },
            textStyle = MaterialTheme.typography.headlineSmall,
            minLines = 4,
            maxLines = 8
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { onCopy(output) }, enabled = output.isNotEmpty(), modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.copy)) }
            OutlinedButton(onClick = { onShare(output) }, enabled = output.isNotEmpty(), modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.share)) }
        }
    }
}

@Composable
internal fun SettingsScreen(
    settings: GeneratorSettings,
    onChange: (GeneratorSettings) -> Unit,
    appearance: Appearance = Appearance.SYSTEM,
    onAppearance: (Appearance) -> Unit = {},
    language: AppLanguage = AppLanguage.SYSTEM,
    onLanguage: (AppLanguage) -> Unit = {},
    onAbout: () -> Unit = {}
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedCard(onClick = onAbout, modifier = Modifier.fillMaxWidth().testTag("about-entry")) {
            Text(stringResource(R.string.about_app), modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.titleMedium)
        }
        SettingGroup(stringResource(R.string.appearance), stringResource(R.string.appearance_help),
            Appearance.entries, appearance, { stringResource(when (it) {
                Appearance.SYSTEM -> R.string.system_default
                Appearance.LIGHT -> R.string.light
                Appearance.DARK -> R.string.dark
            }) }, tag = { "appearance-${it.name}" }, onSelect = onAppearance)
        SettingGroup(stringResource(R.string.language), stringResource(R.string.language_help),
            AppLanguage.entries, language, { stringResource(when (it) {
                AppLanguage.SYSTEM -> R.string.system_default
                AppLanguage.GERMAN -> R.string.language_german
                AppLanguage.ENGLISH -> R.string.language_english
            }) }, tag = { "language-${it.name}" }, onSelect = onLanguage)
        Text(stringResource(R.string.settings_help), style = MaterialTheme.typography.bodyMedium)
        SettingGroup(stringResource(R.string.style), stringResource(R.string.style_help),
            Style.entries, settings.style, { stringResource(when (it) {
                Style.MIXED -> R.string.mixed
                Style.RHYTHMIC -> R.string.rhythmic
                Style.CHAOTIC -> R.string.chaotic
            }) }) { onChange(settings.copy(style = it)) }
        SettingGroup(stringResource(R.string.repetition), stringResource(R.string.repetition_help),
            Repetition.entries, settings.repetition, { stringResource(if (it == Repetition.HIGH) R.string.high else R.string.normal) }) {
                onChange(settings.copy(repetition = it))
            }
        SettingGroup(stringResource(R.string.connectors), stringResource(R.string.connectors_help),
            Connectors.entries, settings.connectors, { stringResource(if (it == Connectors.FREQUENT) R.string.frequent else R.string.normal) }) {
                onChange(settings.copy(connectors = it))
            }
    }
}

@Composable
internal fun AboutDialog(version: AppVersion, onDismiss: () -> Unit, onLinkError: () -> Unit) {
    val context = LocalContext.current
    val sourceUrl = stringResource(R.string.source_url)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.about_app)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.about_description))
                Text(stringResource(R.string.about_version, version.name ?: stringResource(R.string.unknown_version), version.code))
                Text(stringResource(R.string.copyright_notice))
                Text(stringResource(R.string.license_name))
                Text(stringResource(R.string.license_explanation))
                Text(stringResource(R.string.codex_credit))
                TextButton(onClick = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW,
                            sourceUrl.toUri()))
                    } catch (_: ActivityNotFoundException) { onLinkError() }
                    catch (_: SecurityException) { onLinkError() }
                }) {
                    Column {
                        Text(stringResource(R.string.source_code))
                        Text(sourceUrl, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } }
    )
}

@Composable
private fun <T> SettingGroup(title: String, description: String, options: List<T>, selected: T, label: @Composable (T) -> String, tag: (T) -> String = { it.toString() }, onSelect: (T) -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
            Column(Modifier.selectableGroup()) {
                options.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag(option))
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
