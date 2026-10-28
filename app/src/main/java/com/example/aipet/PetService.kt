package com.example.aipet

import android.app.*
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import android.text.TextUtils
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import org.json.JSONObject
import kotlin.math.abs

class PetService : Service() {
    private lateinit var windowManager: WindowManager
    private var petView: PetView? = null
    private var petParams: WindowManager.LayoutParams? = null
    private var chatView: View? = null
    private var chatParams: WindowManager.LayoutParams? = null
    private var menuView: View? = null
    private var menuParams: WindowManager.LayoutParams? = null

    private var desktopMode = PetChatEngine.Mode.LOCAL
    private val conversationHistory = mutableListOf<JSONObject>()
    private var desktopMessagesColumn: LinearLayout? = null
    private var desktopChatScroll: ScrollView? = null
    private var desktopModeLabel: TextView? = null
    private var desktopStatusLabel: TextView? = null

    private var initialX = 0
    private var initialY = 0
    private var touchX = 0f
    private var touchY = 0f
    private var dragging = false
    private var downAt = 0L
    private var lastTapAt = 0L
    private val touchSlop by lazy { (8 * resources.displayMetrics.density).toInt() }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(2026, buildNotification())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showPet()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (petView == null) showPet()
        return START_STICKY
    }

    private fun showPet() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先允许悬浮窗权限", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }
        if (petView != null) return

        val view = PetView(this)
        @Suppress("DEPRECATION")
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            dp(196),
            dp(280),
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(12)
            y = dp(180)
        }
        view.setOnTouchListener { v, event -> handlePetTouch(v, event, params) }
        try {
            windowManager.addView(view, params)
            petView = view
            petParams = params
            val visit = PetStateStore.touch(this)
            val hello = if (visit.todayFirstVisit) PetStateStore.timeGreeting(this) else PetChatEngine.randomIdleLine(this)
            PetAnimator.react(PetMood.HAPPY, hello, 1800L)
        } catch (e: Exception) {
            Toast.makeText(this, "桌宠显示失败：${e.localizedMessage ?: "未知错误"}", Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    private fun handlePetTouch(v: View, event: MotionEvent, params: WindowManager.LayoutParams): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = params.x
                initialY = params.y
                touchX = event.rawX
                touchY = event.rawY
                downAt = SystemClock.uptimeMillis()
                dragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - touchX).toInt()
                val dy = (event.rawY - touchY).toInt()
                if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                    dragging = true
                    PetAnimator.react(PetMood.WALK, "移动中～", 800L)
                    hideMenuWindow()
                }
                if (dragging) {
                    params.x = initialX + dx
                    params.y = initialY + dy
                    try { windowManager.updateViewLayout(v, params) } catch (_: Exception) { }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    snapPetToEdge(params)
                    return true
                }
                val now = SystemClock.uptimeMillis()
                val heldMs = now - downAt
                when {
                    heldMs >= 650L -> {
                        toggleMenuWindow()
                        lastTapAt = 0L
                    }
                    lastTapAt > 0L && now - lastTapAt <= 360L -> {
                        lastTapAt = 0L
                        toggleDesktopChat()
                    }
                    else -> {
                        lastTapAt = now
                        PetAnimator.reactToTap()
                        v.performClick()
                    }
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> return true
        }
        return false
    }

    private fun snapPetToEdge(params: WindowManager.LayoutParams) {
        val screenWidth = resources.displayMetrics.widthPixels
        val targetX = if (params.x + dp(91) < screenWidth / 2) dp(6) else screenWidth - params.width - dp(6)
        params.x = targetX
        try { petView?.let { windowManager.updateViewLayout(it, params) } } catch (_: Exception) { }
        PetAnimator.react(PetMood.HAPPY, "贴边站好～", 900L)
    }

    private fun toggleDesktopChat() {
        if (chatView == null) showDesktopChat() else hideDesktopChat()
    }

    private fun showDesktopChat() {
        if (chatView != null) return
        hideMenuWindow()
        @Suppress("DEPRECATION")
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = roundedBackground(Color.WHITE, stroke = Color.rgb(210, 224, 255))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = "和${PetStateStore.nickname(this@PetService)}聊天"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(66, 72, 108))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        desktopModeLabel = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.rgb(98, 104, 140))
        }
        header.addView(desktopModeLabel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        header.addView(Button(this).apply {
            text = "×"
            isAllCaps = false
            setOnClickListener { hideDesktopChat() }
            minWidth = 0
            minimumWidth = 0
        }, LinearLayout.LayoutParams(dp(42), dp(36)))
        root.addView(header, matchWrap())

        val suggestionRow = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            val chipContainer = LinearLayout(this@PetService).apply { orientation = LinearLayout.HORIZONTAL }
            PetChatEngine.suggestions().forEach { suggestion ->
                chipContainer.addView(makeChip(suggestion) { sendDesktopMessage(suggestion) })
            }
            addView(chipContainer)
        }
        root.addView(suggestionRow, matchWrap(marginTop = 6))

        desktopStatusLabel = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.rgb(95, 101, 140))
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = roundedBackground(Color.rgb(246, 249, 255), Color.rgb(227, 235, 255))
        }
        root.addView(desktopStatusLabel, matchWrap(marginTop = 8))

        desktopChatScroll = ScrollView(this).apply {
            isFillViewport = true
            setPadding(0, dp(6), 0, dp(6))
        }
        desktopMessagesColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        desktopChatScroll?.addView(desktopMessagesColumn, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(desktopChatScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val input = EditText(this).apply {
            hint = "在桌面直接和她说话…"
            setTextColor(Color.rgb(56, 60, 82))
            textSize = 15f
            maxLines = 3
            minHeight = dp(44)
        }
        composer.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        composer.addView(Button(this).apply {
            text = "发送"
            isAllCaps = false
            setOnClickListener {
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) {
                    input.setText("")
                    sendDesktopMessage(text)
                }
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(46)))
        root.addView(composer, matchWrap())

        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        footer.addView(Button(this).apply {
            text = "本地/AI"
            isAllCaps = false
            setOnClickListener { toggleDesktopMode() }
        }, LinearLayout.LayoutParams(0, dp(40), 1f))
        footer.addView(Button(this).apply {
            text = "完整聊天"
            isAllCaps = false
            setOnClickListener { openChat() }
        }, LinearLayout.LayoutParams(0, dp(40), 1f))
        footer.addView(Button(this).apply {
            text = "动作"
            isAllCaps = false
            setOnClickListener { PetAnimator.playDemoSequence() }
        }, LinearLayout.LayoutParams(0, dp(40), 1f))
        root.addView(footer, matchWrap(marginTop = 8))

        val params = WindowManager.LayoutParams(
            dp(320),
            dp(390),
            type,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = petParams?.x?.plus(dp(70)) ?: dp(88)
            y = petParams?.y?.minus(dp(20)) ?: dp(120)
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        try {
            windowManager.addView(root, params)
            chatView = root
            chatParams = params
            val saved = PetChatStore.recent(this, 24)
            conversationHistory.clear() // Saved logs are displayed locally but are not sent as AI context automatically.
            if (saved.isEmpty()) {
                addDesktopMessage("小伙伴", "欢迎来到桌面对话模式！现在聊天记录会保存在本机，随时可以接着聊。", false)
            } else {
                saved.forEach { entry -> addDesktopMessage(if (entry.role == "user") "你" else PetStateStore.nickname(this), entry.content, entry.role == "user") }
            }
            refreshDesktopModeLabel()
            refreshDesktopStatus()
            PetAnimator.react(PetMood.HAPPY, "一起聊天吧～", 1600L)
            input.requestFocus()
            showKeyboard(input)
        } catch (e: Exception) {
            Toast.makeText(this, "打开桌面对话失败：${e.localizedMessage ?: "未知错误"}", Toast.LENGTH_LONG).show()
        }
    }

    private fun sendDesktopMessage(text: String) {
        addDesktopMessage("你", text, true)
        PetChatStore.append(this, "user", text)
        conversationHistory.add(JSONObject().put("role", "user").put("content", text))
        PetAnimator.reactToText(text)
        addDesktopMessage("小伙伴", "正在思考…", false, typing = true)
        PetChatEngine.requestReply(this, text, desktopMode, conversationHistory.dropLast(1).takeLast(10)) { result ->
            removeLastTypingIndicator()
            val reply = result.reply
            addDesktopMessage("小伙伴", reply, false)
            PetChatStore.append(this, "assistant", reply)
            conversationHistory.add(JSONObject().put("role", "assistant").put("content", reply))
            PetAnimator.reactToText(reply, isReply = true)
            refreshDesktopStatus()
            if (!result.ok && desktopMode == PetChatEngine.Mode.API) {
                desktopMode = PetChatEngine.Mode.LOCAL
                refreshDesktopModeLabel()
            }
        }
    }

    private fun removeLastTypingIndicator() {
        val column = desktopMessagesColumn ?: return
        if (column.childCount <= 0) return
        val last = column.getChildAt(column.childCount - 1)
        if (last.tag == "typing") column.removeView(last)
    }

    private fun addDesktopMessage(name: String, text: String, fromUser: Boolean, typing: Boolean = false) {
        val column = desktopMessagesColumn ?: return
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = if (fromUser) Gravity.END else Gravity.START
            tag = if (typing) "typing" else null
        }
        val label = TextView(this).apply {
            this.text = name
            textSize = 11f
            setTextColor(Color.rgb(129, 135, 168))
            setPadding(dp(4), dp(2), dp(4), dp(2))
        }
        val bubble = TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(if (fromUser) Color.WHITE else Color.rgb(67, 73, 107))
            setPadding(dp(12), dp(9), dp(12), dp(9))
            background = roundedBackground(
                if (fromUser) Color.rgb(108, 157, 255) else Color.rgb(244, 247, 255),
                if (fromUser) Color.rgb(108, 157, 255) else Color.rgb(222, 231, 255)
            )
            maxWidth = dp(220)
        }
        wrapper.addView(label)
        wrapper.addView(bubble)
        column.addView(wrapper, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(8)
        })
        desktopChatScroll?.post { desktopChatScroll?.fullScroll(View.FOCUS_DOWN) }
    }

    private fun toggleDesktopMode() {
        if (desktopMode == PetChatEngine.Mode.LOCAL) {
            if (ApiKeyStore.load(this).isNullOrBlank()) {
                Toast.makeText(this, "还没配置独立 AI，先打开完整聊天页完成设置。", Toast.LENGTH_LONG).show()
                openChat()
                return
            }
            desktopMode = PetChatEngine.Mode.API
            conversationHistory.clear() // Start a fresh provider context; saved local history stays local.
        } else {
            desktopMode = PetChatEngine.Mode.LOCAL
            conversationHistory.clear()
        }
        refreshDesktopModeLabel()
        PetAnimator.react(PetMood.HAPPY, if (desktopMode == PetChatEngine.Mode.LOCAL) "切回本地模式" else "切到 AI 模式", 1400L)
    }

    private fun refreshDesktopModeLabel() {
        desktopModeLabel?.text = if (desktopMode == PetChatEngine.Mode.LOCAL) "本地" else "AI"
        refreshDesktopStatus()
    }


    private fun refreshDesktopStatus() {
        val stats = PetStateStore.get(this)
        desktopStatusLabel?.text = "关系：${PetStateStore.relationshipTitle(stats.affection)}  ·  好感 ${stats.affection}  ·  心情 ${stats.mood}%  ·  精力 ${stats.energy}%  ·  饥饿 ${stats.hunger}%  ·  星星币 ${stats.coins}  ·  陪伴 ${stats.streak} 天"
    }

    private fun hideDesktopChat() {
        chatView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) { }
        }
        chatView = null
        chatParams = null
        desktopMessagesColumn = null
        desktopChatScroll = null
        desktopModeLabel = null
        desktopStatusLabel = null
    }

    private fun toggleMenuWindow() {
        if (menuView == null) showMenuWindow() else hideMenuWindow()
    }

    private fun showMenuWindow() {
        if (menuView != null) return
        @Suppress("DEPRECATION")
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = roundedBackground(Color.WHITE, Color.rgb(213, 226, 255))
        }
        root.addView(TextView(this).apply {
            text = "${PetStateStore.nickname(this@PetService)}的菜单"
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(66, 72, 108))
        }, matchWrap())
        root.addView(TextView(this).apply {
            text = "双击桌宠可直接聊天，长按再次打开/关闭这个菜单。"
            textSize = 12f
            setTextColor(Color.rgb(117, 124, 153))
            setPadding(0, dp(4), 0, dp(8))
        }, matchWrap())

        root.addView(makeMenuButton("桌面对话") { toggleDesktopChat(); hideMenuWindow() })
        root.addView(makeMenuButton("摸摸头 + 好感") {
            val before = PetStateStore.get(this)
            val stats = PetStateStore.interact(this, "tap")
            val oldTitle = PetStateStore.relationshipTitle(before.affection)
            val newTitle = PetStateStore.relationshipTitle(stats.affection)
            PetAnimator.react(if (oldTitle != newTitle) PetMood.HAPPY else PetMood.SHY,
                if (oldTitle != newTitle) "关系升级：$newTitle！" else "好感+1 ♥${stats.affection}", 1800L)
            refreshDesktopStatus()
            hideMenuWindow()
        })
        root.addView(makeMenuButton("投喂零食") {
            val message = PetStateStore.feedSnack(this)
            PetAnimator.react(PetMood.EAT, if (message.startsWith("投喂成功")) "谢谢投喂～" else "我还想吃点心…", 1700L)
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            refreshDesktopStatus()
            hideMenuWindow()
        })
        root.addView(makeMenuButton("查看今日状态") {
            val summary = PetStateStore.statusSummary(this)
            PetAnimator.react(PetMood.HAPPY, summary, 2000L)
            refreshDesktopStatus()
            hideMenuWindow()
        })
        root.addView(makeMenuButton("朋友档案 · 签到 / 礼物 / 回忆") { openFriendCenter(); hideMenuWindow() })
        root.addView(makeMenuButton("日常陪伴 · 心情 / 专注") { openDailyLife(); hideMenuWindow() })
        root.addView(makeMenuButton("完整聊天与 AI 设置") { openChat(); hideMenuWindow() })
        root.addView(makeMenuButton("15 种动作演示") { PetAnimator.playDemoSequence(); hideMenuWindow() })
        root.addView(makeMenuButton("休息 / 唤醒") {
            PetAnimator.toggleSleep(); refreshDesktopStatus(); hideMenuWindow()
        })
        root.addView(makeMenuButton("隐藏桌宠") {
            hideDesktopChat(); hideMenuWindow(); stopSelf()
        })
        root.addView(makeMenuButton(if (desktopMode == PetChatEngine.Mode.LOCAL) "切换为 AI 模式" else "切换为本地模式") {
            toggleDesktopMode(); hideMenuWindow()
        })

        val menuScroll = ScrollView(this).apply {
            isFillViewport = true
            addView(root, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val menuHeight = minOf(dp(560), (resources.displayMetrics.heightPixels * 0.76f).toInt())
        val params = WindowManager.LayoutParams(
            dp(220),
            menuHeight,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (petParams?.x ?: dp(24)) + dp(72)
            y = (petParams?.y ?: dp(160)) + dp(10)
        }
        try {
            windowManager.addView(menuScroll, params)
            menuView = menuScroll
            menuParams = params
        } catch (e: Exception) {
            Toast.makeText(this, "打开菜单失败：${e.localizedMessage ?: "未知错误"}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun hideMenuWindow() {
        menuView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) { }
        }
        menuView = null
        menuParams = null
    }

    private fun openChat() {
        val intent = Intent(this, ChatActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }

    private fun openFriendCenter() {
        val intent = Intent(this, FriendCenterActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }

    private fun openDailyLife() {
        val intent = Intent(this, DailyLifeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }

    private fun showKeyboard(view: View) {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun roundedBackground(fill: Int, stroke: Int): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(16).toFloat()
        setColor(fill)
        setStroke(dp(1), stroke)
    }

    private fun makeChip(text: String, action: () -> Unit): TextView = TextView(this).apply {
        this.text = text
        ellipsize = TextUtils.TruncateAt.END
        setSingleLine()
        setTextColor(Color.rgb(72, 79, 117))
        textSize = 13f
        setPadding(dp(12), dp(6), dp(12), dp(6))
        background = roundedBackground(Color.rgb(244, 247, 255), Color.rgb(221, 230, 255))
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            rightMargin = dp(8)
        }
    }

    private fun makeMenuButton(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).apply {
            bottomMargin = dp(8)
        }
    }

    private fun matchWrap(marginTop: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(marginTop)
        }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, "pet_service")
                .setContentTitle("小伙伴桌宠正在运行")
                .setContentText("单击互动，双击桌面对话，长按打开菜单")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(openIntent)
                .setOngoing(true)
                .build()
        } else {
            Notification.Builder(this)
                .setContentTitle("小伙伴桌宠正在运行")
                .setContentText("单击互动，双击桌面对话，长按打开菜单")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(openIntent)
                .setOngoing(true)
                .build()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel("pet_service", "桌宠运行状态", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "桌面宠物运行时显示的通知"
                }
            )
        }
    }

    override fun onDestroy() {
        hideDesktopChat()
        hideMenuWindow()
        petView?.let { view ->
            try { windowManager.removeView(view) } catch (_: Exception) { }
        }
        petView = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE) else stopForeground(true)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
