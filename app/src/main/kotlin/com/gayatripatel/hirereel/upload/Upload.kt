package com.gayatripatel.hirereel.upload

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.gayatripatel.hirereel.core.ApplicationStatus
import com.gayatripatel.hirereel.core.upload.Chunk
import com.gayatripatel.hirereel.core.upload.ChunkPlanner
import com.gayatripatel.hirereel.core.upload.RetryPolicy
import com.gayatripatel.hirereel.data.ApplicationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.util.concurrent.TimeUnit

data class ChunkResult(val ok: Boolean, val httpStatus: Int? = null)

/** Resumable upload API. The real one is a signed-URL multipart upload to object storage. */
interface UploadApi {
    suspend fun confirmedBytes(applicationId: String, fileName: String): Long
    suspend fun sendChunk(applicationId: String, file: File, chunk: Chunk): ChunkResult
    suspend fun complete(applicationId: String)
}

/** Simulated server: confirms chunks after a short delay and remembers progress between worker runs. */
class FakeUploadApi : UploadApi {
    private val confirmed = mutableMapOf<String, Long>()

    override suspend fun confirmedBytes(applicationId: String, fileName: String): Long =
        confirmed["$applicationId/$fileName"] ?: 0L

    override suspend fun sendChunk(applicationId: String, file: File, chunk: Chunk): ChunkResult {
        delay(120)
        confirmed["$applicationId/${file.name}"] = chunk.endExclusive
        return ChunkResult(ok = true, httpStatus = 200)
    }

    override suspend fun complete(applicationId: String) = delay(200)
}

/**
 * Uploads an application's answer videos in 1 MB chunks. WorkManager keeps it alive across app restarts and
 * waits for the network the candidate allowed (Wi-Fi only by default). Resumes from the last confirmed byte.
 */
class UploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {
    private val api: UploadApi by inject()
    private val applications: ApplicationRepository by inject()

    override suspend fun doWork(): Result {
        val applicationId = inputData.getString(KEY_APPLICATION) ?: return Result.failure()
        val files = inputData.getStringArray(KEY_FILES).orEmpty().map(::File).filter { it.exists() }
        val total = files.sumOf { it.length() }
        var doneBefore = 0L

        for (file in files) {
            val confirmed = api.confirmedBytes(applicationId, file.name)
            for (chunk in ChunkPlanner.remaining(file.length(), confirmed)) {
                val result = api.sendChunk(applicationId, file, chunk)
                if (!result.ok) {
                    return if (RetryPolicy.isRetryable(result.httpStatus) && runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
                }
                val uploaded = doneBefore + chunk.endExclusive
                setProgress(workDataOf(KEY_UPLOADED to uploaded, KEY_TOTAL to total))
                applications.setProgress(applicationId, uploaded, total)
            }
            doneBefore += file.length()
        }
        api.complete(applicationId)
        applications.setStatus(applicationId, ApplicationStatus.SUBMITTED)
        return Result.success()
    }

    companion object {
        const val KEY_APPLICATION = "application_id"
        const val KEY_FILES = "files"
        const val KEY_UPLOADED = "uploaded"
        const val KEY_TOTAL = "total"
        private const val MAX_ATTEMPTS = 6
    }
}

class UploadScheduler(
    private val context: Context,
    private val applications: ApplicationRepository,
    private val appScope: CoroutineScope,
) {
    fun enqueue(applicationId: String, files: List<File>, wifiOnly: Boolean) {
        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(
                workDataOf(
                    UploadWorker.KEY_APPLICATION to applicationId,
                    UploadWorker.KEY_FILES to files.map { it.absolutePath }.toTypedArray(),
                ),
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .setRequiresStorageNotLow(true)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .addTag(TAG)
            .build()

        val name = "upload-$applicationId"
        val workManager = WorkManager.getInstance(context)
        workManager.enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, request)

        // Reflect "waiting for Wi-Fi" in the UI while constraints aren't met.
        appScope.launch {
            workManager.getWorkInfosForUniqueWorkFlow(name)
                .mapNotNull { it.firstOrNull() }
                .takeWhile { !it.state.isFinished }
                .collect { info ->
                    when (info.state) {
                        WorkInfo.State.ENQUEUED -> applications.setStatus(applicationId, ApplicationStatus.WAITING_FOR_NETWORK)
                        WorkInfo.State.RUNNING -> applications.setStatus(applicationId, ApplicationStatus.UPLOADING)
                        else -> Unit
                    }
                }
        }
    }

    companion object {
        const val TAG = "interview-upload"
    }
}
