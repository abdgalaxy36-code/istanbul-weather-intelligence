package com.istanbul.weatherintel.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.FilterDrama
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Maps a provider condition description to a weather glyph and tint.
 *
 * Both providers publish free-text conditions (MGM in Turkish, the Met Office
 * in English) plus numeric codes, so the mapping works on the description and
 * falls back to a neutral cloud when nothing matches.
 */
object WeatherIcons {

    /** Amber used for the clear/sunny glyph, as in the approved reference. */
    val SunTint = Color(0xFFF5A623)

    /** The condition families the providers describe. */
    private enum class Kind { SUN, PARTLY, CLOUD, RAIN, SNOW, STORM, FOG }

    private fun classify(condition: String?): Kind {
        val text = condition?.lowercase()?.trim().orEmpty()
        if (text.isEmpty()) return Kind.CLOUD
        return when {
            text.contains("thunder") || text.contains("gök") || text.contains("gok") ||
                text.contains("lightning") || text.contains("yıldırım") || text.contains("yildirim") ->
                Kind.STORM

            text.contains("snow") || text.contains("kar") || text.contains("sleet") ||
                text.contains("ice pellet") || text.contains("hail") || text.contains("dolu") ->
                Kind.SNOW

            text.contains("rain") || text.contains("yağmur") || text.contains("yagmur") ||
                text.contains("sağanak") || text.contains("saganak") ||
                text.contains("shower") || text.contains("drizzle") ->
                Kind.RAIN

            text.contains("fog") || text.contains("sis") || text.contains("pus") ||
                text.contains("haze") || text.contains("duman") || text.contains("mist") ->
                Kind.FOG

            text.contains("partly") || text.contains("parçalı") || text.contains("parcali") ||
                text.contains("az bulutlu") || text.contains("few cloud") ||
                text.contains("scattered") ->
                Kind.PARTLY

            text.contains("overcast") || text.contains("çok bulutlu") || text.contains("cok bulutlu") ||
                text.contains("cloudy") || text.contains("bulutlu") ->
                Kind.CLOUD

            text.contains("clear") || text.contains("sunny") || text.contains("açık") ||
                text.contains("acik") || text.contains("güneş") || text.contains("gunes") ||
                text.contains("güneyli") || text.contains("sun") ->
                Kind.SUN

            else -> Kind.CLOUD
        }
    }

    /** The glyph for a condition description. */
    fun condition(condition: String?): ImageVector = when (classify(condition)) {
        Kind.SUN -> Icons.Filled.WbSunny
        Kind.PARTLY -> Icons.Filled.FilterDrama
        Kind.CLOUD -> Icons.Filled.WbCloudy
        Kind.RAIN -> Icons.Filled.Grain
        Kind.SNOW -> Icons.Filled.AcUnit
        Kind.STORM -> Icons.Filled.Thunderstorm
        Kind.FOG -> Icons.Filled.Visibility
    }

    /** Tint for the glyph so it stays readable on the sky gradient. */
    fun tint(condition: String?): Color = when (classify(condition)) {
        Kind.SUN -> SunTint
        Kind.PARTLY -> Color(0xFF6E8CA8)
        Kind.CLOUD -> Color(0xFF7B93AC)
        Kind.RAIN -> Color(0xFF4A90D9)
        Kind.SNOW -> Color(0xFF7FB3E8)
        Kind.STORM -> Color(0xFFFFB300)
        Kind.FOG -> Color(0xFF8FA3B8)
    }
}
