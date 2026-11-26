package com.gayatripatel.hirereel.data

import android.content.Context
import com.gayatripatel.hirereel.core.ApplicationStatus
import com.gayatripatel.hirereel.core.Job
import com.gayatripatel.hirereel.core.JobApplication
import com.gayatripatel.hirereel.core.SampleJobs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

fun interface Clock {
    fun now(): Long
}

class JobRepository(clock: Clock) {
    private val _jobs = MutableStateFlow(SampleJobs.jobs(clock.now()))
    val jobs: StateFlow<List<Job>> = _jobs.asStateFlow()

    private val _saved = MutableStateFlow(setOf("j4"))
    val saved: StateFlow<Set<String>> = _saved.asStateFlow()

    fun job(id: String): Job? = _jobs.value.firstOrNull { it.id == id }

    fun toggleSaved(id: String) = _saved.update { if (id in it) it - id else it + id }
}

/**
 * Candidate's applications. In production this is a Room table that the upload worker also writes to,
 * so progress survives process death; here it's in memory with demo history.
 */
class ApplicationRepository(private val clock: Clock) {
    private val _applications = MutableStateFlow(SampleJobs.applications(clock.now()))
    val applications: StateFlow<List<JobApplication>> = _applications.asStateFlow()

    fun create(jobId: String, answers: Int, totalBytes: Long): JobApplication {
        val app = JobApplication("a${clock.now()}", jobId, ApplicationStatus.UPLOADING, answers, totalBytes, 0, clock.now())
        _applications.update { listOf(app) + it.filterNot { a -> a.jobId == jobId && a.status == ApplicationStatus.DRAFT } }
        return app
    }

    fun setStatus(id: String, status: ApplicationStatus) = modify(id) { it.copy(status = status, updatedAtMillis = clock.now()) }

    fun setProgress(id: String, uploaded: Long, total: Long) =
        modify(id) { it.copy(uploadedBytes = uploaded, totalBytes = total, status = ApplicationStatus.UPLOADING) }

    private fun modify(id: String, transform: (JobApplication) -> JobApplication) =
        _applications.update { list -> list.map { if (it.id == id) transform(it) else it } }
}

/** Signed-in phone number and upload preference, persisted in SharedPreferences. */
class SessionRepository(context: Context) {
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)

    private val _phone = MutableStateFlow(prefs.getString(KEY_PHONE, null))
    val phone: StateFlow<String?> = _phone.asStateFlow()

    private val _wifiOnly = MutableStateFlow(prefs.getBoolean(KEY_WIFI_ONLY, true))
    val wifiOnly: StateFlow<Boolean> = _wifiOnly.asStateFlow()

    fun signIn(phone: String) {
        prefs.edit().putString(KEY_PHONE, phone).apply()
        _phone.value = phone
    }

    fun setWifiOnly(value: Boolean) {
        prefs.edit().putBoolean(KEY_WIFI_ONLY, value).apply()
        _wifiOnly.value = value
    }

    private companion object {
        const val KEY_PHONE = "phone"
        const val KEY_WIFI_ONLY = "wifi_only"
    }
}
