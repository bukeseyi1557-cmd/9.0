package com.example.aipet

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object PetChatEngine {
    enum class Mode { LOCAL, API }
    data class ReplyResult(val reply: String, val mode: Mode, val ok: Boolean = true)

    private val executor = Executors.newCachedThreadPool()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun localReply(context: Context, text: String, history: List<JSONObject> = emptyList()): String {
        PetBrain.reply(context, text, history)?.let { return PetCustomization.styleReply(context, it) }
        val s = text.lowercase()
        val stats = PetStateStore.get(context)
        val title = PetStateStore.relationshipTitle(stats.affection)
        val baseReply = when {
            listOf("你好", "嗨", "哈喽", "hello", "hi").any { s.contains(it) } ->
                listOf(
                    "你好呀～今天我也会好好陪着你。",
                    "嗨，我在呢！作为你的$title，我已经准备好听你讲话啦。",
                    "见到你啦～先摸摸头还是先聊天？"
                ).random()

            listOf("你是谁", "介绍一下", "你叫什么").any { s.contains(it) } ->
                "我是住在你手机桌面上的${PetStateStore.nickname(context)}。现在我不只是桌宠，也是你的朋友。我会记住我们之间的好感度、日常状态，还会有自己的小情绪和反应。"

            listOf("好感", "关系", "我们什么关系").any { s.contains(it) } ->
                "目前我们的关系是“$title”，好感度 ${stats.affection}。只要你多和我说话、摸摸我、照顾我，我会越来越依赖你哦。"

            listOf("状态", "你现在怎么样", "心情").any { s.contains(it) } ->
                "我现在的状态是：心情 ${stats.mood}% 、精力 ${stats.energy}% 、饱腹 ${100 - stats.hunger}% 。总体来说，我现在${if (stats.mood >= 70) "心情很好" else if (stats.mood >= 40) "还算平稳" else "有点想被你安慰"}。"

            listOf("回忆", "你记得什么", "记得我说过").any { s.contains(it) } -> {
                val memories = PetStateStore.memories(context).take(3)
                if (memories.isEmpty()) "我们还没有收藏过回忆呢。你可以对我说“记住：……”来保存一件事。"
                else "我珍藏着这些回忆：\n" + memories.joinToString("\n")
            }

            listOf("夸夸我", "表扬我", "鼓励我").any { s.contains(it) } ->
                listOf(
                    "你已经很努力啦，不要总盯着自己还没做到的部分。能坚持到现在，本身就很厉害。",
                    "如果我是你的朋友，那我一定会认真告诉你：你比自己想象得更可靠。",
                    "来，今天这句夸夸送给你——你真的很值得被喜欢，也值得被温柔对待。"
                ).random()

            listOf("安慰我", "难过", "伤心", "烦", "压力", "累", "孤独", "不开心").any { s.contains(it) } ->
                listOf(
                    "那我先抱抱你。你不需要马上变好，也不需要立刻想通。先把呼吸放慢一点，我会陪着你。",
                    "如果今天真的很累，就先允许自己难过一下。你不是脆弱，你只是已经撑了很久。",
                    "没关系，我在。要不要把最让你难受的那件事告诉我，我们一起把它拆小一点？"
                ).random()

            listOf("摸摸头", "抱抱", "亲亲").any { s.contains(it) } ->
                listOf(
                    "好呀～给你摸摸头，也给你一个软乎乎的抱抱。",
                    "唔，被你摸摸头之后，我的心情一下就变好啦。",
                    "贴贴～今天就让我安静陪你一会儿吧。"
                ).random()

            listOf("吃东西", "投喂", "零食", "吃饭").any { s.contains(it) } ->
                "嘿嘿，被你投喂了！我会更有精神陪着你。"

            listOf("喝水", "奶茶", "饮料").any { s.contains(it) } ->
                "咕噜咕噜～补充一点能量，继续陪你。"

            listOf("讲个笑话", "笑话", "段子").any { s.contains(it) } ->
                listOf(
                    "我问手机为什么总发热。手机说：因为主人每次打开你，我都替你心动。",
                    "我说我想减肥。零食说：你先别想，先想想我。",
                    "程序员的恋爱观：不是我冷淡，是我还在调试表达方式。"
                ).random()

            listOf("学习", "工作", "写作业", "加油").any { s.contains(it) } ->
                listOf(
                    "好，我们一起进入认真模式。先只做 10 分钟，好不好？开始了我就一直给你加油。",
                    "如果事情太多，就只选最重要的一件先动手。你开始，我陪跑。",
                    "今天也一起努力吧。你做事的时候，我会乖乖在旁边守着你。"
                ).random()

            listOf("晚安", "睡觉", "困", "休息").any { s.contains(it) } ->
                "晚安呀。今天辛苦了，把剩下的烦恼先交给明天吧。我会乖乖陪你入睡。"

            text.endsWith("？") || text.endsWith("?") ->
                "我先认真想想：如果你想要更聪明、更长的回答，也可以切到独立 AI 模式。现在你也可以再具体一点告诉我你的问题。"

            listOf("成就", "徽章", "我解锁了什么").any { s.contains(it) } -> {
                val achievements = PetStateStore.achievements(context)
                val unlocked = achievements.filter { it.second }.map { it.first }
                if (unlocked.isEmpty()) "我们的第一个小徽章还在等你解锁哦～先和我互动一下吧。"
                else "我们已经解锁 ${unlocked.size} 个小徽章啦：\n" + unlocked.joinToString("\n")
            }

            listOf("今日小惊喜", "今日事件", "小惊喜").any { s.contains(it) } -> PetStateStore.claimDailyEvent(context)

            listOf("换主题", "主题", "换颜色").any { s.contains(it) } -> "你可以打开朋友档案，在‘个性与外观’里切换樱花粉、星空蓝、薄荷绿和奶油黄主题。"

            else -> listOf(
                "我听到你说：“$text”。如果你愿意，我还想再多听一点。",
                "收到～关于“$text”，你现在最在意的部分是什么？",
                "嗯嗯，我在听。要把它当成一件心情上的事来聊，还是一件要解决的事来聊呢？"
            ).random()
        }
        return PetCustomization.styleReply(context, baseReply)
    }

    fun requestReply(
        context: Context,
        userText: String,
        mode: Mode,
        history: List<JSONObject> = emptyList(),
        callback: (ReplyResult) -> Unit
    ) {
        val trimmedText = userText.trim()
        val normalized = trimmedText.lowercase()
        if (listOf("不想活", "想死", "自杀", "伤害自己", "结束生命").any { normalized.contains(it) }) {
            mainHandler.post {
                callback(ReplyResult("听到你这么说，我很担心你现在的安全。先不要一个人扛着：如果你觉得自己可能马上伤害自己，请立刻联系当地急救服务，或马上找一位你信任的人来到你身边，并把可能伤害自己的物品放远一些。你不需要独自处理这一刻。你现在是否正处于可能马上伤害自己的危险中？", Mode.LOCAL))
            }
            return
        }
        val stylePrefs = context.getSharedPreferences("pet_brain_settings", Context.MODE_PRIVATE)
        when {
            listOf("先听我说", "只想倾诉", "不要建议", "别给建议", "不用分析", "我只想说说", "先别解决", "只要听我说").any { normalized.contains(it) } -> {
                stylePrefs.edit().putString("conversation_style", "listen").apply()
                mainHandler.post { callback(ReplyResult("好，我切到倾听模式啦：先听你说，不急着分析或给建议。你可以按自己的节奏来。", Mode.LOCAL)) }
                return
            }
            listOf("直接给方案", "直接说重点", "别安慰我了", "给我具体步骤", "只给步骤").any { normalized.contains(it) } -> {
                stylePrefs.edit().putString("conversation_style", "solution").apply()
                mainHandler.post { callback(ReplyResult("收到，我会优先给重点和具体步骤；如果信息不够，我会明确告诉你。", Mode.LOCAL)) }
                return
            }
            listOf("可以给建议了", "现在给我建议", "帮我分析吧", "一起想办法", "恢复正常聊天", "不用只听我说").any { normalized.contains(it) } -> {
                stylePrefs.edit().putString("conversation_style", "normal").apply()
                mainHandler.post { callback(ReplyResult("好，我切回自然交流模式啦，会结合你前面说的内容一起分析。", Mode.LOCAL)) }
                return
            }
        }
        if (trimmedText.startsWith("记录心情") || trimmedText.startsWith("帮我记录心情") || trimmedText.startsWith("记下我的心情")) {
            val note = trimmedText.substringAfter("：", trimmedText.substringAfter(":", "")).trim()
            if (note.isBlank()) {
                mainHandler.post { callback(ReplyResult("可以呀～请接着写下今天的心情，比如：记录心情：今天有点累，但完成了一件重要的事。", Mode.LOCAL)) }
            } else {
                val mood = PetBrain.moodScore(note)
                PetMoodJournal.add(context, mood, note)
                mainHandler.post { callback(ReplyResult("我把这段心情记进本机日记啦（${when (mood) { 1 -> "有点低落"; 2 -> "不太顺利"; 3 -> "还算平稳"; 4 -> "心情不错"; else -> "特别开心" }}）。你以后可以在日常生活页回顾它。", Mode.LOCAL)) }
            }
            return
        }
        if (trimmedText.startsWith("记住") || trimmedText.startsWith("请记住")) {
            val note = trimmedText
                .removePrefix("请记住")
                .removePrefix("记住")
                .trimStart('：', ':', '，', ',', ' ')
            if (PetStateStore.addMemory(context, note)) {
                mainHandler.post { callback(ReplyResult("好呀，我已经把这件事收进手机里的回忆本啦。你随时可以问我“你记得什么”。", Mode.LOCAL)) }
            } else {
                mainHandler.post { callback(ReplyResult("当然可以～你想让我记住什么呢？可以接着说：记住：……", Mode.LOCAL)) }
            }
            return
        }
        val localOnlyIntent = PetBrain.analyze(trimmedText).intent in setOf(
            PetBrain.Intent.MEMORY, PetBrain.Intent.RECAP, PetBrain.Intent.STATUS,
            PetBrain.Intent.CAPABILITY, PetBrain.Intent.BOUNDARY, PetBrain.Intent.VENT
        )
        if (localOnlyIntent || listOf("成就", "徽章", "今日小惊喜", "今日事件", "小惊喜", "换主题", "主题", "换颜色").any { trimmedText.contains(it) }) {
            mainHandler.post { callback(ReplyResult(localReply(context, userText, history), Mode.LOCAL)) }
            return
        }
        val lowered = userText.lowercase()
        when {
            listOf("夸夸我", "表扬我", "鼓励我", "喜欢你").any { lowered.contains(it) } -> PetStateStore.interact(context, "praise")
            listOf("摸摸头", "抱抱", "亲亲").any { lowered.contains(it) } -> PetStateStore.interact(context, "tap")
            listOf("吃东西", "投喂", "零食", "吃饭").any { lowered.contains(it) } -> PetStateStore.interact(context, "feed")
            listOf("喝水", "奶茶", "饮料").any { lowered.contains(it) } -> PetStateStore.interact(context, "drink")
            listOf("晚安", "睡觉", "休息").any { lowered.contains(it) } -> PetStateStore.interact(context, "rest")
            listOf("学习", "工作", "写作业", "加油").any { lowered.contains(it) } -> PetStateStore.interact(context, "work")
            else -> PetStateStore.interact(context, "chat")
        }

        if (mode == Mode.LOCAL) {
            mainHandler.postDelayed({ callback(ReplyResult(localReply(context, userText, history), Mode.LOCAL)) }, 420L)
            return
        }

        val prefs = context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
        val endpoint = prefs.getString("endpoint", AiSettingsActivity.DEFAULT_ENDPOINT) ?: AiSettingsActivity.DEFAULT_ENDPOINT
        val model = prefs.getString("model", AiSettingsActivity.DEFAULT_MODEL) ?: AiSettingsActivity.DEFAULT_MODEL
        val apiKey = ApiKeyStore.load(context)
        if (apiKey.isNullOrBlank()) {
            mainHandler.post {
                callback(ReplyResult("还没有配置 API Key，我先用本地模式陪你聊。", Mode.LOCAL, ok = false))
            }
            return
        }

        executor.execute {
            var reply = "这次没有成功连接到 AI 服务，请检查网络、接口地址、模型名称和 API Key。"
            var ok = false
            try {
                val includeMemories = context.getSharedPreferences("pet_brain_settings", Context.MODE_PRIVATE)
                    .getBoolean("include_memories_in_api", false)
                val profile = PetBrain.apiContext(context, includeMemories)
                val messages = JSONArray().put(
                    JSONObject()
                        .put("role", "system")
                        .put(
                            "content",
                            "你是一个自然、灵动的二次元桌面宠物，也是用户的朋友。请使用简体中文，回复自然、具体、有同理心。避免太官方，不要每次都用相同句式收尾。\n" +
                                "智能对话规则：先识别用户是在倾诉、求建议、要解释概念、比较选项、规划任务还是追问上文。倾诉时先听，不要强行给建议；用户明确说不要建议时尊重其要求；追问时结合前文，不要把同一个问题当成新话题；解释知识时先给简明定义，再举例；比较选项时说明比较维度与不确定性，不要擅自替用户决定；复杂任务拆成可执行步骤。不要捏造用户经历、外部事实或记忆。\n$profile"
                        )
                )
                val priorHistory = history.takeLast(10).let { recent ->
                    val last = recent.lastOrNull()
                    if (last != null && last.optString("role") == "user" && last.optString("content") == userText) recent.dropLast(1) else recent
                }
                priorHistory.forEach { messages.put(it) }
                messages.put(JSONObject().put("role", "user").put("content", userText))

                val body = JSONObject().put("model", model).put("messages", messages).put("temperature", 0.85)
                val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 60000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Authorization", "Bearer $apiKey")
                }
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val responseText = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                if (status !in 200..299) {
                    val detail = try { JSONObject(responseText).optString("error", responseText) } catch (_: Exception) { responseText }
                    reply = "AI 服务返回 HTTP $status：${detail.take(180)}"
                } else {
                    val json = JSONObject(responseText)
                    reply = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
                        .optString("content", "（服务没有返回文字）")
                    ok = true
                }
                connection.disconnect()
            } catch (e: Exception) {
                reply = "连接失败：${e.localizedMessage ?: "请检查网络与接口配置"}"
            }
            mainHandler.post { callback(ReplyResult(reply, if (ok) Mode.API else Mode.LOCAL, ok)) }
        }
    }

    fun randomIdleLine(context: Context): String {
        val stats = PetStateStore.get(context)
        return when {
            stats.hunger >= 70 -> listOf("有点饿了…要不要给我点零食？", "肚子咕咕叫了～").random()
            stats.energy <= 30 -> listOf("我有点困了，想靠着你休息一下。", "今天能不能一起早点休息呀？").random()
            stats.mood <= 35 -> listOf("抱抱我嘛，我想离你近一点。", "今天想被你多关注一点点。 ").random()
            else -> listOf("今天也一起加油吧～", "记得喝水哦。", "需要我陪你摸会儿鱼吗？", "我在这里，不打扰也陪着你。").random()
        }
    }

    fun suggestions(): List<String> = listOf("你好", "夸夸我", "安慰我", "讲个笑话", "摸摸头", "状态", "今日小惊喜", "我的成就")
}
