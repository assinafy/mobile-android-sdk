package com.assinafy.sdk.resources

import com.assinafy.sdk.Logger
import com.assinafy.sdk.NoOpLogger
import com.assinafy.sdk.exceptions.ApiException
import com.assinafy.sdk.exceptions.AssinafyException
import com.assinafy.sdk.exceptions.ValidationException
import com.assinafy.sdk.http.ApiHttpClient
import com.assinafy.sdk.http.HttpRawResponse
import com.assinafy.sdk.models.PaginatedResult
import com.assinafy.sdk.util.ResponseHandler

abstract class BaseResource(
    protected val http: ApiHttpClient,
    protected val defaultAccountId: String? = null,
    protected val logger: Logger = NoOpLogger,
) {
    protected fun accountId(explicit: String? = null): String {
        val id = explicit ?: defaultAccountId
        if (id.isNullOrBlank()) {
            throw ValidationException(
                "Account ID is required. Provide it as a parameter or set a default in the client.",
            )
        }
        return id
    }

    protected fun requireId(value: String?, name: String): String {
        if (value.isNullOrBlank()) throw ValidationException("$name is required")
        return value
    }

    protected suspend fun <T> call(
        label: String,
        type: Class<T>,
        request: suspend () -> HttpRawResponse,
    ): T {
        return try {
            val response = request()
            ResponseHandler.handle(response, type)
        } catch (e: AssinafyException) {
            throw e
        } catch (e: Exception) {
            throw ResponseHandler.toSdkException(e, label)
        }
    }

    protected suspend fun <T> callOptional(
        label: String,
        type: Class<T>,
        request: suspend () -> HttpRawResponse,
    ): T? {
        return try {
            call(label, type, request)
        } catch (e: ApiException) {
            if (e.statusCode == 404) null else throw e
        }
    }

    protected suspend fun callVoid(
        label: String,
        request: suspend () -> HttpRawResponse,
    ) {
        try {
            val response = request()
            ResponseHandler.handleVoid(response)
        } catch (e: AssinafyException) {
            throw e
        } catch (e: Exception) {
            throw ResponseHandler.toSdkException(e, label)
        }
    }

    protected suspend fun callBinary(
        label: String,
        request: suspend () -> ByteArray,
    ): ByteArray {
        return try {
            request()
        } catch (e: AssinafyException) {
            throw e
        } catch (e: Exception) {
            throw ResponseHandler.toSdkException(e, label)
        }
    }

    protected suspend fun <T> callList(
        label: String,
        elementType: Class<T>,
        request: suspend () -> HttpRawResponse,
    ): PaginatedResult<T> {
        return try {
            val response = request()
            ResponseHandler.handleList(response, elementType)
        } catch (e: AssinafyException) {
            throw e
        } catch (e: Exception) {
            throw ResponseHandler.toSdkException(e, label)
        }
    }

    protected suspend fun callMap(
        label: String,
        request: suspend () -> HttpRawResponse,
    ): Map<String, Any> {
        return try {
            val response = request()
            ResponseHandler.handleMap(response)
        } catch (e: AssinafyException) {
            throw e
        } catch (e: Exception) {
            throw ResponseHandler.toSdkException(e, label)
        }
    }
}
