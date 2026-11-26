package com.gayatripatel.hirereel.media

import android.annotation.SuppressLint
import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File

/**
 * Front-camera recorder for interview answers.
 *
 * Memory: records at SD (480p) with a fallback, binds only Preview + VideoCapture (no ImageAnalysis),
 * and unbinds as soon as the screen leaves, so the camera pipeline's buffers are freed immediately.
 */
class InterviewCamera(private val context: Context) {
    private val recorder = Recorder.Builder()
        .setQualitySelector(QualitySelector.from(Quality.SD, FallbackStrategy.higherQualityOrLowerThan(Quality.SD)))
        .build()
    private val videoCapture: VideoCapture<Recorder> = VideoCapture.withOutput(recorder)
    private var recording: Recording? = null
    private var provider: ProcessCameraProvider? = null

    fun bind(owner: LifecycleOwner, previewView: PreviewView) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val cameraProvider = future.get()
            provider = cameraProvider
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, videoCapture)
        }, ContextCompat.getMainExecutor(context))
    }

    /** Requires CAMERA and RECORD_AUDIO, which the screen requests before showing the camera. */
    @SuppressLint("MissingPermission")
    fun start(output: File, onSaved: (File) -> Unit, onError: (String) -> Unit) {
        if (recording != null) return
        recording = videoCapture.output
            .prepareRecording(context, FileOutputOptions.Builder(output).build())
            .withAudioEnabled()
            .start(ContextCompat.getMainExecutor(context)) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    recording = null
                    if (event.hasError()) onError("Recording failed (${event.error})") else onSaved(output)
                }
            }
    }

    fun stop() {
        recording?.stop()
    }

    fun unbind() {
        recording?.close()
        recording = null
        provider?.unbindAll()
    }
}

@Composable
fun rememberInterviewCamera(): InterviewCamera {
    val context = LocalContext.current
    val camera = remember { InterviewCamera(context.applicationContext) }
    DisposableEffect(camera) { onDispose { camera.unbind() } }
    return camera
}

@Composable
fun CameraPreview(camera: InterviewCamera, modifier: Modifier = Modifier) {
    val owner = LocalLifecycleOwner.current
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                camera.bind(owner, this)
            }
        },
    )
}
