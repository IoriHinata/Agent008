package com.agent008.writer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class DeepSeekClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun complete(apiKey: String, messages: List<Pair<String, String>>): String =
        withContext(Dispatchers.IO) {
            require(apiKey.isNotBlank()) { "Сначала добавьте API-ключ в настройках." }
            val messageArray = JSONArray()
            messages.forEach { (role, content) ->
                messageArray.put(JSONObject().put("role", role).put("content", content))
            }
            val payload = JSONObject()
                .put("model", "deepseek-chat")
                .put("messages", messageArray)
                .put("stream", false)
                .put("temperature", 0.8)
            val request = Request.Builder()
                .url("https://api.deepseek.com/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val detail = runCatching { JSONObject(body).optJSONObject("error")?.optString("message") }.getOrNull()
                    throw IllegalStateException("DeepSeek API (${response.code}): ${detail ?: body.take(300)}")
                }
                JSONObject(body).optJSONArray("choices")
                    ?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
                    ?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("DeepSeek вернул пустой ответ.")
            }
        }
}
