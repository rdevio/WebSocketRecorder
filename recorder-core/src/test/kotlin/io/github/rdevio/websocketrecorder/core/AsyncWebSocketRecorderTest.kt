package io.github.rdevio.websocketrecorder.core

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AsyncWebSocketRecorderTest {
    @Test
    fun `record never waits for a slow consumer`() {
        val consumerStarted = CountDownLatch(1)
        val releaseConsumer = CountDownLatch(1)
        val recorder = AsyncWebSocketRecorder(capacity = 1) {
            consumerStarted.countDown()
            releaseConsumer.await()
        }

        recorder.record(event(sequence = 1))
        assertTrue(consumerStarted.await(1, TimeUnit.SECONDS))

        val startedAt = System.nanoTime()
        repeat(1_000) { recorder.record(event(sequence = it.toLong() + 2)) }
        val durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)

        assertTrue(durationMillis < 100, "record took ${durationMillis}ms")
        assertTrue(recorder.droppedEventCount > 0)

        releaseConsumer.countDown()
        recorder.close()
    }

    @Test
    fun `consumer failures do not stop later events`() {
        val consumed = mutableListOf<Long>()
        val completed = CountDownLatch(1)
        val recorder = AsyncWebSocketRecorder {
            if (it.sequence == 1L) error("storage failed")
            consumed += it.sequence
            completed.countDown()
        }

        recorder.record(event(sequence = 1))
        recorder.record(event(sequence = 2))

        assertTrue(completed.await(1, TimeUnit.SECONDS))
        assertEquals(listOf(2L), consumed)
        recorder.close()
    }

    private fun event(sequence: Long) = WebSocketEvent.Cancelled(
        sessionId = "session",
        sequence = sequence,
        timestampNanos = sequence,
    )
}
