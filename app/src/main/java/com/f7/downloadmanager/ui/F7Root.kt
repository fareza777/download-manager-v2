package com.f7.downloadmanager.ui

import android.app.Activity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.f7.downloadmanager.F7ViewModel
import com.f7.downloadmanager.R
import com.f7.downloadmanager.core.DStatus
import com.f7.downloadmanager.core.DownloadItem
import com.f7.downloadmanager.ui.theme.LocalSkin

@Composable
fun F7Root(vm: F7ViewModel) {
    val skin = LocalSkin.current
    val context = LocalContext.current
    val view = LocalView.current
    val items by vm.items.collectAsStateWithLifecycle()
    val speed by vm.speed.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val homeUrl by vm.settings.browserHome.collectAsStateWithLifecycle()
    val pendingShare by vm.pendingShare.collectAsStateWithLifecycle()
    val confirmRemove by vm.settings.confirmRemove.collectAsStateWithLifecycle()
    val ambient by vm.settings.ambient.collectAsStateWithLifecycle()
    val onboardingDone by vm.settings.onboardingDone.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var browserUrl by rememberSaveable { mutableStateOf("") }
    var addOpen by rememberSaveable { mutableStateOf(false) }
    var prefill by rememberSaveable { mutableStateOf("") }
    var detailId by rememberSaveable { mutableStateOf<Long?>(null) }
    val freeBytes = remember(tab, items.size) { vm.storageFreeBytes() }
    val doneToday = remember(items) {
        val dayAgo = System.currentTimeMillis() - 86_400_000L
        items.count { it.status == DStatus.COMPLETED && (it.completedAt ?: 0L) >= dayAgo }
    }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(pendingShare) {
        val link = pendingShare
        if (!link.isNullOrBlank()) {
            prefill = link
            addOpen = true
            vm.consumeShare()
        }
    }
    LaunchedEffect(skin.isDark) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !skin.isDark
        controller.isAppearanceLightNavigationBars = !skin.isDark
    }

    if (!onboardingDone) {
        OnboardingScreen(onDone = { vm.completeOnboarding() })
        return
    }

    val detail = items.firstOrNull { it.id == detailId }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(skin.bgTop, skin.bgBottom))),
    ) {
        AmbientBackdrop(Modifier.matchParentSize(), skin.primary, enabled = tab == 0 && ambient)
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> DownloadsScreen(
                        items = items,
                        speed = speed,
                        history = history,
                        freeBytes = freeBytes,
                        doneToday = doneToday,
                        confirmRemove = confirmRemove,
                        onNew = {
                            prefill = ""
                            addOpen = true
                        },
                        onOpen = { detailId = it.id },
                        onToggle = { item ->
                            when (item.status) {
                                DStatus.DOWNLOADING, DStatus.CONNECTING -> vm.pause(item.id)
                                DStatus.PAUSED, DStatus.QUEUED -> vm.resume(item.id)
                                DStatus.FAILED, DStatus.CANCELED -> vm.retry(item.id)
                                DStatus.COMPLETED -> {
                                    if (item.location != null) openDownloaded(context, item)
                                    else vm.notify(context.getString(R.string.files_no_file))
                                }
                                else -> Unit
                            }
                        },
                        onRemove = { vm.remove(it) },
                        onBatchPause = { ids -> vm.batchPause(ids) },
                        onBatchResume = { ids -> vm.batchResume(ids) },
                        onBatchRemove = { ids -> vm.batchRemove(ids) },
                    )
                    1 -> BrowserScreen(
                        vm = vm,
                        homeUrl = homeUrl ?: "https://duckduckgo.com/",
                        initialUrl = browserUrl,
                        onUrlChange = { browserUrl = it },
                        onDownload = { url, headers, mime ->
                            vm.addLink(url, headers)
                            vm.notify(context.getString(R.string.browser_download_detected))
                        },
                        onMessage = { vm.notify(it) },
                    )
                    2 -> FilesScreen(
                        items = items,
                        freeBytes = freeBytes,
                        vm = vm,
                        onOpen = { item ->
                            if (item.location != null) openDownloaded(context, item)
                            else vm.notify(context.getString(R.string.files_no_file))
                        },
                    )
                    else -> SettingsScreen(vm)
                }

                if (tab == 0 && items.isNotEmpty()) {
                    NewDownloadButton(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 20.dp, bottom = 14.dp),
                        onClick = {
                            prefill = ""
                            addOpen = true
                        },
                    )
                }
                SnackbarHost(
                    hostState = snackbar,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 16.dp)
                        .widthIn(max = 268.dp),
                )
            }
            BottomBar(
                selected = tab,
                onSelect = { tab = it },
            )
        }
    }

    if (addOpen) {
        AddDownloadSheet(
            initialLink = prefill,
            vm = vm,
            onDismiss = { addOpen = false },
        )
    }

    if (detail != null && !addOpen) {
        DetailsSheet(
            item = detail,
            onDismiss = { detailId = null },
            onPause = { vm.pause(detail.id) },
            onResume = { vm.resume(detail.id) },
            onRetry = { vm.retry(detail.id) },
            onOpen = {
                if (detail.location != null) openDownloaded(context, detail)
                else vm.notify(context.getString(R.string.files_no_file))
            },
            onCopy = {
                val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("link", detail.url))
                vm.notify(context.getString(R.string.toast_copied))
            },
            onRemove = {
                vm.remove(detail)
                detailId = null
            },
        )
    }
}

@Composable
private fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    val skin = LocalSkin.current
    val entries = listOf(
        Triple("Downloads", F7Icons.Download, R.string.nav_downloads),
        Triple("Browse", F7Icons.Browser, R.string.nav_browser),
        Triple("Library", F7Icons.Folder, R.string.nav_files),
        Triple("Settings", F7Icons.Sliders, R.string.nav_settings),
    )
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .navigationBarsPadding()
            .shadow(14.dp, RoundedCornerShape(24.dp), clip = false, ambientColor = Color(0x14000000), spotColor = Color(0x1A000000))
            .clip(RoundedCornerShape(24.dp))
            .background(skin.card)
            .border(1.dp, skin.hairline, RoundedCornerShape(24.dp))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        entries.forEachIndexed { index, entry ->
            val active = index == selected
            val background by animateColorAsState(
                targetValue = if (active) skin.primary.copy(alpha = if (skin.isDark) 0.22f else 0.10f) else Color.Transparent,
                animationSpec = tween(180),
                label = "navBackground",
            )
            val contentColor by animateColorAsState(
                targetValue = if (active) skin.primary else skin.muted,
                animationSpec = tween(180),
                label = "navContent",
            )
            val iconScale by animateFloatAsState(
                targetValue = if (active) 1f else 0.9f,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 700f),
                label = "navScale",
            )
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(background)
                    .clickable { onSelect(index) }
                    .padding(vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    entry.second,
                    contentDescription = androidx.compose.ui.res.stringResource(entry.third),
                    tint = contentColor,
                    modifier = Modifier.size(21.dp).graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    androidx.compose.ui.res.stringResource(entry.third),
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun NewDownloadButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val skin = LocalSkin.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 900f),
        label = "fabPress",
    )
    val haptics = LocalHapticFeedback.current
    Row(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(18.dp, CircleShape, clip = false, ambientColor = Color(0x22000000), spotColor = Color(0x33000000))
            .clip(CircleShape)
            .background(skin.primary)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(F7Icons.Plus, null, tint = skin.onPrimary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            androidx.compose.ui.res.stringResource(R.string.new_download),
            style = MaterialTheme.typography.labelLarge,
            color = skin.onPrimary,
        )
    }
}

