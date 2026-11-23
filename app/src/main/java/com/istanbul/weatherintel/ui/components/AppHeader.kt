package com.istanbul.weatherintel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.istanbul.weatherintel.AppPage
import com.istanbul.weatherintel.ui.theme.LocalIsDarkTheme
import com.istanbul.weatherintel.ui.theme.SkyPalette

/**
 * Shared header in the approved visual style: a sky-gradient band with the
 * location identity on the left and the three primary navigation buttons, the
 * theme toggle and Collect Now as circular controls on the right.
 *
 * The three navigation controls live in the header itself, so they are visible
 * on every primary page and while a detail section is open. There is no bottom
 * navigation bar and no drawer.
 */
@Composable
fun AppHeader(
    darkMode: Boolean,
    collecting: Boolean,
    currentPage: AppPage,
    onSelectPage: (AppPage) -> Unit,
    onToggleTheme: () -> Unit,
    onCollectNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = LocalIsDarkTheme.current
    val sky = if (dark) SkyPalette.dark else SkyPalette.light

    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .background(
                Brush.verticalGradient(
                    0.0f to sky[0],
                    0.55f to sky[0],
                    1.0f to sky[1],
                )
            )
            .padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Istanbul",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Text(
                    text = "Weather Intelligence",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.88f),
                )
            }

            // Primary navigation - always visible, never duplicated at the bottom.
            AppPage.entries.forEach { page ->
                HeaderCircleButton(
                    icon = page.icon(),
                    contentDescription = "${page.name} tab",
                    selected = page == currentPage,
                    onClick = { onSelectPage(page) },
                )
            }

            HeaderCircleButton(
                icon = if (dark) Icons.Filled.WbSunny else Icons.Filled.NightsStay,
                contentDescription = "Toggle light or dark theme",
                selected = false,
                onClick = onToggleTheme,
            )

            HeaderCircleButton(
                icon = Icons.Filled.Refresh,
                contentDescription = "Collect now",
                selected = false,
                enabled = !collecting,
                onClick = onCollectNow,
                showProgress = collecting,
            )
        }
    }
}

/** Icon for each primary page. */
private fun AppPage.icon(): ImageVector = when (this) {
    AppPage.HOME -> Icons.Filled.Home
    AppPage.COMPARE -> Icons.Filled.Balance
    AppPage.SETTINGS -> Icons.Filled.Settings
}

/**
 * Circular translucent control used across the header.
 *
 * The selected page is drawn as a solid light disc with the accent-coloured
 * glyph and an underline, which stays legible in both themes.
 */
@Composable
private fun HeaderCircleButton(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    showProgress: Boolean = false,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(start = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(
                    if (selected) Color.White.copy(alpha = 0.95f)
                    else Color.White.copy(alpha = 0.22f)
                )
                .clickable(enabled = enabled, onClick = onClick)
                .semantics { this.contentDescription = contentDescription },
            contentAlignment = Alignment.Center,
        ) {
            if (showProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Color.White,
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = when {
                        selected -> MaterialTheme.colorScheme.primary
                        enabled -> Color.White
                        else -> Color.White.copy(alpha = 0.45f)
                    },
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        // The active page is underlined, mirroring the approved reference.
        Box(
            modifier = Modifier
                .padding(top = 3.dp)
                .width(18.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    if (selected) Color.White else Color.Transparent
                )
        )
    }
}

/** Consistent rounded Material 3 surface used across all UI sections. */
@Composable
fun WeatherCard(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        content = {
            Column(Modifier.padding(16.dp), content = content)
        },
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(bottom = 8.dp),
    )
}

@Composable
fun StatusPill(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Wraps long values naturally on narrow screens instead of clipping them. */
@Composable
fun LabelValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 36.dp)
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
    }
}
