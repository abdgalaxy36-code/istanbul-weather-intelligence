package com.istanbul.weatherintel.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.istanbul.weatherintel.AppPage
import com.istanbul.weatherintel.WeatherUiState
import com.istanbul.weatherintel.data.prefs.ThemeMode
import com.istanbul.weatherintel.ui.components.AppHeader
import com.istanbul.weatherintel.ui.compare.CompareScreen
import com.istanbul.weatherintel.ui.home.HomeScreen
import com.istanbul.weatherintel.ui.screens.AppSection
import com.istanbul.weatherintel.ui.screens.CollectionHistoryScreen
import com.istanbul.weatherintel.ui.screens.CurrentWeatherScreen
import com.istanbul.weatherintel.ui.screens.DashboardScreen
import com.istanbul.weatherintel.ui.screens.DataExportScreen
import com.istanbul.weatherintel.ui.screens.DetailHeader
import com.istanbul.weatherintel.ui.screens.ForecastChangeScreen
import com.istanbul.weatherintel.ui.screens.ForecastScreen
import com.istanbul.weatherintel.ui.screens.HistoricalScreen
import com.istanbul.weatherintel.ui.screens.WarningsScreen
import com.istanbul.weatherintel.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.distinctUntilChanged

/** Existing ViewModel actions are kept as the UI's only mutation boundary. */
data class WeatherActions(
    val onSelectPage: (AppPage) -> Unit,
    val onToggleTheme: () -> Unit,
    val onSetThemeMode: (ThemeMode) -> Unit,
    val onCollectNow: () -> Unit,
    val onSetInterval: (Int) -> Unit,
    val onSetAutoCollect: (Boolean) -> Unit,
    val onSetCollectOnAppOpen: (Boolean) -> Unit,
    val onSetMgmEnabled: (Boolean) -> Unit,
    val onSetMetOfficeEnabled: (Boolean) -> Unit,
    val onSetMetOfficeKey: (String) -> Unit,
    val onClearMetOfficeKey: () -> Unit,
    val onDismissSwipeHint: () -> Unit,
    val onSelectStation: (Long) -> Unit,
    val onSetMgmForecastStation: (Int) -> Unit = {},
    val onExport: (com.istanbul.weatherintel.ExportFormat, String?, Long?, Long?) -> Unit,
    val onOpenSection: (AppSection) -> Unit = {},
)

/**
 * The app shell owns the three-page pager, top navigation, shared header and
 * detail overlay. It intentionally contains no bottom navigation or drawer.
 */
@Composable
fun AppRoot(
    uiState: WeatherUiState,
    actions: WeatherActions,
) {
    val pages = AppPage.entries
    val pagerState = rememberPagerState(
        initialPage = uiState.page.ordinal.coerceIn(pages.indices),
        pageCount = { pages.size },
    )
    val latestPage by rememberUpdatedState(uiState.page)
    var openSection by remember { mutableStateOf<AppSection?>(null) }

    // A detail route replaces only the pager content; shared header/navigation
    // remain mounted. Detail callbacks do not create a second navigation stack.
    val pageActions = remember(actions) {
        actions.copy(onOpenSection = { section -> openSection = section })
    }

    BackHandler(enabled = openSection != null) {
        openSection = null
    }

    // ViewModel -> pager synchronization (e.g. restored state or external event).
    LaunchedEffect(uiState.page) {
        val target = uiState.page.ordinal.coerceIn(pages.indices)
        if (pagerState.currentPage != target) {
            pagerState.animateScrollToPage(target)
        }
    }

    // Pager -> ViewModel synchronization for user swipes. Comparing against the
    // latest state prevents the nav buttons and pager from feeding each other.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { index ->
                val selectedPage = pages[index.coerceIn(pages.indices)]
                if (selectedPage != latestPage) actions.onSelectPage(selectedPage)
            }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.fillMaxSize()) {
            AppHeader(
                darkMode = uiState.headerDarkMode
                    ?: (uiState.themeMode == ThemeMode.DARK),
                collecting = uiState.collecting,
                currentPage = pages[pagerState.currentPage.coerceIn(pages.indices)],
                onSelectPage = { page ->
                    openSection = null
                    actions.onSelectPage(page)
                },
                onToggleTheme = actions.onToggleTheme,
                onCollectNow = actions.onCollectNow,
            )

            Box(
                Modifier
                    .weight(1f)
                    .navigationBarsPadding()
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    key = { page -> pages[page].name },
                ) { pageIndex ->
                    when (pages[pageIndex]) {
                        AppPage.HOME -> HomeScreen(uiState, pageActions)
                        AppPage.COMPARE -> CompareScreen(uiState, pageActions)
                        AppPage.SETTINGS -> SettingsScreen(uiState, pageActions)
                    }
                }

                openSection?.let { section ->
                    Column(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                    ) {
                        DetailHeader(section = section, onBack = { openSection = null })
                        SectionContent(section = section, uiState = uiState)
                    }
                }

                if (uiState.settings?.firstUseSwipeHintShown == false && openSection == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 12.dp),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        SwipeHint(
                            currentPage = pagerState.currentPage.coerceIn(pages.indices),
                            pageCount = pages.size,
                            onDismiss = actions.onDismissSwipeHint,
                        )
                    }
                }
            }
        }
    }
}

/** The selected detail section is rendered from the same existing UI state. */
@Composable
private fun SectionContent(
    section: AppSection,
    uiState: WeatherUiState,
) {
    when (section) {
        AppSection.DASHBOARD -> DashboardScreen(uiState)
        AppSection.CURRENT -> CurrentWeatherScreen(uiState)
        AppSection.HOURLY -> ForecastScreen(
            uiState = uiState,
            product = "hourly",
            title = "Hourly Forecast",
            mgmValues = uiState.mgmHourlyValues,
            metOfficeValues = uiState.metOfficeHourlyValues,
            mgmRunAt = uiState.mgmHourlyRun?.issuedAt,
            metOfficeRunAt = uiState.metOfficeHourlyRun?.issuedAt,
            metOfficeRowCount = uiState.metOfficeHourlyRun?.rowCount ?: 0,
        )
        AppSection.THREE_HOURLY -> ForecastScreen(
            uiState = uiState,
            product = "three-hourly",
            title = "Three-hourly Forecast",
            mgmValues = emptyList(),
            metOfficeValues = uiState.metOfficeThreeHourlyValues,
            mgmRunAt = null,
            metOfficeRunAt = uiState.metOfficeThreeHourlyRun?.issuedAt,
            metOfficeRowCount = uiState.metOfficeThreeHourlyRun?.rowCount ?: 0,
        )
        AppSection.DAILY -> ForecastScreen(
            uiState = uiState,
            product = "daily",
            title = "Daily Forecast",
            mgmValues = uiState.mgmDailyValues,
            metOfficeValues = uiState.metOfficeDailyValues,
            mgmRunAt = uiState.mgmDailyRun?.issuedAt,
            metOfficeRunAt = uiState.metOfficeDailyRun?.issuedAt,
            metOfficeRowCount = uiState.metOfficeDailyRun?.rowCount ?: 0,
        )
        AppSection.HISTORICAL -> HistoricalScreen(uiState)
        AppSection.FORECAST_CHANGE -> ForecastChangeScreen(uiState)
        AppSection.WARNINGS -> WarningsScreen(uiState)
        AppSection.COLLECTION_HISTORY -> CollectionHistoryScreen(uiState)
        AppSection.DATA_EXPORT -> DataExportScreen(uiState)
    }
}

/**
 * Small, dismissible first-use hint in the approved visual style: arrows
 * around a hand glyph, the instruction, and a row of page dots.
 *
 * It is a hint, not a navigation control - the three header buttons remain the
 * only page navigation.
 */
@Composable
private fun SwipeHint(
    currentPage: Int,
    pageCount: Int,
    onDismiss: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.ChevronLeft,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Icon(
                    imageVector = Icons.Filled.Swipe,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Swipe left or right to move between pages",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(pageCount) { index ->
                    Box(
                        modifier = Modifier
                            .size(if (index == currentPage) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == currentPage) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline
                                }
                            )
                    )
                }
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.padding(top = 2.dp),
            ) {
                Text(
                    text = "Got it",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
