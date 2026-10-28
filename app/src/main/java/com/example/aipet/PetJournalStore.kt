package com.example.aipet

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Local-only companion journal. Chat and mood notes never leave the device unless the user explicitly sends text to an AI provider. */
data class PetChatEntry(val role: String, val content: String, val timestamp: Long) {
    val displayName: String get() = if (role == "user") "我" else "小伙伴"
    fun formattedTime(): String = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}

data class MoodEntry(val mood: Int, val note: String, val timestamp: Long) {
    fun formattedTime(): String = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
    fun moodLabel(): String = when (mood) {
        1 -> "有点低落"
        2 -> "不太顺利"
        3 -> "还算平稳"
        4 -> "心情不错"
        else -> "特别开心"
    }
}

object PetChatStore {
    private const val PREFS = "pet_journal"
    private const val KEY_CHAT = "chat_log_json"
    private const val MAX_ENTRIES = 120

    fun append(context: Context, role: String, content: String) {
        if (role !in listOf("user", "assistant") || content.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val entries = parseChat(prefs.getString(KEY_CHAT, "[]") ?: "[]").toMutableList()
        entries.add(PetChatEntry(role, content.trim().take(3000), System.currentTimeMillis()))
        val json = JSONArray()
        entries.takeLast(MAX_ENTRIES).forEach { entry ->
            json.put(JSONObject().put("role", entry.role).put("content", entry.content).put("ts", entry.timestamp))
        }
        prefs.edit().putString(KEY_CHAT, json.toString()).apply()
    }

    fun recent(context: Context, limit: Int = 40): List<PetChatEntry> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_CHAT, "[]") ?: "[]"
        return parseChat(raw).takeLast(limit.coerceAtLeast(0))
    }

    fun historyForApi(context: Context, limit: Int = 10): List<JSONObject> = recent(context, limit).map {
        JSONObject().put("role", it.role).put("content", it.content)
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_CHAT, "[]").apply()
    }

    private fun parseChat(raw: String): List<PetChatEntry> = try {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            val obj = array.optJSONObject(index) ?: return@mapNotNull null
            val role = obj.optString("role")
            val content = obj.optString("content")
            if (role !in listOf("user", "assistant") || content.isBlank()) null
            else PetChatEntry(role, content, obj.optLong("ts", 0L))
        }
    } catch (_: Exception) { emptyList() }
}

object PetMoodJournal {
    private const val PREFS = "pet_journal"
    private const val KEY_MOODS = "mood_log_json"
    private const val KEY_MOOD_CHECK_COUNT = "mood_check_count"
    private const val MAX_ENTRIES = 30

    fun add(context: Context, mood: Int, note: String): MoodEntry {
        val safeMood = mood.coerceIn(1, 5)
        val safeNote = note.trim().take(240)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val entries = parse(prefs.getString(KEY_MOODS, "[]") ?: "[]").toMutableList()
        val entry = MoodEntry(safeMood, safeNote, System.currentTimeMillis())
        entries.add(0, entry)
        val json = JSONArray()
        entries.take(MAX_ENTRIES).forEach {
            json.put(JSONObject().put("mood", it.mood).put("note", it.note).put("ts", it.timestamp))
        }
        prefs.edit()
            .putString(KEY_MOODS, json.toString())
            .putInt(KEY_MOOD_CHECK_COUNT, prefs.getInt(KEY_MOOD_CHECK_COUNT, 0) + 1)
            .apply()
        return entry
    }

    fun recent(context: Context, limit: Int = 20): List<MoodEntry> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_MOODS, "[]") ?: "[]"
        return parse(raw).take(limit.coerceAtLeast(0))
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_MOODS, "[]").apply()
    }

    fun count(context: Context): Int = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_MOOD_CHECK_COUNT, 0)

    private fun parse(raw: String): List<MoodEntry> = try {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            val obj = array.optJSONObject(index) ?: return@mapNotNull null
            val mood = obj.optInt("mood", 3).coerceIn(1, 5)
            MoodEntry(mood, obj.optString("note", ""), obj.optLong("ts", 0L))
        }
    } catch (_: Exception) { emptyList() }
}
