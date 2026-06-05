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

/**
 * Webhook subscription and dispatch management. Each account has at most one subscription. Event ids
 * are listed in [com.assinafy.sdk.WebhookEvent]; verify delivered payloads with
 * [com.assinafy.sdk.support.WebhookVerifier].
 */
class WebhookResource(
    http: ApiHttpClient,
    defaultAccountId: String? = null,
    logger: Logger = NoOpLogger,
) : BaseResource(http, defaultAccountId, logger) {

    /**
     * Registers (replaces) the account's webhook subscription
     * (`PUT /accounts/{accountId}/webhooks/subscriptions`). When [RegisterWebhookRequest.events] is
     * null/empty, [RegisterWebhookRequest.DEFAULT_EVENTS] is used.
     *
     * @throws com.assinafy.sdk.exceptions.ValidationException on an invalid URL or email.
     */
    suspend fun register(request: RegisterWebhookRequest, accountId: String? = null): WebhookSubscription {
        val webhookUrl = requireHttpUrl(request.url)
        val webhookEmail = requireValidEmail(request.email, "Webhook email")
        val id = accountId(accountId)
        val body = mapOf(
            "url" to webhookUrl,
            "email" to webhookEmail,
            "events" to (request.events?.takeIf { it.isNotEmpty() } ?: RegisterWebhookRequest.DEFAULT_EVENTS),
            "is_active" to request.isActive,
        )
        logger.info("Registering webhook", mapOf("eventCount" to (request.events?.size ?: RegisterWebhookRequest.DEFAULT_EVENTS.size)))
        return call("Failed to register webhook", WebhookSubscription::class.java) {
            http.put("/accounts/${pathSegment(id)}/webhooks/subscriptions", toJson(body))
        }
    }

    /** Fetches the account's webhook subscription, or `null` if none is registered (404). */
    suspend fun get(accountId: String? = null): WebhookSubscription? {
        val id = accountId(accountId)
        return callOptional("Failed to fetch webhook subscription", WebhookSubscription::class.java) {
            http.get("/accounts/${pathSegment(id)}/webhooks/subscriptions")
        }
    }

    /** Deletes the account's webhook subscription (`DELETE /accounts/{accountId}/webhooks/subscriptions`). */
    suspend fun delete(accountId: String? = null) {
        val id = accountId(accountId)
        logger.info("Deleting webhook subscription")
        callVoid("Failed to delete webhook subscription") {
            http.delete("/accounts/${pathSegment(id)}/webhooks/subscriptions")
        }
    }

    /** Deactivates the subscription without deleting it (`PUT /accounts/{accountId}/webhooks/inactivate`). */
    suspend fun inactivate(accountId: String? = null): WebhookSubscription {
        val id = accountId(accountId)
        logger.info("Inactivating webhook subscription")
        return call("Failed to inactivate webhook subscription", WebhookSubscription::class.java) {
            http.put("/accounts/${pathSegment(id)}/webhooks/inactivate")
        }
    }

    /** Lists all webhook event types and their descriptions (`GET /webhooks/event-types`). */
    suspend fun listEventTypes(): List<WebhookEventTypeInfo> {
        val result = callList("Failed to list webhook event types", WebhookEventTypeInfo::class.java) {
            http.get("/webhooks/event-types")
        }
        return result.data
    }

    /** Lists past webhook dispatches (`GET /accounts/{accountId}/webhooks`), paginated. */
    suspend fun listDispatches(
        params: ListParams = ListParams(),
        accountId: String? = null,
    ): PaginatedResult<WebhookDispatch> {
        val id = accountId(accountId)
        return callList("Failed to list webhook dispatches", WebhookDispatch::class.java) {
            http.get("/accounts/${pathSegment(id)}/webhooks", params.toQueryMap())
        }
    }

    /** Retries a failed webhook dispatch (`POST /accounts/{accountId}/webhooks/{dispatchId}/retry`). */
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
