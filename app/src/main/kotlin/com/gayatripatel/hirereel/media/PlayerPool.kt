package com.gayatripatel.hirereel.media

import android.content.Context
import android.graphics.Color
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.gayatripatel.hirereel.core.feed.PlayerPoolPolicy

/**
 * A small pool of ExoPlayers for the vertical job-video feed, sized by [PlayerPoolPolicy]:
 * the visible page plays muted on loop, its neighbours are pre-buffered, the rest are released.
 * Buffers are kept short because feed clips are watched for seconds, not minutes.
 */
class PlayerPool(private val context: Context) {

    private val players = mutableStateMapOf<Int, ExoPlayer>()
    private var urls: List<String> = emptyList()

    fun playerFor(page: Int): ExoPlayer? = players[page]

    fun onPage(current: Int, pageUrls: List<String>, isScrolling: Boolean) {
        if (pageUrls != urls) {
            releaseAll()
            urls = pageUrls
        }
        val plan = PlayerPoolPolicy.plan(current, urls.size, players.keys.toSet(), isScrolling)
        plan.release.forEach { players.remove(it)?.release() }
        plan.prepare.forEach { page -> players[page] = createPlayer(urls[page]) }
        players.forEach { (page, player) -> player.playWhenReady = page == plan.play }
    }

    fun pause() = players.values.forEach { it.playWhenReady = false }

    fun releaseAll() {
        players.values.forEach { it.release() }
        players.clear()
    }

    @OptIn(UnstableApi::class)
    private fun createPlayer(url: String): ExoPlayer {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(2_000, 8_000, 500, 1_000)
            .build()
        return ExoPlayer.Builder(context).setLoadControl(loadControl).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            prepare()
        }
    }
}

/** Surface for one feed page. Transparent until the first frame, so the poster underneath shows while buffering. */
@OptIn(UnstableApi::class)
@Composable
fun FeedVideoSurface(player: Player?, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                setShutterBackgroundColor(Color.TRANSPARENT)
                setKeepContentOnPlayerReset(true)
            }
        },
        update = { it.player = player },
        onRelease = { it.player = null },
    )
}
