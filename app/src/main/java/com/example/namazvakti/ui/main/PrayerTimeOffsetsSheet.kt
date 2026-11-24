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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import com.example.namazvakti.R
import com.example.namazvakti.domain.model.PrayerTimeName
import com.example.namazvakti.domain.model.PrayerTimeOffsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrayerTimeOffsetsSheet(
    offsets: PrayerTimeOffsets,
    saveFailed: Boolean,
    onDismiss: () -> Unit,
    onOffsetsChange: (PrayerTimeOffsets) -> Unit,
    onSave: (PrayerTimeOffsets) -> Unit
) {
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
                stringResource(R.string.offsets_settings_title),
                style = MaterialTheme.typography.headlineSmall
            )
            HorizontalDivider()
            PrayerTimeName.entries.forEach { prayer ->
                val minutes = offsets.minutesFor(prayer)
                val prayerLabel = stringResource(prayer.labelStringId())
                val decreaseDescription = stringResource(
                    R.string.offset_decrease_accessibility,
                    prayerLabel
                )
                val increaseDescription = stringResource(
                    R.string.offset_increase_accessibility,
                    prayerLabel
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        prayerLabel,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    TextButton(
                        onClick = { onOffsetsChange(offsets.withOffset(prayer, minutes - 1)) },
                        enabled = minutes > PrayerTimeOffsets.MIN_OFFSET_MINUTES,
                        modifier = Modifier.semantics {
                            contentDescription = decreaseDescription
                        }
                    ) {
                        Text(stringResource(R.string.offset_decrease))
                    }
                    Text(
                        stringResource(R.string.offset_minutes_value, minutes),
                        modifier = Modifier.padding(horizontal = 4.dp),
                        style = MaterialTheme.typography.titleMedium
                    )
                    TextButton(
                        onClick = { onOffsetsChange(offsets.withOffset(prayer, minutes + 1)) },
                        enabled = minutes < PrayerTimeOffsets.MAX_OFFSET_MINUTES,
                        modifier = Modifier.semantics {
                            contentDescription = increaseDescription
                        }
                    ) {
                        Text(stringResource(R.string.offset_increase))
                    }
                }
            }

            OutlinedButton(
                onClick = { onOffsetsChange(PrayerTimeOffsets.ZERO) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.offsets_reset))
            }
            Text(
                stringResource(R.string.offsets_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (saveFailed) {
                Text(
                    stringResource(R.string.offsets_settings_save_failed),
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
                Button(onClick = { onSave(offsets) }) {
                    Text(stringResource(R.string.notification_save))
                }
            }
        }
    }
}

internal val PrayerTimeOffsetsSaver: Saver<PrayerTimeOffsets?, Any> = listSaver(
    save = { offsets ->
        if (offsets == null) emptyList() else listOf(
            offsets.imsakMinutes,
            offsets.sunriseMinutes,
            offsets.dhuhrMinutes,
            offsets.asrMinutes,
            offsets.maghribMinutes,
            offsets.ishaMinutes
        )
    },
    restore = { saved ->
        if (saved.isEmpty()) null else PrayerTimeOffsets(
                imsakMinutes = saved[0],
                sunriseMinutes = saved[1],
                dhuhrMinutes = saved[2],
                asrMinutes = saved[3],
                maghribMinutes = saved[4],
                ishaMinutes = saved[5]
            )
    }
)

private fun PrayerTimeName.labelStringId(): Int = when (this) {
    PrayerTimeName.IMSAK -> R.string.prayer_imsak
    PrayerTimeName.SUNRISE -> R.string.prayer_sunrise
    PrayerTimeName.DHUHR -> R.string.prayer_dhuhr
    PrayerTimeName.ASR -> R.string.prayer_asr
    PrayerTimeName.MAGHRIB -> R.string.prayer_maghrib
    PrayerTimeName.ISHA -> R.string.prayer_isha
}
