package com.f7.downloadmanager.ui

import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.f7.downloadmanager.BuildConfig
import com.f7.downloadmanager.F7ViewModel
import com.f7.downloadmanager.R
import com.f7.downloadmanager.core.Fmt
import com.f7.downloadmanager.ui.theme.LocalSkin
import com.f7.downloadmanager.ui.theme.Presets
import java.util.Calendar
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(vm: F7ViewModel) {
    val skin = LocalSkin.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val theme by vm.settings.theme.collectAsStateWithLifecycle()
    val mode by vm.settings.darkMode.collectAsStateWithLifecycle()
    val threads by vm.settings.threads.collectAsStateWithLifecycle()
    val concurrent by vm.settings.concurrent.collectAsStateWithLifecycle()
    val speedLimit by vm.settings.speedLimitKbps.collectAsStateWithLifecycle()
    val autoResume by vm.settings.autoResume.collectAsStateWithLifecycle()
    val notifications by vm.settings.notifications.collectAsStateWithLifecycle()
    val treeLabel by vm.settings.treeLabel.collectAsStateWithLifecycle()
    val scheduleAt by vm.settings.scheduleAt.collectAsStateWithLifecycle()
    val wifiOnly by vm.settings.wifiOnly.collectAsStateWithLifecycle()
    val chargingOnly by vm.settings.chargingOnly.collectAsStateWithLifecycle()
    val autoRetry by vm.settings.autoRetry.collectAsStateWithLifecycle()
    val maxRetries by vm.settings.maxRetries.collectAsStateWithLifecycle()
    val progressNotif by vm.settings.progressNotifications.collectAsStateWithLifecycle()
    val confirmRemove by vm.settings.confirmRemove.collectAsStateWithLifecycle()
    val ambient by vm.settings.ambient.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var stagingBytes by remember(tab) { mutableStateOf(vm.stagingBytes()) }
    val freeBytes = remember(tab) { vm.storageFreeBytes() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            val label = runCatching { DocumentFile.fromTreeUri(context, uri)?.name }.getOrNull()
                ?: uri.lastPathSegment
                ?: "Folder"
            vm.setTree(uri.toString(), label)
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = androidx.compose.ui.res.stringResource(R.string.settings_title),
            subtitle = null,
        )
        SegmentTabs(
            options = listOf(
                androidx.compose.ui.res.stringResource(R.string.settings_tab_look),
                androidx.compose.ui.res.stringResource(R.string.settings_tab_speed),
                androidx.compose.ui.res.stringResource(R.string.settings_tab_storage),
                androidx.compose.ui.res.stringResource(R.string.settings_tab_about),
            ),
            selected = tab,
            onSelect = { tab = it },
            // The tabs use larger labels than the default option row.
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 90.dp),
        ) {
            when (tab) {
                0 -> {
                    F7Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                androidx.compose.ui.res.stringResource(R.string.settings_theme),
                                style = MaterialTheme.typography.titleSmall,
                                color = skin.ink,
                            )
                            Spacer(Modifier.height(12.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Presets.chunked(3).forEach { row ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        row.forEach { preset ->
                                            val selected = (theme ?: "JADE") == preset.id
                                            Column(
                                                Modifier.weight(1f).clickable { vm.setTheme(preset.id) },
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                            ) {
                                                Box(
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .height(52.dp)
                                                        .clip(RoundedCornerShape(14.dp))
                                                        .background(Brush.linearGradient(listOf(preset.primary, preset.accent)))
                                                        .then(
                                                            if (selected) Modifier.border(2.dp, skin.ink, RoundedCornerShape(14.dp))
                                                            else Modifier,
                                                        ),
                                                )
                                                Spacer(Modifier.height(6.dp))
                                                Text(
                                                    androidx.compose.ui.res.stringResource(preset.labelRes),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = if (selected) skin.ink else skin.muted,
                                                    maxLines = 1,
                                                )
                                            }
                                        }
                                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    F7Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                androidx.compose.ui.res.stringResource(R.string.settings_mode),
                                style = MaterialTheme.typography.titleSmall,
                                color = skin.ink,
                            )
                            Spacer(Modifier.height(10.dp))
                            SegmentTabs(
                                options = listOf(
                                    androidx.compose.ui.res.stringResource(R.string.mode_system),
                                    androidx.compose.ui.res.stringResource(R.string.mode_light),
                                    androidx.compose.ui.res.stringResource(R.string.mode_dark),
                                ),
                                selected = when (mode) {
                                    "light" -> 1
                                    "dark" -> 2
                                    else -> 0
                                },
                                onSelect = { vm.setDarkMode(listOf("system", "light", "dark")[it]) },
                            )
                            Spacer(Modifier.height(14.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Aa", style = MaterialTheme.typography.displayMedium, color = skin.ink)
                                Spacer(Modifier.width(14.dp))
                                Column {
                                    Text(
                                        androidx.compose.ui.res.stringResource(R.string.look_preview_title),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = skin.ink,
                                    )
                                    Text(
                                        androidx.compose.ui.res.stringResource(R.string.look_preview_body),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = skin.muted,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    F7Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            ToggleRow(
                                title = androidx.compose.ui.res.stringResource(R.string.settings_ambient),
                                subtitle = androidx.compose.ui.res.stringResource(R.string.settings_ambient_desc),
                                checked = ambient,
                                onChange = { vm.setAmbient(it) },
                            )
                            Hairline()
                            ToggleRow(
                                title = androidx.compose.ui.res.stringResource(R.string.settings_confirm_remove),
                                subtitle = androidx.compose.ui.res.stringResource(R.string.settings_confirm_remove_desc),
                                checked = confirmRemove,
                                onChange = { vm.setConfirmRemove(it) },
                            )
                        }
                    }
                }
                1 -> {
                    F7Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        androidx.compose.ui.res.stringResource(R.string.settings_connections),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = skin.ink,
                                    )
                                    Text(
                                        androidx.compose.ui.res.stringResource(R.string.settings_connections_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = skin.muted,
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Pill("$threads", background = skin.primary.copy(alpha = 0.12f), contentColor = skin.primary)
                            }
                            Slider(
                                value = threads.toFloat(),
                                onValueChange = { vm.setThreads(it.roundToInt()) },
                                valueRange = 1f..16f,
                                steps = 14,
                                colors = sliderColors(),
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                androidx.compose.ui.res.stringResource(R.string.settings_concurrent),
                                style = MaterialTheme.typography.titleSmall,
                                color = skin.ink,
                            )
                            Text(
                                androidx.compose.ui.res.stringResource(R.string.settings_concurrent_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = skin.muted,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                                (1..4).forEach { value ->
                                    FilterPill("$value", selected = concurrent == value, onClick = { vm.setConcurrent(value) })
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    F7Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        androidx.compose.ui.res.stringResource(R.string.settings_speed_limit),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = skin.ink,
                                    )
                                    Text(
                                        androidx.compose.ui.res.stringResource(R.string.settings_speed_limit_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = skin.muted,
                                    )
                                }
                                Pill(
                                    text = if (speedLimit == 0) androidx.compose.ui.res.stringResource(R.string.settings_speed_unlimited) else "$speedLimit KB/s",
                                    background = skin.primary.copy(alpha = 0.12f),
                                    contentColor = skin.primary,
                                )
                            }
                            Row(
                                Modifier.padding(top = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                val options = listOf(0, 512, 1024, 2048, 5120)
                                options.forEach { option ->
                                    FilterPill(
                                        text = if (option == 0) "Off" else if (option >= 1024) "${option / 1024}M" else "${option}K",
                                        selected = speedLimit == option,
                                        onClick = { vm.setSpeedLimit(option) },
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    F7Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            ToggleRow(
                                title = androidx.compose.ui.res.stringResource(R.string.settings_auto_resume),
                                subtitle = androidx.compose.ui.res.stringResource(R.string.settings_auto_resume_desc),
                                checked = autoResume,
                                onChange = { vm.setAutoResume(it) },
                            )
                            Hairline()
                            ToggleRow(
                                title = androidx.compose.ui.res.stringResource(R.string.settings_notifications),
                                subtitle = androidx.compose.ui.res.stringResource(R.string.settings_notifications_desc),
                                checked = notifications,
                                onChange = { vm.setNotifications(it) },
                            )
                            Hairline()
                            ToggleRow(
                                title = androidx.compose.ui.res.stringResource(R.string.settings_progress_notif),
                                subtitle = androidx.compose.ui.res.stringResource(R.string.settings_progress_notif_desc),
                                checked = progressNotif,
                                onChange = { vm.setProgressNotifications(it) },
                            )
                            Hairline()
                            ToggleRow(
                                title = androidx.compose.ui.res.stringResource(R.string.settings_wifi_only),
                                subtitle = androidx.compose.ui.res.stringResource(R.string.settings_wifi_only_desc),
                                checked = wifiOnly,
                                onChange = { vm.setWifiOnly(it) },
                            )
                            Hairline()
                            ToggleRow(
                                title = androidx.compose.ui.res.stringResource(R.string.settings_charging_only),
                                subtitle = androidx.compose.ui.res.stringResource(R.string.settings_charging_only_desc),
                                checked = chargingOnly,
                                onChange = { vm.setChargingOnly(it) },
                            )
                            Hairline()
                            ToggleRow(
                                title = androidx.compose.ui.res.stringResource(R.string.settings_auto_retry),
                                subtitle = androidx.compose.ui.res.stringResource(R.string.settings_auto_retry_desc),
                                checked = autoRetry,
                                onChange = { vm.setAutoRetry(it) },
                            )
                            if (autoRetry) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    androidx.compose.ui.res.stringResource(R.string.settings_max_retries),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = skin.ink,
                                )
                                Text(
                                    androidx.compose.ui.res.stringResource(R.string.settings_max_retries_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = skin.muted,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                                    listOf(0, 3, 5, 10).forEach { value ->
                                        FilterPill(
                                            text = if (value == 0) "Off" else "$value",
                                            selected = maxRetries == value,
                                            onClick = { vm.setMaxRetries(value) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    F7Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                androidx.compose.ui.res.stringResource(R.string.settings_folder),
                                style = MaterialTheme.typography.titleSmall,
                                color = skin.ink,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                treeLabel ?: androidx.compose.ui.res.stringResource(R.string.settings_folder_app),
                                style = MaterialTheme.typography.bodyMedium,
                                color = skin.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                androidx.compose.ui.res.stringResource(R.string.settings_folder_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = skin.muted,
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PrimaryButton(
                                    text = androidx.compose.ui.res.stringResource(R.string.settings_folder_change),
                                    onClick = { picker.launch(null) },
                                    icon = F7Icons.Folder,
                                )
                                if (treeLabel != null) {
                                    GhostButton(
                                        text = androidx.compose.ui.res.stringResource(R.string.action_clear),
                                        onClick = { vm.setTree(null, null) },
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    F7Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        androidx.compose.ui.res.stringResource(R.string.settings_staging),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = skin.ink,
                                    )
                                    Text(
                                        androidx.compose.ui.res.stringResource(R.string.settings_staging_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = skin.muted,
                                    )
                                }
                                Pill(
                                    text = Fmt.bytes(stagingBytes),
                                    background = skin.primary.copy(alpha = 0.12f),
                                    contentColor = skin.primary,
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${androidx.compose.ui.res.stringResource(R.string.settings_storage_free)} · ${Fmt.bytes(freeBytes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = skin.muted,
                                    modifier = Modifier.weight(1f),
                                )
                                GhostButton(
                                    text = androidx.compose.ui.res.stringResource(R.string.settings_staging_clear),
                                    onClick = {
                                        vm.clearStaging()
                                        stagingBytes = vm.stagingBytes()
                                    },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    F7Card(Modifier.fillMaxWidth(), onClick = {
                        val calendar = Calendar.getInstance()
                        TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                val target = Calendar.getInstance().apply {
                                    set(Calendar.HOUR_OF_DAY, hour)
                                    set(Calendar.MINUTE, minute)
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }
                                if (target.timeInMillis <= System.currentTimeMillis()) {
                                    target.add(Calendar.DAY_OF_YEAR, 1)
                                }
                                vm.schedule(target.timeInMillis)
                            },
                            calendar.get(Calendar.HOUR_OF_DAY),
                            calendar.get(Calendar.MINUTE),
                            true,
                        ).show()
                    }) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(skin.primary.copy(alpha = 0.10f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                androidx.compose.material3.Icon(F7Icons.Clock, null, tint = skin.primary, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    androidx.compose.ui.res.stringResource(R.string.settings_schedule),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = skin.ink,
                                )
                                Text(
                                    if (scheduleAt > 0) Fmt.date(scheduleAt) else androidx.compose.ui.res.stringResource(R.string.settings_schedule_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = skin.muted,
                                )
                            }
                            if (scheduleAt > 0) {
                                IconCircleButton(F7Icons.Close, "Cancel schedule", { vm.schedule(0L) }, size = 36.dp)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    F7Card(Modifier.fillMaxWidth(), onClick = {
                        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            runCatching { context.startActivity(intent) }
                        }
                    }) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(skin.primary.copy(alpha = 0.10f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                androidx.compose.material3.Icon(F7Icons.Shield, null, tint = skin.primary, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    androidx.compose.ui.res.stringResource(R.string.settings_battery),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = skin.ink,
                                )
                                Text(
                                    androidx.compose.ui.res.stringResource(R.string.settings_battery_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = skin.muted,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    F7Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    androidx.compose.ui.res.stringResource(R.string.settings_history),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = skin.ink,
                                )
                                Text(
                                    androidx.compose.ui.res.stringResource(R.string.settings_clear_history_body),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = skin.muted,
                                )
                            }
                            GhostButton(
                                text = androidx.compose.ui.res.stringResource(R.string.action_clear),
                                onClick = { vm.clearFinished() },
                            )
                        }
                    }
                }
                else -> {
                    F7Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(skin.ink),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("F7", style = MaterialTheme.typography.titleMedium, color = skin.bgTop)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        androidx.compose.ui.res.stringResource(R.string.app_name),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = skin.ink,
                                    )
                                    Text(
                                        "${androidx.compose.ui.res.stringResource(R.string.about_version)} ${BuildConfig.VERSION_NAME}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = skin.muted,
                                    )
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            Text(
                                androidx.compose.ui.res.stringResource(R.string.about_privacy),
                                style = MaterialTheme.typography.bodyMedium,
                                color = skin.ink,
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                androidx.compose.ui.res.stringResource(R.string.about_attribution),
                                style = MaterialTheme.typography.bodySmall,
                                color = skin.muted,
                            )
                            Spacer(Modifier.height(14.dp))
                            Hairline()
                            ToggleRow(
                                title = androidx.compose.ui.res.stringResource(R.string.settings_onboarding),
                                subtitle = androidx.compose.ui.res.stringResource(R.string.settings_onboarding_desc),
                                checked = false,
                                onChange = { vm.showOnboarding() },
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Pill(
                                    text = "${androidx.compose.ui.res.stringResource(R.string.about_license)} · GPL-3.0",
                                    background = skin.cardAlt,
                                    contentColor = skin.ink,
                                    icon = F7Icons.Lock,
                                )
                                GhostButton(
                                    text = androidx.compose.ui.res.stringResource(R.string.about_license_text),
                                    onClick = {
                                        runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.gnu.org/licenses/gpl-3.0.html"))
                                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                            )
                                        }
                                    },
                                    icon = F7Icons.External,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun sliderColors() = SliderDefaults.colors(
    thumbColor = LocalSkin.current.primary,
    activeTrackColor = LocalSkin.current.primary,
    inactiveTrackColor = LocalSkin.current.track,
)

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val skin = LocalSkin.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = skin.ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = skin.muted)
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = skin.primary,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = skin.track,
                uncheckedBorderColor = skin.hairline,
            ),
        )
    }
}
