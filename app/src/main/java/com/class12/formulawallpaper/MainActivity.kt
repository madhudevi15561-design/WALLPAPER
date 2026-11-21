package com.class12.formulawallpaper

import android.app.Activity
import android.app.WallpaperManager
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.*

class MainActivity : Activity() {

    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 80, 48, 48)
        }

        val title = TextView(this).apply {
            text = "Class 12 AI Formula Wallpaper"
            textSize = 22f
        }

        statusText = TextView(this).apply {
            textSize = 15f
            setPadding(0, 20, 0, 24)
        }

        val keyInput = EditText(this).apply {
            hint = "Paste Gemini API Key (optional for starter cards)"
            setText(FormulaRepository.getApiKey(this@MainActivity))
        }

        val saveBtn = Button(this).apply {
            text = "Save Key & Fetch Formulas"
            setOnClickListener {
                FormulaRepository.saveApiKey(this@MainActivity, keyInput.text.toString())
                FormulaRepository.refillAllAsync(this@MainActivity)
                Toast.makeText(this@MainActivity, "API key saved & queue refilling!", Toast.LENGTH_SHORT).show()
            }
        }

        val accessibilityBtn = Button(this).apply {
            text = "Open Accessibility Settings"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        val testBtn = Button(this).apply {
            text = "Test Swap Wallpaper Now"
            setOnClickListener {
                val card = FormulaRepository.popNextCard(this@MainActivity)
                val bitmap = WallpaperRenderer.render(this@MainActivity, card)
                WallpaperManager.getInstance(this@MainActivity).setBitmap(
                    bitmap,
                    null,
                    true,
                    WallpaperManager.FLAG_SYSTEM
                )
                bitmap.recycle()
                Toast.makeText(this@MainActivity, "Swapped to ${card.subject}: ${card.title}", Toast.LENGTH_SHORT).show()
            }
        }

        layout.addView(title)
        layout.addView(statusText)
        layout.addView(keyInput)
        layout.addView(saveBtn)
        layout.addView(accessibilityBtn)
        layout.addView(testBtn)
        setContentView(layout)
    }

    override fun onResume() {
        super.onResume()
        statusText.text = if (isAccessibilityEnabled()) {
            "Status: Accessibility enabled (No persistent notification)"
        } else {
            "Status: Turn on 'Formula Wallpaper' in Accessibility Settings"
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val serviceId = "$packageName/${FormulaAccessibilityService::class.java.canonicalName}"
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':').apply { setString(enabled) }
        while (splitter.hasNext()) {
            if (splitter.next().equals(serviceId, ignoreCase = true)) {
                return true
            }
        }
        return false
    }
}
