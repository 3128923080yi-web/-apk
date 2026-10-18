package com.coolapk.port.ui

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/* 轻量 JSON 读取工具（顶层扩展函数，直接 import 包即可使用） */

fun JsonElement?.obj(): JsonObject? = this?.takeIf { it.isJsonObject }?.asJsonObject

fun JsonElement?.arr(): JsonArray = when {
    this == null || this.isJsonNull -> JsonArray()
    this.isJsonArray -> asJsonArray
    this.isJsonObject -> asJsonObject.get("data")?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()
    else -> JsonArray()
}

fun JsonObject.str(vararg keys: String): String {
    for (k in keys) {
        val v = get(k) ?: continue
        if (!v.isJsonNull && v.isJsonPrimitive) return v.asString
    }
    return ""
}

fun JsonObject.elem(vararg keys: String): JsonElement? {
    for (k in keys) get(k)?.let { if (!it.isJsonNull) return it }
    return null
}

object J {
    /** 去掉 HTML 标签，用于 feed 正文展示 */
    fun stripHtml(html: String): String = html
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("\u0026nbsp;", " ")
        .replace("\u0026amp;", "\u0026")
        .replace("\u0026quot;", "\"")
        .replace("\u0026lt;", "<")
        .replace("\u0026gt;", ">")
        .trim()
}