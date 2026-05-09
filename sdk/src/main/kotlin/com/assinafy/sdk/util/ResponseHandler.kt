package com.assinafy.sdk.util

import com.assinafy.sdk.exceptions.ApiException
import com.assinafy.sdk.exceptions.AssinafyException
import com.assinafy.sdk.exceptions.NetworkException
import com.assinafy.sdk.http.HttpRawResponse
import com.assinafy.sdk.models.PaginatedResult
import com.assinafy.sdk.models.PaginationMeta
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import java.io.IOException

object ResponseHandler {
    private val GSON: Gson = Gson()

    fun toJson(value: Any): String = GSON.toJson(value)

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
            @Suppress("UNCHECKED_CAST")
            return null as T
        }
        return try {
            val root = JsonParser.parseString(body)
            val envelope = parseEnvelopeOrNull(root)
            if (envelope != null) {
                if (envelope.status in 200..299) {
                    return GSON.fromJson(envelope.data, type)
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
                    return if (dataNode is JsonObject) {
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

    private class Envelope(val status: Int, val data: JsonElement) {
        fun asMap(): Map<String, Any> = GSON.fromJson(
            JsonParser.parseString(GSON.toJson(this)),
            object : TypeToken<Map<String, Any>>() {}.type,
        )
    }

    private fun parseEnvelopeOrNull(root: JsonElement): Envelope? {
        if (root !is JsonObject || !root.has("status") || !root.has("data")) return null
        return try {
            Envelope(root.get("status").asInt, root.get("data"))
        } catch (e: Exception) {
            null
        }
    }

    private fun JsonObject.toPlainMap(): Map<String, Any> =
        GSON.fromJson(this, object : TypeToken<Map<String, Any>>() {}.type)

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
                else -> emptyList()
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
        return emptyList()
    }

    private fun parsePaginationMeta(headers: Map<String, String>): PaginationMeta? {
        if (headers.isEmpty()) return null
        val currentPage = headers["x-pagination-current-page"]?.trim()?.toIntOrNull()
        val perPage = headers["x-pagination-per-page"]?.trim()?.toIntOrNull()
        val total = headers["x-pagination-total-count"]?.trim()?.toIntOrNull()
        val lastPage = headers["x-pagination-page-count"]?.trim()?.toIntOrNull()
        return if (listOf(currentPage, perPage, total, lastPage).all { it == null }) {
            null
        } else {
            PaginationMeta(currentPage, lastPage, perPage, total)
        }
    }
}