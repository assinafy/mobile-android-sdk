package com.assinafy.sdk.helper

import com.assinafy.sdk.http.ApiHttpClient
import com.assinafy.sdk.http.HttpRawResponse

class MockApiHttpClient(
    private val defaultResponse: HttpRawResponse = HttpRawResponse(200, """{"status":200,"data":{}}""", emptyMap()),
    private val binaryResponse: ByteArray = ByteArray(0),
) : ApiHttpClient {

    val calls = mutableListOf<Call>()

    data class Call(
        val method: String,
        val path: String,
        val body: String? = null,
        val queryParams: Map<String, Any?> = emptyMap(),
    )

    private var responseQueue: ArrayDeque<HttpRawResponse> = ArrayDeque()

    fun enqueue(response: HttpRawResponse) {
        responseQueue.addLast(response)
    }

    private fun nextResponse(): HttpRawResponse =
        if (responseQueue.isEmpty()) defaultResponse else responseQueue.removeFirst()

    override suspend fun get(path: String, queryParams: Map<String, Any?>): HttpRawResponse {
        calls.add(Call("GET", path, queryParams = queryParams))
        return nextResponse()
    }

    override suspend fun post(path: String, jsonBody: String?): HttpRawResponse {
        calls.add(Call("POST", path, body = jsonBody))
        return nextResponse()
    }

    override suspend fun postMultipart(
        path: String,
        fileName: String,
        fileData: ByteArray,
        name: String,
        metadata: String?,
    ): HttpRawResponse {
        calls.add(Call("POST_MULTIPART", path))
        return nextResponse()
    }

    override suspend fun put(path: String, jsonBody: String?): HttpRawResponse {
        calls.add(Call("PUT", path, body = jsonBody))
        return nextResponse()
    }

    override suspend fun delete(path: String): HttpRawResponse {
        calls.add(Call("DELETE", path))
        return nextResponse()
    }

    override suspend fun getBinary(path: String): ByteArray {
        calls.add(Call("GET_BINARY", path))
        return binaryResponse
    }

    fun lastCall(): Call = calls.last()
    fun callCount(): Int = calls.size
}
