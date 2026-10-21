package dev.pierre.japaneseanki.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Material Symbols (m3.material.io/styles/icons), outlined style, ported from the exact
 * path data used by the current mockup study so the set stays visually consistent without
 * fetching an icon font (SPEC.md §4.1).
 */
private fun symbol(name: String, path: String): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 960f,
    viewportHeight = 960f,
).apply {
    // Material Symbols source coordinates use viewBox="0 -960 960 960" (y in [-960, 0]);
    // shift into this builder's [0, 960] viewport with a group translation.
    addGroup(name = "$name-group", translationY = 960f)
    addPath(
        pathData = PathParser().parsePathString(path).toNodes(),
        fill = SolidColor(Color.Black),
        fillAlpha = 1f,
    )
    clearGroup()
}.build()

internal object MaterialSymbols {
    val Edit: ImageVector by lazy {
        symbol(
            "Edit",
            "M200-200h57l391-391-57-57-391 391v57Zm-80 80v-170l528-527q12-11 26.5-17t30.5-6q16 0 " +
                "31 6t26 18l55 56q12 11 17.5 26t5.5 30q0 16-5.5 30.5T817-647L290-120H120Zm640-584-56-56 " +
                "56 56Zm-141 85-28-29 57 57-29-28Z",
        )
    }
    val Play: ImageVector by lazy {
        symbol("Play", "M320-200v-560l440 280-440 280Zm80-280Zm0 134 210-134-210-134v268Z")
    }
    val Pause: ImageVector by lazy {
        symbol("Pause", "M320-200h120v-560H320v560Zm200-560v560h120v-560H520Z")
    }
    val Add: ImageVector by lazy {
        symbol("Add", "M440-440H200v-80h240v-240h80v240h240v80H520v240h-80v-240Z")
    }
    val ErrorOutline: ImageVector by lazy {
        symbol(
            "ErrorOutline",
            "M480-80q-83 0-156-31.5T197-197q-54-54-85.5-127T80-480q0-83 31.5-156T197-763q54-54 127-85.5T480-880q83 0 156 31.5T763-763q54 54 85.5 127T880-480q0 83-31.5 156T763-197q-54 54-127 85.5T480-80Zm0-80q134 0 227-93t93-227q0-134-93-227t-227-93q-134 0-227 93t-93 227q0 134 93 227t227 93Zm-40-200h80v-240h-80v240Zm40 120q17 0 28.5-11.5T520-280q0-17-11.5-28.5T480-320q-17 0-28.5 11.5T440-280q0 17 11.5 28.5T480-240Zm0-240Z",
        )
    }
    val Settings: ImageVector by lazy {
        symbol(
            "Settings",
            "m370-80-16-128q-13-5-24.5-12T307-235l-119 50L78-375l103-78q-1-7-1-13.5v-27q0-6.5 " +
                "1-13.5L78-585l110-190 119 50q11-8 23-15t24-12l16-128h220l16 128q13 5 24.5 12t22.5 " +
                "15l119-50 110 190-103 78q1 7 1 13.5v27q0 6.5-2 13.5l103 78-110 190-118-50q-11 8-23 " +
                "15t-24 12L590-80H370Zm70-80h79l14-106q31-8 57.5-23.5T639-327l99 41 39-68-86-65q5-14 " +
                "7-29.5t2-31.5q0-16-2-31.5t-7-29.5l86-65-39-68-99 42q-22-23-48.5-38.5T533-694l-13-106h-79l" +
                "-14 106q-31 8-57.5 23.5T321-633l-99-41-39 68 86 64q-5 15-7 30t-2 32q0 16 2 31t7 30l-86 " +
                "65 39 68 99-42q22 23 48.5 38.5T427-266l13 106Zm42-180q58 0 99-41t41-99q0-58-41-99t-99-41" +
                "q-59 0-99.5 41T342-480q0 58 40.5 99t99.5 41Zm-2-140Z",
        )
    }
}
