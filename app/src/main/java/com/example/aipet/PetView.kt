package com.example.aipet

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import java.lang.ref.WeakReference
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

enum class PetMood(val frameIndex: Int) {
    IDLE(0), BLINK(1), HAPPY(2), SHY(3), SURPRISED(4), ANGRY(5), EMBARRASSED(6),
    SLEEP(7), EAT(8), DRINK(9), WALK(10), RUN(11), JUMP(12), SIT(13), LIE(14),
    THINKING(3), SAD(6)
}

object PetAnimator {
    private var activeView: WeakReference<PetView>? = null
    internal fun attach(view: PetView) { activeView = WeakReference(view) }
    internal fun detach(view: PetView) { if (activeView?.get() === view) activeView = null }
    fun react(mood: PetMood, bubble: String = "", durationMs: Long = 1800L) { activeView?.get()?.showMood(mood, bubble, durationMs) }
    fun reactToTap() { activeView?.get()?.reactToTap() }
    fun toggleSleep(): Boolean? = activeView?.get()?.toggleSleep()
    fun playDemoSequence(): Boolean = activeView?.get()?.playDemoSequence() ?: false
    fun showStatusBubble() { activeView?.get()?.showStatusBubble() }

    fun reactToText(text: String, isReply: Boolean = false) {
        val s = text.lowercase()
        val reaction = when {
            listOf("晚安", "睡觉", "睡了", "困了", "好困").any { s.contains(it) } -> PetMood.SLEEP to "晚安～"
            listOf("走两步", "走路", "散步").any { s.contains(it) } -> PetMood.WALK to "哒哒哒～"
            listOf("跑步", "跑起来", "冲呀").any { s.contains(it) } -> PetMood.RUN to "冲呀！"
            listOf("跳一下", "跳舞", "蹦蹦跳跳").any { s.contains(it) } -> PetMood.JUMP to "看我的！"
            listOf("坐下", "坐一会", "坐好").any { s.contains(it) } -> PetMood.SIT to "坐下休息～"
            listOf("躺下", "躺一会", "趴下").any { s.contains(it) } -> PetMood.LIE to "躺平一下～"
            listOf("生气", "气死", "烦死", "讨厌").any { s.contains(it) } -> PetMood.ANGRY to "哼！"
            listOf("害羞", "夸我", "可爱", "喜欢你").any { s.contains(it) } -> PetMood.SHY to "才没有脸红呢！"
            listOf("惊讶", "吓一跳", "真的假的").any { s.contains(it) } -> PetMood.SURPRISED to "欸？！"
            listOf("难过", "伤心", "孤独", "委屈", "不开心", "压力").any { s.contains(it) } -> PetMood.SAD to "我在这里。"
            listOf("开心", "高兴", "成功", "好消息", "谢谢").any { s.contains(it) } -> PetMood.HAPPY to "真好～"
            listOf("吃饭", "吃东西", "饿了", "零食").any { s.contains(it) } -> PetMood.EAT to "一起吃点东西！"
            listOf("喝水", "喝饮料", "奶茶").any { s.contains(it) } -> PetMood.DRINK to "咕噜咕噜～"
            listOf("摸摸头", "抱抱").any { s.contains(it) } -> PetMood.EMBARRASSED to "抱抱你～"
            isReply -> PetMood.HAPPY to "嗯嗯，我在听～"
            else -> PetMood.THINKING to "让我想想…"
        }
        react(reaction.first, reaction.second, if (reaction.first == PetMood.SLEEP) 2600L else 1900L)
    }
}

class PetView(context: Context) : View(context) {
    private val atlas: Bitmap? = BitmapFactory.decodeResource(resources, R.drawable.pet_atlas)
    private val spritePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val bubbleBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(193, 219, 255)
        style = Paint.Style.STROKE
        strokeWidth = dp(1.2f)
    }
    private val bubbleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(67, 75, 112)
        textSize = dp(11.5f)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(120, 222, 235, 255) }
    private val platePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(225, 250, 252, 255) }
    private val plateStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 223, 232, 255)
        style = Paint.Style.STROKE
        strokeWidth = dp(1.3f)
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(42, 65, 69, 95) }
    private val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(235, 255, 255, 255) }
    private val chipTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(82, 84, 118)
        textSize = dp(9.6f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private var mood = PetMood.IDLE
    private var moodUntil = 0L
    private var bubbleText = ""
    private var bubbleUntil = 0L
    private var sleeping = false
    private var nextIdleActionAt = SystemClock.uptimeMillis() + 9000L
    private var frameTick = 0
    private var tapIndex = 0
    private var sparklePhase = 0
    private var lastStatsUpdateAt = 0L
    private var cachedStats: PetStats? = null
    private var cachedPalette: PetPalette? = null
    private val sparklePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(180, 255, 255, 255) }

    private val demoHandler = Handler(Looper.getMainLooper())
    private val demoActions = listOf(
        PetMood.BLINK to "眨眨眼～", PetMood.HAPPY to "开心！", PetMood.SHY to "有点害羞…",
        PetMood.SURPRISED to "欸？！", PetMood.ANGRY to "哼！", PetMood.EMBARRASSED to "抱抱…",
        PetMood.THINKING to "让我想想…", PetMood.EAT to "吃点东西～", PetMood.DRINK to "喝口水～",
        PetMood.WALK to "散步时间", PetMood.RUN to "冲呀！", PetMood.JUMP to "跳一下！",
        PetMood.SIT to "坐下休息", PetMood.LIE to "躺平一下", PetMood.SLEEP to "晚安～"
    )
    private var demoIndex = 0
    private var demoRunning = false
    private val demoRunnable = object : Runnable {
        override fun run() {
            if (!demoRunning) return
            if (demoIndex >= demoActions.size) {
                demoRunning = false
                showMood(PetMood.HAPPY, "演示完成～", 1500L)
                return
            }
            val (demoMood, text) = demoActions[demoIndex++]
            showMood(demoMood, text, 900L)
            demoHandler.postDelayed(this, 1050L)
        }
    }

    init {
        contentDescription = "二次元小伙伴桌宠：单击互动，双击桌面对话，长按打开菜单，拖动移动"
        isClickable = true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        PetAnimator.attach(this)
        postInvalidateDelayed(110L)
    }

    override fun onDetachedFromWindow() {
        demoRunning = false
        demoHandler.removeCallbacks(demoRunnable)
        PetAnimator.detach(this)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        frameTick++
        sparklePhase++
        val now = SystemClock.uptimeMillis()
        updateMood(now)
        if (cachedStats == null || now - lastStatsUpdateAt >= 3000L) {
            cachedStats = PetStateStore.get(context)
            cachedPalette = PetCustomization.palette(context)
            lastStatsUpdateAt = now
        }
        val stats = cachedStats ?: PetStateStore.get(context)
        val palette = cachedPalette ?: PetCustomization.palette(context)
        glowPaint.color = palette.glow
        platePaint.color = palette.plate
        plateStroke.color = palette.plateBorder
        bubbleBorderPaint.color = palette.bubbleBorder
        sparklePaint.color = palette.sparkle
        chipTextPaint.color = palette.chipText
        bubbleTextPaint.color = palette.chipText

        val availableSize = min(width - dp(12f), height - dp(64f)).coerceAtLeast(dp(90f))
        val left = (width - availableSize) / 2f
        val top = height - availableSize - dp(44f)
        val bob = if (!sleeping && mood in listOf(PetMood.IDLE, PetMood.BLINK, PetMood.HAPPY, PetMood.SHY)) {
            (sin(frameTick / 8.0) * dp(1.8f)).toFloat()
        } else 0f

        canvas.drawOval(RectF(width * 0.22f, height - dp(18f), width * 0.78f, height - dp(6f)), shadowPaint)
        drawBackplate(canvas, left, top, availableSize, bob)
        drawSparkles(canvas, left, top, availableSize)
        val activeMood = if (sleeping) PetMood.SLEEP else mood
        drawAnimatedSprite(canvas, activeMood, RectF(left, top + bob, left + availableSize, top + availableSize + bob))
        drawStatusChips(canvas, stats)
        if (bubbleText.isNotBlank() && now < bubbleUntil) drawBubble(canvas, bubbleText)
        postInvalidateDelayed(110L)
    }

    private fun drawBackplate(canvas: Canvas, left: Float, top: Float, size: Float, bob: Float) {
        val cx = left + size / 2f
        val cy = top + size / 2f + bob + dp(6f)
        canvas.drawCircle(cx, cy, size * 0.38f, glowPaint)
        canvas.drawRoundRect(RectF(cx - size * 0.34f, cy - size * 0.32f, cx + size * 0.34f, cy + size * 0.34f), dp(26f), dp(26f), platePaint)
        canvas.drawRoundRect(RectF(cx - size * 0.34f, cy - size * 0.32f, cx + size * 0.34f, cy + size * 0.34f), dp(26f), dp(26f), plateStroke)
    }

    private fun drawSparkles(canvas: Canvas, left: Float, top: Float, size: Float) {
        val sparkle = sparklePaint
        val points = listOf(
            Pair(left + size * 0.18f, top + size * 0.12f),
            Pair(left + size * 0.82f, top + size * 0.16f),
            Pair(left + size * 0.12f, top + size * 0.52f),
            Pair(left + size * 0.85f, top + size * 0.48f)
        )
        points.forEachIndexed { index, pair ->
            val pulse = (sin((sparklePhase + index * 12) / 9.0) * dp(1.4f) + dp(3f)).toFloat()
            canvas.drawCircle(pair.first, pair.second, pulse, sparkle)
        }
    }

    private fun updateMood(now: Long) {
        if (!sleeping && mood != PetMood.IDLE && moodUntil > 0L && now >= moodUntil) {
            mood = PetMood.IDLE
            moodUntil = 0L
        }
        if (now >= bubbleUntil) bubbleText = ""
        if (!sleeping && mood == PetMood.IDLE && now >= nextIdleActionAt) {
            val idleLine = PetChatEngine.randomIdleLine(context)
            val personality = PetCustomization.personality(context)
            when {
                personality == "元气活泼" && Random.nextInt(3) == 0 -> showMood(PetMood.JUMP, "嘿！", 1100L)
                personality == "傲娇可爱" && Random.nextInt(3) == 0 -> showMood(PetMood.SHY, "才没有在等你…", 1400L)
                else -> when (Random.nextInt(5)) {
                    0 -> showMood(PetMood.BLINK, "", 380L)
                    1 -> showMood(PetMood.HAPPY, idleLine, 1500L)
                    2 -> showMood(PetMood.THINKING, "嗯…", 1200L)
                    3 -> showMood(PetMood.SIT, "休息一下", 1250L)
                    else -> showStatusBubble()
                }
            }
            val delay = if (personality == "元气活泼") Random.nextLong(22000L, 48000L) else Random.nextLong(30000L, 65000L)
            nextIdleActionAt = now + delay
        }
    }

    private fun drawAnimatedSprite(canvas: Canvas, activeMood: PetMood, destination: RectF) {
        val phase = frameTick / 4.0
        var dx = 0f
        var dy = 0f
        var scale = 1f
        var rotation = 0f
        when (activeMood) {
            PetMood.JUMP -> dy = (-kotlin.math.abs(sin(phase)) * dp(10f)).toFloat()
            PetMood.RUN -> { dx = (sin(phase * 1.7) * dp(3.5f)).toFloat(); rotation = (sin(phase * 1.7) * 3.5).toFloat() }
            PetMood.WALK -> dx = (sin(phase) * dp(2.5f)).toFloat()
            PetMood.HAPPY -> { dy = (-kotlin.math.abs(sin(phase)) * dp(2.2f)).toFloat(); scale = 1f + (kotlin.math.abs(sin(phase)) * 0.018f).toFloat() }
            PetMood.SHY, PetMood.EMBARRASSED -> rotation = (sin(phase / 2.0) * 1.8).toFloat()
            PetMood.SURPRISED -> scale = 1f + (kotlin.math.abs(sin(phase * 1.5)) * 0.035f).toFloat()
            PetMood.SLEEP, PetMood.LIE -> rotation = -1.2f
            else -> { }
        }
        val cx = destination.centerX()
        val cy = destination.centerY()
        canvas.save()
        canvas.translate(dx, dy)
        canvas.rotate(rotation, cx, cy)
        canvas.scale(scale, scale, cx, cy)
        drawSprite(canvas, activeMood.frameIndex, destination)
        canvas.restore()
    }

    private fun drawSprite(canvas: Canvas, frame: Int, destination: RectF) {
        val bitmap = atlas ?: return
        val cols = 5
        val rows = 3
        val cellW = bitmap.width / cols
        val cellH = bitmap.height / rows
        val safeFrame = frame.coerceIn(0, cols * rows - 1)
        val col = safeFrame % cols
        val row = safeFrame / cols
        val source = Rect(col * cellW, row * cellH, (col + 1) * cellW, (row + 1) * cellH)
        canvas.drawBitmap(bitmap, source, destination, spritePaint)
    }

    private fun drawStatusChips(canvas: Canvas, stats: PetStats) {
        val texts = listOf("♥ ${stats.affection}", "Lv.${stats.level}", "☺ ${stats.mood}%")
        var x = dp(8f)
        val y = height - dp(18f)
        texts.forEach { text ->
            val w = chipTextPaint.measureText(text) + dp(14f)
            val rect = RectF(x, y - dp(18f), x + w, y)
            canvas.drawRoundRect(rect, dp(11f), dp(11f), chipPaint)
            canvas.drawRoundRect(rect, dp(11f), dp(11f), bubbleBorderPaint)
            canvas.drawText(text, x + dp(7f), y - dp(6f), chipTextPaint)
            x += w + dp(6f)
        }
    }

    private fun drawBubble(canvas: Canvas, rawText: String) {
        val text = if (rawText.length > 14) rawText.take(13) + "…" else rawText
        val bubbleWidth = (bubbleTextPaint.measureText(text) + dp(22f)).coerceAtMost(width - dp(8f))
        val bubbleHeight = dp(32f)
        val left = (width - bubbleWidth) / 2f
        val top = dp(4f)
        val rect = RectF(left, top, left + bubbleWidth, top + bubbleHeight)
        canvas.drawRoundRect(rect, dp(14f), dp(14f), bubblePaint)
        canvas.drawRoundRect(rect, dp(14f), dp(14f), bubbleBorderPaint)
        val tail = Path().apply {
            moveTo(width / 2f - dp(4f), top + bubbleHeight - dp(1f))
            lineTo(width / 2f, top + bubbleHeight + dp(6f))
            lineTo(width / 2f + dp(4f), top + bubbleHeight - dp(1f))
            close()
        }
        canvas.drawPath(tail, bubblePaint)
        canvas.drawPath(tail, bubbleBorderPaint)
        val baseline = top + (bubbleHeight - bubbleTextPaint.ascent() - bubbleTextPaint.descent()) / 2f
        canvas.drawText(text, width / 2f, baseline, bubbleTextPaint)
    }

    internal fun showMood(newMood: PetMood, bubble: String, durationMs: Long) {
        if (sleeping && newMood !in listOf(PetMood.HAPPY, PetMood.SURPRISED, PetMood.SLEEP)) return
        mood = newMood
        moodUntil = SystemClock.uptimeMillis() + durationMs.coerceAtLeast(250L)
        bubbleText = bubble
        bubbleUntil = if (bubble.isBlank()) 0L else SystemClock.uptimeMillis() + durationMs.coerceAtLeast(700L)
        nextIdleActionAt = moodUntil + Random.nextLong(1800L, 4200L)
        invalidate()
    }

    internal fun reactToTap() {
        if (sleeping) { toggleSleep(); return }
        val before = PetStateStore.get(context)
        val after = PetStateStore.interact(context, "tap")
        cachedStats = after
        lastStatsUpdateAt = SystemClock.uptimeMillis()
        val oldTitle = PetStateStore.relationshipTitle(before.affection)
        val newTitle = PetStateStore.relationshipTitle(after.affection)
        if (oldTitle != newTitle) {
            showMood(PetMood.HAPPY, "我們成為「$newTitle」啦！", 2300L)
            return
        }
        val options = listOf(
            PetMood.HAPPY to "嘿嘿～",
            PetMood.SHY to "摸摸头呀？",
            PetMood.EMBARRASSED to "抱抱～",
            PetMood.SURPRISED to "呀，被戳到了！",
            PetMood.THINKING to "怎么啦？",
            PetMood.JUMP to "看我跳一下！"
        )
        val pick = options[tapIndex++ % options.size]
        showMood(pick.first, pick.second, 1400L)
    }

    internal fun toggleSleep(): Boolean {
        sleeping = !sleeping
        if (sleeping) {
            PetStateStore.interact(context, "rest")
            showMood(PetMood.SLEEP, "呼…晚安", 2200L)
        } else showMood(PetMood.HAPPY, "醒来啦！", 1600L)
        return sleeping
    }

    internal fun playDemoSequence(): Boolean {
        if (demoRunning) return false
        demoRunning = true
        demoIndex = 0
        demoHandler.post(demoRunnable)
        return true
    }

    internal fun showStatusBubble() {
        val stats = PetStateStore.get(context)
        showMood(PetMood.HAPPY, "好感${stats.affection}·${PetStateStore.relationshipTitle(stats.affection)}", 1700L)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
