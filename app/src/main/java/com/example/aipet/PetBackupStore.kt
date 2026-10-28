package com.example.aipet

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Export/restore only local companion data. AI credentials are intentionally excluded. */
object PetBackupStore {
    private const val FORMAT = "android-ai-pet-backup"
    private const val VERSION = 1
    private val includedPrefs = listOf("pet_state", "pet_journal")

    fun export(context: Context): String {
        val root = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("createdAt", System.currentTimeMillis())
            .put("note", "不包含 AI API Key；聊天记录和心情日记仅在你主动使用独立 AI 时才会按聊天请求发送。")
        includedPrefs.forEach { name ->
            val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            val values = JSONObject()
            prefs.all.forEach { (key, value) -> values.put(key, encodeValue(value)) }
            root.put(name, values)
        }
        return root.toString(2)
    }

    fun restore(context: Context, raw: String): String {
        val root = try { JSONObject(raw) } catch (_: Exception) {
            return "备份文件格式不正确，请粘贴完整的桌宠备份 JSON。"
        }
        if (root.optString("format") != FORMAT || root.optInt("version", -1) != VERSION) {
            return "这不是受支持的小伙伴桌宠备份文件，或备份版本不兼容。"
        }
        val state = root.optJSONObject("pet_state")
        val journal = root.optJSONObject("pet_journal")
        if (state == null || journal == null) return "备份内容不完整，未修改当前数据。"

        return try {
            includedPrefs.forEach { name ->
                val values = root.getJSONObject(name)
                val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
                val keys = values.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = values.get(key)
                    when (value) {
                        is JSONObject -> editor.putString(key, value.toString())
                        is JSONArray -> editor.putString(key, value.toString())
                        is String -> editor.putString(key, value)
                        is Boolean -> editor.putBoolean(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is Double -> {
                            val number = value.toLong()
                            if (number.toDouble() == value) editor.putLong(key, number) else editor.putFloat(key, value.toFloat())
                        }
                        else -> editor.putString(key, value.toString())
                    }
                }
                if (!editor.commit()) throw IllegalStateException("保存失败")
            }
            "恢复成功！桌宠数据已从备份导入。请重新打开桌宠或返回朋友档案查看。AI API Key 不在备份内，需要时请重新配置。"
        } catch (_: Exception) {
            "恢复过程中出现问题。请保留原备份文件，并重新打开应用检查数据。"
        }
    }

    private fun encodeValue(value: Any?): Any = when (value) {
        null -> JSONObject.NULL
        is Set<*> -> JSONArray().also { array -> value.forEach { array.put(it?.toString() ?: "") } }
        is Int, is Long, is Double, is Float, is Boolean, is String -> value
        else -> value.toString()
    }
}
