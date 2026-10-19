package com.agent008.writer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class LocalStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("agent008_local", Context.MODE_PRIVATE)

    fun projects(): List<WriterProject> {
        val json = runCatching { JSONArray(prefs.getString("projects", "[]")) }.getOrElse { JSONArray() }
        return (0 until json.length()).mapNotNull { i ->
            runCatching {
                val item = json.getJSONObject(i)
                WriterProject(item.getString("id"), item.getString("title"), item.optString("text", ""))
            }.getOrNull()
        }
    }

    fun saveProject(project: WriterProject) {
        val all = projects().filterNot { it.id == project.id }.toMutableList()
        all.add(0, project)
        val array = JSONArray()
        all.forEach { array.put(JSONObject().put("id", it.id).put("title", it.title).put("text", it.text)) }
        prefs.edit().putString("projects", array.toString()).apply()
    }
}

data class WriterProject(val id: String, val title: String, val text: String)
