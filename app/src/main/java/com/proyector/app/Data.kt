package com.proyector.app

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class VideoItem(val uri: String, val name: String)
data class Playlist(val id: String, val name: String, val items: List<VideoItem>)
data class MediaVideo(val uri: String, val name: String, val folder: String, val durationMs: Long)

fun newId(): String = UUID.randomUUID().toString()

fun fmtTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0L)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

fun loadVideos(ctx: Context): List<MediaVideo> {
    val out = mutableListOf<MediaVideo>()
    val base = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    val proj = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
        MediaStore.Video.Media.DURATION
    )
    try {
        ctx.contentResolver.query(base, proj, null, null, "${MediaStore.Video.Media.DATE_ADDED} DESC")?.use { c ->
            val idC = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameC = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val bucketC = c.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            val durC = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            while (c.moveToNext()) {
                val uri = ContentUris.withAppendedId(base, c.getLong(idC))
                out.add(
                    MediaVideo(
                        uri = uri.toString(),
                        name = (c.getString(nameC) ?: "Video").substringBeforeLast('.'),
                        folder = c.getString(bucketC) ?: "Otros",
                        durationMs = c.getLong(durC)
                    )
                )
            }
        }
    } catch (_: Exception) {
    }
    return out
}

object Store {
    private fun file(ctx: Context) = File(ctx.filesDir, "playlists.json")

    fun load(ctx: Context): List<Playlist> {
        return try {
            val f = file(ctx)
            if (!f.exists()) return emptyList()
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val items = o.getJSONArray("items")
                Playlist(
                    o.getString("id"),
                    o.getString("name"),
                    (0 until items.length()).map { j ->
                        val v = items.getJSONObject(j)
                        VideoItem(v.getString("uri"), v.getString("name"))
                    }
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(ctx: Context, lists: List<Playlist>) {
        val arr = JSONArray()
        lists.forEach { p ->
            val items = JSONArray()
            p.items.forEach { items.put(JSONObject().put("uri", it.uri).put("name", it.name)) }
            arr.put(JSONObject().put("id", p.id).put("name", p.name).put("items", items))
        }
        file(ctx).writeText(arr.toString())
    }
}
