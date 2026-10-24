package dev.pierre.japaneseanki.speech

import kotlinx.serialization.Serializable

@Serializable
internal data class GoogleSpeechSynthesisRequestDto(
    val input: GoogleSpeechInputDto,
    val voice: GoogleSpeechVoiceConfigDto,
    val audioConfig: GoogleSpeechAudioConfigDto,
)

@Serializable
internal data class GoogleSpeechInputDto(
    val text: String,
    val customPronunciations: GoogleSpeechCustomPronunciationsDto? = null,
)

@Serializable
internal data class GoogleSpeechCustomPronunciationsDto(
    val pronunciations: List<GoogleSpeechPronunciationDto>,
)

@Serializable
internal data class GoogleSpeechPronunciationDto(
    val phrase: String,
    val phoneticEncoding: String,
    val pronunciation: String,
)

@Serializable
internal data class GoogleSpeechVoiceConfigDto(
    val languageCode: String,
    val name: String,
)

@Serializable
internal data class GoogleSpeechAudioConfigDto(
    val audioEncoding: String,
    val speakingRate: Double,
)

@Serializable
internal data class GoogleSpeechSynthesisResponseDto(
    val audioContent: String? = null,
)

@Serializable
internal data class GoogleSpeechVoiceListDto(
    val voices: List<GoogleSpeechVoiceDto?>? = null,
)

@Serializable
internal data class GoogleSpeechVoiceDto(
    val name: String? = null,
    val languageCodes: List<String>? = null,
    val ssmlGender: String? = null,
    val naturalSampleRateHertz: Int? = null,
)
