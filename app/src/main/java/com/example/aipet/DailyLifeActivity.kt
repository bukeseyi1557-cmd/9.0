package com.example.aipet

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.CountDownTimer
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import java.util.concurrent.TimeUnit

class DailyLifeActivity : Activity() {
    private var selectedMood = 3
    private lateinit var moodNote: EditText
    private lateinit var moodHistoryBox: LinearLayout
    private lateinit var timerLabel: TextView
    private lateinit var timerStatus: TextView
    private lateinit var focusCountLabel: TextView
    private lateinit var pauseButton: Button
    private var timer: CountDownTimer? = null
    private var remainingMs = 25 * 60 * 1000L
    private var timerRunning = false
    private var timerPaused = false
    private var focusSession = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
        refreshMoodHistory()
        updateTimerUi()
    }

    private fun buildScreen() {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(24))
            setBackgroundColor(Color.rgb(248, 249, 255))
        }
        scroll.addView(root)
        root.addView(TextView(this).apply {
            text = "日常陪伴小屋 ☁️"
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(64, 69, 108))
        }, matchWrap())
        root.addView(TextView(this).apply {
            text = "可以记录自己的心情，也可以让她陪你专注一会儿。这里不打分、不批评，按你舒服的节奏来就好。"
            textSize = 14f
            setTextColor(Color.rgb(108, 112, 145))
            setPadding(0, dp(8), 0, dp(16))
        }, matchWrap())

        root.addView(sectionTitle("今天的心情怎么样？"), matchWrap())
        val moodRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val moodOptions = listOf("😣" to "低落", "😕" to "不太好", "😐" to "一般", "🙂" to "不错", "😄" to "开心")
        moodOptions.forEachIndexed { index, pair ->
            moodRow.addView(Button(this).apply {
                text = "${pair.first}\n${pair.second}"
                textSize = 12f
                isAllCaps = false
                minWidth = 0
                setPadding(dp(3), dp(4), dp(3), dp(4))
                setOnClickListener {
                    selectedMood = index + 1
                    refreshMoodSelection(moodRow, moodOptions)
                }
                layoutParams = LinearLayout.LayoutParams(0, dp(68), 1f).apply {
                    leftMargin = dp(2); rightMargin = dp(2)
                }
            })
        }
        root.addView(moodRow, matchWrap())
        refreshMoodSelection(moodRow, moodOptions)

        moodNote = EditText(this).apply {
            hint = "想说的话（可选）：今天发生了什么？"
            minLines = 2
            maxLines = 4
            gravity = Gravity.TOP
            textSize = 14f
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = roundedBackground(Color.WHITE, Color.rgb(220, 227, 250))
        }
        root.addView(moodNote, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(10); bottomMargin = dp(8)
        })
        root.addView(makeButton("保存今天的心情") { saveMood() }, matchWrap())

        root.addView(sectionTitle("专注陪伴 · 让她陪你完成一小步"), matchWrap(top = 20))
        root.addView(TextView(this).apply {
            text = "默认专注 25 分钟，短休息 5 分钟。计时期间请保持这个页面打开；离开页面后计时会停止，不会偷偷在后台运行。"
            textSize = 13f
            setTextColor(Color.rgb(108, 112, 145))
            setPadding(0, 0, 0, dp(10))
        }, matchWrap())
        val timerCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(18), dp(16), dp(18))
            background = roundedBackground(Color.WHITE, Color.rgb(215, 224, 255))
        }
        timerStatus = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.rgb(106, 112, 150))
        }
        timerCard.addView(timerStatus, matchWrap())
        timerLabel = TextView(this).apply {
            text = "25:00"
            textSize = 42f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(Color.rgb(82, 111, 202))
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(8))
        }
        timerCard.addView(timerLabel, matchWrap())
        focusCountLabel = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.rgb(124, 128, 157))
        }
        timerCard.addView(focusCountLabel, matchWrap())
        val timerButtons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        timerButtons.addView(makeButton("开始专注 25 分钟") { startTimer(25 * 60 * 1000L, true) }, LinearLayout.LayoutParams(0, dp(48), 1f))
        timerButtons.addView(makeButton("休息 5 分钟") { startTimer(5 * 60 * 1000L, false) }, LinearLayout.LayoutParams(0, dp(48), 1f))
        timerCard.addView(timerButtons, matchWrap())
        val controlRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        pauseButton = makeButton("暂停") { pauseOrResume() }
        controlRow.addView(pauseButton, LinearLayout.LayoutParams(0, dp(44), 1f))
        controlRow.addView(makeButton("结束计时") { stopTimer(true) }, LinearLayout.LayoutParams(0, dp(44), 1f))
        timerCard.addView(controlRow, matchWrap())
        root.addView(timerCard, matchWrap())

        root.addView(sectionTitle("最近的心情记录"), matchWrap(top = 20))
        moodHistoryBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = roundedBackground(Color.WHITE, Color.rgb(225, 230, 247))
        }
        root.addView(moodHistoryBox, matchWrap())
        root.addView(makeButton("清空心情记录") { confirmClearMood() }, matchWrap(top = 8))
        setContentView(scroll)
    }

    private fun refreshMoodSelection(row: LinearLayout, options: List<Pair<String, String>>) {
        for (index in 0 until row.childCount) {
            val button = row.getChildAt(index) as? Button ?: continue
            val selected = index + 1 == selectedMood
            button.setTextColor(if (selected) Color.WHITE else Color.rgb(74, 79, 119))
            button.background = roundedBackground(
                if (selected) Color.rgb(111, 145, 235) else Color.WHITE,
                if (selected) Color.rgb(111, 145, 235) else Color.rgb(221, 228, 248)
            )
        }
    }

    private fun saveMood() {
        val entry = PetMoodJournal.add(this, selectedMood, moodNote.text.toString())
        moodNote.setText("")
        refreshMoodHistory()
        val line = when (selectedMood) {
            1 -> "今天好像不太容易。先不用强迫自己振作，我在这里听你说。"
            2 -> "辛苦啦。要不要先把最烦心的那件事放下来一会儿？"
            3 -> "平平稳稳的一天也很好呀。我们慢慢来。"
            4 -> "听起来还不错！希望今天有一点小小的开心留给你。"
            else -> "耶～把这份开心好好收起来吧！我也替你高兴。"
        }
        PetAnimator.react(if (selectedMood <= 2) PetMood.EMBARRASSED else PetMood.HAPPY, line, 2400L)
        Toast.makeText(this, "心情已保存在本地：${entry.moodLabel()}", Toast.LENGTH_SHORT).show()
    }

    private fun refreshMoodHistory() {
        if (!::moodHistoryBox.isInitialized) return
        moodHistoryBox.removeAllViews()
        val entries = PetMoodJournal.recent(this, 12)
        if (entries.isEmpty()) {
            moodHistoryBox.addView(TextView(this).apply {
                text = "还没有记录。无论开心还是不开心，都可以随手记下来。"
                textSize = 13f
                setTextColor(Color.rgb(135, 137, 163))
            }, matchWrap())
            return
        }
        entries.forEachIndexed { index, entry ->
            val line = TextView(this).apply {
                text = "${moodEmoji(entry.mood)} ${entry.moodLabel()}  ·  ${entry.formattedTime()}" + if (entry.note.isNotBlank()) "\n${entry.note}" else ""
                textSize = 13f
                setTextColor(Color.rgb(81, 84, 120))
                setPadding(dp(8), dp(8), dp(8), dp(8))
                background = roundedBackground(if (index % 2 == 0) Color.rgb(248, 249, 255) else Color.WHITE, Color.rgb(238, 240, 250))
            }
            moodHistoryBox.addView(line, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(6)
            })
        }
    }

    private fun confirmClearMood() {
        if (PetMoodJournal.recent(this, 1).isEmpty()) {
            Toast.makeText(this, "暂时没有心情记录", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("清空心情记录？")
            .setMessage("这只会删除本机保存的心情日记，不会影响好感度、聊天记录或回忆本。")
            .setNegativeButton("取消", null)
            .setPositiveButton("确认清空") { _, _ ->
                PetMoodJournal.clear(this)
                refreshMoodHistory()
                Toast.makeText(this, "心情记录已清空", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun startTimer(duration: Long, focus: Boolean) {
        timer?.cancel()
        focusSession = focus
        remainingMs = duration
        timerPaused = false
        timerRunning = true
        val line = if (focus) "我会在旁边陪你专心一会儿。先完成眼前这一小步吧。" else "休息时间到啦～喝点水，活动一下肩膀。"
        PetAnimator.react(if (focus) PetMood.HAPPY else PetMood.SIT, line, 2200L)
        startCountdown()
        updateTimerUi()
    }

    private fun startCountdown() {
        timer?.cancel()
        timer = object : CountDownTimer(remainingMs, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                remainingMs = millisUntilFinished
                updateTimerUi()
            }
            override fun onFinish() {
                remainingMs = 0L
                timerRunning = false
                timerPaused = false
                if (focusSession) {
                    val message = PetStateStore.completeFocusSession(this@DailyLifeActivity)
                    PetAnimator.react(PetMood.JUMP, "专注完成！你很棒～", 2400L)
                    Toast.makeText(this@DailyLifeActivity, message, Toast.LENGTH_LONG).show()
                } else {
                    PetAnimator.react(PetMood.HAPPY, "休息结束啦，要继续还是今天先到这里？", 2300L)
                    Toast.makeText(this@DailyLifeActivity, "休息结束啦～", Toast.LENGTH_SHORT).show()
                }
                updateTimerUi()
            }
        }.start()
    }

    private fun pauseOrResume() {
        if (timerRunning && !timerPaused) {
            timer?.cancel()
            timerPaused = true
            updateTimerUi()
        } else if (timerRunning && timerPaused) {
            timerPaused = false
            startCountdown()
            updateTimerUi()
        } else {
            Toast.makeText(this, "先选择开始专注或休息吧", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopTimer(showToast: Boolean) {
        timer?.cancel()
        timer = null
        timerRunning = false
        timerPaused = false
        remainingMs = if (focusSession) 25 * 60 * 1000L else 5 * 60 * 1000L
        updateTimerUi()
        if (showToast) Toast.makeText(this, "计时已结束，没有发放完成奖励", Toast.LENGTH_SHORT).show()
    }

    private fun updateTimerUi() {
        if (!::timerLabel.isInitialized) return
        val minutes = TimeUnit.MILLISECONDS.toMinutes(remainingMs)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(remainingMs) % 60
        timerLabel.text = String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, seconds)
        timerStatus.text = when {
            remainingMs == 0L -> if (focusSession) "专注完成 · 你做得很棒" else "休息结束 · 准备好再出发"
            timerPaused -> "已暂停 · 慢慢来，不着急"
            !timerRunning -> if (focusSession) "准备开始专注" else "准备开始休息"
            focusSession -> "专注中 · 她正在为你加油"
            else -> "休息中 · 放松一下眼睛和肩膀"
        }
        pauseButton.text = if (timerPaused) "继续" else "暂停"
        focusCountLabel.text = "累计完成专注：${PetStateStore.focusSessionCount(this)} 次 · 完成专注可获得 2 枚星星币"
    }

    override fun onStop() {
        if (timerRunning && !timerPaused) {
            timer?.cancel()
            timerPaused = true
            updateTimerUi()
        }
        super.onStop()
    }

    override fun onDestroy() {
        timer?.cancel()
        timer = null
        super.onDestroy()
    }

    private fun moodEmoji(mood: Int) = when (mood) { 1 -> "😣"; 2 -> "😕"; 3 -> "😐"; 4 -> "🙂"; else -> "😄" }
    private fun sectionTitle(text: String) = TextView(this).apply {
        this.text = text
        textSize = 17f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.rgb(74, 77, 117))
        setPadding(dp(2), dp(8), dp(2), dp(8))
    }
    private fun makeButton(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text
        textSize = 13f
        isAllCaps = false
        setTextColor(Color.rgb(68, 67, 111))
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).apply { bottomMargin = dp(6) }
    }
    private fun roundedBackground(fill: Int, stroke: Int) = android.graphics.drawable.GradientDrawable().apply {
        cornerRadius = dp(15).toFloat()
        setColor(fill)
        setStroke(dp(1), stroke)
    }
    private fun matchWrap(top: Int = 0) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(top) }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
