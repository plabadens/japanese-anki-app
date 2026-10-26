package dev.pierre.japaneseanki

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue

internal enum class AppDestination { Capture, Card, Settings, Sources, Setup }
internal enum class AppBackAction { Exit, Blocked, Capture, Pop, SaveSettings, DismissSentence, DismissMeaning, DismissOcr }

/** Ordinary page destinations. Draft route changes never replace an open Settings/Sources page. */
internal class AppNavigationState(initialRoute: AppRoute) {
    private var stack by mutableStateOf(listOf(initialRoute.destination()))
    val destination: AppDestination get() = stack.last()
    var draftRoute: AppRoute
        get() = if (stack.first() == AppDestination.Card) AppRoute.Card else AppRoute.Capture
        set(value) { stack = listOf(value.destination()) + stack.drop(1) }

    fun open(destination: AppDestination) {
        require(destination != AppDestination.Capture && destination != AppDestination.Card)
        if (this.destination != destination) stack = stack + destination
    }

    fun pop() {
        if (stack.size > 1) stack = stack.dropLast(1)
    }

    fun finishSetup() {
        stack = stack.filterNot { it == AppDestination.Setup }
    }

    fun visibleDestination(onboardingComplete: Boolean, analyzerReady: Boolean): AppDestination =
        if (destination in listOf(AppDestination.Capture, AppDestination.Card) && (!onboardingComplete || !analyzerReady)) {
            AppDestination.Setup
        } else destination

    /** Toolbar Up and system Back execute the same decision. Sheet handlers have priority. */
    fun backAction(
        visible: AppDestination,
        operationBusy: Boolean,
        setupMayClose: Boolean,
        sentenceEditor: Boolean = false,
        meaningPicker: Boolean = false,
        ocrPicker: Boolean = false,
        frozenCardActive: Boolean = false,
    ): AppBackAction = when {
        operationBusy -> AppBackAction.Blocked
        sentenceEditor -> AppBackAction.DismissSentence
        meaningPicker -> AppBackAction.DismissMeaning
        ocrPicker -> AppBackAction.DismissOcr
        visible == AppDestination.Sources -> AppBackAction.Pop
        visible == AppDestination.Settings -> AppBackAction.SaveSettings
        visible == AppDestination.Setup -> if (setupMayClose) AppBackAction.Pop else AppBackAction.Exit
        visible == AppDestination.Card -> if (frozenCardActive) AppBackAction.Exit else AppBackAction.Capture
        else -> AppBackAction.Exit
    }

    companion object {
        val Saver = listSaver<AppNavigationState, String>(
            save = { it.stack.map(AppDestination::name) },
            restore = { names ->
                AppNavigationState(AppRoute.Capture).apply { stack = names.map(AppDestination::valueOf) }
            },
        )
    }
}

private fun AppRoute.destination() = when (this) {
    AppRoute.Capture -> AppDestination.Capture
    AppRoute.Card -> AppDestination.Card
}
