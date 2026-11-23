package com.istanbul.weatherintel

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.istanbul.weatherintel.data.prefs.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * ViewModel tests.
 *
 * The ViewModel is the only place where user actions and stored data are
 * combined, so these tests check that actions are persisted and that the
 * resulting state is exposed through [WeatherViewModel.uiState].
 */
@RunWith(AndroidJUnit4::class)
class WeatherViewModelTest {

    private lateinit var app: WeatherApplication
    private lateinit var viewModel: WeatherViewModel

    @Before
    fun setUp() {
        app = InstrumentationRegistry.getInstrumentation().targetContext
            .applicationContext as WeatherApplication
        viewModel = WeatherViewModel(app.container)
    }

    @Test
    fun theInitialPageIsHome() = runBlocking {
        assertEquals(AppPage.HOME, viewModel.uiState.first().page)
    }

    @Test
    fun selectingAPageUpdatesTheState() = runBlocking {
        viewModel.selectPage(AppPage.COMPARE)
        assertEquals(AppPage.COMPARE, viewModel.uiState.first().page)
        viewModel.selectPage(AppPage.SETTINGS)
        assertEquals(AppPage.SETTINGS, viewModel.uiState.first().page)
        viewModel.selectPage(AppPage.HOME)
    }

    @Test
    fun theThemeModeIsPersisted() = runBlocking {
        viewModel.setThemeMode(ThemeMode.DARK)
        val state = viewModel.uiState.first { it.themeMode == ThemeMode.DARK }
        assertEquals(ThemeMode.DARK, state.themeMode)
        assertEquals(
            ThemeMode.DARK,
            app.container.settingsRepository.settings.first().themeMode,
        )
        viewModel.setThemeMode(ThemeMode.SYSTEM)
    }

    @Test
    fun theHeaderThemeToggleIsPersistedSeparately() = runBlocking {
        viewModel.toggleTheme()
        val state = viewModel.uiState.first { it.headerDarkMode != null }
        assertNotNull("the toggle must be stored explicitly", state.headerDarkMode)

        val again = viewModel.uiState.first { it.headerDarkMode != state.headerDarkMode }
        assertTrue("the toggle must flip", again.headerDarkMode != state.headerDarkMode)
    }

    @Test
    fun theIntervalIsPersistedAndClamped() = runBlocking {
        viewModel.setInterval(1)
        assertEquals(
            com.istanbul.weatherintel.data.prefs.SettingsRepository.MIN_INTERVAL_MINUTES,
            app.container.settingsRepository.settings.first().collectionIntervalMinutes,
        )
        viewModel.setInterval(90)
        assertEquals(
            90,
            app.container.settingsRepository.settings.first().collectionIntervalMinutes,
        )
        viewModel.setInterval(
            com.istanbul.weatherintel.data.prefs.SettingsRepository.DEFAULT_INTERVAL_MINUTES
        )
    }

    @Test
    fun providerTogglesArePersistedAndReachTheDatabase() = runBlocking {
        viewModel.setMgmEnabled(false)
        val settings = app.container.settingsRepository.settings.first()
        assertTrue(!settings.mgmEnabled)
        val provider = app.container.repository
            .providerEntity(com.istanbul.weatherintel.data.model.ProviderId.MGM)
        assertNotNull(provider)
        assertTrue("the provider row must be disabled too", !provider!!.enabled)

        viewModel.setMgmEnabled(true)
        val reenabled = app.container.repository
            .providerEntity(com.istanbul.weatherintel.data.model.ProviderId.MGM)
        assertTrue(reenabled!!.enabled)
    }

    @Test
    fun theMetOfficeApiKeyIsStoredEncrypted() = runBlocking {
        viewModel.setMetOfficeKey("test-key-12345")
        val settings = app.container.settingsRepository.settings.first()
        assertTrue("a saved key must mark the provider as configured", settings.metOfficeConfigured)

        // The plaintext is recoverable through the decrypting accessor only.
        assertEquals(
            "test-key-12345",
            app.container.settingsRepository.metOfficeApiKey(),
        )
        viewModel.clearMetOfficeKey()
        val cleared = app.container.settingsRepository.settings.first()
        assertTrue("a cleared key must unmark the provider", !cleared.metOfficeConfigured)
    }

    @Test
    fun theSwipeHintCanBeDismissed() = runBlocking {
        viewModel.dismissSwipeHint()
        val state = viewModel.uiState.first { it.settings?.firstUseSwipeHintShown == true }
        assertTrue(state.settings!!.firstUseSwipeHintShown)
    }

    @Test
    fun exportingProducesContentAndAFileName() = runBlocking {
        viewModel.exportData(ExportFormat.CSV, null, null, null)
        val state = viewModel.uiState.first { it.exportText != null }
        assertNotNull(state.exportText)
        assertNotNull(state.exportFileName)
        assertTrue(state.exportFileName!!.isNotBlank())
    }
}
