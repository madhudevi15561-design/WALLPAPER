package com.class12.formulawallpaper

import org.json.JSONObject

data class FormulaCard(
    val subject: String,
    val chapter: String,
    val title: String,
    val layout: String,
    val prefix: String,
    val numerator: String,
    val denominator: String,
    val lowerLimit: String,
    val upperLimit: String,
    val takeaway: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("subject", subject)
        put("chapter", chapter)
        put("title", title)
        put("layout", layout)
        put("prefix", prefix)
        put("numerator", numerator)
        put("denominator", denominator)
        put("lowerLimit", lowerLimit)
        put("upperLimit", upperLimit)
        put("takeaway", takeaway)
    }

    companion object {
        fun fromJson(obj: JSONObject): FormulaCard = FormulaCard(
            subject = obj.optString("subject", "PHYSICS"),
            chapter = obj.optString("chapter", "Class 12 NCERT"),
            title = obj.optString("title", "Formula"),
            layout = obj.optString("layout", "simple"),
            prefix = obj.optString("prefix", ""),
            numerator = obj.optString("numerator", ""),
            denominator = obj.optString("denominator", ""),
            lowerLimit = obj.optString("lowerLimit", ""),
            upperLimit = obj.optString("upperLimit", ""),
            takeaway = obj.optString("takeaway", "")
        )
    }
}
