package com.luxboy.mysecretary.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luxboy.mysecretary.R
import com.luxboy.mysecretary.data.update.UpdateInfo
import com.luxboy.mysecretary.ui.components.AppAlertDialog

@Composable
fun UpdateOverlay(viewModel: UpdateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val s = state) {
        UpdateState.Idle, UpdateState.Checking, UpdateState.Failed -> Unit
        is UpdateState.Available -> AvailableDialog(
            info = s.info,
            onUpdate = viewModel::startUpdate,
            onLater = viewModel::dismiss,
        )
        is UpdateState.NeedPermission -> PermissionDialog(
            onOpenSettings = {
                viewModel.openInstallSettings()
                viewModel.retryAfterPermission()
            },
            onCancel = viewModel::dismiss,
        )
        is UpdateState.Downloading -> DownloadingDialog(info = s.info)
    }
}

@Composable
private fun AvailableDialog(
    info: UpdateInfo,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
) {
    AppAlertDialog(
        onDismissRequest = onLater,
        title = {
            Text(text = stringResource(R.string.update_available_title))
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(
                        R.string.update_available_version,
                        info.currentVersion,
                        info.latestVersion,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (info.sizeBytes > 0) {
                    val mb = info.sizeBytes / (1024.0 * 1024.0)
                    Text(
                        text = stringResource(R.string.update_size, String.format("%.1f", mb)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                info.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                    Text(
                        text = stringResource(R.string.update_notes_label),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        text = notes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onUpdate) {
                Text(stringResource(R.string.update_action_now))
            }
        },
        dismissButton = {
            TextButton(onClick = onLater) {
                Text(stringResource(R.string.update_action_later))
            }
        },
    )
}

@Composable
private fun PermissionDialog(
    onOpenSettings: () -> Unit,
    onCancel: () -> Unit,
) {
    AppAlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.update_permission_title)) },
        text = { Text(stringResource(R.string.update_permission_message)) },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.update_open_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun DownloadingDialog(info: UpdateInfo) {
    AppAlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.update_downloading_title)) },
        text = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CircularProgressIndicator()
                Column {
                    Text(
                        text = stringResource(R.string.update_downloading_message, info.latestVersion),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(R.string.update_downloading_notice),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {},
    )
}
