package com.proyector.app

import android.Manifest
import android.content.pm.ActivityInfo
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
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

    var lists by remember { mutableStateOf(Store.load(ctx)) }
    fun update(newLists: List<Playlist>) {
        lists = newLists
        Store.save(ctx, newLists)
    }

    var tab by remember { mutableIntStateOf(0) }
    var folder by remember { mutableStateOf<String?>(null) }
    var openListId by remember { mutableStateOf<String?>(null) }
    var play by remember { mutableStateOf<Pair<List<VideoItem>, Int>?>(null) }
    var picked by remember { mutableStateOf<Set<String>>(emptySet()) }
    var addDialog by remember { mutableStateOf(false) }

    val all = videos ?: emptyList()
    val f = folder
    val cur = play
    val openList = lists.find { it.id == openListId }

    fun toItems(vs: List<MediaVideo>) = vs.map { VideoItem(it.uri, it.name) }
    fun toggle(uri: String) {
        picked = if (uri in picked) picked - uri else picked + uri
    }
    fun pickedItems(): List<VideoItem> = toItems(all.filter { it.uri in picked })
    fun addTo(id: String) {
        val pl = lists.find { it.id == id } ?: return
        val existing = pl.items.map { it.uri }.toSet()
        val add = pickedItems().filter { it.uri !in existing }
        update(lists.map { if (it.id == id) it.copy(items = it.items + add) else it })
        Toast.makeText(ctx, "Agregado a \"${pl.name}\"", Toast.LENGTH_SHORT).show()
        picked = emptySet()
        addDialog = false
    }

    BackHandler(enabled = cur == null && (picked.isNotEmpty() || f != null || openListId != null)) {
        when {
            picked.isNotEmpty() -> picked = emptySet()
            openListId != null -> openListId = null
            else -> folder = null
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (cur != null) {
            PlayerScreen(cur.first, cur.second) { play = null }
        } else {
            Row(Modifier.fillMaxSize().safeDrawingPadding()) {
                val tabs = listOf(
                    "Carpetas" to Icons.Default.Home,
                    "Videos" to Icons.Default.PlayArrow,
                    "Listas" to Icons.AutoMirrored.Filled.List
                )
                NavigationRail(
                    containerColor = Color.Transparent,
                    windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)
                ) {
                    Spacer(Modifier.weight(1f))
                    tabs.forEachIndexed { i, t ->
                        NavigationRailItem(
                            selected = tab == i,
                            onClick = {
                                tab = i
                                folder = null
                                openListId = null
                                picked = emptySet()
                            },
                            icon = { Icon(t.second, contentDescription = null) },
                            label = { Text(t.first) }
                        )
                    }
                    Spacer(Modifier.weight(1f))
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
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
                            onPlay = { i -> play = openList.items to i }
                        )
                        tab == 0 && f != null -> {
                            val vs = all.filter { it.folder == f }
                            VideoListScreen(
                                title = f,
                                videos = vs,
                                picked = picked,
                                onBack = { folder = null },
                                onPlay = { i -> play = toItems(vs) to i },
                                onToggle = { toggle(it) },
                                onAddPicked = { addDialog = true },
                                onClear = { picked = emptySet() }
                            )
                        }
                        tab == 0 -> FoldersScreen(all) { folder = it }
                        tab == 1 -> VideoListScreen(
                            title = "Videos",
                            videos = all,
                            picked = picked,
                            onBack = null,
                            onPlay = { i -> play = toItems(all) to i },
                            onToggle = { toggle(it) },
                            onAddPicked = { addDialog = true },
                            onClear = { picked = emptySet() }
                        )
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
