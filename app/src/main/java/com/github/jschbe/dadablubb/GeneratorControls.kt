package com.github.jschbe.dadablubb

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.jschbe.dadablubb.core.GeneratorSettings
import kotlin.math.roundToInt

@Composable
internal fun GeneratorControls(
    settings: GeneratorSettings,
    maxWordCount: Int,
    enabled: Boolean,
    onSettings: (GeneratorSettings) -> Unit,
    onGenerate: () -> Unit
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 600.dp && fontScale <= 1.3f) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                WordCountCard(settings, maxWordCount, onSettings, true, Modifier.weight(1f))
                GenerateButton(enabled, onGenerate, Modifier.width(140.dp))
            }
        } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WordCountCard(settings, maxWordCount, onSettings, false, Modifier.fillMaxWidth())
            GenerateButton(enabled, onGenerate, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun GenerateButton(enabled: Boolean, onGenerate: () -> Unit, modifier: Modifier) {
    Button(onClick = onGenerate, enabled = enabled, modifier = modifier.heightIn(min = 48.dp)) {
        Text(stringResource(R.string.generate), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun WordCountCard(
    settings: GeneratorSettings,
    maxWordCount: Int,
    onSettings: (GeneratorSettings) -> Unit,
    compact: Boolean,
    modifier: Modifier
) {
    val countLabel = stringResource(R.string.word_count)
    OutlinedCard(modifier) {
        Column(Modifier.padding(if (compact) 8.dp else 12.dp)) {
            if (compact) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                WordCountHeading(Modifier.weight(1f))
                WordCountButtons(settings, maxWordCount, onSettings)
            } else {
                WordCountHeading(Modifier)
                WordCountButtons(settings, maxWordCount, onSettings,
                    Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp))
            }
            if (maxWordCount < 50) Text(
                pluralStringResource(R.plurals.count_limit, maxWordCount, maxWordCount),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
            if (!compact) Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (compact) Text("1", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = settings.wordCount.toFloat(),
                    onValueChange = { onSettings(settings.copy(wordCount = it.roundToInt().coerceIn(1, maxWordCount))) },
                    valueRange = 1f..maxWordCount.toFloat(),
                    steps = (maxWordCount - 2).coerceAtLeast(0),
                    enabled = maxWordCount > 1,
                    modifier = Modifier.weight(1f).semantics { contentDescription = countLabel }
                )
                if (compact) Text("$maxWordCount", style = MaterialTheme.typography.labelMedium)
            }
            if (!compact) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("1", style = MaterialTheme.typography.labelMedium)
                Text("$maxWordCount", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun WordCountHeading(modifier: Modifier) {
    Column(modifier) {
        Text(stringResource(R.string.word_count), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.word_count_help), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun WordCountButtons(
    settings: GeneratorSettings,
    maxWordCount: Int,
    onSettings: (GeneratorSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    val decrease = stringResource(R.string.decrease_count)
    val increase = stringResource(R.string.increase_count)
    val count = stringResource(R.string.current_count, settings.wordCount)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = { onSettings(settings.copy(wordCount = settings.wordCount - 1)) },
            enabled = settings.wordCount > 1,
            modifier = Modifier.size(48.dp).semantics { contentDescription = decrease }) {
            Text(stringResource(R.string.minus_symbol), fontSize = 24.sp)
        }
        Text("${settings.wordCount}", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 40.dp).semantics { contentDescription = count })
        FilledTonalIconButton(onClick = { onSettings(settings.copy(wordCount = settings.wordCount + 1)) },
            enabled = settings.wordCount < maxWordCount,
            modifier = Modifier.size(48.dp).semantics { contentDescription = increase }) {
            Text(stringResource(R.string.plus_symbol), fontSize = 24.sp)
        }
    }
}
