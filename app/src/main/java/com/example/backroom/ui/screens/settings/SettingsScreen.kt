package com.example.backroom.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.backroom.data.PreferencesManager
import com.example.backroom.ui.theme.BackroomTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferencesManager: PreferencesManager,
    onBack: () -> Unit = {},
    onNavigateToBoundaries: () -> Unit = {},
    onNavigateToBlockedUsers: () -> Unit = {},
    onNavigateToSubscription: () -> Unit = {}
) {
    val context = LocalContext.current
    val incomingPreviewsEnabled by preferencesManager.incomingPreviewsFlow.collectAsState()
    val tipsRemindersEnabled by preferencesManager.tipsRemindersFlow.collectAsState()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            // ── PRIVACY & SAFETY ───────────────────────────────
            SettingsSectionHeader(title = "PRIVACY & SAFETY")

            SettingsItem(
                icon = Icons.Filled.GraphicEq,
                title = "Voice Anonymization",
                subtitle = "Basic (mandatory)",
                onClick = {
                    Toast.makeText(context, "Voice anonymization is always on for your safety", Toast.LENGTH_SHORT).show()
                }
            )
            SettingsItem(
                icon = Icons.Filled.Block,
                title = "Blocked Users",
                subtitle = "${preferencesManager.blockedUsers.size} blocked",
                onClick = onNavigateToBlockedUsers
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── LISTENING PREFERENCES ──────────────────────────
            SettingsSectionHeader(title = "LISTENING PREFERENCES")

            SettingsItem(
                icon = Icons.Filled.Tune,
                title = "My Boundaries",
                subtitle = "Topics, intensity, duration",
                onClick = onNavigateToBoundaries
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── NOTIFICATIONS ──────────────────────────────────
            SettingsSectionHeader(title = "NOTIFICATIONS")

            SettingsToggleItem(
                icon = Icons.Filled.Notifications,
                title = "Incoming Previews",
                subtitle = "Get notified when someone needs to talk",
                isChecked = incomingPreviewsEnabled,
                onCheckedChange = { enabled ->
                    preferencesManager.incomingPreviewsEnabled = enabled
                    Toast.makeText(
                        context,
                        if (enabled) "Preview notifications enabled" else "Preview notifications disabled",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
            SettingsToggleItem(
                icon = Icons.Filled.Notifications,
                title = "Tips & Reminders",
                subtitle = "Helpful tips and occasional reminders",
                isChecked = tipsRemindersEnabled,
                onCheckedChange = { enabled ->
                    preferencesManager.tipsRemindersEnabled = enabled
                    Toast.makeText(context, if (enabled) "Tips enabled" else "Tips disabled", Toast.LENGTH_SHORT).show()
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── APP ────────────────────────────────────────────
            SettingsSectionHeader(title = "APP")

            SettingsItem(
                icon = Icons.Filled.Star,
                title = "Backroom Plus",
                subtitle = if (preferencesManager.isSubscriptionActive())
                    "Active — ${preferencesManager.subscriptionTier}" else "Unlock more features",
                onClick = onNavigateToSubscription
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── ACCOUNT ────────────────────────────────────────
            SettingsSectionHeader(title = "ACCOUNT")

            SettingsItem(
                icon = Icons.Filled.Download,
                title = "Export My Data",
                subtitle = "Download your data",
                onClick = { showExportDialog = true }
            )
            SettingsItem(
                icon = Icons.Filled.Delete,
                title = "Delete Account",
                subtitle = "Permanently delete all data",
                onClick = { showDeleteDialog = true },
                isDestructive = true
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Version
            Text(
                text = "Version 1.0.0",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showExportDialog) {
        ExportDataDialog(
            onDismiss = { showExportDialog = false },
            onExport = {
                showExportDialog = false
                val data = preferencesManager.exportUserData()
                Toast.makeText(context, "Data exported: ${data.size} items", Toast.LENGTH_LONG).show()
            }
        )
    }

    if (showDeleteDialog) {
        DeleteAccountDialog(
            onDismiss = { showDeleteDialog = false },
            onDelete = {
                showDeleteDialog = false
                preferencesManager.clear()
                Toast.makeText(context, "Account deleted. All data has been removed.", Toast.LENGTH_LONG).show()
            }
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// COMPONENTS
// ═══════════════════════════════════════════════════════════════

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = MaterialTheme.typography.labelMedium.letterSpacing
        ),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 12.dp)
    )
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isDestructive) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isDestructive) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!isDestructive) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Navigate",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = 44.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

@Composable
private fun SettingsToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!isChecked) }
            .padding(vertical = 14.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary
            )
        )
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = 44.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

@Composable
private fun ExportDataDialog(
    onDismiss: () -> Unit,
    onExport: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Export Your Data", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Text(
                text = "This will export your preferences and settings. No call content is stored, so there's nothing else to export.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(onClick = onExport, shape = RoundedCornerShape(12.dp)) { Text("Export") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) { Text("Cancel") }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun DeleteAccountDialog(
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Delete Account?", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Text(
                text = "This will permanently delete all your data, including:\n\n• Preferences and settings\n• Blocked users list\n• Subscription status\n\nThis action cannot be undone.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onDelete,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("Delete Everything") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) { Text("Cancel") }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

// ═══════════════════════════════════════════════════════════════
// PREVIEWS
// ═══════════════════════════════════════════════════════════════

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun SettingsScreenPreview() {
    BackroomTheme {
        val context = LocalContext.current
        SettingsScreen(preferencesManager = PreferencesManager(context))
    }
}

@Preview(showBackground = true, showSystemUi = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun SettingsScreenDarkPreview() {
    BackroomTheme(darkTheme = true) {
        val context = LocalContext.current
        SettingsScreen(preferencesManager = PreferencesManager(context))
    }
}

