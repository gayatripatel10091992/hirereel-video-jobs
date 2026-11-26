package com.gayatripatel.hirereel

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gayatripatel.hirereel.core.JobFilter
import com.gayatripatel.hirereel.core.JobType
import com.gayatripatel.hirereel.core.SampleJobs
import com.gayatripatel.hirereel.core.interview.InterviewState
import com.gayatripatel.hirereel.core.interview.Take
import com.gayatripatel.hirereel.ui.Tab
import com.gayatripatel.hirereel.ui.TabShell
import com.gayatripatel.hirereel.ui.applications.ApplicationsScreen
import com.gayatripatel.hirereel.ui.applications.ApplicationsUiState
import com.gayatripatel.hirereel.ui.applications.applicationRows
import com.gayatripatel.hirereel.ui.feed.FeedScreen
import com.gayatripatel.hirereel.ui.feed.FeedUiState
import com.gayatripatel.hirereel.ui.interview.InterviewScreen
import com.gayatripatel.hirereel.ui.jobs.JobDetailScreen
import com.gayatripatel.hirereel.ui.jobs.JobsScreen
import com.gayatripatel.hirereel.ui.jobs.JobsUiState
import com.gayatripatel.hirereel.ui.login.LoginScreen
import com.gayatripatel.hirereel.ui.login.LoginUiState
import com.gayatripatel.hirereel.ui.theme.HireReelTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** JVM-rendered screenshots. Camera and video surfaces can't render here, so their gradient posters show instead. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7, application = Application::class)
class ScreenshotTest {
    private val now = SampleJobs.FIXED_NOW
    private val jobs = SampleJobs.jobs(now)
    private val job = jobs.first()

    private fun shot(name: String, dark: Boolean = false, tab: Tab? = null, content: @Composable () -> Unit) {
        captureRoboImage("build/outputs/roborazzi/$name.png") {
            HireReelTheme(darkTheme = dark) {
                if (tab != null) TabShell(tab, {}) { m -> Box(m) { content() } } else content()
            }
        }
    }

    @Test fun login() = shot("01_login") { LoginScreen(LoginUiState(phone = "4155550134", codeSent = true, code = "3812"), {}, {}, {}, {}) }

    @Test fun feed() = shot("02_feed", tab = Tab.FEED) {
        val pager = rememberPagerState(pageCount = { jobs.size })
        FeedScreen(FeedUiState(jobs, setOf("j1")), pager, {}, {}, video = {}, nowMillis = now)
    }

    @Test fun jobs() = shot("03_jobs", tab = Tab.JOBS) {
        JobsScreen(JobsUiState(JobFilter(), JobFilter().apply(jobs), jobs.size), {}, {}, nowMillis = now)
    }

    @Test fun jobs_filtered_dark() = shot("04_jobs_filtered_dark", dark = true, tab = Tab.JOBS) {
        val filter = JobFilter(remoteOnly = true, types = setOf(JobType.FULL_TIME))
        JobsScreen(JobsUiState(filter, filter.apply(jobs), jobs.size), {}, {}, nowMillis = now)
    }

    @Test fun job_detail() = shot("05_job_detail") { JobDetailScreen(job, {}, {}, nowMillis = now) }

    private fun interview(name: String, state: InterviewState) = shot(name) {
        InterviewScreen(job, state, "Question ${state.questionIndex + 1} of ${job.questions.size}", null, camera = {}, {}, {}, {}, {}, {}, {})
    }

    @Test fun interview_ready() = interview("06_interview_ready", InterviewState.Ready(0))

    @Test fun interview_recording() = interview(
        "07_interview_recording",
        InterviewState.Recording(1, elapsedSeconds = 17, maxSeconds = 45, takesUsed = 1),
    )

    @Test fun interview_review() = interview(
        "08_interview_review",
        InterviewState.Reviewing(2, Take(3, "q3.mp4", 71, takeNumber = 2), retakesLeft = 1),
    )

    @Test fun interview_finished() = interview(
        "09_interview_finished",
        InterviewState.Finished(2, listOf(Take(1, "a", 48, 1), Take(2, "b", 39, 2), Take(3, "c", 71, 2))),
    )

    @Test fun applications() = shot("10_applications", tab = Tab.APPLICATIONS) {
        ApplicationsScreen(ApplicationsUiState(applicationRows(SampleJobs.applications(now), jobs, now), wifiOnly = true)) {}
    }
}
