package com.assinafy.sdk.http

import com.assinafy.sdk.SdkConstants
import com.assinafy.sdk.exceptions.ApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class OkHttpApiClient private constructor(
    private val client: OkHttpClient,
    baseUrl: String,
) : ApiHttpClient {

    private val baseUrl = normaliseBaseUrl(baseUrl)

    constructor(
        baseUrl: String,
        apiKey: String?,
        token: String?,
        timeoutMs: Long = 30_000L,
    ) : this(
        client = OkHttpClient.Builder()
            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .addInterceptor { chain ->
                val builder = chain.request().newBuilder()
                    .header("Accept", "application/json")
                    .header("User-Agent", SdkConstants.USER_AGENT)
                when {
                    !apiKey.isNullOrBlank() -> builder.header("X-Api-Key", apiKey)
                    !token.isNullOrBlank() -> builder.header("Authorization", "Bearer $token")
                }
                chain.proceed(builder.build())
            }
            .build(),
        baseUrl = baseUrl,
    )

    internal constructor(client: OkHttpClient, baseUrl: String, @Suppress("UNUSED_PARAMETER") unused: Unit) :
        this(client, baseUrl)

    override suspend fun get(path: String, queryParams: Map<String, Any?>): HttpRawResponse = withContext(Dispatchers.IO) {
        execute(Request.Builder().url(url(path, queryParams)).get().build())
    }

    override suspend fun post(path: String, jsonBody: String?): HttpRawResponse = withContext(Dispatchers.IO) {
        val body = (jsonBody ?: "{}").toRequestBody(JSON)
        execute(Request.Builder().url(url(path)).post(body).build())
    }

    override suspend fun postMultipart(
        path: String,
        fileName: String,
        fileData: ByteArray,
        name: String,
        metadata: String?,
    ): HttpRawResponse = withContext(Dispatchers.IO) {
        val form = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", fileName, fileData.toRequestBody(PDF))
            .addFormDataPart("name", name)
            .also { if (metadata != null) it.addFormDataPart("metadata", metadata) }
            .build()
        execute(Request.Builder().url(url(path)).post(form).build())
    }

    override suspend fun put(path: String, jsonBody: String?): HttpRawResponse = withContext(Dispatchers.IO) {
        val body = (jsonBody ?: "{}").toRequestBody(JSON)
        execute(Request.Builder().url(url(path)).put(body).build())
    }

    override suspend fun delete(path: String): HttpRawResponse = withContext(Dispatchers.IO) {
        execute(Request.Builder().url(url(path)).delete().build())
    }

    override suspend fun getBinary(path: String): ByteArray = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(Request.Builder().url(url(path)).get().build())
        call.enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: okhttp3.Call, response: Response) {
                response.use { r ->
                    val body = r.body?.bytes() ?: ByteArray(0)
                    when {
                        !r.isSuccessful -> {
                            val errorBody = body.toString(Charsets.UTF_8).takeIf { it.isNotBlank() }
                            continuation.resumeWithException(ApiException.fromResponse(r.code, errorBody))
                        }
                        body.isEmpty() -> continuation.resumeWithException(
                            ApiException("Empty binary response", r.code),
                        )
                        else -> continuation.resume(body)
                    }
                }
            }
        })
        continuation.invokeOnCancellation { call.cancel() }
    }

    override suspend fun postSignature(path: String, imageData: ByteArray, contentType: String): HttpRawResponse = withContext(Dispatchers.IO) {
        val body = imageData.toRequestBody(contentType.toMediaType())
        execute(Request.Builder().url(url(path)).post(body).build())
    }

    private fun url(path: String, queryParams: Map<String, Any?> = emptyMap()): HttpUrl {
        val builder = (baseUrl + path).toHttpUrl().newBuilder()
        queryParams.forEach { (name, value) ->
            if (value != null) builder.addQueryParameter(name, value.toString())
        }
        return builder.build()
    }

    private fun execute(request: Request): HttpRawResponse = client.newCall(request).execute().use { r ->
        HttpRawResponse(r.code, r.body?.string(), extractHeaders(r))
    }

    private fun extractHeaders(response: Response): Map<String, String> = response.headers.names().associateWith { name ->
        response.header(name) ?: ""
    }.mapKeys { it.key.lowercase() }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val PDF = "application/pdf".toMediaType()

        private fun normaliseBaseUrl(url: String): String = url.trim().trimEnd('/')

        internal fun forTesting(client: OkHttpClient, baseUrl: String): OkHttpApiClient = OkHttpApiClient(client, baseUrl, Unit)
    }
}
