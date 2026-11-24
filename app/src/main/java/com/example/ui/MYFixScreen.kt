package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.ActiveOperation
import com.example.model.DiagnosticLogLine
import com.example.model.LogSeverity
import com.example.model.LogTag
import com.example.model.MYFixUiState
import com.example.model.SystemStatus
import com.example.ui.theme.CyanEmerald
import com.example.ui.theme.SkyAccent
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import kotlinx.coroutines.launch

@Composable
fun MYFixScreen(
    state: MYFixUiState,
    onDiagnosticClick: () -> Unit,
    onDiagnosticWithoutDeployClick: () -> Unit,
    onDeployClick: () -> Unit,
    onExportSdcardClick: () -> Unit,
    onImportClick: () -> Unit,
    onTestFingerprintLiveClick: () -> Unit,
    onOpenFingerprintSettingsClick: () -> Unit,
    onStopLiveClick: () -> Unit,
    onRefreshStatusClick: () -> Unit,
    onOpenSettingsClick: () -> Unit,
    onClearLogsClick: () -> Unit,
    onDismissBanner: () -> Unit,
    onRequestStoragePermission: () -> Unit,
    onDismissStorageDialog: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val copiedMessage = stringResource(R.string.msg_logs_copied)

    LaunchedEffect(state.bannerMessage) {
        val msg = state.bannerMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            onDismissBanner()
        }
    }

    if (state.showStoragePermissionDialog) {
        AlertDialog(
            onDismissRequest = onDismissStorageDialog,
            title = { Text(stringResource(R.string.msg_storage_permission_title)) },
            text = { Text(stringResource(R.string.msg_storage_permission_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        onDismissStorageDialog()
                        onRequestStoragePermission()
                    },
                    modifier = Modifier.testTag("btn_grant_storage"),
                ) {
                    Text(stringResource(R.string.btn_open_settings))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissStorageDialog,
                    modifier = Modifier.testTag("btn_cancel_storage"),
                ) {
                    Text(stringResource(R.string.btn_cancel))
                }
            },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                            MaterialTheme.colorScheme.background,
                        ),
                    ),
                )
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 1000.dp),
            ) {
                val isWide = maxWidth >= 680.dp
                if (isWide) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .width(360.dp)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            HeaderSection(
                                status = state.status,
                                onRefreshClick = onRefreshStatusClick,
                                onSettingsClick = onOpenSettingsClick,
                            )
                            StatusStrip(status = state.status)
                            ActionGrid(
                                activeOperation = state.activeOperation,
                                onDiagnosticClick = onDiagnosticClick,
                                onDiagnosticWithoutDeployClick = onDiagnosticWithoutDeployClick,
                                onDeployClick = onDeployClick,
                                onExportSdcardClick = onExportSdcardClick,
                                onImportClick = onImportClick,
                            )
                            LiveSensorBar(
                                visible = state.isLiveMonitoring,
                                onTestFingerprintClick = onTestFingerprintLiveClick,
                                onOpenFpSettingsClick = onOpenFingerprintSettingsClick,
                                onStopLiveClick = onStopLiveClick,
                            )
                        }
                        DiagnosticConsoleCard(
                            logs = state.logs,
                            isLive = state.isLiveMonitoring,
                            onCopyClick = {
                                val text = state.logs.joinToString("\n") { it.formatted() }
                                if (text.isNotBlank()) {
                                    clipboardManager.setText(AnnotatedString(text))
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(copiedMessage)
                                    }
                                }
                            },
                            onClearClick = onClearLogsClick,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        HeaderSection(
                            status = state.status,
                            onRefreshClick = onRefreshStatusClick,
                            onSettingsClick = onOpenSettingsClick,
                        )
                        StatusStrip(status = state.status)
                        ActionGrid(
                            activeOperation = state.activeOperation,
                            onDiagnosticClick = onDiagnosticClick,
                            onDiagnosticWithoutDeployClick = onDiagnosticWithoutDeployClick,
                            onDeployClick = onDeployClick,
                            onExportSdcardClick = onExportSdcardClick,
                            onImportClick = onImportClick,
                        )
                        LiveSensorBar(
                            visible = state.isLiveMonitoring,
                            onTestFingerprintClick = onTestFingerprintLiveClick,
                            onOpenFpSettingsClick = onOpenFingerprintSettingsClick,
                            onStopLiveClick = onStopLiveClick,
                        )
                        DiagnosticConsoleCard(
                            logs = state.logs,
                            isLive = state.isLiveMonitoring,
                            onCopyClick = {
                                val text = state.logs.joinToString("\n") { it.formatted() }
                                if (text.isNotBlank()) {
                                    clipboardManager.setText(AnnotatedString(text))
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(copiedMessage)
                                    }
                                }
                            },
                            onClearClick = onClearLogsClick,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderSection(
    status: SystemStatus,
    onRefreshClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    val sourceLabel = if (status.activeBinary.isImported) {
        stringResource(R.string.source_imported)
    } else {
        stringResource(R.string.source_bundled)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (status.activeBinary.isAvailable) CyanEmerald else StatusWarning),
                )
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${status.primaryAbi} · $sourceLabel",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onRefreshClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("btn_refresh_status"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.cd_refresh_status),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("btn_settings"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = stringResource(R.string.cd_settings),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatusStrip(status: SystemStatus) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusPill(
            label = stringResource(R.string.status_abi_label),
            value = if (status.isChecking) stringResource(R.string.status_checking) else status.primaryAbi,
            accent = if (status.isArm64) StatusSuccess else StatusWarning,
        )
        StatusPill(
            label = stringResource(R.string.status_root_label),
            value = when {
                status.isChecking -> stringResource(R.string.status_checking)
                status.isRootAvailable -> stringResource(R.string.status_ok)
                else -> stringResource(R.string.status_denied)
            },
            accent = if (status.isRootAvailable) StatusSuccess else StatusWarning,
        )
        StatusPill(
            label = stringResource(R.string.status_system_label),
            value = when {
                status.isChecking -> stringResource(R.string.status_checking)
                status.isDeployedInSystemLib64 -> stringResource(R.string.status_ready)
                else -> stringResource(R.string.status_missing)
            },
            accent = if (status.isDeployedInSystemLib64) CyanEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        StatusPill(
            label = stringResource(R.string.status_selinux_label),
            value = if (status.isChecking) stringResource(R.string.status_checking) else status.seLinuxMode,
            accent = SkyAccent,
        )
    }
}

@Composable
private fun StatusPill(
    label: String,
    value: String,
    accent: Color,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ActionGrid(
    activeOperation: ActiveOperation,
    onDiagnosticClick: () -> Unit,
    onDiagnosticWithoutDeployClick: () -> Unit,
    onDeployClick: () -> Unit,
    onExportSdcardClick: () -> Unit,
    onImportClick: () -> Unit,
) {
    val isBusy = activeOperation != ActiveOperation.NONE

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ActionPrimaryButton(
                label = stringResource(R.string.btn_diagnostic),
                icon = Icons.Rounded.VerifiedUser,
                isLoading = activeOperation == ActiveOperation.DIAGNOSTIC_DEPLOYED,
                enabled = !isBusy,
                onClick = onDiagnosticClick,
                modifier = Modifier
                    .weight(1f)
                    .testTag("btn_diagnostic"),
            )
            ActionTonalButton(
                label = stringResource(R.string.btn_diagnostic_no_deploy),
                icon = Icons.Rounded.Science,
                isLoading = activeOperation == ActiveOperation.DIAGNOSTIC_SANDBOX,
                enabled = !isBusy,
                onClick = onDiagnosticWithoutDeployClick,
                modifier = Modifier
                    .weight(1f)
                    .testTag("btn_diagnostic_no_deploy"),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ActionTonalButton(
                label = stringResource(R.string.btn_deploy),
                icon = Icons.Rounded.SystemUpdateAlt,
                isLoading = activeOperation == ActiveOperation.DEPLOY,
                enabled = !isBusy,
                onClick = onDeployClick,
                modifier = Modifier
                    .weight(1f)
                    .testTag("btn_deploy"),
            )
            ActionOutlinedButton(
                label = stringResource(R.string.btn_export_sdcard),
                icon = Icons.Rounded.SdStorage,
                isLoading = activeOperation == ActiveOperation.EXPORT,
                enabled = !isBusy,
                onClick = onExportSdcardClick,
                modifier = Modifier
                    .weight(1f)
                    .testTag("btn_export_sdcard"),
            )
        }
        ActionOutlinedButton(
            label = stringResource(R.string.btn_import),
            icon = Icons.Rounded.FileUpload,
            isLoading = activeOperation == ActiveOperation.IMPORT,
            enabled = !isBusy,
            onClick = onImportClick,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("btn_import"),
        )
    }
}

@Composable
private fun LiveSensorBar(
    visible: Boolean,
    onTestFingerprintClick: () -> Unit,
    onOpenFpSettingsClick: () -> Unit,
    onStopLiveClick: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(
                onClick = onTestFingerprintClick,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("btn_test_fingerprint_live"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Fingerprint,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.btn_test_fingerprint_live),
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            OutlinedButton(
                onClick = onOpenFpSettingsClick,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("btn_open_fp_settings"),
            ) {
                Text(
                    text = stringResource(R.string.btn_open_fp_settings),
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            OutlinedButton(
                onClick = onStopLiveClick,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                border = BorderStroke(1.dp, StatusError.copy(alpha = 0.7f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusError),
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("btn_stop_live"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Stop,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.btn_stop_live),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun ActionPrimaryButton(
    label: String,
    icon: ImageVector,
    isLoading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = modifier.defaultMinSize(minHeight = 52.dp),
    ) {
        ActionButtonContent(label = label, icon = icon, isLoading = isLoading)
    }
}

@Composable
private fun ActionTonalButton(
    label: String,
    icon: ImageVector,
    isLoading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = modifier.defaultMinSize(minHeight = 52.dp),
    ) {
        ActionButtonContent(label = label, icon = icon, isLoading = isLoading)
    }
}

@Composable
private fun ActionOutlinedButton(
    label: String,
    icon: ImageVector,
    isLoading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = modifier.defaultMinSize(minHeight = 52.dp),
    ) {
        ActionButtonContent(label = label, icon = icon, isLoading = isLoading)
    }
}

@Composable
private fun ActionButtonContent(
    label: String,
    icon: ImageVector,
    isLoading: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DiagnosticConsoleCard(
    logs: List<DiagnosticLogLine>,
    isLive: Boolean,
    onCopyClick: () -> Unit,
    onClearClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.lastIndex)
        }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.8f)),
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Terminal,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.console_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (isLive) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(StatusSuccess),
                                )
                                Text(
                                    text = stringResource(R.string.console_live_badge),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CyanEmerald,
                                )
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onCopyClick,
                        enabled = logs.isNotEmpty(),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("btn_copy_logs"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = stringResource(R.string.cd_copy_logs),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(
                        onClick = onClearClick,
                        enabled = logs.isNotEmpty(),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("btn_clear_logs"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = stringResource(R.string.cd_clear_logs),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = logs.isEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 24.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Terminal,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.size(32.dp),
                        )
                        Text(
                            text = stringResource(R.string.console_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (logs.isNotEmpty()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("diagnostic_console_list"),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(logs, key = { it.id }) { line ->
                            ConsoleLogRow(line = line)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConsoleLogRow(line: DiagnosticLogLine) {
    val tagColor = when (line.tag) {
        LogTag.CORE -> CyanEmerald
        LogTag.HAL -> SkyAccent
        LogTag.FOD -> StatusSuccess
        LogTag.SYS -> MaterialTheme.colorScheme.onSurfaceVariant
        LogTag.ERR -> StatusError
    }
    val msgColor = when (line.severity) {
        LogSeverity.ERROR -> StatusError
        LogSeverity.WARNING -> StatusWarning
        LogSeverity.SUCCESS -> MaterialTheme.colorScheme.onSurface
        LogSeverity.INFO -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "[${line.tag}]",
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = tagColor,
        )
        Text(
            text = line.message,
            style = MaterialTheme.typography.bodySmall,
            color = msgColor,
            modifier = Modifier.weight(1f),
        )
    }
}
