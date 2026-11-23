package com.istanbul.weatherintel.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

/**
 * Lightweight Compose line chart used for history and forecast-change views.
 * It plots only real stored values - empty series draw nothing.
 */
@Composable
fun LineChart(
    points: List<Pair<Long, Double>>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    fillColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
    unit: String? = null,
) {
    val axisColor = MaterialTheme.colorScheme.outline
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    if (points.size < 2) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(140.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Not enough data to chart",
                style = MaterialTheme.typography.bodySmall,
                color = labelColor,
            )
        }
        return
    }

    val values = points.map { it.second }
    val minValue = values.min()
    val maxValue = values.max()
    val range = (maxValue - minValue).takeIf { it > 0.0001 } ?: 1.0
    val minTime = points.first().first
    val maxTime = points.last().first
    val timeRange = (maxTime - minTime).takeIf { it > 0 } ?: 1L

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = format(maxValue) + (unit?.let { " $it" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
            )
            Text(
                text = format(minValue) + (unit?.let { " $it" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
            )
        }
        Spacer(Modifier.height(4.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
        ) {
            val w = size.width
            val h = size.height
            val path = Path()
            points.forEachIndexed { index, (time, value) ->
                val x = w * ((time - minTime).toFloat() / timeRange.toFloat())
                val y = h - ((value - minValue).toFloat() / range.toFloat()) * (h - 8f) - 4f
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 3f),
            )
            // Fill under the curve.
            val fillPath = Path().apply {
                addPath(path)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(path = fillPath, color = fillColor)
            // Axis line.
            drawLine(
                color = axisColor,
                start = Offset(0f, h),
                end = Offset(w, h),
                strokeWidth = 1.5f,
            )
            // Points.
            points.forEach { (time, value) ->
                val x = w * ((time - minTime).toFloat() / timeRange.toFloat())
                val y = h - ((value - minValue).toFloat() / range.toFloat()) * (h - 8f) - 4f
                drawCircle(color = lineColor, radius = 3.5f, center = Offset(x, y))
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = points.first().first.toShortLabel(),
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
            )
            Text(
                text = points.last().first.toShortLabel(),
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
            )
        }
    }
}

/** Horizontal bar comparison for two provider values. */
@Composable
fun ComparisonBar(
    label: String,
    mgmValue: Double?,
    metOfficeValue: Double?,
    unit: String?,
    modifier: Modifier = Modifier,
) {
    val mgmColor = MaterialTheme.colorScheme.primary
    val moColor = MaterialTheme.colorScheme.secondary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val values = listOfNotNull(mgmValue, metOfficeValue)
    if (values.isEmpty()) {
        Column(modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = labelColor)
            Text(
                "Not provided",
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
            )
        }
        return
    }
    val max = values.max().takeIf { it != 0.0 }?.let { kotlin.math.abs(it) } ?: 1.0

    Column(modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            unit?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = labelColor)
            }
        }
        Spacer(Modifier.height(4.dp))
        BarRow("MGM", mgmValue, max, mgmColor)
        Spacer(Modifier.height(3.dp))
        BarRow("Met Office", metOfficeValue, max, moColor)
    }
}

@Composable
private fun BarRow(label: String, value: Double?, max: Double, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(min = 44.dp, max = 68.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (value != null) {
                val fraction = (kotlin.math.abs(value) / max).toFloat().coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(color),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = value?.let { format(it) } ?: "Not provided",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.widthIn(min = 44.dp, max = 76.dp),
        )
    }
}

private fun format(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString()
    else String.format(Locale.US, "%.1f", v)

private fun Long.toShortLabel(): String {
    val instant = java.time.Instant.ofEpochMilli(this)
    return java.time.format.DateTimeFormatter
        .ofPattern("d MMM HH:mm", Locale.UK)
        .format(instant.atZone(com.istanbul.weatherintel.ui.IstanbulTimes.zone))
}

