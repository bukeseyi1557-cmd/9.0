package com.example.aipet

import android.content.Context
import android.graphics.Color

data class PetPalette(
    val glow: Int,
    val plate: Int,
    val plateBorder: Int,
    val bubbleBorder: Int,
    val sparkle: Int,
    val chipText: Int
)

object PetCustomization {
    private const val PREFS = "pet_state"
    private const val KEY_THEME = "pet_theme"
    private const val KEY_PERSONALITY = "pet_personality"

    val themes = listOf("樱花粉", "星空蓝", "薄荷绿", "奶油黄")
    val personalities = listOf("温柔陪伴", "元气活泼", "傲娇可爱")

    fun theme(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY_THEME, "樱花粉") ?: "樱花粉"

    fun setTheme(context: Context, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_THEME, value).apply()
    }

    fun personality(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY_PERSONALITY, "温柔陪伴") ?: "温柔陪伴"

    fun setPersonality(context: Context, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_PERSONALITY, value).apply()
    }

    fun palette(context: Context): PetPalette = when (theme(context)) {
        "星空蓝" -> PetPalette(
            Color.argb(130, 95, 145, 255), Color.argb(232, 239, 246, 255),
            Color.rgb(143, 180, 255), Color.rgb(124, 163, 245),
            Color.argb(205, 222, 236, 255), Color.rgb(55, 76, 125)
        )
        "薄荷绿" -> PetPalette(
            Color.argb(125, 125, 226, 193), Color.argb(232, 240, 255, 248),
            Color.rgb(145, 220, 196), Color.rgb(111, 199, 171),
            Color.argb(205, 219, 255, 238), Color.rgb(48, 105, 89)
        )
        "奶油黄" -> PetPalette(
            Color.argb(125, 255, 210, 120), Color.argb(234, 255, 250, 231),
            Color.rgb(246, 211, 144), Color.rgb(235, 185, 103),
            Color.argb(210, 255, 242, 202), Color.rgb(117, 85, 39)
        )
        else -> PetPalette(
            Color.argb(125, 255, 187, 218), Color.argb(234, 255, 245, 251),
            Color.rgb(245, 194, 222), Color.rgb(232, 153, 194),
            Color.argb(210, 255, 224, 240), Color.rgb(115, 65, 96)
        )
    }

    fun styleReply(context: Context, reply: String): String = when (personality(context)) {
        "元气活泼" -> if (reply.startsWith("嘿嘿") || reply.startsWith("冲呀")) reply else listOf("好耶！", "收到收到～", "嗯嗯！").random() + reply
        "傲娇可爱" -> when {
            reply.contains("安慰") || reply.contains("难过") || reply.contains("抱抱") -> reply + "……才不是特别担心你呢，只是刚好想陪着你。"
            reply.contains("好感") -> reply + "哼，才没有因为你高兴呢！"
            else -> reply + if (reply.endsWith("～") || reply.endsWith("！")) "哼～" else "。才、才不是特意关心你。"
        }
        else -> reply
    }

    fun personalityPrompt(context: Context): String = when (personality(context)) {
        "元气活泼" -> "性格设定：元气活泼，积极、俏皮、鼓励感强，但不要过度使用感叹号。"
        "傲娇可爱" -> "性格设定：轻微傲娇、嘴硬心软、可爱俏皮；不能羞辱用户，用户难过时要认真温柔地支持。"
        else -> "性格设定：温柔陪伴，耐心倾听，表达自然、有分寸，不强迫用户回复。"
    }
}
