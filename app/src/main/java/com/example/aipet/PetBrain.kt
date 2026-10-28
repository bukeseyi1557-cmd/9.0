package com.example.aipet

import android.content.Context
import org.json.JSONObject

/**
 * Lightweight on-device conversation layer.
 * It does not call a network service; it classifies common intents and uses recent context,
 * user-approved memories, and the companion's current state to produce more relevant replies.
 */
object PetBrain {
    enum class Emotion { SAD, ANXIOUS, ANGRY, TIRED, LONELY, HAPPY, BORED, NEUTRAL }
    enum class Intent { FOLLOW_UP, RECAP, ADVICE, EMOTIONAL_SUPPORT, BOUNDARY, VENT, MEMORY, STATUS, PLAN, EXPLAIN, COMPARE, DECISION, CAPABILITY, UNKNOWN }

    data class Analysis(val intent: Intent, val emotion: Emotion, val isQuestion: Boolean, val isShortFollowUp: Boolean)

    fun analyze(text: String): Analysis {
        val s = text.trim().lowercase()
        val emotion = when {
            containsAny(s, "不想活", "自杀", "伤害自己", "难过", "伤心", "委屈", "崩溃", "不开心", "心情不好", "失落") -> Emotion.SAD
            containsAny(s, "焦虑", "紧张", "担心", "害怕", "慌", "压力大", "心慌", "睡不着") -> Emotion.ANXIOUS
            containsAny(s, "生气", "愤怒", "气死", "烦死了", "讨厌") -> Emotion.ANGRY
            containsAny(s, "累死", "好累", "疲惫", "困死", "没精神") -> Emotion.TIRED
            containsAny(s, "孤独", "没人陪", "没有朋友", "寂寞") -> Emotion.LONELY
            containsAny(s, "开心", "高兴", "成功了", "太棒了", "好消息", "好耶") -> Emotion.HAPPY
            containsAny(s, "无聊", "不知道干嘛", "没事做", "没意思") -> Emotion.BORED
            else -> Emotion.NEUTRAL
        }
        // Short follow-ups are recognized only when the whole message is a follow-up phrase.
        // This avoids treating a complete question such as “为什么我最近很焦虑” as contextless follow-up.
        val numberedFollowUp = s.matches(Regex("^(请问)?(?:第)?[1-9一二三四五六七八九](?:点|条|个)(?:呢|是什么|展开|详细说说)?[？?。！!]?$") )
        val shortFollowUp = s.length <= 18 && (isShortFollowUpPhrase(s) || numberedFollowUp)
        val intent = when {
            containsAny(s, "我不想聊", "别问了", "让我静静", "先不说了", "不想说", "我想自己待会", "让我一个人待会") -> Intent.BOUNDARY
            containsAny(s, "只想倾诉", "先听我说", "不要建议", "别给建议", "不用分析", "我只想说说", "先别解决", "只要听我说") -> Intent.VENT
            containsAny(s, "总结今天", "总结一下", "复盘", "今天聊了什么", "回顾一下") -> Intent.RECAP
            shortFollowUp -> Intent.FOLLOW_UP
            containsAny(s, "回忆", "你记得什么", "记住了什么", "我说过什么", "记得我") -> Intent.MEMORY
            containsAny(s, "你的状态", "桌宠状态", "你现在怎么样", "你现在心情", "好感度", "我们什么关系") -> Intent.STATUS
            containsAny(s, "你能做什么", "你会什么", "有什么功能", "怎么用你") -> Intent.CAPABILITY
            containsAny(s, "计划", "安排一下", "帮我规划", "列个清单", "拆分任务", "学习计划", "工作计划") -> Intent.PLAN
            containsAny(s, "比较一下", "对比一下", "哪个好", "哪个更好", "优缺点", "利弊") -> Intent.COMPARE
            containsAny(s, "帮我做决定", "替我分析选择", "怎么选", "该选哪个", "选哪个", "要不要") -> Intent.DECISION
            containsAny(s, "解释一下", "讲讲什么是", "给我讲讲", "通俗解释", "是什么意思", "原理是什么") -> Intent.EXPLAIN
            containsAny(s, "怎么办", "怎么解决", "给我建议", "你有什么建议", "帮我想想", "该不该", "怎么处理") -> Intent.ADVICE
            emotion != Emotion.NEUTRAL || containsAny(s, "难过", "伤心", "委屈", "崩溃", "不开心", "心情不好") -> Intent.EMOTIONAL_SUPPORT
            else -> Intent.UNKNOWN
        }
        return Analysis(intent, emotion, s.endsWith("?") || s.endsWith("？") || s.contains("怎么") || s.contains("为什么"), shortFollowUp)
    }

    fun reply(context: Context, text: String, history: List<JSONObject> = emptyList()): String? {
        val cleaned = text.trim()
        val s = cleaned.lowercase()
        val analysis = analyze(cleaned)
        val storedEntries = PetChatStore.recent(context, 20).toMutableList()
        val storedLast = storedEntries.lastOrNull()
        if (storedLast?.role == "user" && storedLast.content.trim() == cleaned) storedEntries.removeAt(storedEntries.lastIndex)
        val storedContext = storedEntries.map { JSONObject().put("role", it.role).put("content", it.content) }
        val contextualHistory = if (history.isNotEmpty()) history else storedContext
        val lastUser = contextualHistory.lastOrNull { it.optString("role") == "user" }?.optString("content", "")?.trim().orEmpty()
        val lastAssistant = contextualHistory.lastOrNull { it.optString("role") == "assistant" }?.optString("content", "")?.trim().orEmpty()
        val nickname = PetStateStore.nickname(context)
        val stats = PetStateStore.get(context)
        val stylePrefs = context.getSharedPreferences("pet_brain_settings", Context.MODE_PRIVATE)
        val conversationStyle = stylePrefs.getString("conversation_style", "normal") ?: "normal"

        if (containsAny(s, "不想活", "想死", "自杀", "伤害自己", "结束生命")) {
            return "听到你这么说，我很担心你现在的安全。先不要一个人扛着：如果你觉得自己可能马上伤害自己，请立刻联系当地急救服务，或马上找一位你信任的人来到你身边，并把可能伤害自己的物品放远一些。你不需要独自处理这一刻。你现在是否正处于可能马上伤害自己的危险中？"
        }

        if (containsAny(s, "可以给建议了", "现在给我建议", "帮我分析吧", "一起想办法", "恢复正常聊天", "不用只听我说")) {
            stylePrefs.edit().putString("conversation_style", "normal").apply()
            return "好，我切回一起分析的模式啦。我会先结合你刚才说的内容，再给出具体、可执行的建议。"
        }
        if (containsAny(s, "直接给方案", "直接说重点", "别安慰我了", "给我具体步骤", "只给步骤")) {
            stylePrefs.edit().putString("conversation_style", "solution").apply()
            return "收到，我会尽量直接给重点和步骤；如果信息不够，我会明确说出还缺什么。"
        }
        if (analysis.intent == Intent.VENT) {
            stylePrefs.edit().putString("conversation_style", "listen").apply()
            return "好，我先切到倾听模式：先不分析、不急着给建议。你可以按自己的节奏说，我会先认真听。"
        }

        if (analysis.intent == Intent.BOUNDARY) {
            return listOf(
                "好，我不追问啦。你可以先安静待一会儿，我会在这里。等你想说话时再叫我就好。",
                "知道啦，不用勉强自己解释。我先乖乖陪着你，不打扰你。",
                "好呀，给你一点自己的空间。照顾好自己，想回来聊天时我还在。"
            ).random()
        }

        if (conversationStyle == "listen" && analysis.emotion != Emotion.NEUTRAL) {
            return when (analysis.emotion) {
                Emotion.SAD -> "我听着呢，不急着给建议。你可以把心里那部分慢慢说出来，不用一次讲清楚。"
                Emotion.ANXIOUS -> "嗯，我在认真听。你不用马上想出办法，可以先把最让你担心的部分说出来。"
                Emotion.ANGRY -> "我听到了，这件事确实让你很生气。你可以继续说，我先不替任何人下结论。"
                Emotion.TIRED -> "听起来你真的累了。我先陪你缓一缓，不把聊天变成待办清单。"
                Emotion.LONELY -> "我在听。孤独感上来的时候可能很难受，你可以照自己的节奏说，不必急着解决它。"
                Emotion.HAPPY -> "我愿意听你多讲讲这件开心的事！你最想分享的是哪一部分？"
                Emotion.BORED, Emotion.NEUTRAL -> "我在听，你可以接着说。"
            }
        }
        if (conversationStyle == "solution" && (analysis.emotion != Emotion.NEUTRAL || analysis.intent == Intent.ADVICE)) {
            return adviceForContext(lastUser, cleaned)
        }

        if (analysis.intent == Intent.MEMORY && !containsAny(s, "记住：", "记住:", "请记住")) {
            val memories = PetStateStore.memories(context)
            if (memories.isEmpty()) return "我现在还没有收藏回忆。你可以明确对我说“记住：我最近在学 Kotlin”，我就会把这句话保存在手机本地。"
            val knownTerms = listOf("工作", "学习", "喜欢", "不喜欢", "家人", "朋友", "目标", "计划", "游戏", "老师", "学校", "生活", "心情", "梦想", "生日", "名字", "考试")
                .filter { s.contains(it) }
            val queryTerms = knownTerms + cleaned.replace(Regex("[，。？！、,.?!：:]"), " ")
                .replace(Regex("你记得|记得我|我说过|告诉过你|什么回忆|吗|呢|啊|呀|的|我|你|了"), " ")
                .split(Regex("\\s+")).filter { it.length >= 2 }
            val matched = memories.filter { memory -> queryTerms.any { memory.contains(it, ignoreCase = true) } }.take(5)
            val chosen = if (matched.isNotEmpty()) matched else memories.take(4)
            return "我在回忆本里找到这些内容：\n" + chosen.joinToString("\n") { "· $it" } + "\n如果有哪条不准确，你可以去朋友档案里修改或清除。"
        }

        if (analysis.intent == Intent.RECAP) {
            val recent = PetChatStore.recent(context, 14)
                .filter { it.content.isNotBlank() }
                .takeLast(10)
            if (recent.isEmpty()) return "我们还没有足够的聊天记录可以复盘。你可以先和我聊聊今天发生的事。"
            val userLines = recent.filter { it.role == "user" }.takeLast(5).map { it.content.replace(Regex("\\s+"), " ").take(90) }
            val assistantLines = recent.filter { it.role == "assistant" }.takeLast(2).map { it.content.replace(Regex("\\s+"), " ").take(80) }
            return buildString {
                append("我根据手机里最近保存的聊天，整理了一份小复盘：\n")
                if (userLines.isNotEmpty()) {
                    append("\n你提到过：\n")
                    userLines.forEach { append("· ").append(it).append("\n") }
                }
                if (assistantLines.isNotEmpty()) {
                    append("\n我们聊到的方向：\n")
                    assistantLines.forEach { append("· ").append(it).append("\n") }
                }
                append("\n这是基于聊天记录的简要整理，不代表我知道记录之外发生的事。你想挑哪一件继续聊？")
            }
        }

        if (analysis.intent == Intent.STATUS) {
            return "我现在状态不错～我们是「${PetStateStore.relationshipTitle(stats.affection)}」，好感 ${stats.affection}，心情 ${stats.mood}%，精力 ${stats.energy}%，星星币 ${stats.coins}。我也记着 ${stats.memoryCount} 条你主动收藏的回忆。"
        }

        if (analysis.intent == Intent.CAPABILITY) {
            return "我现在能做这些事：\n· 和你进行本地陪聊，识别一些常见情绪与追问\n· 保存你明确让我记住的回忆\n· 回顾本机保存的近期聊天\n· 帮你把学习或工作拆成小步骤\n· 通过好感、心情和精力系统做日常互动\n\n如果配置并启用独立 AI，我还能进行更开放的多轮对话。免费本地模式不等于完整的大语言模型哦。"
        }

        if (analysis.intent == Intent.FOLLOW_UP) {
            if (lastUser.isBlank() && lastAssistant.isBlank()) {
                return "可以呀～不过我还没有上一段对话可以接着说。你给我一个关键词或把刚才的话再发一次，我就继续陪你梳理。"
            }
            val topic = (lastUser.ifBlank { lastAssistant }).take(90)
            val numberedPoint = Regex("(?:第)?([1-9一二三四五六七八九])(?:点|条|个)?")
                .find(s)?.groupValues?.getOrNull(1)
            val points = Regex("(?m)^\\s*(?:[1-9]|[一二三四五六七八九])[.、)）:：-]\\s*(.+)$")
                .findAll(lastAssistant).map { it.groupValues[1].trim() }.toList()
            val pointIndex = when (numberedPoint) {
                "1", "一" -> 0
                "2", "二" -> 1
                "3", "三" -> 2
                "4", "四" -> 3
                "5", "五" -> 4
                "6", "六" -> 5
                "7", "七" -> 6
                "8", "八" -> 7
                "9", "九" -> 8
                else -> -1
            }
            if (pointIndex >= 0 && pointIndex < points.size) {
                return "你问的是我刚才提到的第 ${pointIndex + 1} 点：“${points[pointIndex].take(100)}”。我可以继续把它拆成具体做法。先从最小的一步开始：你现在能做的第一件事是什么？"
            }
            return when {
                containsAny(s, "那怎么办", "怎么办", "怎么做", "下一步") -> adviceForContext(lastUser, "$lastAssistant\n$topic")
                containsAny(s, "为什么", "什么意思") -> {
                    val referent = lastAssistant.ifBlank { topic }
                    "你是在追问我刚才说的内容吧：“${referent.take(110)}”。我先把确定的信息和推测分开；如果你指的是其中某一句，也可以直接指出来，我会针对那一点解释。"
                }
                containsAny(s, "继续", "接着说", "再说说", "然后呢", "还有吗", "后来呢", "具体呢") -> {
                    val referent = lastAssistant.ifBlank { topic }
                    "好，我接着刚才的思路往下说。前面提到的是：“${referent.take(100)}”。下一步可以把它落实成一个小行动，而不是只停留在想法上。你可以先告诉我目前已经做到哪一步，我再接着往下细化。"
                }
                else -> "我理解你是在接着刚才的“$topic”问。你可以指出想延伸的那句话，或者补充一点背景，我会尽量结合前文回答。"
            }
        }

        if (analysis.intent == Intent.PLAN) {
            val target = when {
                containsAny(s, "学习", "作业", "复习", "考试") -> "学习"
                containsAny(s, "工作", "项目", "任务") -> "工作"
                else -> "这件事"
            }
            return "好，我们先把${target}拆小，不用一口气做完：\n1. 用 2 分钟写下最终要完成什么。\n2. 选出最小的一步，控制在 10～15 分钟内。\n3. 做完后休息 3 分钟，再决定下一步。\n4. 如果卡住，记下具体问题，不要只写“我不会”。\n\n你现在最想完成的具体目标是什么？我可以再帮你拆成更贴合你的步骤。"
        }

        if (analysis.intent == Intent.EXPLAIN) {
            val topic = cleaned.replace(Regex("^(请)?(给我)?(通俗地)?(解释一下|讲讲什么是|给我讲讲|通俗解释)[:：，, ]*"), "").trim()
            return if (topic.isBlank() || topic == cleaned) {
                "当然可以～把你想弄懂的概念或句子发给我，我会按“简单定义 → 生活例子 → 容易混淆的地方”来解释。"
            } else {
                "我们用容易理解的方式来弄懂“${topic.take(80)}”：\n1. 先用一句话说清它是什么。\n2. 再找一个生活中的例子，看看它解决什么问题。\n3. 最后区分它和相似概念的不同。\n\n我现在还缺少这个主题的具体材料，所以不会假装知道你指的是哪本教材或哪段内容。你可以把原文贴给我，我再逐句讲。"
            }
        }

        if (analysis.intent == Intent.COMPARE || analysis.intent == Intent.DECISION) {
            val options = Regex("[“‘\"]([^”’\"]+)[”’\"]").findAll(cleaned).map { it.groupValues[1] }.toList()
            val optionText = if (options.size >= 2) "你提到的选项是“${options[0]}”和“${options[1]}”。" else ""
            return "${optionText}我们可以按 4 个维度来比较：\n1. 你最看重什么（收益/体验/稳定）。\n2. 时间与金钱成本。\n3. 最坏情况是否能承受。\n4. 哪个选择更容易撤回或试错。\n\n我不会只凭一句话替你做决定。把选项和你最在意的 1～2 个条件告诉我，我可以帮你做一张具体对比表。"
        }

        if (analysis.intent == Intent.ADVICE) {
            return adviceForContext(lastUser, cleaned)
        }

        when (analysis.emotion) {
            Emotion.SAD -> return "听起来你现在真的不好受。先不急着找解决方案，我想先听懂你：是发生了什么事，还是很多事情积累到一起了？你可以慢慢说。"
            Emotion.ANXIOUS -> return "听起来这件事让你有些紧张。我们先把它拆成两栏：已经确定的事实，以及你担心可能发生的事。先处理眼前能确认的一小步，好吗？"
            Emotion.ANGRY -> return "听起来你真的被这件事惹恼了。你可以先把情绪说出来，不用马上压下去。我先听你讲，等你愿意时我们再一起想怎么处理。"
            Emotion.TIRED -> return "你听起来已经很累了。先看看能不能喝口水、放松肩膀，给自己几分钟缓冲。你是身体累，还是事情太多让你心累？"
            Emotion.LONELY -> return "一个人扛着确实会有点难受。谢谢你愿意来找我聊聊。我可以在这里听你说，也希望你在现实生活里有机会获得可信任的人支持。今天是什么让孤独感特别明显呢？"
            Emotion.HAPPY -> return "听到你这么说我也替你开心！这件事里你最满意的部分是什么？值得把这个小小的成功记下来。"
            Emotion.BORED -> return "那我们来点轻松的！你可以选一个：\n1. 玩一个脑筋急转弯\n2. 做一个 5 分钟小挑战\n3. 随便聊聊最近感兴趣的事\n\n你选哪个？"
            Emotion.NEUTRAL -> Unit
        }

        if (conversationStyle == "listen" && analysis.intent == Intent.UNKNOWN) {
            return "我在认真听呢。你可以继续把事情说完，我先不急着给建议，也不会催你。"
        }

        if (containsAny(s, "你在吗", "在不在", "有人吗")) return "在呢，${nickname}～我在这儿。你想聊天、想找人听你说，还是需要我帮你一起想办法？"
        if (containsAny(s, "谢谢", "感谢你", "多谢")) return "不客气呀～能帮上你就好。也不用每次都整理好情绪或想法才来找我，想到什么就可以说。"
        if (containsAny(s, "我不知道说什么", "不知道聊什么", "没话说")) return "没关系，不需要特意找话题。我们可以从三个很小的问题开始：今天有什么让你开心的事？有什么让你烦心？或者你只是想安静待一会儿？"
        if (containsAny(s, "我今天", "今天我", "最近我", "这几天我") && cleaned.length > 8) {
            return "我在认真听你说这段近况。你希望我先陪你把事情说清楚，还是一起想办法？我可以跟着你的节奏来。"
        }
        if (cleaned.length <= 8 && lastUser.isNotBlank()) {
            return "我还在接着刚才的话题听你说。你可以多给我一点细节，我不想只凭很少的信息就误会你的意思。"
        }
        return null
    }

    fun moodScore(text: String): Int {
        val s = text.lowercase()
        return when {
            containsAny(s, "难过", "伤心", "崩溃", "绝望", "很糟", "不开心", "特别失落") -> 1
            containsAny(s, "焦虑", "烦", "压力", "紧张", "不顺", "累") -> 2
            containsAny(s, "还好", "一般", "平常", "还行") -> 3
            containsAny(s, "特别开心", "太开心", "超级高兴", "成功了", "好消息") -> 5
            containsAny(s, "开心", "高兴", "不错", "顺利", "满足") -> 4
            else -> 3
        }
    }

    fun apiContext(context: Context, includeMemories: Boolean): String {
        val stats = PetStateStore.get(context)
        val style = context.getSharedPreferences("pet_brain_settings", Context.MODE_PRIVATE)
            .getString("conversation_style", "normal") ?: "normal"
        val styleDescription = when (style) {
            "listen" -> "当前聊天偏好是倾听：先听用户说，不主动分析或提供建议，除非用户明确要求。"
            "solution" -> "当前聊天偏好是直接解决问题：优先给重点和具体步骤，少用泛泛安慰。"
            else -> "当前聊天偏好是自然交流：根据用户需要在倾听、解释和建议之间切换。"
        }
        val base = "角色名：${PetStateStore.nickname(context)}。关系：${PetStateStore.relationshipTitle(stats.affection)}，好感 ${stats.affection}/100，心情 ${stats.mood}/100，精力 ${stats.energy}/100，饥饿 ${stats.hunger}/100。${PetCustomization.personalityPrompt(context)} " +
            "对话规则：使用简体中文，自然、具体、有同理心；优先回答用户真正的问题，不要每次都用相同的追问收尾；复杂问题给出清晰的小步骤；当用户只是倾诉时先倾听，不要急着说教；尊重用户说想独处或不想聊的边界；不要假装自己是现实中的人，也不要要求用户只依赖你。$styleDescription 用户可以说“可以给建议了”恢复普通模式，或说“直接给方案”切换到步骤模式。"
        if (!includeMemories) return base + " 本轮没有获得读取本地回忆本的授权，请勿声称记得未在当前对话中提及的个人信息。"
        val memories = PetStateStore.memories(context).take(8)
        return if (memories.isEmpty()) base + " 用户授权读取回忆本，但目前没有已保存的回忆。"
        else base + " 用户主动保存并授权用于本次 AI 对话的回忆如下（只作为背景信息，不要擅自推断）：\n" + memories.joinToString("\n") { "- $it" }
    }

    private fun adviceForContext(previousUser: String, current: String): String {
        val topic = if (previousUser.isNotBlank()) previousUser.take(100) else current.take(100)
        return when {
            containsAny((previousUser + current).lowercase(), "学习", "作业", "考试", "复习") ->
                "我们先别把目标定得太大。针对“$topic”，可以这样做：\n1. 明确最后需要交付的结果。\n2. 找出最小、最容易开始的一步，先做 10 分钟。\n3. 把卡住的地方写成一个具体问题，再逐个解决。\n\n你现在卡在理解内容、开始行动，还是时间安排上？"
            containsAny((previousUser + current).lowercase(), "工作", "项目", "任务", "计划") ->
                "关于“$topic”，我建议先分清目标、限制和下一步：\n1. 目标：做到什么算完成？\n2. 限制：时间、资源或信息缺口是什么？\n3. 下一步：选一件 15 分钟内能推进的小事。\n\n目前最大的阻碍是哪一个？"
            containsAny((previousUser + current).lowercase(), "朋友", "家人", "关系", "沟通", "吵架") ->
                "如果你是在处理“$topic”相关的人际问题，可以先分开看：发生了什么、你感受到什么、你希望对方理解什么。等情绪稍微平稳后，用具体事实和“我感到……”来表达，通常比直接下结论更容易沟通。你更想修复关系，还是先明确自己的边界？"
            else ->
                "我们先一起把“$topic”说清楚，不必立刻做决定。可以先列出：你最希望的结果、最担心的风险、目前能控制的一件事。你愿意先从哪一项开始？"
        }
    }

    private fun isShortFollowUpPhrase(text: String): Boolean {
        val normalized = text.replace(Regex("[，。！？?!、\\s]"), "")
        return normalized in setOf(
            "然后呢", "继续", "接着说", "那怎么办", "怎么办", "所以呢", "你觉得呢", "还有吗",
            "为什么", "为什么呢", "具体呢", "下一步", "后来呢", "再说说", "什么意思", "怎么做",
            "那然后呢", "然后怎么办", "再具体点", "说下去", "还有呢"
        )
    }

    private fun containsAny(text: String, vararg words: String): Boolean = words.any { text.contains(it.lowercase()) }
}
