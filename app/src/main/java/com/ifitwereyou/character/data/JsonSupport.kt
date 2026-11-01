package com.ifitwereyou.character.data

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

internal fun asRecord(value: Any?): JSONObject? = value as? JSONObject

internal fun asArray(value: Any?): JSONArray? = value as? JSONArray

internal fun text(value: Any?, fallback: String = ""): String {
    if (value is String) return if (value.isNotBlank()) value else fallback
    return when (value) {
        is Number, is Boolean -> value.toString()
        else -> fallback
    }
}

internal fun numberValue(value: Any?, fallback: Double = 0.0): Double {
    val parsed = when (value) {
        is Number -> value.toDouble()
        null -> return fallback
        else -> value.toString().toDoubleOrNull() ?: return fallback
    }
    return if (parsed.isFinite()) parsed else fallback
}

internal fun stringList(value: Any?): List<String> {
    val array = value as? JSONArray
    if (array != null) {
        return (0 until array.length()).mapNotNull { index ->
            text(array.opt(index)).takeIf { it.isNotBlank() }
        }
    }
    return text(value).takeIf { it.isNotBlank() }?.let { listOf(it) } ?: emptyList()
}

internal fun JSONObject.optObject(vararg keys: String): JSONObject? {
    for (key in keys) {
        val value = opt(key)
        if (value is JSONObject) return value
    }
    return null
}

internal fun JSONObject.optArray(vararg keys: String): JSONArray? {
    for (key in keys) {
        val value = opt(key)
        if (value is JSONArray) return value
    }
    return null
}

internal fun JSONObject.optTextValue(vararg keys: String): String {
    for (key in keys) {
        val value = text(opt(key))
        if (value.isNotEmpty()) return value
    }
    return ""
}

internal fun JSONObject.hasAny(vararg keys: String): Boolean = keys.any { has(it) && opt(it) != null }

internal fun putNullable(target: JSONObject, key: String, value: Any?) {
    target.put(key, value ?: JSONObject.NULL)
}

/** 模型输出常常带说明文字或 ``` 代码块，这里做宽松解析。 */
internal fun parseJsonLoose(raw: String): Any? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null

    val unfenced = trimmed
        .replace(Regex("^```(?:json)?\\s*", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s*```$"), "")
        .trim()

    val candidates = mutableListOf(trimmed, unfenced)
    val start = unfenced.indexOf('{')
    val end = unfenced.lastIndexOf('}')
    if (start != -1 && end > start) {
        candidates.add(unfenced.substring(start, end + 1))
    }

    for (candidate in candidates) {
        parseObjectCandidate(candidate)?.let { return it }
        val repaired = candidate
            .replace(Regex(",\\s*([}\\]])"), "$1")
            .replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), "")
        parseObjectCandidate(repaired)?.let { return it }
    }
    return null
}

private fun parseObjectCandidate(candidate: String): Any? {
    if (candidate.isBlank()) return null
    return try {
        val parsed = JSONTokener(candidate).nextValue()
        if (parsed is JSONObject || parsed is JSONArray) parsed else null
    } catch (_: Exception) {
        null
    }
}

internal fun clipCodePoints(value: Any?, limit: Int): String {
    val textValue = text(value).trim()
    if (textValue.isEmpty()) return ""
    val count = textValue.codePointCount(0, textValue.length)
    if (count <= limit) return textValue
    val end = textValue.offsetByCodePoints(0, limit)
    return textValue.substring(0, end) + "…"
}
