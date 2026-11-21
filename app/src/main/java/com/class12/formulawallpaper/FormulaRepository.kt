package com.class12.formulawallpaper

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

object FormulaRepository {
    private const val PREFS = "formula_wallpaper_prefs"
    private const val KEY_API = "gemini_api_key"
    private const val KEY_SUBJECT_IDX = "subject_idx"
    private const val KEY_SEEN = "seen_titles"
    private val SUBJECTS = listOf("PHYSICS", "CHEMISTRY", "MATHS")
    private val isFetching = AtomicBoolean(false)

    fun saveApiKey(context: Context, key: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_API, key.trim()).apply()
    }

    fun getApiKey(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_API, "") ?: ""

    fun popNextCard(context: Context): FormulaCard {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val idx = prefs.getInt(KEY_SUBJECT_IDX, 0) % SUBJECTS.size
        val subject = SUBJECTS[idx]
        prefs.edit().putInt(KEY_SUBJECT_IDX, (idx + 1) % SUBJECTS.size).apply()

        val queue = getQueue(context, subject)
        val card = if (queue.isNotEmpty()) {
            val first = queue.removeAt(0)
            saveQueue(context, subject, queue)
            first
        } else {
            starterCard(subject)
        }

        recordSeen(context, card.title)
        if (queue.size < 3) {
            refillQueueAsync(context, subject)
        }
        return card
    }

    fun refillAllAsync(context: Context) {
        Thread {
            SUBJECTS.forEach { subject ->
                if (getQueue(context, subject).size < 3) {
                    fetchFromGemini(context, subject)
                }
            }
        }.start()
    }

    private fun refillQueueAsync(context: Context, subject: String) {
        if (!isFetching.compareAndSet(false, true)) return
        Thread {
            try {
                fetchFromGemini(context, subject)
            } finally {
                isFetching.set(false)
            }
        }.start()
    }

    private fun fetchFromGemini(context: Context, subject: String) {
        val apiKey = getApiKey(context)
        if (apiKey.isBlank()) return

        val seenList = getSeenTitles(context).takeLast(60).joinToString(", ")
        val prompt = """
            Generate 8 distinct Class 12 CBSE/NCERT $subject formulas or key concepts.
            NEVER repeat any of these previously shown titles: [$seenList].
            Do NOT use LaTeX commands (never write \frac, \int, \sqrt, or backslashes).
            Use real textbook symbols like Δ, α, β, γ, θ, λ, μ, ν, ω, ε₀, π, →, ⇌, ², ³.
            Return ONLY a valid JSON array of objects with these exact keys:
            - "subject": "$subject"
            - "chapter": short NCERT chapter name
            - "title": concept name
            - "layout": one of "simple", "fraction", "integral", or "root"
            - "prefix": left side before the main structure, e.g. "ΔV = " or "I = " (or "" if none)
            - "numerator": top of fraction, or expression inside root/integral, or full formula if layout is "simple"
            - "denominator": bottom of fraction, or differential like "dx" / "dt" (or "" if unused)
            - "lowerLimit": lower integral bound if layout is "integral" (else "")
            - "upperLimit": upper integral bound if layout is "integral" (else "")
            - "takeaway": 1 short sentence explaining what it means in plain words
        """.trimIndent()

        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
            connectTimeout = 15000
            readTimeout = 30000
        }

        val payload = JSONObject().apply {
            put("contents", JSONArray().put(
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))
            ))
            put("generationConfig", JSONObject().put("responseMimeType", "application/json"))
        }

        try {
            conn.outputStream.use { it.write(payload.toString().toByteArray()) }
            if (conn.responseCode != 200) return

            val responseText = conn.inputStream.bufferedReader().readText()
            val rawJson = JSONObject(responseText)
                .getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts")
                .getJSONObject(0).getString("text")
                .trim()
                .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

            val arr = JSONArray(rawJson)
            val queue = getQueue(context, subject)
            for (i in 0 until arr.length()) {
                queue.add(FormulaCard.fromJson(arr.getJSONObject(i)))
            }
            saveQueue(context, subject, queue)
        } catch (_: Exception) {
        } finally {
            conn.disconnect()
        }
    }

    private fun getQueue(context: Context, subject: String): MutableList<FormulaCard> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("queue_$subject", "[]") ?: "[]"
        val arr = JSONArray(raw)
        return MutableList(arr.length()) { FormulaCard.fromJson(arr.getJSONObject(it)) }
    }

    private fun saveQueue(context: Context, subject: String, list: List<FormulaCard>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("queue_$subject", arr.toString()).apply()
    }

    private fun getSeenTitles(context: Context): MutableList<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SEEN, "[]") ?: "[]"
        val arr = JSONArray(raw)
        return MutableList(arr.length()) { arr.getString(it) }
    }

    private fun recordSeen(context: Context, title: String) {
        val list = getSeenTitles(context)
        if (!list.contains(title)) list.add(title)
        val arr = JSONArray()
        list.takeLast(250).forEach { arr.put(it) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_SEEN, arr.toString()).apply()
    }

    private fun starterCard(subject: String): FormulaCard = when (subject) {
        "PHYSICS" -> FormulaCard(
            subject = "PHYSICS",
            chapter = "Electrostatic Potential",
            title = "Potential Difference",
            layout = "fraction",
            prefix = "ΔV = ",
            numerator = "W",
            denominator = "q₀",
            lowerLimit = "",
            upperLimit = "",
            takeaway = "Work done per unit positive test charge moved between two points."
        )
        "CHEMISTRY" -> FormulaCard(
            subject = "CHEMISTRY",
            chapter = "Electrochemistry",
            title = "Nernst Equation (298 K)",
            layout = "simple",
            prefix = "",
            numerator = "E = E° - (0.059 / n) log Q",
            denominator = "",
            lowerLimit = "",
            upperLimit = "",
            takeaway = "Relates cell potential to standard potential and reaction quotient."
        )
        else -> FormulaCard(
            subject = "MATHS",
            chapter = "Integrals",
            title = "Definite Integral",
            layout = "integral",
            prefix = "I = ",
            numerator = "f(x)",
            denominator = "dx",
            lowerLimit = "a",
            upperLimit = "b",
            takeaway = "Net signed area between the curve f(x) and the x-axis from a to b."
        )
    }
}
