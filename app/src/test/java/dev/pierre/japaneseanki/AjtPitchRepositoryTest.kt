package dev.pierre.japaneseanki

import java.util.Collections
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AjtPitchRepositoryTest {
    @Test fun repeatedPrepareOpensOnceAndReusesOneHandle() {
        var opens = 0
        val store = FakeStore()
        val repository = AjtPitchRepository { opens++; store }

        assertEquals(AjtPitchAvailability.Ready, repository.prepare())
        assertEquals(AjtPitchAvailability.Ready, repository.prepare())
        repository.pitch("猫", "ねこ")
        repository.pitch("犬", "いぬ")

        assertEquals(1, opens)
        assertEquals(1, store.probes)
        assertEquals(2, store.queries)
        assertFalse(store.closed)
        assertEquals(AjtPitchAvailability.Ready, repository.availability.value)
    }

    @Test fun concurrentPrepareOpensOnce() {
        var opens = 0
        val store = FakeStore(openDelayMillis = 50)
        val repository = AjtPitchRepository { opens++; store }
        val ready = CountDownLatch(8)
        val failures = Collections.synchronizedList(mutableListOf<Throwable>())
        val threads = (1..8).map {
            Thread {
                runCatching { assertEquals(AjtPitchAvailability.Ready, repository.prepare()) }
                    .exceptionOrNull()?.let(failures::add)
                ready.countDown()
            }.also { it.start() }
        }
        assertTrue(ready.await(20, TimeUnit.SECONDS))
        threads.forEach { it.join(20_000) }

        assertTrue(failures.toString(), failures.isEmpty())
        assertEquals(1, opens)
        assertEquals(1, store.probes)
    }

    @Test fun availabilityIsPublishedWhileOpening() {
        val seen = mutableListOf<AjtPitchAvailability>()
        lateinit var repository: AjtPitchRepository
        val store = FakeStore(onProbe = { seen += repository.availability.value })
        repository = AjtPitchRepository { store }

        assertEquals(AjtPitchAvailability.NotPrepared, repository.availability.value)
        repository.prepare()

        assertEquals(listOf<AjtPitchAvailability>(AjtPitchAvailability.Preparing), seen)
        assertEquals(AjtPitchAvailability.Ready, repository.availability.value)
    }

    @Test fun invalidIndexClosesProvisionalHandleAndRetryRecovers() {
        val broken = FakeStore(probeResult = { throw IllegalStateException("unsupported schema") })
        val healthy = FakeStore()
        val queue = ArrayDeque(listOf<AjtPitchStore>(broken, healthy))
        val repository = AjtPitchRepository { queue.removeFirst() }

        assertEquals(AjtPitchAvailability.Unavailable(AjtPitchFailure.INVALID_INDEX), repository.prepare())
        assertTrue(broken.closed)
        assertEquals(AjtPitchAvailability.Unavailable(AjtPitchFailure.INVALID_INDEX), repository.availability.value)
        // A failed index must not answer lookups from a closed handle.
        assertEquals(emptyList<AjtPitchRecord>(), repository.pitch("猫", "ねこ"))
        assertEquals(0, broken.queries)

        // Retry: an explicit preparation after failure clears the error.
        assertEquals(AjtPitchAvailability.Ready, repository.prepare())
        assertEquals(AjtPitchAvailability.Ready, repository.availability.value)
        assertFalse(healthy.closed)
    }

    @Test fun emptyIndexIsUnavailableAndClosed() {
        val store = FakeStore(probeResult = { false })
        val repository = AjtPitchRepository { store }

        assertEquals(AjtPitchAvailability.Unavailable(AjtPitchFailure.EMPTY_INDEX), repository.prepare())
        assertTrue(store.closed)
        assertEquals(emptyList<AjtPitchRecord>(), repository.pitch("猫", "ねこ"))
    }

    @Test fun missingAndFailedOpensAreUnavailable() {
        val missing = AjtPitchRepository { null }
        assertEquals(AjtPitchAvailability.Unavailable(AjtPitchFailure.MISSING_INDEX), missing.prepare())

        val failing = AjtPitchRepository { throw IllegalStateException("disk unavailable") }
        assertEquals(AjtPitchAvailability.Unavailable(AjtPitchFailure.OPEN_FAILED), failing.prepare())
    }

    @Test fun cancelledSetupClosesProvisionalHandleAndLeavesNoOrphan() {
        var attempt = 0
        val cancelled = FakeStore(probeResult = { throw CancellationException("setup cancelled") })
        val healthy = FakeStore()
        val repository = AjtPitchRepository {
            attempt++
            if (attempt == 1) cancelled else healthy
        }

        val failure = runCatching { repository.prepare() }.exceptionOrNull()
        assertTrue(failure is CancellationException)
        assertTrue(cancelled.closed)
        assertEquals(AjtPitchAvailability.Unavailable(AjtPitchFailure.OPEN_FAILED), repository.availability.value)
        assertEquals(0, cancelled.queries)

        // The cancelled handle was not stranded: the next preparation opens a fresh one.
        assertEquals(AjtPitchAvailability.Ready, repository.prepare())
        assertEquals(2, attempt)
    }

    @Test fun blankQueriesNeverReachTheStore() {
        val store = FakeStore()
        val repository = AjtPitchRepository { store }
        repository.prepare()

        assertEquals(emptyList<AjtPitchRecord>(), repository.pitch("", "ねこ"))
        assertEquals(emptyList<AjtPitchRecord>(), repository.pitch("  ", "ねこ"))
        assertEquals(emptyList<AjtPitchRecord>(), repository.pitch("猫", " "))
        assertEquals(0, store.queries)
    }

    @Test fun queriesBeforePreparationReturnNoRecords() {
        var opens = 0
        val store = FakeStore()
        val repository = AjtPitchRepository { opens++; store }
        assertEquals(emptyList<AjtPitchRecord>(), repository.pitch("猫", "ねこ"))
        assertEquals(0, opens)
        assertEquals(0, store.queries)
    }

    private class FakeStore(
        private val probeResult: () -> Boolean = { true },
        private val openDelayMillis: Long = 0,
        private val onProbe: () -> Unit = {},
    ) : AjtPitchStore {
        var probes = 0
        var queries = 0
        var closed = false

        override fun probe(): Boolean {
            onProbe()
            if (openDelayMillis > 0) Thread.sleep(openDelayMillis)
            probes++
            return probeResult()
        }

        override fun pitch(expression: String, reading: String): List<AjtPitchRecord> {
            queries++
            return listOf(AjtPitchRecord("<span>$expression</span>", 1))
        }

        override fun close() {
            closed = true
        }
    }
}
