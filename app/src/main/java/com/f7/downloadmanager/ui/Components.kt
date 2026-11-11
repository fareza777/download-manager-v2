package com.f7.downloadmanager.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.LocalIndication
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import com.f7.downloadmanager.core.Categories
import com.f7.downloadmanager.core.DStatus
import com.f7.downloadmanager.ui.theme.LocalSkin
import kotlin.math.max

@Composable
fun F7Card(
    modifier: Modifier = Modifier,
    color: Color = LocalSkin.current.card,
    shape: RoundedCornerShape = RoundedCornerShape(22.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val skin = LocalSkin.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 900f),
        label = "cardPress",
    )
    val base = modifier
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .shadow(10.dp, shape, clip = false, ambientColor = Color(0x12000000), spotColor = Color(0x18000000))
        .clip(shape)
        .background(color)
    Surface(
        modifier = if (onClick != null) {
            base.clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick,
            )
        } else {
            base
        },
        shape = shape,
        color = color,
        border = BorderStroke(1.dp, skin.hairline),
    ) {
        Column(content = content)
    }
}

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineLarge, color = LocalSkin.current.ink, maxLines = 1)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalSkin.current.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) trailing()
    }
}

@Composable
fun Sparkline(values: List<Float>, color: Color, modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    val idle = values.isEmpty() || values.all { it <= 0.001f }
    Canvas(modifier) {
        if (idle) return@Canvas
        if (values.size < 2) {
            drawLine(
                color = color.copy(alpha = 0.25f),
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
            return@Canvas
        }
        val peak = max(values.maxOrNull() ?: 1f, 0.15f)
        val stepX = size.width / (values.size - 1).toFloat()
        val path = Path()
        var lastX = 0f
        var lastY = size.height
        values.forEachIndexed { index, value ->
            val x = index * stepX
            val y = size.height - (value / peak) * (size.height * 0.82f) - 3f
            if (index == 0) {
                path.moveTo(x, y)
            } else {
                val midX = (lastX + x) / 2f
                path.cubicTo(midX, lastY, midX, y, x, y)
            }
            lastX = x
            lastY = y
        }
        val fillPath = Path().apply {
            addPath(path)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(listOf(color.copy(alpha = 0.22f), Color.Transparent)),
        )
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawCircle(color = color, radius = 3.2.dp.toPx(), center = Offset(lastX, lastY))
        drawCircle(color = skin.card, radius = 1.4.dp.toPx(), center = Offset(lastX, lastY))
    }
}

@Composable
fun ProgressLine(progress: Float, color: Color, height: Dp = 6.dp, track: Color? = null) {
    val skin = LocalSkin.current
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "progressFill",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(track ?: skin.track),
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .height(height)
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(color, color.copy(alpha = 0.72f)))),
        )
    }
}

@Composable
fun SegmentsBar(segments: List<com.f7.downloadmanager.core.Segment>, color: Color, modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        segments.forEach { seg ->
            val target = if (seg.length > 0) (seg.done.toFloat() / seg.length).coerceIn(0f, 1f) else 0f
            val fraction by animateFloatAsState(
                targetValue = target,
                animationSpec = tween(320, easing = FastOutSlowInEasing),
                label = "segmentFill",
            )
            Box(
                Modifier
                    .weight(1f)
                    .height(7.dp)
                    .clip(CircleShape)
                    .background(skin.track),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(7.dp)
                        .clip(CircleShape)
                        .background(color),
                )
            }
        }
    }
}

@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    background: Color = LocalSkin.current.cardAlt,
    contentColor: Color = LocalSkin.current.ink,
    icon: ImageVector? = null,
) {
    Row(
        modifier
            .clip(CircleShape)
            .background(background)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = contentColor, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = contentColor, maxLines = 1)
    }
}

@Composable
fun FilterPill(text: String, selected: Boolean, onClick: () -> Unit) {
    val skin = LocalSkin.current
    Surface(
        shape = CircleShape,
        color = if (selected) skin.primary else skin.card,
        border = if (selected) null else BorderStroke(1.dp, skin.hairline),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) skin.onPrimary else skin.muted,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            maxLines = 1,
        )
    }
}

@Composable
fun IconCircleButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = LocalSkin.current.cardAlt,
    tint: Color = LocalSkin.current.ink,
    size: Dp = 42.dp,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(size * 0.48f))
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val skin = LocalSkin.current
    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(skin.primary)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = skin.onPrimary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = skin.onPrimary, maxLines = 1)
    }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val skin = LocalSkin.current
    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(skin.cardAlt)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = skin.ink, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(7.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = skin.ink, maxLines = 1)
    }
}

@Composable
fun CategoryTile(category: String, modifier: Modifier = Modifier, size: Dp = 46.dp) {
    val skin = LocalSkin.current
    val (icon, tint) = when (category) {
        Categories.VIDEO -> F7Icons.Play to skin.primary
        Categories.AUDIO -> F7Icons.Sparkle to skin.primary
        Categories.IMAGE -> F7Icons.File to skin.primary
        Categories.DOCUMENT -> F7Icons.File to skin.primary
        Categories.ARCHIVE -> F7Icons.Storage to skin.primary
        Categories.APK -> F7Icons.Shield to skin.primary
        else -> F7Icons.Download to skin.primary
    }
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .background(tint.copy(alpha = if (skin.isDark) 0.18f else 0.10f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.44f))
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = LocalSkin.current.muted,
        modifier = modifier.padding(horizontal = 4.dp),
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
fun StatBlock(label: String, value: String, modifier: Modifier = Modifier, emphasize: Boolean = false) {
    val skin = LocalSkin.current
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = skin.muted, maxLines = 1)
        Spacer(Modifier.height(3.dp))
        Text(
            value,
            style = if (emphasize) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyMedium,
            color = skin.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    action: (@Composable () -> Unit)? = null,
) {
    val skin = LocalSkin.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(skin.primary.copy(alpha = 0.09f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = skin.primary, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = skin.ink)
        Spacer(Modifier.height(6.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = skin.muted,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(18.dp))
            action()
        }
    }
}

@Composable
fun StatusDot(status: DStatus) {
    val skin = LocalSkin.current
    val color = when (status) {
        DStatus.DOWNLOADING, DStatus.CONNECTING, DStatus.SAVING -> skin.success
        DStatus.COMPLETED -> skin.primary
        DStatus.PAUSED -> skin.muted
        DStatus.FAILED -> skin.danger
        else -> skin.muted
    }
    Box(Modifier.size(8.dp).clip(CircleShape).background(color))
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(LocalSkin.current.hairline))
}

@Composable
fun SegmentTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(skin.cardAlt)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, option ->
            val active = index == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(13.dp))
                    .background(if (active) skin.card else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    option,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) skin.ink else skin.muted,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun MiniBar(progress: Float, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.height(5.dp).fillMaxWidth().clip(CircleShape).background(LocalSkin.current.track)) {
        Box(Modifier.fillMaxSize(fraction = progress.coerceIn(0f, 1f)).clip(CircleShape).background(color))
    }
}

@Composable
fun KeyValueRow(label: String, value: String, modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    Row(modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = skin.muted, modifier = Modifier.width(110.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = skin.ink,
            modifier = Modifier.weight(1f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
