package com.proyector.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent { ProyectorTheme { App() } }
    }
}

@Composable
fun ProyectorTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val base = if (Build.VERSION.SDK_INT >= 31) dynamicDarkColorScheme(ctx) else darkColorScheme()
    val scheme = base.copy(background = Color(0xFF0E0E11), surface = Color(0xFF0E0E11))
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val activity = ctx as ComponentActivity

    val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO
    else Manifest.permission.READ_EXTERNAL_STORAGE
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(perm) }

    var videos by remember { mutableStateOf<List<MediaVideo>?>(null) }
    LaunchedEffect(granted) {
        if (granted) videos = withContext(Dispatchers.IO) { loadVideos(ctx) }
    }

    val pb = remember { Playback(ctx) }
    DisposableEffect(Unit) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_STOP) pb.exo.pause()
        }
        activity.lifecycle.addObserver(obs)
        onDispose {
            activity.lifecycle.removeObserver(obs)
            pb.release()
        }
    }

    var lists by remember { mutableStateOf(Store.load(ctx)) }
    fun update(newLists: List<Playlist>) {
        lists = newLists
        Store.save(ctx, newLists)
    }

    var tab by remember { mutableIntStateOf(0) }
    var folder by remember { mutableStateOf<String?>(null) }
    var openListId by remember { mutableStateOf<String?>(null) }
    var picked by remember { mutableStateOf<Set<String>>(emptySet()) }
    var addDialog by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }

    val all = videos ?: emptyList()
    val f = folder
    val openList = lists.find { it.id == openListId }

    fun toItems(vs: List<MediaVideo>) = vs.map { VideoItem(it.uri, it.name) }
    fun playQueue(vs: List<MediaVideo>, i: Int) {
        pb.start(toItems(vs), i)
        fullscreen = true
    }
    fun toggle(uri: String) {
        picked = if (uri in picked) picked - uri else picked + uri
    }
    fun addTo(id: String) {
        val pl = lists.find { it.id == id } ?: return
        val existing = pl.items.map { it.uri }.toSet()
        val add = toItems(all.filter { it.uri in picked }).filter { it.uri !in existing }
        update(lists.map { if (it.id == id) it.copy(items = it.items + add) else it })
        Toast.makeText(ctx, "Agregado a \"${pl.name}\"", Toast.LENGTH_SHORT).show()
        picked = emptySet()
        addDialog = false
    }

    val inPlayer = fullscreen && pb.active
    BackHandler(enabled = !inPlayer && (picked.isNotEmpty() || f != null || openListId != null)) {
        when {
            picked.isNotEmpty() -> picked = emptySet()
            openListId != null -> openListId = null
            else -> folder = null
        }
    }

    if (inPlayer) {
        PlayerScreen(pb) { fullscreen = false }
    } else {
        Scaffold(
            bottomBar = {
                Column {
                    if (pb.active) MiniPlayer(pb) { fullscreen = true }
                    NavigationBar {
                        val go = { i: Int ->
                            tab = i
                            folder = null
                            openListId = null
                            picked = emptySet()
                        }
                        NavigationBarItem(
                            selected = tab == 0,
                            onClick = { go(0) },
                            icon = { Icon(Icons.Default.Home, contentDescription = null) },
                            label = { Text("Inicio") }
                        )
                        NavigationBarItem(
                            selected = tab == 1,
                            onClick = { go(1) },
                            icon = { GlyphIcon(Glyph.FOLDER, LocalContentColor.current) },
                            label = { Text("Carpetas") }
                        )
                        NavigationBarItem(
                            selected = tab == 2,
                            onClick = { go(2) },
                            icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                            label = { Text("Listas") }
                        )
                    }
                }
            }
        ) { inner ->
            Box(Modifier.padding(inner).fillMaxSize()) {
                when {
                    !granted -> PermissionScreen { launcher.launch(perm) }
                    videos == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    openList != null -> PlaylistDetail(
                        p = openList,
                        onBack = { openListId = null },
                        onChange = { np -> update(lists.map { if (it.id == np.id) np else it }) },
                        onDelete = {
                            update(lists.filter { it.id != openList.id })
                            openListId = null
                        },
                        onPlay = { i ->
                            pb.start(openList.items, i)
                            fullscreen = true
                        }
                    )
                    f != null -> {
                        val vs = all.filter { it.folder == f }
                        VideoListScreen(
                            title = f,
                            videos = vs,
                            picked = picked,
                            onBack = { folder = null },
                            onPlay = { i -> playQueue(vs, i) },
                            onToggle = { toggle(it) },
                            onAddPicked = { addDialog = true },
                            onClear = { picked = emptySet() }
                        )
                    }
                    tab == 0 -> HomeScreen(
                        videos = all,
                        lists = lists,
                        picked = picked,
                        onOpenFolder = { folder = it; tab = 1 },
                        onOpenList = { openListId = it; tab = 2 },
                        onPlay = { vs, i -> playQueue(vs, i) },
                        onToggle = { toggle(it) },
                        onAddPicked = { addDialog = true },
                        onClear = { picked = emptySet() }
                    )
                    tab == 1 -> FoldersScreen(all) { folder = it }
                    else -> ListsScreen(
                        lists = lists,
                        onOpen = { openListId = it },
                        onCreate = { name ->
                            val np = Playlist(newId(), name, emptyList())
                            update(lists + np)
                            openListId = np.id
                        }
                    )
                }
            }
        }
    }

    if (addDialog) {
        AddToListDialog(
            lists = lists,
            count = picked.size,
            onPick = { addTo(it) },
            onNew = { name ->
                val np = Playlist(newId(), name, emptyList())
                update(lists + np)
                addTo(np.id)
            },
            onDismiss = { addDialog = false }
        )
    }
}
