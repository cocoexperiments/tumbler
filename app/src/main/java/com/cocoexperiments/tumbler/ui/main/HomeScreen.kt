package com.cocoexperiments.tumbler.ui.main

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cocoexperiments.tumbler.R
import com.cocoexperiments.tumbler.ui.components.CompanyFooter
import com.cocoexperiments.tumbler.ui.components.Wordmark

@Composable
fun HomeScreen(
    state: MainUiState,
    onToggleLock: (Boolean) -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestFullScreen: () -> Unit,
    onPreview: () -> Unit,
    onChangePin: () -> Unit,
    onOpenSecuritySettings: () -> Unit,
    onOpenWebsite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val statusColor by animateColorAsState(
        if (state.lockEnabled) colors.primaryContainer else colors.surfaceContainerHigh,
        label = "statusColor",
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Wordmark(large = true)

        Surface(shape = RoundedCornerShape(28.dp), color = statusColor) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.home_enable), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = stringResource(
                            when {
                                state.lockEnabled -> R.string.home_status_active
                                state.allPermissionsGranted -> R.string.home_status_off
                                else -> R.string.home_status_setup
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.lockEnabled,
                    onCheckedChange = onToggleLock,
                    enabled = state.lockEnabled || state.allPermissionsGranted,
                )
            }
        }

        SystemLockCard(secure = state.systemLockSecure, onOpenSettings = onOpenSecuritySettings)

        Text(
            stringResource(R.string.home_permissions),
            style = MaterialTheme.typography.titleSmall,
            color = colors.primary,
        )
        PermissionRow(R.string.perm_overlay_title, R.string.perm_overlay_desc, state.overlayGranted, onRequestOverlay)
        if (Build.VERSION.SDK_INT >= 33) {
            PermissionRow(
                R.string.perm_notifications_title,
                R.string.perm_notifications_desc,
                state.notificationsGranted,
                onRequestNotifications,
            )
        }
        if (Build.VERSION.SDK_INT >= 34) {
            PermissionRow(
                R.string.perm_fullscreen_title,
                R.string.perm_fullscreen_desc,
                state.fullScreenGranted,
                onRequestFullScreen,
            )
        }

        Spacer(Modifier.height(4.dp))
        Button(onClick = onPreview, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.home_preview))
        }
        OutlinedButton(onClick = onChangePin, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.home_change_pin))
        }
        Text(
            stringResource(R.string.home_note),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
        CompanyFooter(onClick = onOpenWebsite, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun PermissionRow(title: Int, description: Int, granted: Boolean, onGrant: () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 12.dp, bottom = 12.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (granted) {
                Text(
                    stringResource(R.string.granted),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            } else {
                FilledTonalButton(onClick = onGrant) { Text(stringResource(R.string.grant)) }
            }
        }
    }
}

@Composable
private fun SystemLockCard(secure: Boolean, onOpenSettings: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = if (secure) colors.surfaceContainerHigh else colors.primaryContainer.copy(alpha = 0.5f),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                stringResource(if (secure) R.string.system_lock_title else R.string.system_lock_only_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(if (secure) R.string.system_lock_body else R.string.system_lock_only_body),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            if (secure) {
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(onClick = onOpenSettings) { Text(stringResource(R.string.system_lock_action)) }
            }
        }
    }
}
