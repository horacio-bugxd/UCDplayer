package com.proyector.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class VideoItem(val uri: String, val name: String)
data class Playlist(val id: String, val name: String, val items: List<VideoItem>)

fun newId(): String = UUID.randomUUID().toString()

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
