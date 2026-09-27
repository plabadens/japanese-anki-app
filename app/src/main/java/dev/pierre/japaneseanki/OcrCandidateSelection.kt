package dev.pierre.japaneseanki

/** Image-space bounds in pixels, with right/bottom exclusive as in Android rectangles. */
data class OcrBounds(val left: Int, val top: Int, val right: Int, val bottom: Int)

data class OcrBlock(val text: String, val confidence: Float, val bounds: OcrBounds)
data class OcrCandidate(val text: String, val blocks: List<OcrBlock>, val meanConfidence: Float)

/** Prefer the candidate with the strongest block confidence; ties deterministically prefer horizontal. */
internal fun chooseOcrCandidate(horizontal: OcrCandidate, vertical: OcrCandidate): OcrCandidate {
    val horizontalValid = horizontal.text.isNotBlank()
    val verticalValid = vertical.text.isNotBlank()
    return when {
        horizontalValid && !verticalValid -> horizontal
        verticalValid && !horizontalValid -> vertical
        horizontal.meanConfidence >= vertical.meanConfidence -> horizontal
        else -> vertical
    }
}

internal fun meanBlockConfidence(blocks: List<OcrBlock>): Float =
    if (blocks.isEmpty()) 0f else blocks.sumOf { it.confidence.toDouble() }.toFloat() / blocks.size

