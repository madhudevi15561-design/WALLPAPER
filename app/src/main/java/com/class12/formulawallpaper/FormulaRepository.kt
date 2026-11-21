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
    private const val KEY_STATUS = "last_api_status"
    private const val KEY_CLEANED = "cleaned_v4_queue"
    private val SUBJECTS = listOf("PHYSICS", "CHEMISTRY", "MATHS")
    private val MODELS = listOf("gemini-3.5-flash-lite", "gemini-3.8-flash")
    private val isFetching = AtomicBoolean(false)

    fun saveApiKey(context: Context, key: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_API, key.trim()).apply()
    }

    fun getApiKey(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_API, "") ?: ""

    fun getLastStatus(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_STATUS, "Not fetched yet") ?: "Not fetched yet"

    fun getQueueSummary(context: Context): String {
        ensureCleanQueueOnce(context)
        val p = getQueue(context, "PHYSICS").size
        val c = getQueue(context, "CHEMISTRY").size
        val m = getQueue(context, "MATHS").size
        return "Ready in Queue -> Phy: $p | Chem: $c \vert{} Maths:$m"
    }

    private fun setStatus(context: Context, msg: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_STATUS, msg).apply()
    }

    private fun ensureCleanQueueOnce(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_CLEANED, false)) {
            prefs.edit()
                .remove("queue_PHYSICS")
                .remove("queue_CHEMISTRY")
                .remove("queue_MATHS")
                .putBoolean(KEY_CLEANED, true)
                .apply()
        }
    }

    fun popNextCard(context: Context): FormulaCard {
        ensureCleanQueueOnce(context)
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

    fun refillAllAsync(context: Context, onUpdate: (() -> Unit)? = null) {
        ensureCleanQueueOnce(context)
        Thread {
            setStatus(context, "Fetching AI formulas...")
            onUpdate?.invoke()
            var anySuccess = false
            SUBJECTS.forEach { subject ->
                if (getQueue(context, subject).size < 4) {
                    if (fetchFromGemini(context, subject)) {
                        anySuccess = true
                    }
                    onUpdate?.invoke()
                }
            }
            if (anySuccess) {
                setStatus(context, "Connected! Fresh AI formulas loaded.")
            }
            onUpdate?.invoke()
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

    private fun fetchFromGemini(context: Context, subject: String): Boolean {
        val apiKey = getApiKey(context)
        if (apiKey.isBlank()) {
            setStatus(context, "No API key saved")
            return false
        }

        val seenList = getSeenTitles(context).takeLast(60).joinToString(", ")
        val prompt = """
            Generate 8 distinct Class 12 CBSE/NCERT $subject formulas or key concepts.
            NEVER repeat any of these previously shown titles: [$seenList].
            Do not use slashes or code commands in formulas.
            Write multiplication by placing symbols right next to each other without "*" or "x" between variables (for example write "Kb m" or "nRT", never "K_b * m").
            Use real textbook symbols like Δ, α, β, γ, θ, λ, μ, ν, ω, ε₀, π, →, ⇌, ², ³.
            For subscripts on symbols, use an underscore right after the letter like T_b, K_b, q_0, E_cell so the renderer draws them as proper small subscripts.
            Return ONLY a valid JSON array of objects with these exact keys:
            - "subject": "$subject"
            - "chapter": short NCERT chapter name
            - "title": concept name
            - "layout": one of "simple", "fraction", "integral", or "root"
            - "prefix": left side before the main structure, e.g. "ΔT_b = " or "I = " (or "" if none)
            - "numerator": top of fraction, or expression inside root/integral, or full formula if layout is "simple"
            - "denominator": bottom of fraction, or differential like "dx" or "dt" (or "" if unused)
            - "lowerLimit": lower integral bound if layout is "integral" (else "")
            - "upperLimit": upper integral bound if layout is "integral" (else "")
            - "takeaway": 1 short sentence explaining what it means in plain words
        """.trimIndent()

        var lastError = "Unknown error"

        for (model in MODELS) {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
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
                val code = conn.responseCode
                if (code != 200) {
                    val errBody = conn.errorStream?.bufferedReader()?.readText()?.take(120) ?: ""
                    lastError = "HTTP $code ($model):$errBody"
                    continue
                }

                val responseText = conn.inputStream.bufferedReader().readText()
                val rawJson = JSONObject(responseText)
                    .getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts")
                    .getJSONObject(0).getString("text")
                    .trim()
                    .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

                val arr = if (rawJson.startsWith("[")) {
                    JSONArray(rawJson)
                } else {
                    val obj = JSONObject(rawJson)
                    val firstKey = obj.keys().next()
                    obj.getJSONArray(firstKey)
                }

                val queue = getQueue(context, subject)
                for (i in 0 until arr.length()) {
                    queue.add(FormulaCard.fromJson(arr.getJSONObject(i)))
                }
                saveQueue(context, subject, queue)
                return true
            } catch (e: Exception) {
                lastError = "${e.javaClass.simpleName}: ${e.message}"
            } finally {
                conn.disconnect()
            }
        }

        setStatus(context, "API Error: $lastError")
        return false
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
            denominator = "q_0",
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
