package com.example.aipet

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*

class FriendCenterActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var statsCard: TextView
    private lateinit var affectionBar: ProgressBar
    private lateinit var moodBar: ProgressBar
    private lateinit var energyBar: ProgressBar
    private lateinit var hungerBar: ProgressBar
    private lateinit var coinLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
        refresh()
    }

    private fun buildScreen() {
        val scroll = ScrollView(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(24))
            setBackgroundColor(Color.rgb(255, 248, 252))
        }
        scroll.addView(root)

        root.addView(TextView(this).apply {
            text = "朋友档案 ✨"
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(68, 53, 86))
        }, matchWrap())
        root.addView(TextView(this).apply {
            text = "关系不是一串数字，而是我们一起留下的小日常。所有档案与回忆都保存在这台手机本地。"
            textSize = 14f
            setTextColor(Color.rgb(112, 99, 122))
            setPadding(0, dp(8), 0, dp(16))
        }, matchWrap())

        statsCard = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.rgb(68, 72, 108))
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = roundedBackground(Color.WHITE, Color.rgb(221, 230, 255))
        }
        root.addView(statsCard, matchWrap())

        root.addView(makeSectionTitle("关系状态"), matchWrap(top = 16))
        affectionBar = addStatusBar("好感度", 100)
        moodBar = addStatusBar("心情", 100)
        energyBar = addStatusBar("精力", 100)
        hungerBar = addStatusBar("饥饿感", 100)

        coinLabel = TextView(this).apply {
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(102, 89, 140))
            setPadding(dp(2), dp(10), dp(2), dp(10))
        }
        root.addView(coinLabel, matchWrap())

        root.addView(makeSectionTitle("每天可以做的小事"), matchWrap(top = 10))
        root.addView(makeActionButton("每日签到 · 获得 5 枚星星币") {
            val message = PetStateStore.checkIn(this)
            refresh()
            PetAnimator.react(PetMood.HAPPY, if (message.startsWith("签到成功")) "签到成功！♥+3" else "你今天来过啦～", 1700L)
            toast(message)
        })
        root.addView(makeActionButton("完成今日小目标 · 奖励 3 枚星星币") {
            val message = completeDailyTask()
            refresh()
            PetAnimator.react(PetMood.HAPPY, if (message.startsWith("完成")) "今天也很棒！" else "明天还有新目标～", 1600L)
            toast(message)
        })
        root.addView(makeActionButton("发现今日小惊喜 · 每天一次") {
            val message = PetStateStore.claimDailyEvent(this)
            refresh()
            PetAnimator.react(PetMood.SURPRISED, if (message.startsWith("今天的小惊喜")) "明天再来看看～" else "发现惊喜啦！", 1900L)
            toast(message)
        })
        root.addView(makeActionButton("投喂零食 · 花费 2 枚星星币") {
            val message = PetStateStore.feedSnack(this)
            refresh()
            PetAnimator.react(PetMood.EAT, if (message.startsWith("投喂成功")) "谢谢投喂～" else "肚子还在等零食…", 1700L)
            toast(message)
        })
        root.addView(makeActionButton("送一份小礼物 · 花费 5 枚星星币") {
            val message = PetStateStore.giveGift(this)
            refresh()
            PetAnimator.react(PetMood.SHY, if (message.contains("关系升级")) "我们升级啦！" else "这是送给我的嘛？", 1900L)
            toast(message)
        })

        root.addView(makeSectionTitle("个性与外观"), matchWrap(top = 10))
        root.addView(makeActionButton("更换桌宠主题") { chooseTheme() })
        root.addView(makeActionButton("选择她的性格") { choosePersonality() })
        root.addView(makeActionButton("查看成就徽章") { showAchievements() })

        root.addView(makeSectionTitle("专属设置与回忆"), matchWrap(top = 10))
        root.addView(makeActionButton("给她取一个专属昵称") { renamePet() })
        root.addView(makeActionButton("写下一条共同回忆") { addMemoryDialog() })
        root.addView(makeActionButton("翻看回忆相册") { showMemories() })
        root.addView(makeActionButton("清空回忆本") { clearMemoriesDialog() })
        root.addView(makeActionButton("查看关系成长说明") { showRelationshipGuide() })
        root.addView(makeActionButton("日常陪伴小屋 · 心情日记 / 专注计时") { openDailyLife() })
        root.addView(makeActionButton("查看本地聊天记录") { showChatHistory() })
        root.addView(makeActionButton("清空聊天记录") { clearChatHistoryDialog() })

        root.addView(makeSectionTitle("备份与恢复"), matchWrap(top = 10))
        root.addView(makeActionButton("导出桌宠数据备份") { exportBackup() })
        root.addView(makeActionButton("从备份恢复桌宠数据") { importBackupDialog() })

        root.addView(TextView(this).apply {
            text = "小提示：好感度会随着日常互动逐步增加；精力、心情和饥饿感会随时间缓慢变化。这里没有惩罚机制，偶尔忙几天也没关系。"
            textSize = 12f
            setTextColor(Color.rgb(135, 124, 145))
            setPadding(dp(4), dp(18), dp(4), dp(4))
        }, matchWrap())

        setContentView(scroll)
    }

    private fun addStatusBar(label: String, max: Int): ProgressBar {
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(5), dp(2), dp(5))
        }
        wrapper.addView(TextView(this).apply {
            text = label
            textSize = 13f
            setTextColor(Color.rgb(105, 96, 126))
        }, matchWrap())
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            this.max = max
            progress = 0
        }
        wrapper.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8)).apply { topMargin = dp(4) })
        root.addView(wrapper, matchWrap())
        return bar
    }

    private fun refresh() {
        val stats = PetStateStore.get(this)
        val name = PetStateStore.nickname(this)
        statsCard.text = "$name  ·  Lv.${stats.level}\n关系：${PetStateStore.relationshipTitle(stats.affection)}\n好感度：${stats.affection}/100  ·  连续陪伴：${stats.streak} 天\n回忆：${stats.memoryCount} 条  ·  累计心情记录：${PetMoodJournal.count(this)} 次\n聊天记录：${PetChatStore.recent(this, 200).size} 条  ·  专注完成：${PetStateStore.focusSessionCount(this)} 次\n主题：${PetCustomization.theme(this)}  ·  性格：${PetCustomization.personality(this)}"
        affectionBar.progress = stats.affection
        moodBar.progress = stats.mood
        energyBar.progress = stats.energy
        hungerBar.progress = stats.hunger
        coinLabel.text = "⭐ 星星币：${stats.coins} 枚"
    }

    private fun completeDailyTask(): String {
        val prefs = getSharedPreferences("pet_state", MODE_PRIVATE)
        val today = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault()).format(System.currentTimeMillis())
        if (prefs.getString("daily_task_day", null) == today) return "今天的小目标已经完成啦，明天再来一个新目标吧～"
        val stats = PetStateStore.get(this)
        prefs.edit()
            .putString("daily_task_day", today)
            .putInt("coins", stats.coins + 3)
            .putInt("affection", (stats.affection + 2).coerceAtMost(100))
            .putInt("mood", (stats.mood + 3).coerceAtMost(100))
            .putInt("daily_task_count", prefs.getInt("daily_task_count", 0) + 1)
            .apply()
        return "完成今日小目标！获得 3 枚星星币，好感 +2。今天也很棒！"
    }


    private fun chooseTheme() {
        val themes = PetCustomization.themes.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("选择桌宠主题")
            .setSingleChoiceItems(themes, PetCustomization.themes.indexOf(PetCustomization.theme(this)).coerceAtLeast(0)) { dialog, which ->
                PetCustomization.setTheme(this, themes[which])
                refresh()
                PetAnimator.react(PetMood.HAPPY, "换了新主题，好看吗？", 1700L)
                dialog.dismiss()
                toast("已切换为${themes[which]}主题")
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun choosePersonality() {
        val personalities = PetCustomization.personalities.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("选择她的性格")
            .setItems(personalities) { _, which ->
                PetCustomization.setPersonality(this, personalities[which])
                refresh()
                val line = when (personalities[which]) {
                    "元气活泼" -> "好耶！以后我们一起元气满满～"
                    "傲娇可爱" -> "哼，只是稍微换了下风格而已！"
                    else -> "以后也会温柔地陪着你。"
                }
                PetAnimator.react(PetMood.HAPPY, line, 1900L)
                toast("性格已切换为${personalities[which]}")
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showAchievements() {
        val entries = PetStateStore.achievements(this)
        val lines = entries.joinToString("\n\n") { (title, unlocked) ->
            "${if (unlocked) "🏅 已解锁" else "🔒 未解锁"}  $title"
        }
        AlertDialog.Builder(this)
            .setTitle("成就徽章 · ${entries.count { it.second }}/${entries.size}")
            .setMessage(lines)
            .setPositiveButton("继续努力", null)
            .show()
    }

    private fun clearMemoriesDialog() {
        if (PetStateStore.memories(this).isEmpty()) {
            toast("回忆本已经是空的啦")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("清空回忆本？")
            .setMessage("这会删除保存在本机的全部回忆，且无法恢复。好感度和其他养成数据不会受到影响。")
            .setNegativeButton("取消", null)
            .setPositiveButton("确认清空") { _, _ ->
                PetStateStore.clearMemories(this)
                refresh()
                toast("回忆本已清空")
            }
            .show()
    }

    private fun renamePet() {
        val edit = EditText(this).apply {
            setText(PetStateStore.nickname(this@FriendCenterActivity))
            hint = "输入她的名字"
            inputType = InputType.TYPE_CLASS_TEXT
            setSelection(text.length)
        }
        AlertDialog.Builder(this)
            .setTitle("专属昵称")
            .setMessage("给她取个你喜欢的名字吧（最多 16 个字）。")
            .setView(edit)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存") { _, _ ->
                PetStateStore.setNickname(this, edit.text.toString())
                refresh()
                PetAnimator.react(PetMood.HAPPY, "以后我就叫这个名字啦！", 1800L)
                toast("昵称已保存")
            }
            .show()
    }

    private fun addMemoryDialog() {
        val edit = EditText(this).apply {
            hint = "例如：今天我完成了一件很难的事，她陪我庆祝。"
            minLines = 3
            maxLines = 5
            gravity = Gravity.TOP
        }
        AlertDialog.Builder(this)
            .setTitle("写下一条回忆")
            .setMessage("这条回忆只保存在你的手机里，不会自动上传。")
            .setView(edit)
            .setNegativeButton("取消", null)
            .setPositiveButton("收藏") { _, _ ->
                if (PetStateStore.addMemory(this, edit.text.toString())) {
                    refresh()
                    PetAnimator.react(PetMood.HAPPY, "这段回忆我会好好收着～", 1800L)
                    toast("回忆已保存到本地")
                } else toast("先写一点内容再收藏吧")
            }
            .show()
    }

    private fun showMemories() {
        val memories = PetStateStore.memories(this)
        if (memories.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("回忆相册")
                .setMessage("这里还空空的。你可以把开心的时刻、完成的小目标，或想记住的事写下来。")
                .setPositiveButton("去写一条") { _, _ -> addMemoryDialog() }
                .setNegativeButton("关闭", null)
                .show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("回忆相册 · ${memories.size} 条")
            .setItems(memories.toTypedArray(), null)
            .setPositiveButton("写新回忆") { _, _ -> addMemoryDialog() }
            .setNegativeButton("关闭", null)
            .show()
    }


    private fun openDailyLife() {
        startActivity(Intent(this, DailyLifeActivity::class.java))
    }

    private fun showChatHistory() {
        val entries = PetChatStore.recent(this, 40)
        if (entries.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("本地聊天记录")
                .setMessage("还没有保存的聊天。你和她之后的对话会保存在这台手机上。")
                .setPositiveButton("知道啦", null)
                .show()
            return
        }
        val content = entries.joinToString("\n\n") { "${it.formattedTime()}  ${if (it.role == "user") "我" else PetStateStore.nickname(this)}：\n${it.content}" }
        val view = ScrollView(this).apply {
            addView(TextView(this@FriendCenterActivity).apply {
                text = content
                textSize = 14f
                setTextColor(Color.rgb(72, 74, 109))
                setPadding(dp(14), dp(12), dp(14), dp(12))
            })
        }
        AlertDialog.Builder(this)
            .setTitle("本地聊天记录 · 最近 ${entries.size} 条")
            .setView(view)
            .setPositiveButton("关闭", null)
            .show()
    }

    private fun clearChatHistoryDialog() {
        if (PetChatStore.recent(this, 1).isEmpty()) {
            toast("本地聊天记录已经是空的啦")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("清空聊天记录？")
            .setMessage("这会删除本机保存的聊天记录，无法恢复。好感度、回忆本和心情日记不会受到影响。")
            .setNegativeButton("取消", null)
            .setPositiveButton("确认清空") { _, _ ->
                PetChatStore.clear(this)
                refresh()
                toast("聊天记录已清空")
            }
            .show()
    }


    private fun exportBackup() {
        val backup = PetBackupStore.export(this)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "小伙伴桌宠数据备份")
            putExtra(Intent.EXTRA_TEXT, backup)
        }
        startActivity(Intent.createChooser(send, "保存或发送桌宠备份"))
    }

    private fun importBackupDialog() {
        val edit = EditText(this).apply {
            hint = "粘贴之前导出的完整 JSON 备份内容"
            minLines = 6
            maxLines = 12
            gravity = Gravity.TOP
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        AlertDialog.Builder(this)
            .setTitle("恢复桌宠数据")
            .setMessage("恢复会覆盖当前的好感度、昵称、养成进度、回忆、聊天记录和心情日记。建议先导出当前数据作为备份。AI API Key 不会导入。")
            .setView(edit)
            .setNegativeButton("取消", null)
            .setPositiveButton("继续检查备份") { _, _ ->
                val raw = edit.text.toString().trim()
                if (raw.isBlank()) {
                    toast("请先粘贴备份内容")
                } else {
                    AlertDialog.Builder(this)
                        .setTitle("确认恢复？")
                        .setMessage("确认后，当前本地桌宠数据将被备份中的数据替换。此操作无法自动撤销。")
                        .setNegativeButton("再想想", null)
                        .setPositiveButton("确认恢复") { _, _ ->
                            val result = PetBackupStore.restore(this, raw)
                            refresh()
                            toast(result)
                        }
                        .show()
                }
            }
            .show()
    }

    private fun showRelationshipGuide() {
        AlertDialog.Builder(this)
            .setTitle("关系成长说明")
            .setMessage("刚认识：0–19\n熟悉伙伴：20–39\n好朋友：40–64\n知心好友：65–84\n最重要的朋友：85–100\n\n摸摸头、聊天、完成每日小目标、送礼物都能增加好感。升级时她会有特别反应。")
            .setPositiveButton("知道啦", null)
            .show()
    }

    private fun makeSectionTitle(text: String) = TextView(this).apply {
        this.text = text
        textSize = 17f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.rgb(77, 68, 101))
        setPadding(dp(2), dp(8), dp(2), dp(8))
    }

    private fun makeActionButton(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text
        textSize = 14f
        isAllCaps = false
        setTextColor(Color.rgb(73, 59, 95))
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply {
            bottomMargin = dp(6)
        }
    }

    private fun roundedBackground(fill: Int, stroke: Int) = android.graphics.drawable.GradientDrawable().apply {
        cornerRadius = dp(16).toFloat()
        setColor(fill)
        setStroke(dp(1), stroke)
    }

    private fun matchWrap(top: Int = 0) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        topMargin = dp(top)
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
