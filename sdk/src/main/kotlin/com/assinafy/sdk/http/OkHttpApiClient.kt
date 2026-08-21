package com.assinafy.sdk.http

import com.assinafy.sdk.SdkConstants
import com.assinafy.sdk.exceptions.ApiException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Default cancellable [ApiHttpClient] backed by OkHttp.
 *
 * Authentication headers are attached only to requests that match the configured base URL's
 * scheme, host, and port, so redirects to another origin cannot receive credentials. HTTP 429 is
 * retried at most twice for read-only methods, honoring bounded server delay headers; mutation
 * requests are never replayed.
 *
 */
class OkHttpApiClient private constructor(
    private val client: OkHttpClient,
    baseUrl: String,
) : ApiHttpClient {

    private val baseUrl = normaliseBaseUrl(baseUrl)

    /**
     * Creates the default transport.
     *
     * @param baseUrl Absolute Assinafy API root, including `/v1`.
     * @param apiKey Optional API key; mutually exclusive with [token] at the client level.
     * @param token Optional Bearer token.
     * @param timeoutMs Connect, read, and write timeout in milliseconds.
     */
    constructor(
        baseUrl: String,
        apiKey: String?,
        token: String?,
        timeoutMs: Long = 30_000L,
    ) : this(
        client = buildClient(baseUrl, apiKey, token, timeoutMs),
        baseUrl = baseUrl,
    )

    internal constructor(client: OkHttpClient, baseUrl: String, @Suppress("UNUSED_PARAMETER") unused: Unit) :
        this(client, baseUrl)

    override suspend fun get(path: String, queryParams: Map<String, Any?>): HttpRawResponse =
        execute(Request.Builder().url(url(path, queryParams)).get().build())

    override suspend fun post(path: String, jsonBody: String?): HttpRawResponse {
        val body = jsonBody?.toRequestBody(JSON) ?: EMPTY_BODY
        return execute(Request.Builder().url(url(path)).post(body).build())
    }

    override suspend fun postMultipart(
        path: String,
        fileName: String,
        fileData: ByteArray,
        name: String,
        metadata: String?,
    ): HttpRawResponse {
        val form = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", fileName, fileData.toRequestBody(PDF))
            .addFormDataPart("name", name)
            .also { if (metadata != null) it.addFormDataPart("metadata", metadata) }
            .build()
        return execute(Request.Builder().url(url(path)).post(form).build())
    }

    override suspend fun postMultipartFile(
        path: String,
        fieldName: String,
        fileName: String,
        fileData: ByteArray,
        contentType: String,
    ): HttpRawResponse {
        val form = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(fieldName, fileName, fileData.toRequestBody(contentType.toMediaType()))
            .build()
        return execute(Request.Builder().url(url(path)).post(form).build())
    }

    override suspend fun put(path: String, jsonBody: String?): HttpRawResponse {
        val body = jsonBody?.toRequestBody(JSON) ?: EMPTY_BODY
        return execute(Request.Builder().url(url(path)).put(body).build())
    }

    override suspend fun patch(path: String, jsonBody: String?): HttpRawResponse {
        val body = jsonBody?.toRequestBody(JSON) ?: EMPTY_BODY
        return execute(Request.Builder().url(url(path)).patch(body).build())
    }

    override suspend fun delete(path: String, jsonBody: String?): HttpRawResponse {
        val request = Request.Builder().url(url(path))
        if (jsonBody == null) request.delete() else request.delete(jsonBody.toRequestBody(JSON))
        return execute(request.build())
    }

    override suspend fun getBinary(path: String): ByteArray {
        val raw = executeWithRateLimitRetry(
            Request.Builder().url(url(path)).header("Accept", "*/*").get().build(),
        )
        val body = raw.body ?: ByteArray(0)
        if (raw.statusCode !in 200..299) {
            val errorBody = body.toString(Charsets.UTF_8).takeIf { it.isNotBlank() }
            throw ApiException.fromResponse(raw.statusCode, errorBody)
        }
        if (body.isEmpty()) throw ApiException("Empty binary response", raw.statusCode)
        return body
    }

    override suspend fun postSignature(path: String, imageData: ByteArray, contentType: String): HttpRawResponse {
        val body = imageData.toRequestBody(contentType.toMediaType())
        return execute(Request.Builder().url(url(path)).post(body).build())
    }

    private fun url(path: String, queryParams: Map<String, Any?> = emptyMap()): HttpUrl {
        val builder = (baseUrl + path).toHttpUrl().newBuilder()
        queryParams.forEach { (name, value) ->
            if (value != null) builder.addQueryParameter(name, value.toString())
        }
        return builder.build()
    }

    private suspend fun execute(request: Request): HttpRawResponse {
        val raw = executeWithRateLimitRetry(request)
        return HttpRawResponse(raw.statusCode, raw.body?.toString(Charsets.UTF_8), raw.headers)
    }

    private suspend fun executeWithRateLimitRetry(request: Request): RawHttpResponse {
        var attempt = 0
        while (true) {
            val response = executeOnce(request)
            if (response.statusCode != 429 || request.method !in RETRYABLE_METHODS || attempt >= MAX_RETRIES) {
                return response
            }
            attempt++
            delay(retryDelayMs(response.headers, attempt))
        }
    }

    private suspend fun executeOnce(request: Request): RawHttpResponse = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        call.enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: okhttp3.Call, response: Response) {
                try {
                    response.use { r ->
                        val result = RawHttpResponse(r.code, r.body?.bytes(), extractHeaders(r))
                        continuation.resume(result)
                    }
                } catch (e: IOException) {
                    continuation.resumeWithException(e)
                }
            }
        })
        continuation.invokeOnCancellation { call.cancel() }
    }

    private fun extractHeaders(response: Response): Map<String, String> = response.headers.names().associateWith { name ->
        response.header(name) ?: ""
    }.mapKeys { it.key.lowercase() }

    private fun retryDelayMs(headers: Map<String, String>, attempt: Int): Long {
        val hinted = headers["retry-after"]?.let(::parseRetryAfter)
            ?: headers["x-rate-limit-reset"]?.toDoubleOrNull()?.times(1_000)?.toLong()
        val fallback = 1_000L shl (attempt - 1).coerceAtMost(3)
        return (hinted ?: fallback).coerceIn(0, MAX_RETRY_DELAY_MS)
    }

    private fun parseRetryAfter(value: String): Long? {
        value.toDoubleOrNull()?.let { return (it * 1_000).toLong() }
        val parser = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("GMT")
        }
        return runCatching { parser.parse(value)?.time?.minus(System.currentTimeMillis()) }.getOrNull()
    }

    private data class RawHttpResponse(
        val statusCode: Int,
        val body: ByteArray?,
        val headers: Map<String, String>,
    )

    internal companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val PDF = "application/pdf".toMediaType()
        private val EMPTY_BODY = ByteArray(0).toRequestBody()
        private val RETRYABLE_METHODS = setOf("GET", "HEAD", "OPTIONS")
        private const val MAX_RETRIES = 2
        private const val MAX_RETRY_DELAY_MS = 30_000L

        private fun normaliseBaseUrl(url: String): String = url.trim().trimEnd('/')

        private fun buildClient(baseUrl: String, apiKey: String?, token: String?, timeoutMs: Long): OkHttpClient {
            val origin = normaliseBaseUrl(baseUrl).toHttpUrl()
            return OkHttpClient.Builder()
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .addNetworkInterceptor { chain ->
                    val request = chain.request()
                    val builder = request.newBuilder()
                        .removeHeader("X-Api-Key")
                        .removeHeader("Authorization")
                        .header("Accept", request.header("Accept") ?: "application/json")
                        .header("User-Agent", SdkConstants.USER_AGENT)
                    val sameOrigin = request.url.scheme == origin.scheme &&
                        request.url.host == origin.host &&
                        request.url.port == origin.port
                    if (sameOrigin) {
                        when {
                            !apiKey.isNullOrBlank() -> builder.header("X-Api-Key", apiKey)
                            !token.isNullOrBlank() -> builder.header("Authorization", "Bearer $token")
                        }
                    }
                    chain.proceed(builder.build())
                }
                .build()
        }

        internal fun forTesting(client: OkHttpClient, baseUrl: String): OkHttpApiClient = OkHttpApiClient(client, baseUrl, Unit)
    }
}
