package com.assinafy.sdk.resources

import com.assinafy.sdk.Logger
import com.assinafy.sdk.NoOpLogger
import com.assinafy.sdk.exceptions.ApiException
import com.assinafy.sdk.exceptions.AssinafyException
import com.assinafy.sdk.http.ApiHttpClient
import com.assinafy.sdk.http.HttpRawResponse
import com.assinafy.sdk.models.PaginatedResult
import com.assinafy.sdk.util.ApiValidator
import com.assinafy.sdk.util.ResponseHandler
import com.assinafy.sdk.util.UrlEncoding

abstract class BaseResource(
    protected val http: ApiHttpClient,
    protected val defaultAccountId: String? = null,
    protected val logger: Logger = NoOpLogger,
) {
    protected fun accountId(explicit: String? = null): String = ApiValidator.requireAccountId(explicit, defaultAccountId)

    protected fun requireId(value: String?, name: String): String = ApiValidator.requireNonBlank(value, name)

    protected fun pathSegment(value: String): String = UrlEncoding.pathSegment(value)

    protected fun queryString(vararg params: Pair<String, Any?>): String = UrlEncoding.queryString(*params)

    protected fun toJson(value: Any): String = ResponseHandler.toJson(value)

    protected fun toJsonAllowNulls(value: Any): String = ResponseHandler.toJsonAllowNulls(value)

    protected suspend fun <T> call(
        label: String,
        type: Class<T>,
        request: suspend () -> HttpRawResponse,
    ): T = runCatching {
        ResponseHandler.handle(request(), type)
    }.getOrElse { e -> throw e.coerceAsSdkException(label) }

    protected suspend fun <T> callOptional(
        label: String,
        type: Class<T>,
        request: suspend () -> HttpRawResponse,
    ): T? = runCatching {
        call(label, type, request)
    }.getOrElse { e ->
        if (e is ApiException && e.statusCode == 404) null else throw e
    }

    protected suspend fun callVoid(label: String, request: suspend () -> HttpRawResponse) = runCatching {
        ResponseHandler.handleVoid(request())
    }.getOrElse { e -> throw e.coerceAsSdkException(label) }

    protected suspend fun callBinary(label: String, request: suspend () -> ByteArray): ByteArray = runCatching { request() }.getOrElse { e -> throw e.coerceAsSdkException(label) }

    protected suspend fun <T> callList(
        label: String,
        elementType: Class<T>,
        request: suspend () -> HttpRawResponse,
    ): PaginatedResult<T> = runCatching {
        ResponseHandler.handleList(request(), elementType)
    }.getOrElse { e -> throw e.coerceAsSdkException(label) }

    protected suspend fun callMap(
        label: String,
        request: suspend () -> HttpRawResponse,
    ): Map<String, Any> = runCatching {
        ResponseHandler.handleMap(request())
    }.getOrElse { e -> throw e.coerceAsSdkException(label) }

    private fun Throwable.coerceAsSdkException(label: String): AssinafyException = when (this) {
        is AssinafyException -> this
        else -> ResponseHandler.toSdkException(this, label)
    }
}
