package com.cocoexperiments.tumbler.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cocoexperiments.tumbler.R
import com.cocoexperiments.tumbler.domain.AppLockPolicy
import com.cocoexperiments.tumbler.domain.LockMode
import com.cocoexperiments.tumbler.domain.LockedApp
import com.cocoexperiments.tumbler.util.AppEntry

/** Pick the apps to lock and a mode for each. Locked apps are listed first. */
@Composable
fun LockedAppsScreen(
    apps: List<AppEntry>?,
    locked: Map<String, LockedApp>,
    onBack: () -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onMode: (String, LockMode) -> Unit,
    onMinutes: (String, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    val colors = MaterialTheme.colorScheme
    // Locked apps first, decided when the list loads so rows do not jump around while toggling.
    val sorted = remember(apps) { apps.orEmpty().sortedByDescending { it.packageName in locked } }

    Column(modifier = modifier.fillMaxSize().systemBarsPadding()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(stringResource(R.string.apps_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.apps_count, locked.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        if (apps == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(sorted, key = { it.packageName }) { entry ->
                    AppRow(entry, locked[entry.packageName], onToggle, onMode, onMinutes)
                }
            }
        }
    }
}

@Composable
private fun AppRow(
    entry: AppEntry,
    locked: LockedApp?,
    onToggle: (String, Boolean) -> Unit,
    onMode: (String, LockMode) -> Unit,
    onMinutes: (String, Int) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(20.dp), color = if (locked != null) colors.surfaceContainerHigh else colors.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                entry.icon?.let {
                    Image(bitmap = it, contentDescription = null, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)))
                }
                Column(Modifier.weight(1f)) {
                    Text(entry.label, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = when {
                            locked == null -> stringResource(R.string.apps_not_locked)
                            locked.mode == LockMode.PRIVACY -> stringResource(R.string.apps_summary_privacy, locked.minutes)
                            else -> stringResource(R.string.apps_summary_wellbeing, AppLockPolicy.PAUSE_SECONDS, locked.minutes)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                Switch(checked = locked != null, onCheckedChange = { onToggle(entry.packageName, it) })
            }
            if (locked != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LockMode.entries.forEach { mode ->
                        FilterChip(
                            selected = locked.mode == mode,
                            onClick = { onMode(entry.packageName, mode) },
                            label = {
                                Text(stringResource(if (mode == LockMode.PRIVACY) R.string.mode_privacy else R.string.mode_wellbeing))
                            },
                        )
                    }
                }
                Text(
                    stringResource(if (locked.mode == LockMode.PRIVACY) R.string.apps_grace_label else R.string.apps_open_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLockPolicy.options(locked.mode).forEach { minutes ->
                        FilterChip(
                            selected = locked.minutes == minutes,
                            onClick = { onMinutes(entry.packageName, minutes) },
                            label = { Text(stringResource(R.string.minutes_short, minutes)) },
                        )
                    }
                }
            }
        }
    }
}
