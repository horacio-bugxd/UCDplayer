package com.proyector.app

import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(pb: Playback, onExit: () -> Unit) {
    val ctx = LocalContext.current
    val activity = ctx as ComponentActivity
    val exo = pb.exo
    BackHandler(onBack = onExit)

    val modes = remember {
        listOf(
            AspectRatioFrameLayout.RESIZE_MODE_FIT to "Ajustar",
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Zoom",
            AspectRatioFrameLayout.RESIZE_MODE_FILL to "Estirar"
        )
    }
    var modeIdx by remember { mutableIntStateOf(0) }
    var showControls by remember { mutableStateOf(false) }
    var dragFrac by remember { mutableStateOf<Float?>(null) }
    var interaction by remember { mutableIntStateOf(0) }
    val prog by rememberProgress(exo, showControls)

    // Horizontal, pantalla completa y pantalla siempre encendida mientras se ve el video
    DisposableEffect(Unit) {
        val w = activity.window
        val c = WindowCompat.getInsetsController(w, w.decorView)
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c.hide(WindowInsetsCompat.Type.systemBars())
        w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            c.show(WindowInsetsCompat.Type.systemBars())
            w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    // Oculta los controles 4 s después de la última interacción
    LaunchedEffect(showControls, interaction) {
        if (showControls) {
            delay(4000)
            if (dragFrac == null) showControls = false
        }
    }

    val seekable = prog.dur > 0 && prog.seekable
    val notSeekable = exo.playbackState == Player.STATE_READY && !seekable

    fun onSeekChange(f: Float) {
        if (dragFrac == null) exo.setSeekParameters(SeekParameters.CLOSEST_SYNC)
        dragFrac = f
        interaction++
        exo.seekTo((f * prog.dur).toLong())
    }

    fun onSeekFinish() {
        val f = dragFrac
        exo.setSeekParameters(SeekParameters.EXACT)
        if (f != null) exo.seekTo((f * prog.dur).toLong())
        dragFrac = null
        interaction++
    }

    fun seekBy(delta: Long) {
        val t = exo.currentPosition + delta
        exo.seekTo(if (prog.dur > 0) t.coerceIn(0L, prog.dur) else t.coerceAtLeast(0L))
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
            update = { it.resizeMode = modes[modeIdx].first },
            onRelease = { it.player = null }
        )

        // Gestos: 1 toque = controles, 2 toques = pausa, deslizar = cambiar de video
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { pb.togglePlay() },
                        onTap = { showControls = !showControls }
                    )
                }
                .pointerInput(Unit) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onHorizontalDrag = { _, d -> total += d },
                        onDragEnd = {
                            if (total < -120f) pb.next() else if (total > 120f) pb.previous()
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
            val shownPos = dragFrac?.let { (it * prog.dur).toLong() } ?: prog.pos
            val frac = dragFrac ?: if (prog.dur > 0) prog.pos.toFloat() / prog.dur else 0f
            val cur = pb.items.getOrNull(pb.index)

            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(110.dp)
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)))
                )
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(170.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
                )

                Row(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .displayCutoutPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onExit) {
                        Icon(Icons.Default.Close, contentDescription = "Volver", tint = Color.White)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            cur?.name ?: "",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${pb.index + 1} / ${pb.items.size}",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    TextButton(
                        onClick = {
                            modeIdx = (modeIdx + 1) % modes.size
                            interaction++
                        },
                        colors = white
                    ) { Text("Aspecto: ${modes[modeIdx].second}") }
                }

                Row(
                    Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(onClick = { seekBy(-10_000L) }, colors = white) { Text("−10 s") }
                    IconButton(onClick = { pb.previous(); interaction++ }, modifier = Modifier.size(56.dp)) {
                        GlyphIcon(Glyph.PREV, Color.White, 32.dp)
                    }
                    Box(
                        Modifier
                            .size(76.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable { pb.togglePlay(); interaction++ },
                        contentAlignment = Alignment.Center
                    ) {
                        GlyphIcon(
                            if (pb.paused) Glyph.PLAY else Glyph.PAUSE,
                            MaterialTheme.colorScheme.onPrimary,
                            40.dp
                        )
                    }
                    IconButton(onClick = { pb.next(); interaction++ }, modifier = Modifier.size(56.dp)) {
                        GlyphIcon(Glyph.NEXT, Color.White, 32.dp)
                    }
                    TextButton(onClick = { seekBy(10_000L) }, colors = white) { Text("+10 s") }
                }

                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .displayCutoutPadding()
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    if (notSeekable) {
                        Text(
                            "Este archivo no permite adelantar ni retroceder",
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(fmtTime(shownPos), color = Color.White, style = MaterialTheme.typography.labelLarge)
                        SeekBar(
                            frac = frac,
                            playing = !pb.paused,
                            enabled = seekable,
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.White.copy(alpha = 0.3f),
                            onChange = { onSeekChange(it) },
                            onFinish = { onSeekFinish() },
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                        )
                        Text(fmtTime(prog.dur), color = Color.White, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
