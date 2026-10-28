package com.assinafy.sdk.util

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale

/** Preserves the legacy epoch-seconds API while accepting current ISO-8601 delivery timestamps. */
internal class UnixSecondsAdapter : JsonDeserializer<Long> {
    override fun deserialize(json: JsonElement, type: Type, context: JsonDeserializationContext): Long? {
        if (json.isJsonNull) return null
        val value = json.asString
        value.toLongOrNull()?.let { return it }
        val normalized = value.replace(Regex("\\.[0-9]+(?=Z|[+-])"), "")
            .replace(Regex("Z$"), "+0000")
            .replace(Regex("([+-][0-9]{2}):([0-9]{2})$"), "$1$2")
        val position = ParsePosition(0)
        val parsed = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).apply { isLenient = false }.parse(normalized, position)
        if (parsed == null || position.index != normalized.length) throw JsonParseException("Invalid delivery timestamp")
        return parsed.time / 1_000
    }
}
