package com.github.jschbe.dadablubb

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.core.net.toUri
import com.github.jschbe.dadablubb.core.*
import com.github.jschbe.dadablubb.data.OutputError
import com.github.jschbe.dadablubb.ui.theme.DadaBlubbTheme

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocales.localizedContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowPolicy.apply(this)
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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        WindowPolicy.apply(this)
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
    val copiedLabel = stringResource(R.string.copied)
    val shareTitle = stringResource(R.string.share_title)
    val outputState by model.outputState.collectAsState()
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
                    ) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = null,
                            modifier = Modifier.size(24.dp).testTag("settings-back-icon"))
                    }
                },
                actions = {
                    Box {
                        IconButton(
                            onClick = { menuVisible = true },
                            modifier = Modifier.semantics { contentDescription = moreOptionsLabel }
                        ) { Text(stringResource(R.string.menu_symbol), fontSize = 28.sp) }
                        DropdownMenu(expanded = menuVisible, onDismissRequest = { menuVisible = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.about_app)) },
                                modifier = Modifier.testTag("about-entry"), onClick = {
                                    menuVisible = false
                                    aboutVisible = true
                                })
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
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            if (model.busy) LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = workingLabel })
            if (settingsVisible) SettingsScreen(
                model.settings, model::updateSettings,
                model.appearance, model::updateAppearance, language, onLanguage
            )
            else GeneratorScreen(
                settings = model.settings,
                output = outputState.text,
                wrapHints = outputState.wrapHints,
                showOutputPlaceholder = outputState.showPlaceholder,
                outputLoaded = outputState.loaded,
                outputError = outputState.error,
                enabled = model.ready,
                maxWordCount = model.maxWordCount,
                onSettings = model::updateSettings,
                onGenerate = model::generate,
                onOutputChange = model::editOutput,
                onCopy = { text ->
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(appName, text))
                    Toast.makeText(context, copiedLabel, Toast.LENGTH_SHORT).show()
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
    maxWordCount: Int = 50,
    showOutputPlaceholder: Boolean = true,
    outputLoaded: Boolean = true,
    outputError: OutputError? = null,
    wrapHints: OutputWrapHints? = null
) {
    val focus = LocalFocusManager.current
    val outputLabel = stringResource(R.string.generated_text)
    GeneratorLayout(controls = {
        GeneratorControls(settings, maxWordCount, enabled, onSettings) { focus.clearFocus(); onGenerate() }
    }, editor = {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.generated_text), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = output,
                onValueChange = onOutputChange,
                visualTransformation = remember(wrapHints) {
                    wrapHints?.let(::StemBoundaryTransformation) ?: androidx.compose.ui.text.input.VisualTransformation.None
                },
                modifier = Modifier.fillMaxWidth().weight(1f).testTag("output")
                    .semantics { contentDescription = outputLabel },
                enabled = outputLoaded,
                placeholder = if (showOutputPlaceholder) ({ Text(stringResource(R.string.output_placeholder)) }) else null,
                isError = outputError != null,
                supportingText = outputError?.let { error -> {
                    Text(stringResource(when (error) {
                        OutputError.READ -> R.string.output_read_failed
                        OutputError.WRITE -> R.string.output_write_failed
                        OutputError.TOO_LARGE -> R.string.output_too_large
                        OutputError.INVALID_TEXT -> R.string.output_invalid_text
                    }))
                } },
                textStyle = MaterialTheme.typography.headlineSmall,
                minLines = 1,
                maxLines = Int.MAX_VALUE
            )
        }
    }, actions = { OutputActions(output, onCopy, onShare) })
}

@Composable
private fun OutputActions(output: String, onCopy: (String) -> Unit, onShare: (String) -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 320.dp && fontScale > 1.3f) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onCopy(output) }, enabled = output.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.copy)) }
                OutlinedButton(onClick = { onShare(output) }, enabled = output.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.share)) }
            }
        } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
    onLanguage: (AppLanguage) -> Unit = {}
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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
    val openLink: (String) -> Unit = { url ->
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (_: ActivityNotFoundException) { onLinkError() }
        catch (_: SecurityException) { onLinkError() }
    }
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
                AboutLink(stringResource(R.string.source_code), stringResource(R.string.source_url), openLink)
                AboutLink(stringResource(R.string.privacy_policy), stringResource(R.string.privacy_url), openLink)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } }
    )
}

@Composable
private fun AboutLink(label: String, url: String, onOpen: (String) -> Unit) {
    TextButton(onClick = { onOpen(url) }) {
        Column {
            Text(label)
            Text(url, style = MaterialTheme.typography.bodySmall)
        }
    }
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
