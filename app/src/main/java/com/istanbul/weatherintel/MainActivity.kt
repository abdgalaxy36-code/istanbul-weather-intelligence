package com.istanbul.weatherintel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.istanbul.weatherintel.ui.AppRoot
import com.istanbul.weatherintel.ui.WeatherActions
import com.istanbul.weatherintel.ui.theme.IstanbulWeatherTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as WeatherApplication).container

        setContent {
            val viewModel: WeatherViewModel = viewModel(
                factory = WeatherViewModel.factory(container)
            )
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            IstanbulWeatherTheme(
                themeMode = uiState.themeMode,
                headerDarkOverride = uiState.headerDarkMode,
            ) {
                AppRoot(
                    uiState = uiState,
                    actions = WeatherActions(
                        onSelectPage = viewModel::selectPage,
                        onToggleTheme = viewModel::toggleTheme,
                        onSetThemeMode = viewModel::setThemeMode,
                        onCollectNow = viewModel::collectNow,
                        onSetInterval = viewModel::setInterval,
                        onSetAutoCollect = viewModel::setAutoCollect,
                        onSetCollectOnAppOpen = viewModel::setCollectOnAppOpen,
                        onSetMgmEnabled = viewModel::setMgmEnabled,
                        onSetMetOfficeEnabled = viewModel::setMetOfficeEnabled,
                        onSetMetOfficeKey = viewModel::setMetOfficeKey,
                        onClearMetOfficeKey = viewModel::clearMetOfficeKey,
                        onDismissSwipeHint = viewModel::dismissSwipeHint,
                        onSelectStation = viewModel::selectStation,
                        onSetMgmForecastStation = viewModel::setMgmForecastStation,
                        onExport = viewModel::exportData,
                    ),
                )
            }
        }
    }
}

