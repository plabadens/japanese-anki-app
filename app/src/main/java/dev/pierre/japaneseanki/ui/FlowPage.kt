package dev.pierre.japaneseanki.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** One owner for page insets and the app's centred reading width; sheets own their own insets. */
@Composable
internal fun FlowPage(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(modifier = modifier.fillMaxSize(), topBar = topBar) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(Modifier.widthIn(max = AppContentMaxWidth).fillMaxSize(), content = content)
        }
    }
}
