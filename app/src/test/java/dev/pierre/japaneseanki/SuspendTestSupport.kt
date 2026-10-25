package dev.pierre.japaneseanki

import kotlinx.coroutines.test.runTest

internal fun <T> runSuspendTest(block: suspend () -> T): T {
    var outcome: Result<T>? = null
    runTest {
        outcome = try {
            Result.success(block())
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }
    return requireNotNull(outcome).getOrThrow()
}

internal fun DraftAudioController.runAttemptForTest(
    attempt: DraftAudioAttempt,
): DraftAudioAttemptResult = runSuspendTest { runAttempt(attempt) }
