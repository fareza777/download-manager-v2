package com.f7.downloadmanager.ui

import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.isActive

/**
 * A very cheap ambient glow drawn by the GPU through AGSL.
 *
 * Guard rails, so this can never become the reason the app janks or drains a battery:
 * - API 33+ only; older devices keep the static gradient.
 * - Disabled in battery saver and when the system animation scale is zero.
 * - Redraws at ~30 fps, never on the main-thread layout pass.
 * - Any shader compile error falls back to the plain gradient (no crash).
 */
private const val AGSL = """
uniform float2 uSize;
uniform float uTime;
uniform float3 uColor;

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / max(uSize, float2(1.0));
    float2 p = uv - float2(0.5, 0.34);
    float d = length(p * float2(1.05, 1.5));
    float glow = smoothstep(1.0, 0.0, d);
    float sweep = sin(uv.x * 2.4 + uTime) * 0.5 + 0.5;
    float alpha = glow * 0.085 + (1.0 - uv.y) * sweep * 0.014;
    return half4(half3(uColor), half(alpha));
}
"""

private const val FRAME_INTERVAL_NANOS = 33_000_000L

@Composable
fun AmbientBackdrop(modifier: Modifier = Modifier, color: Color, enabled: Boolean = true) {
    if (!enabled) return
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val context = LocalContext.current
    val powerManager = remember { context.getSystemService(PowerManager::class.java) }
    val animationsOff = remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
    if (animationsOff || powerManager?.isPowerSaveMode == true) return

    val shader = remember(color) {
        runCatching {
            RuntimeShader(AGSL).apply {
                setFloatUniform("uColor", color.red, color.green, color.blue)
            }
        }.getOrNull()
    } ?: return

    val paint = remember { Paint().apply { isAntiAlias = false } }
    var phase by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        var last = 0L
        while (isActive) {
            withFrameNanos { now ->
                if (now - last >= FRAME_INTERVAL_NANOS) {
                    last = now
                    phase = ((now / 1_000_000_000.0) % 42.0 / 42.0).toFloat() * 6.283f
                }
            }
        }
    }

    Box(
        modifier.drawBehind {
            shader.setFloatUniform("uSize", size.width, size.height)
            shader.setFloatUniform("uTime", phase)
            paint.shader = shader
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
            }
        },
    )
}
