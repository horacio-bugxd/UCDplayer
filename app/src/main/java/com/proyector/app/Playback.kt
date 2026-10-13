package com.proyector.app

import android.content.Context
import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/** Reproductor único de la app: sigue sonando mientras navegas por la biblioteca. */
class Playback(ctx: Context) {
    val exo: ExoPlayer = ExoPlayer.Builder(ctx).build()

    var items by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    var index by mutableIntStateOf(0)
        private set
    var paused by mutableStateOf(true)
        private set

    val active: Boolean get() = items.isNotEmpty()

    init {
        exo.addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                paused = !playWhenReady
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                index = exo.currentMediaItemIndex
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) exo.playWhenReady = false
            }
        })
    }

    fun start(list: List<VideoItem>, startIndex: Int) {
        items = list
        index = startIndex
        exo.setMediaItems(list.map { MediaItem.fromUri(Uri.parse(it.uri)) }, startIndex, 0L)
        exo.prepare()
        exo.playWhenReady = true
    }

    fun togglePlay() {
        if (exo.playbackState == Player.STATE_ENDED) {
            exo.seekTo(exo.currentMediaItemIndex, 0L)
            exo.playWhenReady = true
        } else {
            exo.playWhenReady = !exo.playWhenReady
        }
    }

    fun next() {
        if (exo.hasNextMediaItem()) {
            exo.seekToNextMediaItem()
            exo.playWhenReady = true
        }
    }

    fun previous() {
        if (exo.hasPreviousMediaItem()) {
            exo.seekToPreviousMediaItem()
            exo.playWhenReady = true
        }
    }

    fun stop() {
        exo.stop()
        exo.clearMediaItems()
        items = emptyList()
        index = 0
        paused = true
    }

    fun release() {
        exo.release()
    }
}

data class Prog(val pos: Long, val dur: Long, val seekable: Boolean)

@Composable
fun rememberProgress(exo: ExoPlayer, enabled: Boolean): State<Prog> {
    val s = remember { mutableStateOf(Prog(0L, 0L, true)) }
    LaunchedEffect(enabled) {
        while (enabled) {
            s.value = Prog(
                exo.currentPosition,
                exo.duration.coerceAtLeast(0L),
                exo.isCurrentMediaItemSeekable
            )
            delay(250)
        }
    }
    return s
}

enum class Glyph { PLAY, PAUSE, NEXT, PREV, FOLDER }

@Composable
fun GlyphIcon(g: Glyph, tint: Color, dp: Dp = 24.dp) {
    Canvas(Modifier.size(dp)) {
        val w = size.width
        val h = size.height
        fun tri(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
            val p = Path()
            p.moveTo(x1 * w, y1 * h)
            p.lineTo(x2 * w, y2 * h)
            p.lineTo(x3 * w, y3 * h)
            p.close()
            drawPath(p, tint)
        }
        when (g) {
            Glyph.PLAY -> tri(0.30f, 0.18f, 0.30f, 0.82f, 0.82f, 0.50f)
            Glyph.PAUSE -> {
                drawRoundRect(tint, Offset(w * 0.22f, h * 0.18f), Size(w * 0.20f, h * 0.64f), CornerRadius(w * 0.05f))
                drawRoundRect(tint, Offset(w * 0.58f, h * 0.18f), Size(w * 0.20f, h * 0.64f), CornerRadius(w * 0.05f))
            }
            Glyph.NEXT -> {
                tri(0.18f, 0.22f, 0.18f, 0.78f, 0.68f, 0.50f)
                drawRoundRect(tint, Offset(w * 0.72f, h * 0.22f), Size(w * 0.10f, h * 0.56f), CornerRadius(w * 0.03f))
            }
            Glyph.PREV -> {
                tri(0.82f, 0.22f, 0.82f, 0.78f, 0.32f, 0.50f)
                drawRoundRect(tint, Offset(w * 0.18f, h * 0.22f), Size(w * 0.10f, h * 0.56f), CornerRadius(w * 0.03f))
            }
            Glyph.FOLDER -> {
                drawRoundRect(tint, Offset(w * 0.10f, h * 0.20f), Size(w * 0.36f, h * 0.22f), CornerRadius(w * 0.05f))
                drawRoundRect(tint, Offset(w * 0.10f, h * 0.30f), Size(w * 0.80f, h * 0.52f), CornerRadius(w * 0.08f))
            }
        }
    }
}

/** Barra de progreso ondulada, con toque y arrastre propios. */
@Composable
fun SeekBar(
    frac: Float,
    playing: Boolean,
    enabled: Boolean,
    color: Color,
    trackColor: Color,
    onChange: (Float) -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val amp by animateFloatAsState(if (playing && enabled) 1f else 0f, label = "amp")
    val inf = rememberInfiniteTransition(label = "wave")
    val phase by inf.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label = "phase"
    )
    val change by rememberUpdatedState(onChange)
    val finish by rememberUpdatedState(onFinish)

    Box(
        modifier
            .height(40.dp)
            .pointerInput(enabled) {
                if (enabled) {
                    detectTapGestures { o ->
                        change((o.x / size.width).coerceIn(0f, 1f))
                        finish()
                    }
                }
            }
            .pointerInput(enabled) {
                if (enabled) {
                    detectHorizontalDragGestures(
                        onDragStart = { o -> change((o.x / size.width).coerceIn(0f, 1f)) },
                        onHorizontalDrag = { c, _ ->
                            c.consume()
                            change((c.position.x / size.width).coerceIn(0f, 1f))
                        },
                        onDragEnd = { finish() },
                        onDragCancel = { finish() }
                    )
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val cy = size.height / 2f
            val stroke = 4.dp.toPx()
            val x = w * frac.coerceIn(0f, 1f)
            val a = amp * 3.dp.toPx()
            val wl = 22.dp.toPx()

            drawLine(trackColor, Offset(x, cy), Offset(w, cy), strokeWidth = stroke, cap = StrokeCap.Round)

            val path = Path()
            path.moveTo(0f, cy)
            var px = 0f
            while (px <= x) {
                path.lineTo(px, cy + a * sin(px / wl * 2f * PI.toFloat() + phase))
                px += 3f
            }
            drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
            drawCircle(color, radius = 9.dp.toPx(), center = Offset(x, cy))
        }
    }
}
