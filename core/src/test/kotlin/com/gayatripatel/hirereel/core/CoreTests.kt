package com.gayatripatel.hirereel.core

import com.gayatripatel.hirereel.core.feed.PlayerPoolPolicy
import com.gayatripatel.hirereel.core.interview.InterviewEvent
import com.gayatripatel.hirereel.core.interview.InterviewSession
import com.gayatripatel.hirereel.core.interview.InterviewState
import com.gayatripatel.hirereel.core.upload.ChunkPlanner
import com.gayatripatel.hirereel.core.upload.RetryPolicy
import com.gayatripatel.hirereel.core.upload.progressLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerPoolPolicyTest {
    @Test fun `prepares neighbours and plays only the settled page`() {
        val plan = PlayerPoolPolicy.plan(currentPage = 0, pageCount = 5, alreadyPrepared = emptySet(), isScrolling = false)
        assertEquals(setOf(0, 1), plan.prepare)
        assertEquals(0, plan.play)
    }

    @Test fun `swiping forward releases the page that fell out of the window`() {
        val plan = PlayerPoolPolicy.plan(currentPage = 3, pageCount = 5, alreadyPrepared = setOf(1, 2, 3), isScrolling = false)
        assertEquals(setOf(4), plan.prepare)
        assertEquals(setOf(1), plan.release)
        assertEquals(3, plan.play)
    }

    @Test fun `never more than three players and nothing plays mid-swipe`() {
        for (page in 0 until 20) {
            val plan = PlayerPoolPolicy.plan(page, 20, emptySet(), isScrolling = true)
            assertTrue(plan.prepare.size <= 3)
            assertNull(plan.play)
        }
        assertEquals(setOf(7), PlayerPoolPolicy.plan(0, 0, setOf(7), false).release)
    }
}

class InterviewSessionTest {
    private val questions = listOf(
        InterviewQuestion(1, "Intro", maxSeconds = 3, prepSeconds = 2, retakesAllowed = 1),
        InterviewQuestion(2, "Why us", maxSeconds = 2, prepSeconds = 1, retakesAllowed = 0),
    )

    @Test fun `countdown leads to a timed recording that caps at the limit`() {
        val s = InterviewSession(questions)
        s.on(InterviewEvent.Start)
        assertEquals(InterviewState.Countdown(0, 2, 0), s.state)
        s.on(InterviewEvent.Tick)
        s.on(InterviewEvent.Tick)
        assertTrue(s.shouldRecord)
        repeat(5) { s.on(InterviewEvent.Tick) }
        val rec = s.state as InterviewState.Recording
        assertEquals(3, rec.elapsedSeconds)
        assertTrue(s.reachedLimit())
    }

    @Test fun `retakes are limited per question and kept takes are collected`() {
        val s = InterviewSession(questions)
        fun recordOnce(path: String) {
            s.on(InterviewEvent.Start)
            while (s.state is InterviewState.Countdown) s.on(InterviewEvent.Tick)
            s.on(InterviewEvent.Tick)
            s.on(InterviewEvent.Saved(path))
        }
        recordOnce("q1-take1.mp4")
        assertEquals(1, (s.state as InterviewState.Reviewing).retakesLeft)
        s.on(InterviewEvent.Retake)
        while (s.state is InterviewState.Countdown) s.on(InterviewEvent.Tick)
        s.on(InterviewEvent.Saved("q1-take2.mp4"))
        val review = s.state as InterviewState.Reviewing
        assertEquals(0, review.retakesLeft)
        assertEquals(2, review.take.takeNumber)
        assertEquals(review, s.on(InterviewEvent.Retake)) // no retakes left
        s.on(InterviewEvent.Accept)
        assertEquals("Question 2 of 2", s.progressLabel)

        recordOnce("q2.mp4")
        s.on(InterviewEvent.Accept)
        val done = s.state as InterviewState.Finished
        assertEquals(listOf("q1-take2.mp4", "q2.mp4"), done.takes.map { it.filePath })
        assertFalse(s.shouldRecord)
    }
}

class UploadTest {
    @Test fun `chunks cover the file exactly`() {
        val chunks = ChunkPlanner.plan(2_500_000, 1_000_000)
        assertEquals(3, chunks.size)
        assertEquals(500_000, chunks.last().size)
        assertEquals(2_500_000, chunks.sumOf { it.size })
        assertTrue(ChunkPlanner.plan(0).isEmpty())
    }

    @Test fun `resume re-sends only unconfirmed chunks`() {
        val left = ChunkPlanner.remaining(2_500_000, confirmedBytes = 1_400_000, chunkBytes = 1_000_000)
        assertEquals(listOf(1, 2), left.map { it.index })
        assertTrue(ChunkPlanner.remaining(2_500_000, 2_500_000, 1_000_000).isEmpty())
    }

    @Test fun `backoff doubles up to a cap and client errors are not retried`() {
        assertEquals(0, RetryPolicy.delayMillis(0))
        assertEquals(10_000, RetryPolicy.delayMillis(1))
        assertEquals(40_000, RetryPolicy.delayMillis(3))
        assertEquals(300_000, RetryPolicy.delayMillis(12))
        assertTrue(RetryPolicy.isRetryable(503))
        assertTrue(RetryPolicy.isRetryable(null))
        assertFalse(RetryPolicy.isRetryable(413))
        assertEquals("1.0 MB of 2.0 MB", progressLabel(1_048_576, 2_097_152))
    }
}

class JobsLogicTest {
    private val now = SampleJobs.FIXED_NOW
    private val jobs = SampleJobs.jobs(now)

    @Test fun `filters combine and results are newest first`() {
        assertEquals(listOf("j1", "j4", "j5"), JobFilter(remoteOnly = true).apply(jobs).map { it.id })
        assertEquals(listOf("j1"), JobFilter(query = "kotlin austin").apply(jobs).map { it.id })
        assertEquals(listOf("j3"), JobFilter(types = setOf(JobType.PART_TIME)).apply(jobs).map { it.id })
        assertEquals(listOf("j1", "j4"), JobFilter(minSalaryK = 100).apply(jobs).map { it.id })
        assertFalse(JobFilter().isActive)
    }

    @Test fun `formatting`() {
        assertEquals("\$140k–\$175k", JobFormat.salary(jobs[0]))
        assertEquals("Austin, TX · Remote", JobFormat.location(jobs[0]))
        assertEquals("3h ago", JobFormat.posted(jobs[0].postedAtMillis, now))
        assertEquals("2d ago", JobFormat.posted(jobs[2].postedAtMillis, now))
        assertEquals("1w ago", JobFormat.posted(jobs[4].postedAtMillis, now))
        assertEquals("1.4k applicants", JobFormat.applicants(1_380))
        assertEquals("Be an early applicant", JobFormat.applicants(7))
        assertEquals("1:05", JobFormat.timer(65))
    }

    @Test fun `otp`() {
        assertEquals("381204", OtpParser.parse("<#> 381204 is your HireReel code\nAbCdEfGhIjK"))
        assertEquals("990011", OtpParser.parse("Job #123456 update. Your OTP: 990011"))
    }
}
