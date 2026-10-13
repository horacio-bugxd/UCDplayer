package com.proyector.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ProyectorTheme { App() } }
    }
}

@Composable
fun ProyectorTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun App() {
    val ctx = LocalContext.current
    var lists by remember { mutableStateOf(Store.load(ctx)) }
    var openId by remember { mutableStateOf<String?>(null) }
    var playingId by remember { mutableStateOf<String?>(null) }

    fun update(newLists: List<Playlist>) {
        lists = newLists
        Store.save(ctx, newLists)
    }

    val playing = lists.find { it.id == playingId }
    val open = lists.find { it.id == openId }

    when {
        playing != null -> PlayerScreen(playing.items) { playingId = null }
        open != null -> PlaylistScreen(
            p = open,
            onBack = { openId = null },
            onChange = { np -> update(lists.map { if (it.id == np.id) np else it }) },
            onDelete = {
                update(lists.filter { it.id != open.id })
                openId = null
            },
            onPlay = { playingId = open.id }
        )
        else -> ListsScreen(
            lists = lists,
            onOpen = { openId = it },
            onCreate = { name ->
                val np = Playlist(newId(), name, emptyList())
                update(lists + np)
                openId = np.id
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListsScreen(lists: List<Playlist>, onOpen: (String) -> Unit, onCreate: (String) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Mis listas") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { name = ""; showDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Nueva lista") }
            )
        }
    ) { inner ->
        if (lists.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
                Text("Aún no tienes listas.\nToca \"Nueva lista\" para empezar.")
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(260.dp),
                modifier = Modifier.padding(inner),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(lists, key = { it.id }) { p ->
                    Card(onClick = { onOpen(p.id) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp)) {
                            Text(p.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(4.dp))
                            Text("${p.items.size} videos", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Nueva lista") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val n = name.trim()
                        if (n.isNotEmpty()) {
                            showDialog = false
                            onCreate(n)
                        }
                    }
                ) { Text("Crear") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Cancelar") } }
        )
    }
}

fun displayName(ctx: Context, uri: Uri): String {
    ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            val n = c.getString(0)
            if (!n.isNullOrBlank()) return n.substringBeforeLast('.')
        }
    }
    return "Video"
}

fun swap(list: List<VideoItem>, a: Int, b: Int): List<VideoItem> {
    if (a !in list.indices || b !in list.indices) return list
    val m = list.toMutableList()
    val t = m[a]
    m[a] = m[b]
    m[b] = t
    return m
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    p: Playlist,
    onBack: () -> Unit,
    onChange: (Playlist) -> Unit,
    onDelete: () -> Unit,
    onPlay: () -> Unit
) {
    val ctx = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val added = uris.map { uri ->
            try {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }
            VideoItem(uri.toString(), displayName(ctx, uri))
        }
        if (added.isNotEmpty()) onChange(p.copy(items = p.items + added))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Eliminar lista")
                    }
                }
            )
        }
    ) { inner ->
        Column(Modifier.padding(inner).padding(horizontal = 16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                Button(onClick = onPlay, enabled = p.items.isNotEmpty()) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Reproducir")
                }
                FilledTonalButton(onClick = { picker.launch(arrayOf("video/*")) }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Agregar videos")
                }
            }
            if (p.items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Esta lista está vacía.\nToca \"Agregar videos\".")
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                    itemsIndexed(p.items) { i, v ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${i + 1}", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.width(16.dp))
                                Text(v.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                IconButton(onClick = { onChange(p.copy(items = swap(p.items, i, i - 1))) }, enabled = i > 0) { Text("▲") }
                                IconButton(onClick = { onChange(p.copy(items = swap(p.items, i, i + 1))) }, enabled = i < p.items.lastIndex) { Text("▼") }
                                IconButton(onClick = { onChange(p.copy(items = p.items.filterIndexed { k, _ -> k != i })) }) {
                                    Icon(Icons.Default.Close, contentDescription = "Quitar")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Eliminar \"${p.name}\"?") },
            text = { Text("Los videos no se borran del teléfono, solo la lista.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } }
        )
    }
}
