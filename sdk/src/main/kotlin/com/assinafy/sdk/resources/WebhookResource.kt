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
import com.assinafy.sdk.util.requireValidEmail
import java.net.URI

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
        val webhookUrl = requireHttpUrl(request.url)
        val webhookEmail = requireValidEmail(request.email, "Webhook email")
        val id = accountId(accountId)
        val body = mapOf(
            "url" to webhookUrl,
            "email" to webhookEmail,
            "events" to (request.events?.takeIf { it.isNotEmpty() } ?: DEFAULT_EVENTS),
            "is_active" to request.isActive,
        )
        logger.info("Registering webhook", mapOf("eventCount" to (request.events?.size ?: DEFAULT_EVENTS.size)))
        return call("Failed to register webhook", WebhookSubscription::class.java) {
            http.put("/accounts/${pathSegment(id)}/webhooks/subscriptions", toJson(body))
        }
    }

    suspend fun get(accountId: String? = null): WebhookSubscription? {
        val id = accountId(accountId)
        return callOptional("Failed to fetch webhook subscription", WebhookSubscription::class.java) {
            http.get("/accounts/${pathSegment(id)}/webhooks/subscriptions")
        }
    }

    suspend fun delete(accountId: String? = null) {
        val id = accountId(accountId)
        logger.info("Deleting webhook subscription")
        callVoid("Failed to delete webhook subscription") {
            http.delete("/accounts/${pathSegment(id)}/webhooks/subscriptions")
        }
    }

    suspend fun inactivate(accountId: String? = null): WebhookSubscription {
        val id = accountId(accountId)
        logger.info("Inactivating webhook subscription")
        return call("Failed to inactivate webhook subscription", WebhookSubscription::class.java) {
            http.put("/accounts/${pathSegment(id)}/webhooks/inactivate")
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
            http.get("/accounts/${pathSegment(id)}/webhooks", params.toQueryMap())
        }
    }

    suspend fun retryDispatch(dispatchId: String, accountId: String? = null): WebhookDispatch {
        val id = accountId(accountId)
        val did = requireId(dispatchId, "Dispatch ID")
        return call("Failed to retry webhook dispatch", WebhookDispatch::class.java) {
            http.post("/accounts/${pathSegment(id)}/webhooks/${pathSegment(did)}/retry")
        }
    }

    private fun requireHttpUrl(url: String): String {
        val normalized = requireId(url, "Webhook URL")
        val uri = try {
            URI(normalized)
        } catch (e: Exception) {
            throw ValidationException("Invalid webhook URL", mapOf("url" to url))
        }
        val scheme = uri.scheme?.lowercase()
        if ((scheme != "https" && scheme != "http") || uri.host.isNullOrBlank()) {
            throw ValidationException("Invalid webhook URL", mapOf("url" to url))
        }
        return normalized
    }
}
