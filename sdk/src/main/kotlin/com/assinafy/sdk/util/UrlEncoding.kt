package com.assinafy.sdk.util

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal object UrlEncoding {
    fun pathSegment(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

    fun queryString(vararg params: Pair<String, Any?>): String {
        val encoded = params
            .filter { (_, value) -> value != null }
            .joinToString("&") { (name, value) ->
                "${pathSegment(name)}=${pathSegment(value.toString())}"
            }
        return if (encoded.isEmpty()) "" else "?$encoded"
    }
}
