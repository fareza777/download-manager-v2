package com.f7.downloadmanager.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.f7.downloadmanager.R
import com.f7.downloadmanager.ui.theme.LocalSkin
import kotlinx.coroutines.launch

private data class OnboardingPage(val title: Int, val body: Int)

private val pages = listOf(
    OnboardingPage(R.string.onboarding_1_title, R.string.onboarding_1_body),
    OnboardingPage(R.string.onboarding_2_title, R.string.onboarding_2_body),
    OnboardingPage(R.string.onboarding_3_title, R.string.onboarding_3_body),
    OnboardingPage(R.string.onboarding_4_title, R.string.onboarding_4_body),
)

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val skin = LocalSkin.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(pageCount = { pages.size })
    var notificationsGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) notificationsGranted = true }

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(skin.bgTop, skin.bgBottom)))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(skin.ink),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("F7", style = MaterialTheme.typography.labelMedium, color = skin.accent)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    androidx.compose.ui.res.stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleSmall,
                    color = skin.ink,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                androidx.compose.ui.res.stringResource(R.string.onboarding_skip),
                style = MaterialTheme.typography.labelLarge,
                color = skin.muted,
                modifier = Modifier.clickable(onClick = onDone).padding(8.dp),
            )
        }

        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) { index ->
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PageArt(index)
                Spacer(Modifier.height(28.dp))
                Text(
                    androidx.compose.ui.res.stringResource(pages[index].title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = skin.ink,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    androidx.compose.ui.res.stringResource(pages[index].body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = skin.muted,
                    textAlign = TextAlign.Center,
                )
                if (index == pages.lastIndex) {
                    Spacer(Modifier.height(18.dp))
                    if (notificationsGranted) {
                        Pill(
                            text = androidx.compose.ui.res.stringResource(R.string.onboarding_notified),
                            background = skin.primary.copy(alpha = 0.10f),
                            contentColor = skin.primary,
                            icon = F7Icons.Check,
                        )
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        GhostButton(
                            text = androidx.compose.ui.res.stringResource(R.string.onboarding_notify),
                            onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                            icon = F7Icons.Shield,
                        )
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            pages.indices.forEach { index ->
                val active = pager.currentPage == index
                val width by animateDpAsState(
                    targetValue = if (active) 26.dp else 8.dp,
                    animationSpec = tween(220),
                    label = "dot",
                )
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .size(width = width, height = 8.dp)
                        .clip(CircleShape)
                        .background(if (active) skin.primary else skin.track),
                )
            }
        }

        PrimaryButton(
            text = androidx.compose.ui.res.stringResource(
                if (pager.currentPage == pages.lastIndex) R.string.onboarding_start else R.string.onboarding_next,
            ),
            onClick = {
                if (pager.currentPage == pages.lastIndex) {
                    onDone()
                } else {
                    scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            icon = if (pager.currentPage == pages.lastIndex) F7Icons.Check else F7Icons.Forward,
        )
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun PageArt(index: Int) {
    val skin = LocalSkin.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(238.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(skin.card),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize().padding(26.dp)) {
            when (index) {
                0 -> {
                    val plate = Size(size.width * 0.62f, size.height * 0.78f)
                    val left = (size.width - plate.width) / 2f
                    val top = (size.height - plate.height) / 2f
                    drawRoundRect(
                        color = skin.ink,
                        topLeft = Offset(left, top),
                        size = plate,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(28.dp.toPx()),
                    )
                    drawRoundRect(
                        color = skin.accent,
                        topLeft = Offset(left + 16.dp.toPx(), top + plate.height - 22.dp.toPx()),
                        size = Size(plate.width - 32.dp.toPx(), 6.dp.toPx()),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
                    )
                    drawCircle(
                        color = skin.accent,
                        radius = 5.dp.toPx(),
                        center = Offset(left + plate.width - 24.dp.toPx(), top + 24.dp.toPx()),
                    )
                }
                1 -> {
                    val count = 8
                    val gap = 6.dp.toPx()
                    val tickW = (size.width - gap * (count - 1)) / count
                    repeat(count) { i ->
                        val frac = listOf(0.9f, 0.7f, 1f, 0.55f, 0.85f, 0.65f, 0.95f, 0.75f)[i]
                        val h = size.height * 0.68f
                        val x = i * (tickW + gap)
                        drawRoundRect(
                            color = skin.track,
                            topLeft = Offset(x, (size.height - h) / 2f),
                            size = Size(tickW, h),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(tickW / 2f),
                        )
                        drawRoundRect(
                            color = if (i % 3 == 0) skin.accent else skin.primary,
                            topLeft = Offset(x, (size.height - h) / 2f + h * (1f - frac)),
                            size = Size(tickW, h * frac),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(tickW / 2f),
                        )
                    }
                }
                2 -> {
                    val frame = Size(size.width * 0.78f, size.height * 0.66f)
                    val left = (size.width - frame.width) / 2f
                    val top = (size.height - frame.height) / 2f
                    drawRoundRect(
                        color = skin.cardAlt,
                        topLeft = Offset(left, top),
                        size = frame,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()),
                    )
                    drawRoundRect(
                        color = skin.primary,
                        topLeft = Offset(left, top),
                        size = Size(frame.width, 8.dp.toPx()),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
                    )
                    repeat(3) { i ->
                        drawRoundRect(
                            color = skin.hairline,
                            topLeft = Offset(left + 14.dp.toPx(), top + 22.dp.toPx() + i * 12.dp.toPx()),
                            size = Size(frame.width * (0.66f - i * 0.12f), 5.dp.toPx()),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.5.dp.toPx()),
                        )
                    }
                    val cx = left + frame.width - 24.dp.toPx()
                    val cy = top + frame.height - 24.dp.toPx()
                    drawCircle(skin.primary, 15.dp.toPx(), Offset(cx, cy))
                    drawLine(
                        color = skin.onPrimary,
                        start = Offset(cx, cy - 6.dp.toPx()),
                        end = Offset(cx, cy + 6.dp.toPx()),
                        strokeWidth = 2.5.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = skin.onPrimary,
                        start = Offset(cx - 5.dp.toPx(), cy + 1.dp.toPx()),
                        end = Offset(cx, cy + 6.dp.toPx()),
                        strokeWidth = 2.5.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = skin.onPrimary,
                        start = Offset(cx + 5.dp.toPx(), cy + 1.dp.toPx()),
                        end = Offset(cx, cy + 6.dp.toPx()),
                        strokeWidth = 2.5.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
                else -> {
                    val cx = size.width / 2f
                    val cy = size.height * 0.42f
                    drawCircle(skin.primary.copy(alpha = 0.10f), size.minDimension * 0.30f, Offset(cx, cy))
                    drawCircle(skin.primary, size.minDimension * 0.19f, Offset(cx, cy), style = Stroke(4.dp.toPx()))
                    drawRoundRect(
                        color = skin.primary,
                        topLeft = Offset(cx - 12.dp.toPx(), cy - 4.dp.toPx()),
                        size = Size(24.dp.toPx(), 18.dp.toPx()),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()),
                    )
                    drawCircle(skin.card, 3.dp.toPx(), Offset(cx, cy + 5.dp.toPx()))
                    val barTop = size.height - 26.dp.toPx()
                    drawRoundRect(
                        color = skin.track,
                        topLeft = Offset(10.dp.toPx(), barTop),
                        size = Size(size.width - 20.dp.toPx(), 8.dp.toPx()),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
                    )
                    drawRoundRect(
                        color = skin.accent,
                        topLeft = Offset(10.dp.toPx(), barTop),
                        size = Size((size.width - 20.dp.toPx()) * 0.58f, 8.dp.toPx()),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
                    )
                }
            }
        }
    }
}
