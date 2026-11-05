package com.example.namazvakti.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.namazvakti.R
import com.example.namazvakti.domain.model.PrayerNotificationPrayer
import com.example.namazvakti.domain.model.PrayerNotificationSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrayerNotificationSettingsSheet(
    settings: PrayerNotificationSettings,
    saveFailed: Boolean,
    exactAlarmNeeded: Boolean,
    onDismiss: () -> Unit,
    onSave: (PrayerNotificationSettings) -> Unit,
    onRequestExactAlarm: () -> Unit
) {
    var notificationsEnabled by remember(settings) { mutableStateOf(settings.notificationsEnabled) }
    var enabledPrayers by remember(settings) { mutableStateOf(settings.enabledPrayers) }
    var leadMinutes by remember(settings) { mutableIntStateOf(settings.leadMinutes) }
    var religiousDaysEnabled by remember(settings) { mutableStateOf(settings.religiousDaysEnabled) }
    var leadMenuExpanded by rememberSaveable { mutableStateOf(false) }
    val leadValues = listOf(0, 5, 10, 15, 30, 45)
    val leadLabels = stringArrayResource(R.array.notification_lead_choices)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.notification_settings_title),
                style = MaterialTheme.typography.headlineSmall
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.notifications_enabled), modifier = Modifier.weight(1f))
                Switch(
                    checked = notificationsEnabled,
                    onCheckedChange = { notificationsEnabled = it }
                )
            }

            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.religious_day_notifications_enabled), modifier = Modifier.weight(1f))
                Switch(
                    checked = religiousDaysEnabled,
                    onCheckedChange = { religiousDaysEnabled = it }
                )
            }

            HorizontalDivider()
            PrayerNotificationPrayer.entries.forEach { prayer ->
                val checked = prayer in enabledPrayers
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = checked,
                            enabled = notificationsEnabled,
                            role = Role.Checkbox
                        ) {
                            enabledPrayers = if (checked) enabledPrayers - prayer else enabledPrayers + prayer
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = checked,
                        enabled = notificationsEnabled,
                        onCheckedChange = null
                    )
                    Text(
                        text = stringResource(prayer.stringId()),
                        color = if (notificationsEnabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = stringResource(R.string.notification_lead_time),
                style = MaterialTheme.typography.titleMedium
            )
            androidx.compose.foundation.layout.Box {
                OutlinedButton(
                    onClick = { leadMenuExpanded = true },
                    enabled = notificationsEnabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(leadLabels[leadValues.indexOf(leadMinutes).coerceAtLeast(0)])
                }
                DropdownMenu(
                    expanded = leadMenuExpanded,
                    onDismissRequest = { leadMenuExpanded = false }
                ) {
                    leadValues.forEachIndexed { index, minutes ->
                        DropdownMenuItem(
                            text = { Text(leadLabels[index]) },
                            onClick = {
                                leadMinutes = minutes
                                leadMenuExpanded = false
                            }
                        )
                    }
                }
            }

            if (exactAlarmNeeded && notificationsEnabled) {
                Text(
                    text = stringResource(R.string.precise_widget_updates_disabled),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = onRequestExactAlarm,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.enable_precise_widget_updates))
                }
            }

            if (saveFailed) {
                Text(
                    text = stringResource(R.string.notification_settings_save_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.notification_cancel))
                }
                Button(
                    onClick = {
                        onSave(
                            PrayerNotificationSettings(
                                notificationsEnabled = notificationsEnabled,
                                enabledPrayers = enabledPrayers,
                                leadMinutes = leadMinutes,
                                religiousDaysEnabled = religiousDaysEnabled
                            )
                        )
                    }
                ) {
                    Text(stringResource(R.string.notification_save))
                }
            }
        }
    }
}

private fun PrayerNotificationPrayer.stringId(): Int = when (this) {
    PrayerNotificationPrayer.IMSAK -> R.string.prayer_imsak
    PrayerNotificationPrayer.SUNRISE -> R.string.prayer_sunrise
    PrayerNotificationPrayer.DHUHR -> R.string.prayer_dhuhr
    PrayerNotificationPrayer.ASR -> R.string.prayer_asr
    PrayerNotificationPrayer.MAGHRIB -> R.string.prayer_maghrib
    PrayerNotificationPrayer.ISHA -> R.string.prayer_isha
}
