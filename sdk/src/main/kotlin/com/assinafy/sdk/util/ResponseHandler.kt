package com.assinafy.sdk.util

import com.assinafy.sdk.exceptions.ApiException
import com.assinafy.sdk.exceptions.AssinafyException
import com.assinafy.sdk.exceptions.NetworkException
import com.assinafy.sdk.http.HttpRawResponse
import com.assinafy.sdk.models.PaginatedResult
import com.assinafy.sdk.models.PaginationMeta
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import java.io.IOException

internal object ResponseHandler {
    private val GSON: Gson = Gson()
    private val GSON_WITH_NULLS: Gson = GsonBuilder().serializeNulls().create()

    fun toJson(value: Any): String = GSON.toJson(value)

    /**
     * Serializes [value] while keeping explicit `null` entries (e.g. `{"expires_at": null}`).
     * Use this for request bodies where a `null` value is semantically meaningful, since the
     * default serializer omits null entries entirely.
     */
    fun toJsonAllowNulls(value: Any): String = GSON_WITH_NULLS.toJson(value)

    fun <T> handle(response: HttpRawResponse, type: Class<T>): T {
        validateSuccess(response)
        return parseEnvelope(response.body, type)
    }

    fun handleMap(response: HttpRawResponse): Map<String, Any> {
        validateSuccess(response)
        return parseEnvelopeAsMap(response.body)
    }

    fun <T> handleList(response: HttpRawResponse, elementType: Class<T>): PaginatedResult<T> {
        validateSuccess(response)
        return PaginatedResult(
            data = parseListData(response.body, elementType),
            meta = parsePaginationMeta(response.headers),
        )
    }

    fun handleVoid(response: HttpRawResponse) {
        validateSuccess(response)
        if (response.body.isNullOrBlank()) return
        try {
            parseEnvelopeOrNull(JsonParser.parseString(response.body))?.let { envelope ->
                if (envelope.status !in 200..299) {
                    throw ApiException.fromResponse(envelope.status, envelope.asMap())
                }
            }
        } catch (e: AssinafyException) {
            throw e
        } catch (e: Exception) {
            throw AssinafyException("Failed to parse response: ${e.message}", emptyMap(), e)
        }
    }

    fun toSdkException(e: Throwable, label: String): AssinafyException = when (e) {
        is AssinafyException -> e
        is IOException -> NetworkException("$label: ${e.message}", e)
        else -> AssinafyException("$label: ${e.message}", emptyMap(), e)
    }

    private fun validateSuccess(response: HttpRawResponse) {
        if (response.statusCode !in 200..299) {
            throw ApiException.fromResponse(response.statusCode, tryParseBody(response.body))
        }
    }

    private fun tryParseBody(body: String?): Any? {
        if (body.isNullOrBlank()) return null
        return try {
            GSON.fromJson(body, object : TypeToken<Map<String, Any>>() {}.type)
        } catch (e: Exception) {
            body
        }
    }

    private fun <T> parseEnvelope(body: String?, type: Class<T>): T {
        if (body.isNullOrBlank()) {
            throw AssinafyException("Empty response body where ${type.simpleName} was expected")
        }
        return try {
            val root = JsonParser.parseString(body)
            val envelope = parseEnvelopeOrNull(root)
            if (envelope != null) {
                if (envelope.status in 200..299) {
                    val data = envelope.data
                    if (data == null || data.isJsonNull) {
                        throw AssinafyException("Empty response data where ${type.simpleName} was expected")
                    }
                    return GSON.fromJson(data, type)
                }
                throw ApiException.fromResponse(envelope.status, envelope.asMap())
            }
            GSON.fromJson(root, type)
        } catch (e: AssinafyException) {
            throw e
        } catch (e: Exception) {
            throw AssinafyException("Failed to parse response: ${e.message}", emptyMap(), e)
        }
    }

    private fun parseEnvelopeAsMap(body: String?): Map<String, Any> {
        if (body.isNullOrBlank()) return emptyMap()
        return try {
            val root = JsonParser.parseString(body)
            val envelope = parseEnvelopeOrNull(root)
            if (envelope != null) {
                if (envelope.status in 200..299) {
                    val dataNode = envelope.data
                    return if (dataNode == null || dataNode.isJsonNull) {
                        emptyMap()
                    } else if (dataNode is JsonObject) {
                        dataNode.toPlainMap()
                    } else {
                        mapOf("data" to GSON.fromJson<Any>(dataNode, Any::class.java))
                    }
                }
                throw ApiException.fromResponse(envelope.status, envelope.asMap())
            }
            (root as? JsonObject)?.toPlainMap() ?: emptyMap()
        } catch (e: AssinafyException) {
            throw e
        } catch (e: Exception) {
            throw AssinafyException("Failed to parse response: ${e.message}", emptyMap(), e)
        }
    }

    private class Envelope(
        val status: Int,
        val message: String?,
        val data: JsonElement?,
        private val root: JsonObject,
    ) {
        fun asMap(): Map<String, Any> = root.toPlainMap()
    }

    private fun parseEnvelopeOrNull(root: JsonElement): Envelope? {
        if (root !is JsonObject || !root.has("status")) return null
        val statusNode = root.get("status")
        val status = if (statusNode.isJsonPrimitive && statusNode.asJsonPrimitive.isNumber) {
            statusNode.asString.toIntOrNull()
        } else {
            null
        }
        if (status == null) throw AssinafyException("Malformed response envelope: status must be an integer")
        return Envelope(
            status = status,
            message = root.get("message")?.takeUnless(JsonElement::isJsonNull)?.asString,
            data = root.get("data"),
            root = root,
        )
    }

    private fun JsonObject.toPlainMap(): Map<String, Any> = GSON.fromJson(this, object : TypeToken<Map<String, Any>>() {}.type)

    private fun <T> parseListData(body: String?, elementType: Class<T>): List<T> {
        if (body.isNullOrBlank()) return emptyList()
        return try {
            val root = JsonParser.parseString(body)
            val envelope = parseEnvelopeOrNull(root)
            if (envelope != null) {
                if (envelope.status !in 200..299) {
                    throw ApiException.fromResponse(envelope.status, envelope.asMap())
                }
                return extractArray(envelope.data, elementType)
            }
            when (root) {
                is JsonArray -> extractArray(root, elementType)
                is JsonObject -> extractArray(root.get("data"), elementType)
                else -> throw AssinafyException("Malformed list response: expected an array")
            }
        } catch (e: AssinafyException) {
            throw e
        } catch (e: Exception) {
            throw AssinafyException("Failed to parse list response: ${e.message}", emptyMap(), e)
        }
    }

    private fun <T> extractArray(element: JsonElement?, elementType: Class<T>): List<T> {
        if (element == null || element.isJsonNull) return emptyList()
        if (element is JsonArray) {
            return element.map { GSON.fromJson(it, elementType) }
        }
        if (element is JsonObject && element.has("data")) {
            return extractArray(element.get("data"), elementType)
        }
        throw AssinafyException("Malformed list response: data must be an array")
    }

    private fun parsePaginationMeta(headers: Map<String, String>): PaginationMeta? {
        if (headers.isEmpty()) return null
        // Normalize so the lookup does not depend on the caller having lowercased header keys.
        val lc = headers.entries.associate { it.key.lowercase() to it.value }
        val currentPage = lc["x-pagination-current-page"]?.trim()?.toIntOrNull()
        val perPage = lc["x-pagination-per-page"]?.trim()?.toIntOrNull()
        val total = lc["x-pagination-total-count"]?.trim()?.toIntOrNull()
        val lastPage = lc["x-pagination-page-count"]?.trim()?.toIntOrNull()
        return if (listOf(currentPage, perPage, total, lastPage).all { it == null }) {
            null
        } else {
            PaginationMeta(currentPage, lastPage, perPage, total)
        }
    }
}
