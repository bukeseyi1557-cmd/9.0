package com.example.aipet

import android.content.Context
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

data class PetStats(
    val affection: Int,
    val mood: Int,
    val energy: Int,
    val hunger: Int,
    val streak: Int,
    val level: Int,
    val todayFirstVisit: Boolean,
    val coins: Int = 0,
    val nickname: String = "小伙伴",
    val checkInAvailable: Boolean = true,
    val memoryCount: Int = 0
)

object PetStateStore {
    private const val PREFS = "pet_state"
    private const val KEY_AFFECTION = "affection"
    private const val KEY_MOOD = "mood"
    private const val KEY_ENERGY = "energy"
    private const val KEY_HUNGER = "hunger"
    private const val KEY_STREAK = "streak"
    private const val KEY_LAST_DAY = "last_day"
    private const val KEY_LAST_DECAY_TS = "last_decay_ts"
    private const val KEY_COINS = "coins"
    private const val KEY_CHECKIN_DAY = "checkin_day"
    private const val KEY_NICKNAME = "nickname"
    private const val KEY_MEMORIES = "memories_json"
    private const val KEY_GIFT_COUNT = "gift_count"
    private const val KEY_INTERACTION_COUNT = "interaction_count"
    private const val KEY_EVENT_DAY = "daily_event_day"

    fun touch(context: Context): PetStats {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val today = todayKey(now)
        val lastDay = prefs.getString(KEY_LAST_DAY, null)
        val firstVisit = lastDay != today

        var affection = prefs.getInt(KEY_AFFECTION, 12)
        var mood = prefs.getInt(KEY_MOOD, 72)
        var energy = prefs.getInt(KEY_ENERGY, 78)
        var hunger = prefs.getInt(KEY_HUNGER, 28)
        var streak = prefs.getInt(KEY_STREAK, 0)
        val lastDecay = prefs.getLong(KEY_LAST_DECAY_TS, now)
        val hours = ((now - lastDecay) / (1000 * 60 * 60)).toInt().coerceAtLeast(0)
        if (hours > 0) {
            hunger = clamp(hunger + hours * 3)
            energy = clamp(energy - hours * 2)
            mood = clamp(mood - hours)
        }

        if (firstVisit) {
            val yesterday = todayKey(now - 24L * 60L * 60L * 1000L)
            streak = if (lastDay == yesterday) streak + 1 else 1
            affection = clamp(affection + 2)
            mood = clamp(mood + 6)
            energy = clamp(energy + 5)
        }

        if (firstVisit || hours > 0) {
            val editor = prefs.edit()
                .putInt(KEY_AFFECTION, affection)
                .putInt(KEY_MOOD, mood)
                .putInt(KEY_ENERGY, energy)
                .putInt(KEY_HUNGER, hunger)
            if (firstVisit) editor.putInt(KEY_STREAK, streak).putString(KEY_LAST_DAY, today)
            if (hours > 0 || prefs.getLong(KEY_LAST_DECAY_TS, 0L) == 0L) editor.putLong(KEY_LAST_DECAY_TS, now)
            editor.apply()
        }

        return PetStats(
            affection, mood, energy, hunger, streak, levelOf(affection), firstVisit,
            coins = prefs.getInt(KEY_COINS, 10),
            nickname = prefs.getString(KEY_NICKNAME, "小伙伴") ?: "小伙伴",
            checkInAvailable = prefs.getString(KEY_CHECKIN_DAY, null) != today,
            memoryCount = readMemories(prefs.getString(KEY_MEMORIES, "[]") ?: "[]").size
        )
    }

    fun get(context: Context): PetStats = touch(context).copy(todayFirstVisit = false)

    fun interact(context: Context, type: String): PetStats {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        var stats = get(context)
        var affection = stats.affection
        var mood = stats.mood
        var energy = stats.energy
        var hunger = stats.hunger
        when (type) {
            "tap" -> { affection += 1; mood += 2 }
            "chat" -> { affection += 2; mood += 1; energy -= 1 }
            "praise" -> { affection += 3; mood += 3 }
            "feed" -> { hunger -= 12; mood += 2; affection += 1 }
            "drink" -> { energy += 5; mood += 1 }
            "rest" -> { energy += 10; mood += 3 }
            "ignore" -> { mood -= 1 }
            "work" -> { mood += 1; energy -= 2; affection += 1 }
        }
        affection = clamp(affection)
        mood = clamp(mood)
        energy = clamp(energy)
        hunger = clamp(hunger)
        prefs.edit()
            .putInt(KEY_AFFECTION, affection)
            .putInt(KEY_MOOD, mood)
            .putInt(KEY_ENERGY, energy)
            .putInt(KEY_HUNGER, hunger)
            .putInt(KEY_INTERACTION_COUNT, prefs.getInt(KEY_INTERACTION_COUNT, 0) + 1)
            .putLong(KEY_LAST_DECAY_TS, System.currentTimeMillis())
            .apply()
        return PetStats(
            affection, mood, energy, hunger, stats.streak, levelOf(affection), false,
            coins = prefs.getInt(KEY_COINS, 10),
            nickname = prefs.getString(KEY_NICKNAME, "小伙伴") ?: "小伙伴",
            checkInAvailable = prefs.getString(KEY_CHECKIN_DAY, null) != todayKey(System.currentTimeMillis()),
            memoryCount = readMemories(prefs.getString(KEY_MEMORIES, "[]") ?: "[]").size
        )
    }


    fun checkIn(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = todayKey(System.currentTimeMillis())
        if (prefs.getString(KEY_CHECKIN_DAY, null) == today) return "今天已经签到过啦，明天再来领惊喜吧～"
        val stats = get(context)
        val coins = prefs.getInt(KEY_COINS, 10) + 5
        prefs.edit()
            .putString(KEY_CHECKIN_DAY, today)
            .putInt(KEY_COINS, coins)
            .putInt(KEY_AFFECTION, clamp(stats.affection + 3))
            .putInt(KEY_MOOD, clamp(stats.mood + 8))
            .putInt(KEY_ENERGY, clamp(stats.energy + 5))
            .apply()
        return "签到成功！获得 5 枚星星币，好感 +3，心情也变好啦～"
    }

    fun feedSnack(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val coins = prefs.getInt(KEY_COINS, 10)
        if (coins < 2) return "星星币不够啦～每天签到可以获得 5 枚。"
        val stats = get(context)
        prefs.edit()
            .putInt(KEY_COINS, coins - 2)
            .putInt(KEY_HUNGER, clamp(stats.hunger - 18))
            .putInt(KEY_MOOD, clamp(stats.mood + 4))
            .putInt(KEY_AFFECTION, clamp(stats.affection + 1))
            .apply()
        return "投喂成功！她开心地吃下了点心，饥饿感降低啦～（-2 星星币）"
    }

    fun giveGift(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val coins = prefs.getInt(KEY_COINS, 10)
        if (coins < 5) return "星星币不够送礼物啦～每天签到可以获得 5 枚。"
        val stats = get(context)
        val oldTitle = relationshipTitle(stats.affection)
        val affection = clamp(stats.affection + 5)
        prefs.edit()
            .putInt(KEY_COINS, coins - 5)
            .putInt(KEY_AFFECTION, affection)
            .putInt(KEY_MOOD, clamp(stats.mood + 7))
            .putInt(KEY_GIFT_COUNT, prefs.getInt(KEY_GIFT_COUNT, 0) + 1)
            .apply()
        val newTitle = relationshipTitle(affection)
        return if (oldTitle != newTitle) {
            "她收到礼物后特别开心！你们的关系升级为「$newTitle」啦！"
        } else {
            listOf("她把礼物抱在怀里，眼睛亮晶晶的！好感 +5。", "礼物她很喜欢！她悄悄把它收进了珍藏盒。好感 +5。", "她开心地向你道谢，今天的心情值拉满啦！好感 +5。").random()
        }
    }

    fun nickname(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_NICKNAME, "小伙伴") ?: "小伙伴"
    }

    fun setNickname(context: Context, value: String) {
        val cleaned = value.trim().take(16).ifBlank { "小伙伴" }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_NICKNAME, cleaned).apply()
    }

    fun addMemory(context: Context, text: String): Boolean {
        val cleaned = text.trim().take(180)
        if (cleaned.isBlank()) return false
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val entries = readMemories(prefs.getString(KEY_MEMORIES, "[]") ?: "[]").toMutableList()
        val timestamp = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(System.currentTimeMillis())
        entries.add(0, "$timestamp  $cleaned")
        val json = JSONArray()
        entries.take(30).forEach { json.put(it) }
        prefs.edit().putString(KEY_MEMORIES, json.toString()).apply()
        return true
    }

    fun memories(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return readMemories(prefs.getString(KEY_MEMORIES, "[]") ?: "[]")
    }

    private fun readMemories(raw: String): List<String> {
        return try {
            val json = JSONArray(raw)
            (0 until json.length()).mapNotNull { index -> json.optString(index).takeIf { it.isNotBlank() } }
        } catch (_: Exception) { emptyList() }
    }

    fun clearMemories(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_MEMORIES, "[]").apply()
    }

    fun completeFocusSession(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stats = get(context)
        val count = prefs.getInt("focus_session_count", 0) + 1
        prefs.edit()
            .putInt(KEY_COINS, stats.coins + 2)
            .putInt(KEY_AFFECTION, clamp(stats.affection + 1))
            .putInt(KEY_MOOD, clamp(stats.mood + 3))
            .putInt("focus_session_count", count)
            .apply()
        return "专注完成！获得 2 枚星星币，好感 +1。你认真投入的样子很棒。"
    }

    fun focusSessionCount(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("focus_session_count", 0)

    fun claimDailyEvent(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = todayKey(System.currentTimeMillis())
        if (prefs.getString(KEY_EVENT_DAY, null) == today) return "今天的小惊喜已经发现过啦，明天再来看看吧～"
        val stats = get(context)
        val event = listOf(
            "你们在桌面角落发现了一颗闪闪发亮的星星！",
            "她翻到一张小纸条，上面写着：今天也要对自己好一点。",
            "窗边好像飘来了一片花瓣，她开心地把它夹进回忆本。",
            "她找到一颗幸运糖果，决定把今天的好运分给你一半。",
            "她偷偷准备了一个小小的惊喜：谢谢你一直以来的陪伴。"
        )[Calendar.getInstance().get(Calendar.DAY_OF_YEAR) % 5]
        prefs.edit()
            .putString(KEY_EVENT_DAY, today)
            .putInt(KEY_COINS, prefs.getInt(KEY_COINS, 10) + 2)
            .putInt(KEY_AFFECTION, clamp(stats.affection + 1))
            .putInt(KEY_MOOD, clamp(stats.mood + 5))
            .apply()
        addMemory(context, "今日小惊喜：$event")
        return "$event 奖励：2 枚星星币、好感 +1。已经帮你收进回忆本啦。"
    }

    fun achievements(context: Context): List<Pair<String, Boolean>> {
        val stats = get(context)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val gifts = prefs.getInt(KEY_GIFT_COUNT, 0)
        val interactions = prefs.getInt(KEY_INTERACTION_COUNT, 0)
        return listOf(
            "初次相遇 · 和她互动一次" to (interactions >= 1),
            "渐渐熟悉 · 好感达到 20" to (stats.affection >= 20),
            "成为好友 · 好感达到 40" to (stats.affection >= 40),
            "知心伙伴 · 好感达到 65" to (stats.affection >= 65),
            "特别的朋友 · 好感达到 85" to (stats.affection >= 85),
            "礼物收藏家 · 送出 3 份礼物" to (gifts >= 3),
            "回忆收藏家 · 保存 10 条回忆" to (stats.memoryCount >= 10),
            "七日相伴 · 连续陪伴 7 天" to (stats.streak >= 7),
            "认真生活 · 完成 7 次每日目标" to (prefs.getInt("daily_task_count", 0) >= 7),
            "惊喜发现家 · 发现过一次今日事件" to (prefs.getString(KEY_EVENT_DAY, null) != null),
            "专注搭档 · 完成 5 次专注" to (prefs.getInt("focus_session_count", 0) >= 5),
            "心情记录员 · 记录 7 次心情" to (PetMoodJournal.count(context) >= 7)
        )
    }

    fun relationshipTitle(affection: Int): String = when {
        affection < 20 -> "刚认识"
        affection < 40 -> "熟悉伙伴"
        affection < 65 -> "好朋友"
        affection < 85 -> "知心好友"
        else -> "最重要的朋友"
    }

    fun statusSummary(context: Context): String {
        val s = get(context)
        return "好感 ${s.affection}｜${relationshipTitle(s.affection)}｜心情 ${s.mood}%｜精力 ${s.energy}%"
    }

    fun timeGreeting(context: Context): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val stats = get(context)
        val relation = relationshipTitle(stats.affection)
        return when {
            hour in 5..10 -> "早上好呀～今天也请多关照，我可是你的$relation。"
            hour in 11..13 -> "中午啦，记得吃饭哦，我在这边陪你。"
            hour in 14..17 -> "下午好～要不要我陪你放松一下，再继续加油？"
            hour in 18..22 -> "晚上好呀，今天过得怎么样？我想听你说说。"
            else -> "这么晚还没睡呀？如果你愿意，我可以安静陪你一会儿。"
        }
    }

    private fun levelOf(affection: Int): Int = 1 + affection / 15
    private fun clamp(value: Int): Int = min(100, max(0, value))
    private fun todayKey(ts: Long): String = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(ts)
}
