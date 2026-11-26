package com.gayatripatel.hirereel.ui.interview

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gayatripatel.hirereel.core.InterviewQuestion
import com.gayatripatel.hirereel.core.Job
import com.gayatripatel.hirereel.core.JobFormat
import com.gayatripatel.hirereel.core.interview.InterviewEvent
import com.gayatripatel.hirereel.core.interview.InterviewSession
import com.gayatripatel.hirereel.core.interview.InterviewState
import com.gayatripatel.hirereel.data.ApplicationRepository
import com.gayatripatel.hirereel.data.JobRepository
import com.gayatripatel.hirereel.data.SessionRepository
import com.gayatripatel.hirereel.media.CameraPreview
import com.gayatripatel.hirereel.media.rememberInterviewCamera
import com.gayatripatel.hirereel.ui.theme.RecordRed
import com.gayatripatel.hirereel.upload.UploadScheduler
import kotlinx.coroutines.Job as CoroutineJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.io.File

class InterviewViewModel(
    jobId: String,
    jobs: JobRepository,
    private val applications: ApplicationRepository,
    private val uploads: UploadScheduler,
    private val session: SessionRepository,
    private val filesDir: File,
) : ViewModel() {
    val job: Job = checkNotNull(jobs.job(jobId)) { "Unknown job $jobId" }
    private val interview = InterviewSession(job.questions)
    private val _state = MutableStateFlow(interview.state)
    val state: StateFlow<InterviewState> = _state.asStateFlow()
    val progressLabel: String get() = interview.progressLabel
    private var ticker: CoroutineJob? = null

    fun send(event: InterviewEvent) {
        _state.value = interview.on(event)
        val s = _state.value
        val needsClock = s is InterviewState.Countdown || s is InterviewState.Recording
        if (needsClock && ticker?.isActive != true) {
            ticker = viewModelScope.launch {
                while (isActive) {
                    delay(1_000)
                    send(InterviewEvent.Tick)
                }
            }
        } else if (!needsClock) {
            ticker?.cancel()
        }
    }

    val reachedLimit: Boolean get() = interview.reachedLimit()

    fun outputFile(): File {
        val s = _state.value
        val take = (s as? InterviewState.Recording)?.takesUsed ?: 0
        return File(filesDir, "interviews").apply { mkdirs() }.resolve("${job.id}-q${s.questionIndex + 1}-take$take.mp4")
    }

    /** Creates the application and hands the videos to WorkManager. Returns false if nothing was recorded. */
    fun submit(): Boolean {
        val finished = _state.value as? InterviewState.Finished ?: return false
        val files = finished.takes.map { File(it.filePath) }
        val app = applications.create(job.id, finished.takes.size, files.sumOf { it.length() })
        uploads.enqueue(app.id, files, session.wifiOnly.value)
        return true
    }
}

@Composable
fun InterviewRoute(jobId: String, onClose: () -> Unit, onSubmitted: () -> Unit) {
    val viewModel: InterviewViewModel = koinViewModel(parameters = { parametersOf(jobId) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val needed = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    var granted by remember {
        mutableStateOf(needed.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED })
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        granted = result.values.all { it }
    }
    if (!granted) {
        PermissionScreen(onAllow = { launcher.launch(needed) }, onClose = onClose)
        return
    }

    val camera = rememberInterviewCamera()
    var error by remember { mutableStateOf<String?>(null) }
    val recording = state is InterviewState.Recording
    LaunchedEffect(recording) {
        if (recording) {
            camera.start(
                viewModel.outputFile(),
                onSaved = { viewModel.send(InterviewEvent.Saved(it.absolutePath)) },
                onError = { error = it },
            )
        }
    }
    LaunchedEffect(state) { if (viewModel.reachedLimit) camera.stop() }

    InterviewScreen(
        job = viewModel.job,
        state = state,
        progressLabel = viewModel.progressLabel,
        error = error,
        camera = { CameraPreview(camera, Modifier.fillMaxSize()) },
        onStart = { viewModel.send(InterviewEvent.Start) },
        onStop = { camera.stop() },
        onRetake = { viewModel.send(InterviewEvent.Retake) },
        onAccept = { viewModel.send(InterviewEvent.Accept) },
        onSubmit = { if (viewModel.submit()) onSubmitted() },
        onClose = onClose,
    )
}

@Composable
fun InterviewScreen(
    job: Job,
    state: InterviewState,
    progressLabel: String,
    error: String?,
    camera: @Composable () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRetake: () -> Unit,
    onAccept: () -> Unit,
    onSubmit: () -> Unit,
    onClose: () -> Unit,
) {
    val question: InterviewQuestion? = job.questions.getOrNull(state.questionIndex)
    Box(Modifier.fillMaxSize().background(Color(0xFF101014))) {
        if (state !is InterviewState.Finished) {
            Box(Modifier.fillMaxSize()) {
                CameraPlaceholder()
                camera()
            }
        }
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Leave interview", tint = Color.White) }
                Text(
                    if (state is InterviewState.Finished) job.title else progressLabel,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (state is InterviewState.Recording) RecBadge(state)
            }
            if (state is InterviewState.Recording) {
                LinearProgressIndicator(
                    progress = { state.elapsedSeconds / state.maxSeconds.toFloat() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    color = RecordRed,
                    trackColor = Color.White.copy(alpha = 0.25f),
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (state is InterviewState.Countdown) {
                    Text("${state.secondsLeft}", color = Color.White, fontSize = 120.sp, fontWeight = FontWeight.Bold)
                }
                if (state is InterviewState.Reviewing) {
                    Icon(Icons.Filled.PlayCircle, contentDescription = "Play your answer", tint = Color.White, modifier = Modifier.size(80.dp))
                }
            }
            error?.let { Text(it, color = RecordRed, modifier = Modifier.padding(16.dp)) }
            BottomPanel(state, question, onStart, onStop, onRetake, onAccept, onSubmit)
        }
    }
}

@Composable
private fun CameraPlaceholder() {
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF2B2A33), Color(0xFF15141A)))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White.copy(alpha = 0.12f), modifier = Modifier.size(260.dp))
    }
}

@Composable
private fun RecBadge(state: InterviewState.Recording) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).background(RecordRed, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(
            "${JobFormat.timer(state.elapsedSeconds)} / ${JobFormat.timer(state.maxSeconds)}",
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun BottomPanel(
    state: InterviewState,
    question: InterviewQuestion?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRetake: () -> Unit,
    onAccept: () -> Unit,
    onSubmit: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            when (state) {
                is InterviewState.Finished -> {
                    Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("All ${state.takes.size} answers recorded", style = MaterialTheme.typography.titleLarge)
                    state.takes.forEachIndexed { i, take ->
                        Text(
                            "Answer ${i + 1} · ${JobFormat.timer(take.durationSeconds)}" + if (take.takeNumber > 1) " · take ${take.takeNumber}" else "",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onSubmit, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Submit application") }
                    Text(
                        "Videos upload in the background, even if you close the app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                else -> {
                    Text(
                        question?.prompt.orEmpty(),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(6.dp))
                    question?.let {
                        Text(
                            "Up to ${it.maxSeconds}s · ${it.retakesAllowed} retakes",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    when (state) {
                        is InterviewState.Ready -> Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                            Icon(Icons.Filled.Videocam, null)
                            Spacer(Modifier.width(8.dp))
                            Text("I'm ready")
                        }
                        is InterviewState.Countdown -> Text("Recording starts in ${state.secondsLeft}…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        is InterviewState.Recording -> Box(
                            Modifier.size(72.dp).border(4.dp, RecordRed, CircleShape).padding(14.dp)
                                .clip(RoundedCornerShape(8.dp)).background(RecordRed).clickable(onClick = onStop),
                        )
                        is InterviewState.Reviewing -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = onRetake, enabled = state.retakesLeft > 0, modifier = Modifier.weight(1f).height(52.dp)) {
                                Icon(Icons.Filled.Replay, null)
                                Spacer(Modifier.width(6.dp))
                                Text(if (state.retakesLeft > 0) "Retake (${state.retakesLeft})" else "No retakes left")
                            }
                            Button(
                                onClick = onAccept,
                                modifier = Modifier.weight(1f).height(52.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                            ) { Text("Use this answer") }
                        }
                        is InterviewState.Finished -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionScreen(onAllow: () -> Unit, onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.Videocam, null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("Camera and microphone", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Your answers are recorded on this phone and only uploaded when you submit.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        Button(onClick = onAllow) { Text("Allow access") }
        OutlinedButton(onClick = onClose, modifier = Modifier.padding(top = 8.dp)) { Text("Not now") }
    }
}
