package dev.pierre.japaneseanki

import org.junit.Assert.assertEquals
import org.junit.Test

class AppNavigationStateTest {
    @Test
    fun settingsAndSourcesAreExplicitPagesWithTheSameDraftUnderneath() {
        val navigation = AppNavigationState(AppRoute.Card)
        navigation.open(AppDestination.Settings)
        navigation.open(AppDestination.Sources)
        assertEquals(AppDestination.Sources, navigation.destination)
        assertEquals(AppRoute.Card, navigation.draftRoute)
        navigation.pop()
        assertEquals(AppDestination.Settings, navigation.destination)
        assertEquals(AppBackAction.SaveSettings, navigation.back())
        // A failed Settings save does not pop the stack. Only the successful callback calls pop.
        assertEquals(AppDestination.Settings, navigation.destination)
        navigation.pop()
        assertEquals(AppDestination.Card, navigation.destination)
        assertEquals(AppBackAction.Capture, navigation.back())
    }

    @Test
    fun recoveryCanVisitSettingsWithoutChangingItsFrozenDraftRoute() {
        val navigation = AppNavigationState(AppRoute.Card)
        assertEquals(AppBackAction.Exit, navigation.back(frozen = true))
        navigation.open(AppDestination.Settings)
        assertEquals(AppBackAction.SaveSettings, navigation.back(frozen = true))
        navigation.open(AppDestination.Sources)
        assertEquals(AppBackAction.Pop, navigation.back(frozen = true))
        navigation.pop()
        navigation.pop()
        assertEquals(AppDestination.Card, navigation.destination)
    }

    @Test
    fun actualWritesConsumeBackRatherThanFallingThroughToActivityExit() {
        val navigation = AppNavigationState(AppRoute.Card)
        AppDestination.entries.forEach { destination ->
            assertEquals(AppBackAction.Blocked, navigation.backAction(destination, operationBusy = true, setupMayClose = true))
        }
    }

    @Test
    fun sheetsDismissBeforeTheirUnderlyingDestinationAndSetupCanExitWithoutBypassingIt() {
        val navigation = AppNavigationState(AppRoute.Card)
        assertEquals(AppBackAction.DismissSentence, navigation.backAction(AppDestination.Card, false, true, sentenceEditor = true))
        assertEquals(AppBackAction.DismissMeaning, navigation.backAction(AppDestination.Card, false, true, meaningPicker = true))
        assertEquals(AppBackAction.DismissOcr, navigation.backAction(AppDestination.Card, false, true, ocrPicker = true))
        assertEquals(AppBackAction.Exit, navigation.backAction(AppDestination.Setup, false, false))
        navigation.open(AppDestination.Setup)
        assertEquals(AppBackAction.Pop, navigation.back())
        navigation.finishSetup()
        assertEquals(AppDestination.Card, navigation.destination)
    }

    @Test
    fun requiredSetupIsAPageButDoesNotCoverSettingsOrSources() {
        val navigation = AppNavigationState(AppRoute.Card)
        assertEquals(AppDestination.Setup, navigation.visibleDestination(false, true))
        assertEquals(AppDestination.Setup, navigation.visibleDestination(true, false))
        assertEquals(AppDestination.Card, navigation.visibleDestination(true, true))
        navigation.open(AppDestination.Settings)
        assertEquals(AppDestination.Settings, navigation.visibleDestination(false, false))
        navigation.open(AppDestination.Sources)
        assertEquals(AppDestination.Sources, navigation.visibleDestination(false, false))
    }

    private fun AppNavigationState.back(frozen: Boolean = false) = backAction(
        destination, operationBusy = false, setupMayClose = true, frozenCardActive = frozen,
    )
}
