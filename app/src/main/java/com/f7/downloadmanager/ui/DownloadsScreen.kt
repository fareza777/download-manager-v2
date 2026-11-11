package com.f7.downloadmanager.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.f7.downloadmanager.F7ViewModel
import com.f7.downloadmanager.R
import com.f7.downloadmanager.core.Categories
import com.f7.downloadmanager.core.DStatus
import com.f7.downloadmanager.core.DownloadItem
import com.f7.downloadmanager.core.Fmt
import com.f7.downloadmanager.core.Segment
import com.f7.downloadmanager.ui.theme.LocalSkin
import com.f7.downloadmanager.ui.theme.tabular
import com.f7.downloadmanager.util.Links
import kotlin.math.roundToInt

@Composable
fun DownloadsScreen(
    items: List<DownloadItem>,
    speed: Long,
    history: List<Float>,
    freeBytes: Long,
    doneToday: Int,
    confirmRemove: Boolean,
    onNew: () -> Unit,
    onOpen: (DownloadItem) -> Unit,
    onToggle: (DownloadItem) -> Unit,
    onRemove: (DownloadItem) -> Unit,
    onBatchPause: (Set<Long>) -> Unit,
    onBatchResume: (Set<Long>) -> Unit,
    onBatchRemove: (Set<Long>) -> Unit,
) {
    val skin = LocalSkin.current
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableIntStateOf(0) }
    var selectMode by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var pendingRemove by remember { mutableStateOf<List<DownloadItem>?>(null) }
    val filtered = remember(items, filter, query, sort) {
        val byStatus = when (filter) {
            1 -> items.filter { it.isActive || it.status == DStatus.QUEUED }
            2 -> items.filter { it.status == DStatus.PAUSED }
            3 -> items.filter { it.status == DStatus.COMPLETED }
            4 -> items.filter { it.status == DStatus.FAILED || it.status == DStatus.CANCELED }
            else -> items
        }
        val needle = query.trim().lowercase()
        val searched = if (needle.isEmpty()) byStatus else byStatus.filter {
            it.fileName.lowercase().contains(needle) || it.url.lowercase().contains(needle)
        }
        when (sort) {
            1 -> searched.sortedBy { it.createdAt }
            2 -> searched.sortedBy { it.fileName.lowercase() }
            3 -> searched.sortedByDescending { it.totalBytes }
            else -> searched.sortedByDescending { it.createdAt }
        }
    }
    val active = items.count { it.isActive || it.status == DStatus.QUEUED }
    val sortLabels = listOf(R.string.sort_newest, R.string.sort_oldest, R.string.sort_name, R.string.sort_size)

    fun clearSelection() {
        selected = emptySet()
        selectMode = false
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = androidx.compose.ui.res.stringResource(R.string.downloads_title),
            subtitle = androidx.compose.ui.res.stringResource(R.string.downloads_subtitle),
        ) {
            Pill(
                text = if (speed > 0) "${Fmt.speed(speed)}/s" else androidx.compose.ui.res.stringResource(R.string.speed_idle),
                background = skin.primary.copy(alpha = if (skin.isDark) 0.22f else 0.10f),
                contentColor = skin.primary,
                icon = F7Icons.Gauge,
            )
        }

        SpeedConsole(
            speed = speed,
            history = history,
            active = active,
            activeItem = items.firstOrNull { it.isActive && it.segments.isNotEmpty() },
            freeBytes = freeBytes,
            doneToday = doneToday,
        )

        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        androidx.compose.ui.res.stringResource(R.string.search_hint),
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                textStyle = MaterialTheme.typography.bodySmall,
                singleLine = true,
                shape = CircleShape,
                leadingIcon = {
                    Icon(F7Icons.Search, null, tint = skin.muted, modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconCircleButton(
                            icon = F7Icons.Close,
                            contentDescription = "Clear search",
                            onClick = { query = "" },
                            size = 30.dp,
                            background = Color.Transparent,
                            tint = skin.muted,
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = skin.primary,
                    unfocusedBorderColor = skin.hairline,
                    cursorColor = skin.primary,
                ),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                androidx.compose.ui.res.stringResource(if (selectMode) R.string.select_done else R.string.select_mode),
                style = MaterialTheme.typography.labelLarge,
                color = if (selectMode) skin.primary else skin.muted,
                modifier = Modifier.clickable { if (selectMode) clearSelection() else selectMode = true },
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val labels = listOf(
                R.string.filter_all,
                R.string.filter_active,
                R.string.filter_paused,
                R.string.filter_done,
                R.string.filter_failed,
            )
            labels.forEachIndexed { index, res ->
                FilterPill(
                    text = androidx.compose.ui.res.stringResource(res),
                    selected = filter == index,
                    onClick = { filter = index },
                )
            }
            FilterPill(
                text = androidx.compose.ui.res.stringResource(
                    R.string.sort_label,
                    androidx.compose.ui.res.stringResource(sortLabels[sort]),
                ),
                selected = false,
                onClick = { sort = (sort + 1) % sortLabels.size },
            )
        }
        if (selectMode) {
            Spacer(Modifier.height(8.dp))
            BatchBar(
                count = selected.size,
                onPause = { onBatchPause(selected); clearSelection() },
                onResume = { onBatchResume(selected); clearSelection() },
                onRemove = { if (selected.isNotEmpty()) pendingRemove = items.filter { it.id in selected } },
                onClose = { clearSelection() },
            )
        }
        Spacer(Modifier.height(10.dp))

        if (filtered.isEmpty()) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                EmptyState(
                    icon = F7Icons.Download,
                    title = androidx.compose.ui.res.stringResource(R.string.empty_downloads_title),
                    body = androidx.compose.ui.res.stringResource(R.string.empty_downloads_body),
                ) {
                    PrimaryButton(
                        text = androidx.compose.ui.res.stringResource(R.string.empty_downloads_cta),
                        onClick = onNew,
                        icon = F7Icons.Plus,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(filtered, key = { it.id }) { item ->
                    DownloadRow(
                        item = item,
                        selectMode = selectMode,
                        selected = item.id in selected,
                        onClick = {
                            if (selectMode) {
                                selected = if (item.id in selected) selected - item.id else selected + item.id
                            } else {
                                onOpen(item)
                            }
                        },
                        onToggle = { onToggle(item) },
                        onRequestRemove = {
                            if (confirmRemove) pendingRemove = listOf(item) else onRemove(item)
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    pendingRemove?.let { targets ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            containerColor = skin.card,
            title = {
                Text(
                    androidx.compose.ui.res.stringResource(R.string.remove_title),
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = {
                Text(
                    androidx.compose.ui.res.stringResource(R.string.remove_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                PrimaryButton(
                    text = androidx.compose.ui.res.stringResource(R.string.action_remove),
                    onClick = {
                        targets.forEach { onRemove(it) }
                        pendingRemove = null
                        clearSelection()
                    },
                )
            },
            dismissButton = {
                GhostButton(
                    text = androidx.compose.ui.res.stringResource(R.string.action_cancel),
                    onClick = { pendingRemove = null },
                )
            },
        )
    }
}

@Composable
private fun SpeedConsole(
    speed: Long,
    history: List<Float>,
    active: Int,
    activeItem: DownloadItem?,
    freeBytes: Long,
    doneToday: Int,
) {
    val skin = LocalSkin.current
    val shown by animateFloatAsState(
        targetValue = speed.toFloat(),
        animationSpec = tween(900, easing = LinearEasing),
        label = "smoothSpeed",
    )
    val segments = activeItem?.segments.orEmpty().filter { it.length > 0 }.take(24)

    F7Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        androidx.compose.ui.res.stringResource(R.string.live_speed),
                        style = MaterialTheme.typography.labelMedium,
                        color = skin.muted,
                    )
                    if (active > 0) {
                        Spacer(Modifier.width(8.dp))
                        Pill(
                            text = androidx.compose.ui.res.stringResource(R.string.active_count, active),
                            background = skin.primary.copy(alpha = 0.10f),
                            contentColor = skin.primary,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        Fmt.speed(shown.toLong()),
                        style = MaterialTheme.typography.displayMedium.tabular(),
                        color = skin.ink,
                    )
                    Text(
                        "/s",
                        style = MaterialTheme.typography.labelMedium,
                        color = skin.muted,
                        modifier = Modifier.padding(start = 4.dp, bottom = 7.dp),
                    )
                }
                if (segments.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    SegmentTicks(segments, skin.primary)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                if (active > 0) {
                    Sparkline(history, skin.primary, Modifier.width(88.dp).height(44.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        androidx.compose.ui.res.stringResource(
                            R.string.live_connections,
                            segments.size.coerceAtLeast(active),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = skin.muted,
                    )
                } else {
                    Text(
                        androidx.compose.ui.res.stringResource(R.string.idle_storage, Fmt.bytes(freeBytes)),
                        style = MaterialTheme.typography.labelMedium,
                        color = skin.ink,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        androidx.compose.ui.res.stringResource(R.string.idle_today, doneToday),
                        style = MaterialTheme.typography.labelSmall,
                        color = skin.muted,
                    )
                }
            }
        }
    }
}

@Composable
private fun SegmentTicks(segments: List<Segment>, color: Color) {
    val skin = LocalSkin.current
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        segments.forEach { seg ->
            val target = if (seg.length > 0) (seg.done.toFloat() / seg.length).coerceIn(0f, 1f) else 0f
            val fraction by animateFloatAsState(
                targetValue = target,
                animationSpec = tween(320, easing = FastOutSlowInEasing),
                label = "tickFill",
            )
            Box(
                Modifier
                    .width(4.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(skin.track),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(fraction.coerceAtLeast(0.04f))
                        .background(color),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadRow(
    item: DownloadItem,
    selectMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    onRequestRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val skin = LocalSkin.current
    val haptics = LocalHapticFeedback.current
    val swipe = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> if (item.status != DStatus.COMPLETED && item.status != DStatus.SAVING) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggle()
                }
                SwipeToDismissBoxValue.EndToStart -> onRequestRemove()
                SwipeToDismissBoxValue.Settled -> Unit
            }
            false
        },
    )
    SwipeToDismissBox(
        state = swipe,
        modifier = modifier.fillMaxWidth(),
        backgroundContent = { SwipeBackdrop(swipe.dismissDirection, item.status) },
    ) {
        F7Card(Modifier.fillMaxWidth(), onClick = onClick) {
            Row(
                Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selectMode) {
                    Box(
                        Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(if (selected) skin.primary else skin.track),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            Icon(F7Icons.Check, null, tint = skin.onPrimary, modifier = Modifier.size(15.dp))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                } else {
                    CategoryTile(item.category)
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        item.fileName,
                        style = MaterialTheme.typography.titleSmall,
                        color = skin.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(item.status)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            statusText(item),
                            style = MaterialTheme.typography.bodySmall,
                            color = skin.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        metaText(item),
                        style = MaterialTheme.typography.labelSmall,
                        color = skin.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.status != DStatus.COMPLETED && item.status != DStatus.FAILED && item.status != DStatus.CANCELED) {
                        Spacer(Modifier.height(9.dp))
                        ProgressLine(
                            progress = rowProgress(item),
                            color = when (item.status) {
                                DStatus.PAUSED -> skin.muted
                                DStatus.SAVING -> skin.success
                                else -> skin.primary
                            },
                            height = 5.dp,
                        )
                    }
                }
                if (!selectMode) {
                    Spacer(Modifier.width(8.dp))
                    when (item.status) {
                        DStatus.DOWNLOADING, DStatus.CONNECTING ->
                            IconCircleButton(F7Icons.Pause, "Pause", onToggle)
                        DStatus.PAUSED, DStatus.QUEUED ->
                            IconCircleButton(F7Icons.Play, "Resume", onToggle)
                        DStatus.FAILED, DStatus.CANCELED ->
                            IconCircleButton(F7Icons.Retry, "Retry", onToggle, background = skin.danger.copy(alpha = 0.12f), tint = skin.danger)
                        DStatus.SAVING -> Unit
                        else ->
                            IconCircleButton(F7Icons.Check, "Open", onToggle, background = skin.primary.copy(alpha = 0.12f), tint = skin.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun BatchBar(
    count: Int,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRemove: () -> Unit,
    onClose: () -> Unit,
) {
    val skin = LocalSkin.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(skin.primary.copy(alpha = 0.10f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            androidx.compose.ui.res.stringResource(R.string.selected_count, count),
            style = MaterialTheme.typography.labelLarge,
            color = skin.primary,
            modifier = Modifier.weight(1f),
        )
        IconCircleButton(F7Icons.Pause, "Pause selected", onPause, size = 36.dp, background = skin.card, tint = skin.ink)
        Spacer(Modifier.width(6.dp))
        IconCircleButton(F7Icons.Play, "Resume selected", onResume, size = 36.dp, background = skin.card, tint = skin.ink)
        Spacer(Modifier.width(6.dp))
        IconCircleButton(F7Icons.Trash, "Remove selected", onRemove, size = 36.dp, background = skin.card, tint = skin.danger)
        Spacer(Modifier.width(6.dp))
        IconCircleButton(F7Icons.Close, "Close selection", onClose, size = 36.dp, background = Color.Transparent, tint = skin.muted)
    }
}

private fun rowProgress(item: DownloadItem): Float {
    if (!item.isHls) return item.progress
    val seg = item.segments.firstOrNull() ?: return 0f
    val total = seg.end + 1
    return if (total > 0) (seg.done.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
}

@Composable
private fun metaText(item: DownloadItem): String {
    val host = runCatching { java.net.URI(item.url).host }.getOrNull()
        ?.removePrefix("www.") ?: "—"
    val size = if (item.isHls && item.totalBytes <= 0L) Fmt.bytes(item.downloadedBytes) else Fmt.bytes(item.totalBytes)
    val eta = if (item.status == DStatus.DOWNLOADING && item.speed > 0 && !item.isHls) {
        Fmt.eta((item.totalBytes - item.downloadedBytes).coerceAtLeast(0L), item.speed)
            ?.let { "  ·  " + androidx.compose.ui.res.stringResource(R.string.eta_short, it) }
            .orEmpty()
    } else {
        ""
    }
    val retries = if (item.retries > 0) {
        "  ·  " + androidx.compose.ui.res.stringResource(R.string.retry_count, item.retries)
    } else {
        ""
    }
    return "$host  ·  $size$eta$retries"
}

@Composable
private fun SwipeBackdrop(direction: SwipeToDismissBoxValue, status: DStatus) {
    val skin = LocalSkin.current
    val remove = direction == SwipeToDismissBoxValue.EndToStart
    val resumes = status == DStatus.PAUSED || status == DStatus.QUEUED
    val color = if (remove) skin.danger else skin.primary
    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(22.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 22.dp),
        contentAlignment = if (remove) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (remove) {
                Text(
                    androidx.compose.ui.res.stringResource(R.string.action_remove),
                    style = MaterialTheme.typography.labelLarge,
                    color = skin.danger,
                )
                Spacer(Modifier.width(8.dp))
                Icon(F7Icons.Trash, null, tint = skin.danger, modifier = Modifier.size(20.dp))
            } else {
                Icon(if (resumes) F7Icons.Play else F7Icons.Pause, null, tint = skin.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    androidx.compose.ui.res.stringResource(if (resumes) R.string.action_resume else R.string.action_pause),
                    style = MaterialTheme.typography.labelLarge,
                    color = skin.primary,
                )
            }
        }
    }
}

@Composable
private fun statusText(item: DownloadItem): String {
    return when (item.status) {
        DStatus.CONNECTING -> androidx.compose.ui.res.stringResource(R.string.status_connecting)
        DStatus.QUEUED -> item.error ?: androidx.compose.ui.res.stringResource(R.string.status_queued)
        DStatus.DOWNLOADING -> {
            if (item.isHls) {
                val seg = item.segments.firstOrNull()
                val total = (seg?.end ?: 0L) + 1
                val counters = androidx.compose.ui.res.stringResource(
                    R.string.hls_segments,
                    (seg?.done ?: 0L).toInt(),
                    total.toInt(),
                )
                val speedText = if (item.speed > 0) "  ·  ${Fmt.speed(item.speed)}/s" else ""
                counters + speedText
            } else {
                val speedText = if (item.speed > 0) "  ·  ${Fmt.speed(item.speed)}/s" else ""
                "${Fmt.percent(item.progress)}  ·  ${Fmt.bytes(item.totalBytes)}$speedText"
            }
        }
        DStatus.PAUSED -> "${androidx.compose.ui.res.stringResource(R.string.status_paused)}  ·  ${Fmt.percent(rowProgress(item))}"
        DStatus.SAVING -> androidx.compose.ui.res.stringResource(R.string.status_saving)
        DStatus.COMPLETED -> "${Fmt.bytes(item.totalBytes)}  ·  ${androidx.compose.ui.res.stringResource(R.string.status_completed)}"
        DStatus.FAILED -> (item.error ?: androidx.compose.ui.res.stringResource(R.string.status_failed)) +
            if (item.retries > 0) "  ·  retry ${item.retries}" else ""
        DStatus.CANCELED -> androidx.compose.ui.res.stringResource(R.string.status_canceled)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDownloadSheet(
    initialLink: String,
    vm: F7ViewModel,
    onDismiss: () -> Unit,
) {
    val skin = LocalSkin.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboard = LocalClipboardManager.current
    var text by remember { mutableStateOf(initialLink) }
    var batch by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val threads by vm.settings.threads.collectAsStateWithLifecycle()

    val clipped = remember { Links.extract(clipboard.getText()?.text) }
    val isHls = Links.looksLikeHls(text)
    val isMagnet = text.trim().startsWith("magnet:", true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = skin.card,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(skin.hairline),
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(bottom = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    androidx.compose.ui.res.stringResource(R.string.add_download_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = skin.ink,
                    modifier = Modifier.weight(1f),
                )
                IconCircleButton(F7Icons.Close, "Close", onDismiss, size = 38.dp)
            }
            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    error = null
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(androidx.compose.ui.res.stringResource(R.string.add_url_hint)) },
                textStyle = MaterialTheme.typography.bodyMedium,
                singleLine = !batch,
                minLines = if (batch) 4 else 1,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                trailingIcon = {
                    IconCircleButton(
                        icon = F7Icons.Clipboard,
                        contentDescription = "Paste",
                        onClick = {
                            val value = clipboard.getText()?.text ?: clipped
                            if (!value.isNullOrBlank()) text = value
                        },
                        size = 36.dp,
                        background = Color.Transparent,
                        tint = skin.primary,
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = skin.primary,
                    unfocusedBorderColor = skin.hairline,
                    cursorColor = skin.primary,
                ),
            )
            if (error != null) {
                Spacer(Modifier.height(6.dp))
                Text(error!!, style = MaterialTheme.typography.bodySmall, color = skin.danger)
            }

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    androidx.compose.ui.res.stringResource(R.string.add_category),
                    style = MaterialTheme.typography.labelMedium,
                    color = skin.muted,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    androidx.compose.ui.res.stringResource(R.string.add_batch_toggle),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (batch) skin.primary else skin.muted,
                    modifier = Modifier.clickable {
                        batch = !batch
                        if (batch) category = null
                    },
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterPill(
                    text = "Auto",
                    selected = category == null,
                    onClick = { category = null },
                )
                Categories.all.forEach { cat ->
                    FilterPill(
                        text = categoryLabel(cat),
                        selected = category == cat,
                        onClick = { category = cat },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    androidx.compose.ui.res.stringResource(R.string.add_threads),
                    style = MaterialTheme.typography.labelMedium,
                    color = skin.muted,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "$threads",
                    style = MaterialTheme.typography.titleMedium,
                    color = skin.ink,
                )
            }
            Slider(
                value = threads.toFloat(),
                onValueChange = { vm.setThreads(it.roundToInt()) },
                valueRange = 1f..16f,
                steps = 14,
                colors = SliderDefaults.colors(
                    thumbColor = skin.primary,
                    activeTrackColor = skin.primary,
                    inactiveTrackColor = skin.track,
                ),
            )

            if (isHls) {
                Text(
                    androidx.compose.ui.res.stringResource(R.string.add_hls_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = skin.muted,
                )
            } else if (isMagnet) {
                Text(
                    androidx.compose.ui.res.stringResource(R.string.add_magnet_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = skin.muted,
                )
            } else {
                Text(
                    androidx.compose.ui.res.stringResource(R.string.add_probe_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = skin.muted,
                )
            }

            Spacer(Modifier.height(18.dp))
            val invalidMessage = androidx.compose.ui.res.stringResource(R.string.add_invalid_url)
            val noLinksMessage = androidx.compose.ui.res.stringResource(R.string.add_no_valid_urls)
            PrimaryButton(
                text = androidx.compose.ui.res.stringResource(R.string.add_start),
                onClick = {
                    val value = text.trim()
                    if (value.isEmpty()) {
                        error = invalidMessage
                        return@PrimaryButton
                    }
                    val links = if (batch) Links.all(value).ifEmpty { listOf(value) } else listOf(value)
                    if (links.none { it.startsWith("http", true) || it.startsWith("magnet:", true) }) {
                        error = noLinksMessage
                        return@PrimaryButton
                    }
                    vm.addRaw(value, category)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                icon = F7Icons.Download,
            )
        }
    }
}

@Composable
fun categoryLabel(category: String): String = when (category) {
    Categories.VIDEO -> androidx.compose.ui.res.stringResource(R.string.cat_video)
    Categories.AUDIO -> androidx.compose.ui.res.stringResource(R.string.cat_audio)
    Categories.IMAGE -> androidx.compose.ui.res.stringResource(R.string.cat_image)
    Categories.DOCUMENT -> androidx.compose.ui.res.stringResource(R.string.cat_document)
    Categories.ARCHIVE -> androidx.compose.ui.res.stringResource(R.string.cat_archive)
    Categories.APK -> androidx.compose.ui.res.stringResource(R.string.cat_apk)
    else -> androidx.compose.ui.res.stringResource(R.string.cat_other)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsSheet(
    item: DownloadItem,
    onDismiss: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
    onRemove: () -> Unit,
) {
    val skin = LocalSkin.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = skin.card,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(skin.hairline),
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryTile(item.category, size = 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        item.fileName,
                        style = MaterialTheme.typography.titleLarge,
                        color = skin.ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        statusText(item),
                        style = MaterialTheme.typography.bodySmall,
                        color = skin.muted,
                        maxLines = 1,
                    )
                }
                IconCircleButton(F7Icons.Close, "Close", onDismiss, size = 38.dp)
            }

            Spacer(Modifier.height(16.dp))
            ProgressLine(item.progress, skin.primary, height = 8.dp)
            Spacer(Modifier.height(14.dp))

            Row(Modifier.fillMaxWidth()) {
                StatBlock(
                    label = androidx.compose.ui.res.stringResource(R.string.details_size),
                    value = Fmt.bytes(item.totalBytes),
                    modifier = Modifier.weight(1f),
                )
                StatBlock(
                    label = androidx.compose.ui.res.stringResource(R.string.details_speed),
                    value = if (item.speed > 0) "${Fmt.speed(item.speed)}/s" else "—",
                    modifier = Modifier.weight(1f),
                )
                StatBlock(
                    label = androidx.compose.ui.res.stringResource(R.string.details_connections),
                    value = if (item.isHls) "HLS" else "${item.segments.count { it.length > 0 }.coerceAtLeast(1)}",
                    modifier = Modifier.weight(1f),
                )
            }

            if (item.segments.size > 1) {
                Spacer(Modifier.height(16.dp))
                Text(
                    androidx.compose.ui.res.stringResource(R.string.details_segments),
                    style = MaterialTheme.typography.labelSmall,
                    color = skin.muted,
                )
                Spacer(Modifier.height(7.dp))
                SegmentsBar(item.segments.filter { it.length > 0 }, skin.primary)
            }

            if (!item.error.isNullOrBlank()) {
                Spacer(Modifier.height(14.dp))
                Text(item.error!!, style = MaterialTheme.typography.bodySmall, color = skin.danger)
            }

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (item.status) {
                    DStatus.DOWNLOADING, DStatus.CONNECTING ->
                        PrimaryButton(androidx.compose.ui.res.stringResource(R.string.action_pause), onPause, Modifier.weight(1f), F7Icons.Pause)
                    DStatus.PAUSED, DStatus.QUEUED ->
                        PrimaryButton(androidx.compose.ui.res.stringResource(R.string.action_resume), onResume, Modifier.weight(1f), F7Icons.Play)
                    DStatus.COMPLETED ->
                        PrimaryButton(androidx.compose.ui.res.stringResource(R.string.action_open), onOpen, Modifier.weight(1f), F7Icons.External)
                    else ->
                        PrimaryButton(androidx.compose.ui.res.stringResource(R.string.action_retry), onRetry, Modifier.weight(1f), F7Icons.Retry)
                }
                GhostButton(
                    text = androidx.compose.ui.res.stringResource(R.string.action_remove),
                    onClick = onRemove,
                    icon = F7Icons.Trash,
                )
            }

            Spacer(Modifier.height(18.dp))
            Hairline()
            Spacer(Modifier.height(12.dp))
            KeyValueRow(androidx.compose.ui.res.stringResource(R.string.details_url), item.url)
            if (item.isHls) {
                KeyValueRow(androidx.compose.ui.res.stringResource(R.string.details_status), "HLS")
            }
            KeyValueRow(
                androidx.compose.ui.res.stringResource(R.string.details_saved_to),
                item.location ?: androidx.compose.ui.res.stringResource(R.string.settings_folder_app),
            )
            KeyValueRow(
                androidx.compose.ui.res.stringResource(R.string.details_created),
                Fmt.date(item.createdAt),
            )
            if (!item.checksum.isNullOrBlank()) {
                KeyValueRow(androidx.compose.ui.res.stringResource(R.string.details_checksum), item.checksum!!)
            }
            Text(
                androidx.compose.ui.res.stringResource(R.string.details_retry_hint),
                style = MaterialTheme.typography.bodySmall,
                color = skin.muted,
                modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.height(12.dp))
            GhostButton(
                text = androidx.compose.ui.res.stringResource(R.string.action_copy_link),
                onClick = onCopy,
                icon = F7Icons.Clipboard,
            )
        }
    }
}

