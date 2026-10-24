package dev.pierre.japaneseanki

import kotlinx.serialization.Serializable

@Serializable
internal data class TranslationRequestDto(
    val q: String,
    val source: String,
    val target: String,
    val format: String,
    val model: String,
)

@Serializable
internal data class TranslationResponseDto(
    val data: TranslationDataDto,
)

@Serializable
internal data class TranslationDataDto(
    val translations: List<TranslatedTextDto>,
)

@Serializable
internal data class TranslatedTextDto(
    val translatedText: String = "",
)
