package com.agent008.writer

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

class LocalStore(context: Context) {
    private val app = context.applicationContext
    private val securePrefs by lazy {
        val masterKey = MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            app, "agent008_secure", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    private val prefs by lazy { app.getSharedPreferences("agent008_local", Context.MODE_PRIVATE) }

    fun apiKey(): String = securePrefs.getString("deepseek_api_key", "").orEmpty()
    fun saveApiKey(key: String) { securePrefs.edit().putString("deepseek_api_key", key.trim()).apply() }
    fun removeApiKey() { securePrefs.edit().remove("deepseek_api_key").apply() }

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
