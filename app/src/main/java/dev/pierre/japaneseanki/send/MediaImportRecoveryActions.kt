package dev.pierre.japaneseanki.send

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.pierre.japaneseanki.MediaImportOperation
import dev.pierre.japaneseanki.PreferenceFile
import dev.pierre.japaneseanki.AppPreferenceStorage
import dev.pierre.japaneseanki.PreferenceValues
import dev.pierre.japaneseanki.R
import dev.pierre.japaneseanki.SubmissionPhase
import dev.pierre.japaneseanki.SubmissionRecoveryKeys
import dev.pierre.japaneseanki.clearFrozenSubmission

internal fun pendingMediaRecoveryOperation(preferences: PreferenceValues): MediaImportOperation? {
    if (SubmissionPhase.fromPersisted(preferences.getString(SubmissionRecoveryKeys.STATUS, null)) !=
        SubmissionPhase.MEDIA_IMPORTING
    ) return null
    return preferences.getString(SubmissionRecoveryKeys.MEDIA_OPERATION, null)
        ?.let { name -> runCatching { MediaImportOperation.valueOf(name) }.getOrNull() }
}

internal fun discardInterruptedMediaRecovery(storage: AppPreferenceStorage) {
    storage.update(PreferenceFile.DRAFT, clearFrozenSubmission())
}

@Composable
internal fun MediaImportRecoveryActions(
    operation: MediaImportOperation?,
    onRetry: () -> Unit,
    onDiscardAudio: () -> Unit,
    onRemoveImage: () -> Unit,
) {
    TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
        Text(stringResource(R.string.card_retry_media_import_may_duplicate))
    }
    if (operation == MediaImportOperation.IMAGE) {
        TextButton(onClick = onRemoveImage, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.card_remove_interrupted_image))
        }
    } else {
        TextButton(onClick = onDiscardAudio, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.card_discard_interrupted_send_keep_draft))
        }
    }
}
