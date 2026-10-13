package com.proyector.app

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val thumbCache = LruCache<String, Bitmap>(150)

@Composable
fun Thumb(uri: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(thumbCache.get(uri), uri) {
        if (value == null && uri.isNotEmpty() && Build.VERSION.SDK_INT >= 29) {
            value = withContext(Dispatchers.IO) {
                try {
                    ctx.contentResolver.loadThumbnail(Uri.parse(uri), Size(360, 270), null)
                        .also { thumbCache.put(uri, it) }
                } catch (e: Exception) {
                    null
                }
            }
        }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        bmp?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
fun Header(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        actions()
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp)
    )
}

@Composable
fun SelectionHeader(count: Int, onClear: () -> Unit, onAdd: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClear) { Icon(Icons.Default.Close, contentDescription = "Cancelar") }
        Text("$count seleccionados", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        Button(onClick = onAdd) { Text("Agregar a lista") }
    }
}

@Composable
fun EmptyText(text: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun PermissionScreen(onAsk: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Proyector necesita acceso a tus videos", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Elige \"Permitir todo\" para ver todas tus carpetas.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAsk) { Text("Dar acceso") }
    }
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit) {
    TextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text("Buscar videos") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) { Icon(Icons.Default.Close, contentDescription = "Borrar") }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    )
}

@Composable
fun FolderCard(name: String, count: Int, cover: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick)) {
        Thumb(cover, Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(24.dp)))
        Spacer(Modifier.height(8.dp))
        Text(
            name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Text(
            "$count videos",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 0.dp)
        )
        Spacer(Modifier.height(4.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoRow(v: MediaVideo, selected: Boolean, onClick: () -> Unit, onLong: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Thumb(v.uri, Modifier.width(120.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)))
            Text(
                fmtTime(v.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(5.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 5.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(v.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                v.folder,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = "Seleccionado", tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
        }
    }
}

@Composable
fun HomeScreen(
    videos: List<MediaVideo>,
    lists: List<Playlist>,
    picked: Set<String>,
    onOpenFolder: (String) -> Unit,
    onOpenList: (String) -> Unit,
    onPlay: (List<MediaVideo>, Int) -> Unit,
    onToggle: (String) -> Unit,
    onAddPicked: () -> Unit,
    onClear: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(videos, query) {
        if (query.isBlank()) videos else videos.filter { it.name.contains(query, ignoreCase = true) }
    }
    val folders = remember(videos) {
        videos.groupBy { it.folder }.toList().sortedBy { it.first.lowercase() }
    }
    val selecting = picked.isNotEmpty()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            if (selecting) SelectionHeader(picked.size, onClear, onAddPicked) else Header("Proyector")
        }
        item { SearchField(query) { query = it } }
        if (query.isBlank()) {
            if (folders.isNotEmpty()) {
                item { SectionTitle("Carpetas") }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(folders, key = { it.first }) { f ->
                            FolderCard(f.first, f.second.size, f.second.first().uri, Modifier.width(150.dp)) {
                                onOpenFolder(f.first)
                            }
                        }
                    }
                }
            }
            if (lists.isNotEmpty()) {
                item { SectionTitle("Mis listas") }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(lists, key = { it.id }) { p ->
                            FolderCard(p.name, p.items.size, p.items.firstOrNull()?.uri ?: "", Modifier.width(150.dp)) {
                                onOpenList(p.id)
                            }
                        }
                    }
                }
            }
            item { SectionTitle("Videos recientes") }
        }
        if (filtered.isEmpty()) {
            item { EmptyText(if (videos.isEmpty()) "No se encontraron videos en el teléfono." else "Sin resultados.") }
        } else {
            itemsIndexed(filtered, key = { _, v -> v.uri }) { i, v ->
                Box(Modifier.padding(horizontal = 8.dp)) {
                    VideoRow(
                        v,
                        selected = v.uri in picked,
                        onClick = { if (selecting) onToggle(v.uri) else onPlay(filtered, i) },
                        onLong = { onToggle(v.uri) }
                    )
                }
            }
        }
    }
}

@Composable
fun FoldersScreen(videos: List<MediaVideo>, onOpen: (String) -> Unit) {
    val folders = remember(videos) {
        videos.groupBy { it.folder }.toList().sortedBy { it.first.lowercase() }
    }
    Column(Modifier.fillMaxSize()) {
        Header("Carpetas")
        if (folders.isEmpty()) {
            EmptyText("No se encontraron videos en el teléfono.")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(folders, key = { it.first }) { f ->
                    FolderCard(f.first, f.second.size, f.second.first().uri, Modifier.fillMaxWidth()) {
                        onOpen(f.first)
                    }
                }
            }
        }
    }
}

@Composable
fun VideoListScreen(
    title: String,
    videos: List<MediaVideo>,
    picked: Set<String>,
    onBack: (() -> Unit)?,
    onPlay: (Int) -> Unit,
    onToggle: (String) -> Unit,
    onAddPicked: () -> Unit,
    onClear: () -> Unit
) {
    val selecting = picked.isNotEmpty()
    Column(Modifier.fillMaxSize()) {
        if (selecting) {
            SelectionHeader(picked.size, onClear, onAddPicked)
        } else {
            Header(title, onBack = onBack, actions = {
                if (videos.isNotEmpty()) {
                    IconButton(onClick = { onPlay(0) }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Reproducir todo")
                    }
                }
            })
        }
        if (videos.isEmpty()) {
            EmptyText("No hay videos aquí.")
        } else {
            LazyColumn(contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                itemsIndexed(videos, key = { _, v -> v.uri }) { i, v ->
                    VideoRow(
                        v,
                        selected = v.uri in picked,
                        onClick = { if (selecting) onToggle(v.uri) else onPlay(i) },
                        onLong = { onToggle(v.uri) }
                    )
                }
            }
        }
    }
}

@Composable
fun ListsScreen(lists: List<Playlist>, onOpen: (String) -> Unit, onCreate: (String) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        Header("Listas", actions = {
            FilledTonalButton(onClick = { name = ""; showDialog = true }) { Text("Nueva") }
        })
        if (lists.isEmpty()) {
            EmptyText("Aún no tienes listas.\nMantén pulsado un video para seleccionarlo y agregarlo a una lista.")
        } else {
            LazyColumn(contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                items(lists, key = { it.id }) { p ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onOpen(p.id) }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Thumb(p.items.firstOrNull()?.uri ?: "", Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${p.items.size} videos",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                    enabled = name.isNotBlank(),
                    onClick = { showDialog = false; onCreate(name.trim()) }
                ) { Text("Crear") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Cancelar") } }
        )
    }
}

fun swap(list: List<VideoItem>, a: Int, b: Int): List<VideoItem> {
    if (a !in list.indices || b !in list.indices) return list
    val m = list.toMutableList()
    val t = m[a]
    m[a] = m[b]
    m[b] = t
    return m
}

@Composable
fun PlaylistDetail(
    p: Playlist,
    onBack: () -> Unit,
    onChange: (Playlist) -> Unit,
    onDelete: () -> Unit,
    onPlay: (Int) -> Unit
) {
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Header(p.name, onBack = onBack, actions = {
            if (p.items.isNotEmpty()) {
                IconButton(onClick = { onPlay(0) }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Reproducir")
                }
            }
            IconButton(onClick = { confirm = true }) {
                Icon(Icons.Default.Delete, contentDescription = "Eliminar lista")
            }
        })
        if (p.items.isEmpty()) {
            EmptyText("Lista vacía.\nEn Inicio o Carpetas, mantén pulsado un video para seleccionarlo y agregarlo aquí.")
        } else {
            LazyColumn(contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                itemsIndexed(p.items) { i, v ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onPlay(i) }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Thumb(v.uri, Modifier.width(88.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)))
                        Spacer(Modifier.width(12.dp))
                        Text(v.name, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        IconButton(
                            onClick = { onChange(p.copy(items = swap(p.items, i, i - 1))) },
                            enabled = i > 0,
                            modifier = Modifier.size(36.dp)
                        ) { Text("▲") }
                        IconButton(
                            onClick = { onChange(p.copy(items = swap(p.items, i, i + 1))) },
                            enabled = i < p.items.lastIndex,
                            modifier = Modifier.size(36.dp)
                        ) { Text("▼") }
                        IconButton(
                            onClick = { onChange(p.copy(items = p.items.filterIndexed { k, _ -> k != i })) },
                            modifier = Modifier.size(36.dp)
                        ) { Icon(Icons.Default.Close, contentDescription = "Quitar") }
                    }
                }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("¿Eliminar \"${p.name}\"?") },
            text = { Text("Los videos no se borran del teléfono, solo la lista.") },
            confirmButton = { TextButton(onClick = { confirm = false; onDelete() }) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
fun AddToListDialog(
    lists: List<Playlist>,
    count: Int,
    onPick: (String) -> Unit,
    onNew: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agregar $count videos a…") },
        text = {
            Column {
                if (lists.isNotEmpty()) {
                    LazyColumn(Modifier.heightIn(max = 160.dp)) {
                        items(lists, key = { it.id }) { p ->
                            TextButton(onClick = { onPick(p.id) }, modifier = Modifier.fillMaxWidth()) {
                                Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("O crear lista nueva") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onNew(name.trim()) }) { Text("Crear y agregar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun MiniPlayer(pb: Playback, onOpen: () -> Unit) {
    val cur = pb.items.getOrNull(pb.index) ?: return
    val prog by rememberProgress(pb.exo, true)
    val frac = if (prog.dur > 0) (prog.pos.toFloat() / prog.dur).coerceIn(0f, 1f) else 0f
    val tint = MaterialTheme.colorScheme.onSurface

    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Column {
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Thumb(cur.uri, Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(cur.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${pb.index + 1} / ${pb.items.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { pb.togglePlay() }) {
                    GlyphIcon(if (pb.paused) Glyph.PLAY else Glyph.PAUSE, tint, 28.dp)
                }
                IconButton(onClick = { pb.next() }) { GlyphIcon(Glyph.NEXT, tint, 28.dp) }
                IconButton(onClick = { pb.stop() }) { Icon(Icons.Default.Close, contentDescription = "Detener") }
            }
            LinearProgressIndicator(
                progress = { frac },
                modifier = Modifier.fillMaxWidth().height(3.dp)
            )
        }
    }
}
