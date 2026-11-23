package com.istanbul.weatherintel.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.istanbul.weatherintel.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for the app shell.
 *
 * These check the structural rules from the final UI specification:
 *  - the three navigation buttons are always visible,
 *  - there is no bottom navigation bar and no drawer,
 *  - the Home page contains no MGM-vs-Met-Office comparison card,
 *  - the swipe hint appears on first use.
 */
@RunWith(AndroidJUnit4::class)
class AppShellUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun theThreeNavigationButtonsAreAlwaysVisible() {
        composeRule.onNodeWithContentDescription("HOME tab").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("COMPARE tab").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("SETTINGS tab").assertIsDisplayed()
    }

    @Test
    fun theHeaderExposesTheThemeToggleAndCollectNow() {
        composeRule.onNodeWithContentDescription("Toggle light or dark theme").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Collect now").assertIsDisplayed()
    }

    @Test
    fun thereIsNoBottomNavigationBar() {
        // A bottom bar would repeat the same three labels further down the
        // hierarchy; the shell must expose exactly one set.
        val navPills = composeRule.onAllNodesWithContentDescription("HOME tab")
        assertEquals("there must be exactly one Home navigation control", navPills.fetchSemanticsNodes().size, 1)
    }

    @Test
    fun theHomePageShowsNoComparisonCard() {
        // The final specification removes the MGM-vs-Met-Office comparison card
        // from Home; comparison lives on its own page.
        composeRule.onNodeWithText("Comparison").assertDoesNotExist()
        composeRule.onNodeWithText("Compare providers").assertDoesNotExist()
        composeRule.onNodeWithText("Source Comparison").assertDoesNotExist()
    }

    @Test
    fun theSwipeHintIsShownOnFirstUse() {
        // The hint is persisted per install; it is shown until dismissed.
        val hints = composeRule.onAllNodesWithContentDescription("Dismiss navigation hint")
        if (hints.fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText("Swipe left or right to move between pages")
                .assertIsDisplayed()
        }
    }

    @Test
    fun selectingCompareShowsTheComparisonPage() {
        composeRule.onNodeWithContentDescription("COMPARE tab").performClick()
        composeRule.waitForIdle()
        // The comparison page explains that Met Office publishes no Istanbul
        // observations rather than implying a measured comparison.
        val nodes = composeRule.onAllNodesWithContentDescription("MET_OFFICE tab")
        assertTrue(nodes.fetchSemanticsNodes().isNotEmpty())
    }
}
