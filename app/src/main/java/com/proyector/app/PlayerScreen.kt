package com.proyector.app

import android.net.Uri
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(items: List<VideoItem>, start: Int, onExit: () -> Unit) {
    val ctx = LocalContext.current
    val activity = ctx as ComponentActivity
    BackHandler(onBack = onExit)

    val exo = remember {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItems(items.map { MediaItem.fromUri(Uri.parse(it.uri)) }, start, 0L)
            prepare()
            playWhenReady = true
        }
    }

    val modes = remember {
        listOf(
            AspectRatioFrameLayout.RESIZE_MODE_FIT to "Ajustar",
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Zoom",
            AspectRatioFrameLayout.RESIZE_MODE_FILL to "Estirar"
        )
    }
    var modeIdx by remember { mutableIntStateOf(0) }
    var index by remember { mutableIntStateOf(start) }
    var paused by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    var interaction by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        val w = activity.window
        val c = WindowCompat.getInsetsController(w, w.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c.hide(WindowInsetsCompat.Type.systemBars())
        w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            c.show(WindowInsetsCompat.Type.systemBars())
            w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                paused = !playWhenReady
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                index = exo.currentMediaItemIndex
            }
        }
        exo.addListener(listener)
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_STOP) exo.pause()
        }
        activity.lifecycle.addObserver(obs)
        onDispose {
            activity.lifecycle.removeObserver(obs)
            exo.removeListener(listener)
            exo.release()
        }
    }

    // Oculta los controles 4 s después de la última interacción
    LaunchedEffect(showControls, interaction) {
        if (showControls) {
            delay(4000)
            if (!dragging) showControls = false
        }
    }

    // Actualiza posición y duración mientras los controles están visibles
    LaunchedEffect(showControls) {
        while (showControls) {
            if (!dragging) position = exo.currentPosition
            duration = exo.duration.coerceAtLeast(0L)
            delay(250)
        }
    }

    fun seekBy(delta: Long) {
        val d = exo.duration
        val t = exo.currentPosition + delta
        exo.seekTo(if (d > 0) t.coerceIn(0L, d) else t.coerceAtLeast(0L))
        position = exo.currentPosition
        interaction++
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                PlayerView(c).apply {
                    useController = false
                    player = exo
                }
            },
            update = { it.resizeMode = modes[modeIdx].first }
        )

        // Gestos: 1 toque = controles, 2 toques = pausa, deslizar = cambiar de video
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { exo.playWhenReady = !exo.playWhenReady },
                        onTap = { showControls = !showControls }
                    )
                }
                .pointerInput(Unit) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onHorizontalDrag = { _, d -> total += d },
                        onDragEnd = {
                            if (total < -120f && exo.hasNextMediaItem()) {
                                exo.seekToNextMediaItem()
                                exo.playWhenReady = true
                            } else if (total > 120f && exo.hasPreviousMediaItem()) {
                                exo.seekToPreviousMediaItem()
                                exo.playWhenReady = true
                            }
                        }
                    )
                }
        )

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            val white = ButtonDefaults.textButtonColors(contentColor = Color.White)
            Box(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .pointerInput(Unit) { detectTapGestures(onTap = { interaction++ }) }
                        .displayCutoutPadding()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onExit) {
                        Icon(Icons.Default.Close, contentDescription = "Salir", tint = Color.White)
                    }
                    Text(
                        items.getOrNull(index)?.name ?: "",
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    TextButton(
                        onClick = {
                            modeIdx = (modeIdx + 1) % modes.size
                            interaction++
                        },
                        colors = white
                    ) { Text("Aspecto: ${modes[modeIdx].second}") }
                }

                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .pointerInput(Unit) { detectTapGestures(onTap = { interaction++ }) }
                        .displayCutoutPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    val max = maxOf(duration, 1L).toFloat()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(fmtTime(if (dragging) dragValue.toLong() else position), color = Color.White)
                        Slider(
                            value = (if (dragging) dragValue else position.toFloat()).coerceIn(0f, max),
                            onValueChange = {
                                if (!dragging) exo.setSeekParameters(SeekParameters.CLOSEST_SYNC)
                                dragging = true
                                dragValue = it
                                interaction++
                                exo.seekTo(it.toLong())
                            },
                            onValueChangeFinished = {
                                exo.setSeekParameters(SeekParameters.EXACT)
                                exo.seekTo(dragValue.toLong())
                                position = dragValue.toLong()
                                dragging = false
                                interaction++
                            },
                            valueRange = 0f..max,
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                        )
                        Text(fmtTime(duration), color = Color.White)
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                if (exo.hasPreviousMediaItem()) {
                                    exo.seekToPreviousMediaItem()
                                    exo.playWhenReady = true
                                }
                                interaction++
                            },
                            colors = white
                        ) { Text("◀ Anterior") }
                        TextButton(onClick = { seekBy(-10_000L) }, colors = white) { Text("−10 s") }
                        TextButton(
                            onClick = {
                                exo.playWhenReady = !exo.playWhenReady
                                interaction++
                            },
                            colors = white
                        ) { Text(if (paused) "Reproducir" else "Pausar") }
                        TextButton(onClick = { seekBy(10_000L) }, colors = white) { Text("+10 s") }
                        TextButton(
                            onClick = {
                                if (exo.hasNextMediaItem()) {
                                    exo.seekToNextMediaItem()
                                    exo.playWhenReady = true
                                }
                                interaction++
                            },
                            colors = white
                        ) { Text("Siguiente ▶") }
                    }
                }
            }
        }
    }
}
