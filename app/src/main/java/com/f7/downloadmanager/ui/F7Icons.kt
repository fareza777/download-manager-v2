package com.f7.downloadmanager.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object F7Icons {
    val Download: ImageVector = stroke("Download") {
        moveTo(12f, 3.6f); lineTo(12f, 15f)
        moveTo(7.2f, 10.4f); lineTo(12f, 15.2f); lineTo(16.8f, 10.4f)
        moveTo(4.6f, 20f); lineTo(19.4f, 20f)
    }

    val Browser: ImageVector = stroke("Browser") {
        moveTo(3.6f, 5.4f); lineTo(20.4f, 5.4f); lineTo(20.4f, 18.6f); lineTo(3.6f, 18.6f); close()
        moveTo(3.6f, 9.6f); lineTo(20.4f, 9.6f)
        moveTo(6.6f, 7.5f); lineTo(6.62f, 7.5f)
        moveTo(9.6f, 7.5f); lineTo(9.62f, 7.5f)
    }

    val Folder: ImageVector = stroke("Folder") {
        moveTo(3.6f, 6.8f); lineTo(9.2f, 6.8f); lineTo(11.2f, 9.2f); lineTo(20.4f, 9.2f)
        lineTo(20.4f, 18.8f); lineTo(3.6f, 18.8f); close()
    }

    val Sliders: ImageVector = stroke("Sliders") {
        moveTo(4f, 7.6f); lineTo(20f, 7.6f)
        moveTo(4f, 12f); lineTo(20f, 12f)
        moveTo(4f, 16.4f); lineTo(20f, 16.4f)
        moveTo(8.6f, 7.6f); lineTo(8.62f, 7.6f)
        moveTo(15f, 12f); lineTo(15.02f, 12f)
        moveTo(10.4f, 16.4f); lineTo(10.42f, 16.4f)
    }

    val Play: ImageVector = filled("Play") {
        moveTo(8f, 5.2f); lineTo(19f, 12f); lineTo(8f, 18.8f); close()
    }

    val Pause: ImageVector = filled("Pause") {
        moveTo(7f, 5f); lineTo(10.2f, 5f); lineTo(10.2f, 19f); lineTo(7f, 19f); close()
        moveTo(13.8f, 5f); lineTo(17f, 5f); lineTo(17f, 19f); lineTo(13.8f, 19f); close()
    }

    val Close: ImageVector = stroke("Close") {
        moveTo(6f, 6f); lineTo(18f, 18f)
        moveTo(18f, 6f); lineTo(6f, 18f)
    }

    val Retry: ImageVector = stroke("Retry") {
        moveTo(19.4f, 12f)
        curveTo(19.4f, 16.1f, 16.1f, 19.4f, 12f, 19.4f)
        curveTo(7.9f, 19.4f, 4.6f, 16.1f, 4.6f, 12f)
        curveTo(4.6f, 7.9f, 7.9f, 4.6f, 12f, 4.6f)
        curveTo(14.9f, 4.6f, 17.4f, 6.3f, 18.6f, 8.8f)
        moveTo(19.2f, 4.4f); lineTo(19.2f, 9.4f); lineTo(14.2f, 9.4f)
    }

    val Check: ImageVector = stroke("Check") {
        moveTo(5f, 12.6f); lineTo(9.8f, 17.4f); lineTo(19f, 6.8f)
    }

    val Plus: ImageVector = stroke("Plus") {
        moveTo(12f, 5f); lineTo(12f, 19f)
        moveTo(5f, 12f); lineTo(19f, 12f)
    }

    val Clipboard: ImageVector = stroke("Clipboard") {
        moveTo(8.4f, 5.4f); lineTo(5.6f, 5.4f); lineTo(5.6f, 19.6f); lineTo(18.4f, 19.6f); lineTo(18.4f, 5.4f); lineTo(15.6f, 5.4f)
        moveTo(9f, 3.6f); lineTo(15f, 3.6f); lineTo(15f, 7.2f); lineTo(9f, 7.2f); close()
    }

    val Search: ImageVector = stroke("Search") {
        moveTo(10.6f, 4.4f); lineTo(10.6f, 4.4f)
        curveTo(7.2f, 4.4f, 4.4f, 7.2f, 4.4f, 10.6f)
        curveTo(4.4f, 14f, 7.2f, 16.8f, 10.6f, 16.8f)
        curveTo(14f, 16.8f, 16.8f, 14f, 16.8f, 10.6f)
        curveTo(16.8f, 7.2f, 14f, 4.4f, 10.6f, 4.4f); close()
        moveTo(15.2f, 15.2f); lineTo(20f, 20f)
    }

    val Trash: ImageVector = stroke("Trash") {
        moveTo(4.6f, 6.6f); lineTo(19.4f, 6.6f)
        moveTo(9.4f, 3.8f); lineTo(14.6f, 3.8f); lineTo(14.6f, 6.6f); lineTo(9.4f, 6.6f); close()
        moveTo(6.4f, 6.6f); lineTo(7.4f, 20f); lineTo(16.6f, 20f); lineTo(17.6f, 6.6f)
        moveTo(10.2f, 10f); lineTo(10.4f, 16.6f)
        moveTo(13.8f, 10f); lineTo(13.6f, 16.6f)
    }

    val Share: ImageVector = stroke("Share") {
        moveTo(12f, 15.4f); lineTo(12f, 3.8f)
        moveTo(8.2f, 7.4f); lineTo(12f, 3.6f); lineTo(15.8f, 7.4f)
        moveTo(5.2f, 13.4f); lineTo(5.2f, 20f); lineTo(18.8f, 20f); lineTo(18.8f, 13.4f)
    }

    val More: ImageVector = filled("More") {
        moveTo(12f, 5.4f)
        curveTo(12.7f, 5.4f, 13.2f, 5.9f, 13.2f, 6.6f)
        curveTo(13.2f, 7.3f, 12.7f, 7.8f, 12f, 7.8f)
        curveTo(11.3f, 7.8f, 10.8f, 7.3f, 10.8f, 6.6f)
        curveTo(10.8f, 5.9f, 11.3f, 5.4f, 12f, 5.4f); close()
        moveTo(12f, 10.8f)
        curveTo(12.7f, 10.8f, 13.2f, 11.3f, 13.2f, 12f)
        curveTo(13.2f, 12.7f, 12.7f, 13.2f, 12f, 13.2f)
        curveTo(11.3f, 13.2f, 10.8f, 12.7f, 10.8f, 12f)
        curveTo(10.8f, 11.3f, 11.3f, 10.8f, 12f, 10.8f); close()
        moveTo(12f, 16.2f)
        curveTo(12.7f, 16.2f, 13.2f, 16.7f, 13.2f, 17.4f)
        curveTo(13.2f, 18.1f, 12.7f, 18.6f, 12f, 18.6f)
        curveTo(11.3f, 18.6f, 10.8f, 18.1f, 10.8f, 17.4f)
        curveTo(10.8f, 16.7f, 11.3f, 16.2f, 12f, 16.2f); close()
    }

    val Gauge: ImageVector = stroke("Gauge") {
        moveTo(4.2f, 17f)
        curveTo(4.2f, 12.7f, 7.7f, 9.2f, 12f, 9.2f)
        curveTo(16.3f, 9.2f, 19.8f, 12.7f, 19.8f, 17f)
        moveTo(12f, 17f); lineTo(15.8f, 12.4f)
        moveTo(4.2f, 17f); lineTo(19.8f, 17f)
    }

    val Storage: ImageVector = stroke("Storage") {
        moveTo(4.4f, 6.2f); lineTo(19.6f, 6.2f); lineTo(19.6f, 11.4f); lineTo(4.4f, 11.4f); close()
        moveTo(4.4f, 13.4f); lineTo(19.6f, 13.4f); lineTo(19.6f, 18.6f); lineTo(4.4f, 18.6f); close()
        moveTo(8f, 8.8f); lineTo(8.02f, 8.8f)
        moveTo(8f, 16f); lineTo(8.02f, 16f)
    }

    val Shield: ImageVector = stroke("Shield") {
        moveTo(12f, 3.4f); lineTo(19.4f, 6.2f); lineTo(19.4f, 12f)
        curveTo(19.4f, 16.2f, 16.2f, 19.4f, 12f, 20.6f)
        curveTo(7.8f, 19.4f, 4.6f, 16.2f, 4.6f, 12f)
        lineTo(4.6f, 6.2f); close()
        moveTo(9f, 12f); lineTo(11.4f, 14.4f); lineTo(15.4f, 9.6f)
    }

    val Clock: ImageVector = stroke("Clock") {
        moveTo(12f, 3.8f)
        curveTo(16.5f, 3.8f, 20.2f, 7.5f, 20.2f, 12f)
        curveTo(20.2f, 16.5f, 16.5f, 20.2f, 12f, 20.2f)
        curveTo(7.5f, 20.2f, 3.8f, 16.5f, 3.8f, 12f)
        curveTo(3.8f, 7.5f, 7.5f, 3.8f, 12f, 3.8f); close()
        moveTo(12f, 7.6f); lineTo(12f, 12.2f); lineTo(15.4f, 14.2f)
    }

    val Back: ImageVector = stroke("Back") {
        moveTo(19f, 12f); lineTo(5f, 12f)
        moveTo(11f, 6f); lineTo(5f, 12f); lineTo(11f, 18f)
    }

    val Forward: ImageVector = stroke("Forward") {
        moveTo(5f, 12f); lineTo(19f, 12f)
        moveTo(13f, 6f); lineTo(19f, 12f); lineTo(13f, 18f)
    }

    val ChevronDown: ImageVector = stroke("ChevronDown") {
        moveTo(6f, 9.5f); lineTo(12f, 15.5f); lineTo(18f, 9.5f)
    }

    val File: ImageVector = stroke("File") {
        moveTo(6f, 3.6f); lineTo(14f, 3.6f); lineTo(18.4f, 8f); lineTo(18.4f, 20.4f); lineTo(6f, 20.4f); close()
        moveTo(14f, 3.6f); lineTo(14f, 8f); lineTo(18.4f, 8f)
    }

    val Sparkle: ImageVector = filled("Sparkle") {
        moveTo(12f, 3.2f); lineTo(13.8f, 9.6f); lineTo(20.4f, 12f); lineTo(13.8f, 14.4f)
        lineTo(12f, 20.8f); lineTo(10.2f, 14.4f); lineTo(3.6f, 12f); lineTo(10.2f, 9.6f); close()
    }

    val Lock: ImageVector = stroke("Lock") {
        moveTo(6.4f, 10.6f); lineTo(17.6f, 10.6f); lineTo(17.6f, 20f); lineTo(6.4f, 20f); close()
        moveTo(8.6f, 10.6f); lineTo(8.6f, 7.8f)
        curveTo(8.6f, 5.9f, 10f, 4.2f, 12f, 4.2f)
        curveTo(14f, 4.2f, 15.4f, 5.9f, 15.4f, 7.8f)
        lineTo(15.4f, 10.6f)
    }

    val External: ImageVector = stroke("External") {
        moveTo(14f, 4.4f); lineTo(19.6f, 4.4f); lineTo(19.6f, 10f)
        moveTo(19.6f, 4.4f); lineTo(11.4f, 12.6f)
        moveTo(16.6f, 13.8f); lineTo(16.6f, 19.6f); lineTo(4.4f, 19.6f); lineTo(4.4f, 7.4f); lineTo(10.2f, 7.4f)
    }
}

private fun stroke(name: String, block: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathFillType = PathFillType.NonZero,
            pathBuilder = block,
        )
    }.build()

private fun filled(name: String, block: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black), pathBuilder = block)
    }.build()
