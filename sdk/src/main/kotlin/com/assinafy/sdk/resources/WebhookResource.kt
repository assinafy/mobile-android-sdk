package com.assinafy.sdk.resources

import com.assinafy.sdk.Logger
import com.assinafy.sdk.NoOpLogger
import com.assinafy.sdk.exceptions.ValidationException
import com.assinafy.sdk.http.ApiHttpClient
import com.assinafy.sdk.models.PaginatedResult
import com.assinafy.sdk.models.WebhookDispatch
import com.assinafy.sdk.models.WebhookEventTypeInfo
import com.assinafy.sdk.models.WebhookSubscription
import com.assinafy.sdk.request.ListParams
import com.assinafy.sdk.request.RegisterWebhookRequest
import com.assinafy.sdk.util.ResponseHandler

private val DEFAULT_EVENTS = listOf(
    "document_ready",
    "document_prepared",
    "signer_signed_document",
    "signer_rejected_document",
    "document_processing_failed",
)

class WebhookResource(
    http: ApiHttpClient,
    defaultAccountId: String? = null,
    logger: Logger = NoOpLogger,
) : BaseResource(http, defaultAccountId, logger) {

    suspend fun register(request: RegisterWebhookRequest, accountId: String? = null): WebhookSubscription {
        if (request.url.isBlank()) throw ValidationException("Webhook URL is required")
        if (request.email.isBlank()) throw ValidationException("Webhook email is required")
        val id = accountId(accountId)
        val body = mapOf(
            "url" to request.url,
            "email" to request.email,
            "events" to (request.events?.takeIf { it.isNotEmpty() } ?: DEFAULT_EVENTS),
            "is_active" to request.isActive,
        )
        logger.info("Registering webhook", mapOf("url" to request.url))
        return call("Failed to register webhook", WebhookSubscription::class.java) {
            http.put("/accounts/$id/webhooks/subscriptions", ResponseHandler.GSON.toJson(body))
        }
    }

    suspend fun get(accountId: String? = null): WebhookSubscription? {
        val id = accountId(accountId)
        return callOptional("Failed to fetch webhook subscription", WebhookSubscription::class.java) {
            http.get("/accounts/$id/webhooks/subscriptions")
        }
    }

    suspend fun delete(accountId: String? = null) {
        val id = accountId(accountId)
        logger.info("Deleting webhook subscription")
        callVoid("Failed to delete webhook subscription") {
            http.delete("/accounts/$id/webhooks/subscriptions")
        }
    }

    suspend fun inactivate(accountId: String? = null): WebhookSubscription {
        val id = accountId(accountId)
        logger.info("Inactivating webhook subscription")
        return call("Failed to inactivate webhook subscription", WebhookSubscription::class.java) {
            http.put("/accounts/$id/webhooks/inactivate")
        }
    }

    suspend fun listEventTypes(): List<WebhookEventTypeInfo> {
        val result = callList("Failed to list webhook event types", WebhookEventTypeInfo::class.java) {
            http.get("/webhooks/event-types")
        }
        return result.data
    }

    suspend fun listDispatches(
        params: ListParams = ListParams(),
        accountId: String? = null,
    ): PaginatedResult<WebhookDispatch> {
        val id = accountId(accountId)
        return callList("Failed to list webhook dispatches", WebhookDispatch::class.java) {
            http.get("/accounts/$id/webhooks", params.toQueryMap())
        }
    }

    suspend fun retryDispatch(dispatchId: String, accountId: String? = null): WebhookDispatch {
        val id = accountId(accountId)
        val did = requireId(dispatchId, "Dispatch ID")
        return call("Failed to retry webhook dispatch", WebhookDispatch::class.java) {
            http.post("/accounts/$id/webhooks/$did/retry")
        }
    }
}
