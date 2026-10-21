package dev.pierre.japaneseanki

/** Image-space bounds in pixels, with right/bottom exclusive as in Android rectangles. */
data class OcrBounds(val left: Int, val top: Int, val right: Int, val bottom: Int)

data class OcrBlock(val text: String, val bounds: OcrBounds)

/** Converts recognizer bounds to the orientation-corrected source image's pixel space. */
internal fun scaleOcrBounds(
    bounds: OcrBounds,
    decodedWidth: Int,
    decodedHeight: Int,
    sourceWidth: Int,
    sourceHeight: Int,
): OcrBounds {
    require(decodedWidth > 0 && decodedHeight > 0 && sourceWidth > 0 && sourceHeight > 0)
    return OcrBounds(
        (bounds.left * sourceWidth.toDouble() / decodedWidth).toInt(),
        (bounds.top * sourceHeight.toDouble() / decodedHeight).toInt(),
        (bounds.right * sourceWidth.toDouble() / decodedWidth).toInt(),
        (bounds.bottom * sourceHeight.toDouble() / decodedHeight).toInt(),
    )
}

