package dev.pierre.japaneseanki

/**
 * The user actions that authorize billable preparation. This object deliberately knows nothing
 * about navigation, recomposition, or providers: only a new committed sentence or selected word
 * can reach the corresponding operation callbacks.
 */
internal class PreparationAuthorization(
    initialCommittedSentence: String?,
    private val persistSentenceCommit: (String) -> Boolean,
    private val authorizeTranslation: (String) -> Unit,
    private val authorizeSentenceSpeech: (String) -> Unit,
    private val authorizeWordRecording: (String, TargetSpan) -> Boolean,
) {
    private var committedSentence: String? = initialCommittedSentence
    private var selectedWordIdentity: String? = null

    /** Returns true only when this new committed sentence authorizes its two sentence operations. */
    fun commitSentence(sentence: String, beforeAuthorization: () -> Unit = {}): Boolean {
        if (sentence.isBlank() || sentence == committedSentence) return false
        if (!persistSentenceCommit(sentence)) return false
        committedSentence = sentence
        selectedWordIdentity = null
        // Apply editor invalidation only after persistence succeeds, before it grants new work.
        beforeAuthorization()
        authorizeTranslation(sentence)
        authorizeSentenceSpeech(sentence)
        return true
    }

    /** Returns true only for a newly selected word; failed work is retried elsewhere, explicitly. */
    fun selectWord(identity: String, target: TargetSpan): Boolean {
        if (identity.isBlank() || identity == selectedWordIdentity) return false
        if (!authorizeWordRecording(identity, target)) return false
        selectedWordIdentity = identity
        return true
    }

    /** Starts a fresh draft after the user explicitly discards or replaces the previous one. */
    fun reset() {
        committedSentence = null
        selectedWordIdentity = null
    }
}
