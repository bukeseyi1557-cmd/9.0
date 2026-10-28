package com.example.aipet

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class AiSettingsActivity : Activity() {
    private lateinit var endpointInput: EditText
    private lateinit var modelInput: EditText
    private lateinit var keyInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("ai_settings", MODE_PRIVATE)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(24), dp(22), dp(24))
            setBackgroundColor(Color.rgb(255, 248, 252))
        }
        root.addView(TextView(this).apply {
            text = "独立 AI 接口设置"
            textSize = 25f
            setTextColor(Color.rgb(68, 53, 86))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }, matchWrap())
        root.addView(TextView(this).apply {
            text = "填写兼容 OpenAI Chat Completions 格式的 HTTPS 接口。API Key 使用 Android Keystore 加密保存在本机。API 服务商可能单独收费；ChatGPT Plus 订阅不等于 API 额度。"
            textSize = 14f
            setTextColor(Color.rgb(105, 94, 115))
            setPadding(0, dp(10), 0, dp(18))
            setLineSpacing(dp(3).toFloat(), 1.05f)
        }, matchWrap())

        root.addView(label("接口地址（完整路径）"), matchWrap())
        endpointInput = EditText(this).apply {
            hint = "https://api.openai.com/v1/chat/completions"
            setSingleLine(true)
            textSize = 14f
            setText(prefs.getString("endpoint", DEFAULT_ENDPOINT) ?: DEFAULT_ENDPOINT)
        }
        root.addView(endpointInput, matchWrap())

        root.addView(label("模型名称"), matchWrap())
        modelInput = EditText(this).apply {
            hint = "例如 gpt-4o-mini"
            setSingleLine(true)
            textSize = 14f
            setText(prefs.getString("model", DEFAULT_MODEL) ?: DEFAULT_MODEL)
        }
        root.addView(modelInput, matchWrap())

        root.addView(label("API Key"), matchWrap())
        keyInput = EditText(this).apply {
            hint = if (ApiKeyStore.load(this@AiSettingsActivity).isNullOrBlank()) "粘贴你的 API Key" else "已保存密钥；输入新密钥可替换，留空则保留"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
            textSize = 14f
        }
        root.addView(keyInput, matchWrap())

        val save = Button(this).apply {
            text = "保存设置"
            isAllCaps = false
            setOnClickListener { saveSettings() }
        }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52))
        lp.topMargin = dp(20)
        root.addView(save, lp)

        val clear = Button(this).apply {
            text = "清除已保存的 API Key"
            isAllCaps = false
            setOnClickListener {
                ApiKeyStore.save(this@AiSettingsActivity, "")
                keyInput.setText("")
                keyInput.hint = "粘贴你的 API Key"
                Toast.makeText(this@AiSettingsActivity, "已清除 API Key", Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(clear, matchWrap())
        setContentView(root)
    }

    private fun saveSettings() {
        val endpoint = endpointInput.text.toString().trim()
        val model = modelInput.text.toString().trim()
        val newKey = keyInput.text.toString().trim()
        if (!endpoint.startsWith("https://", ignoreCase = true)) {
            Toast.makeText(this, "为保护密钥，请填写 HTTPS 接口地址", Toast.LENGTH_LONG).show()
            return
        }
        if (model.isBlank()) {
            Toast.makeText(this, "请填写模型名称", Toast.LENGTH_SHORT).show()
            return
        }
        getSharedPreferences("ai_settings", MODE_PRIVATE).edit()
            .putString("endpoint", endpoint)
            .putString("model", model)
            .apply()
        if (newKey.isNotBlank()) ApiKeyStore.save(this, newKey)
        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun label(value: String) = TextView(this).apply {
        text = value
        textSize = 14f
        setTextColor(Color.rgb(76, 62, 91))
        setPadding(0, dp(10), 0, 0)
    }

    private fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val DEFAULT_ENDPOINT = "https://api.openai.com/v1/chat/completions"
        const val DEFAULT_MODEL = "gpt-4o-mini"
    }
}
