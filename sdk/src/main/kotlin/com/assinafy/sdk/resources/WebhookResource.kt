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
import com.assinafy.sdk.request.WebhookDispatchParams
import com.assinafy.sdk.util.requireValidEmail
import java.net.URI

/**
 * Webhook subscription and dispatch management. Each account has at most one subscription. Event ids
 * are listed in [com.assinafy.sdk.WebhookEvent]; verify delivered payloads with
 * [com.assinafy.sdk.support.WebhookVerifier].
 * API and transport failures use the SDK's typed exception hierarchy.
 */
class WebhookResource internal constructor(
    http: ApiHttpClient,
    defaultAccountId: String? = null,
    logger: Logger = NoOpLogger,
) : BaseResource(http, defaultAccountId, logger) {

    /**
     * Registers (replaces) the account's webhook subscription
     * (`PUT /accounts/{accountId}/webhooks/subscriptions`). When [RegisterWebhookRequest.events] is
     * null/empty, [RegisterWebhookRequest.DEFAULT_EVENTS] is used.
     *
     * @param request Destination URL, delivery contact, event IDs, and active state.
     * @param accountId Account override; otherwise the client's default account is used.
     * @return Complete subscription stored for the account.
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

    /**
     * Fetches the account's webhook subscription.
     *
     * @param accountId Account override; otherwise the client's default account is used.
     * @return Subscription, or `null` when the API returns HTTP 404.
     */
    suspend fun get(accountId: String? = null): WebhookSubscription? {
        val id = accountId(accountId)
        return callOptional("Failed to fetch webhook subscription", WebhookSubscription::class.java) {
            http.get("/accounts/${pathSegment(id)}/webhooks/subscriptions")
        }
    }

    /**
     * Deactivates the subscription without deleting it (`PUT /accounts/{accountId}/webhooks/inactivate`).
     *
     * The API has no delete endpoint for a subscription (`DELETE .../webhooks/subscriptions` returns
     * 404); to stop deliveries, inactivate it or overwrite it with [register].
     *
     * @param accountId Account override; otherwise the client's default account is used.
     * @return Complete inactive subscription.
     */
    suspend fun inactivate(accountId: String? = null): WebhookSubscription {
        val id = accountId(accountId)
        logger.info("Inactivating webhook subscription")
        return call("Failed to inactivate webhook subscription", WebhookSubscription::class.java) {
            http.put("/accounts/${pathSegment(id)}/webhooks/inactivate")
        }
    }

    /**
     * Lists webhook event types (`GET /webhooks/event-types`).
     *
     * @return Wire event IDs and human-readable descriptions.
     */
    suspend fun listEventTypes(): List<WebhookEventTypeInfo> {
        val result = callList("Failed to list webhook event types", WebhookEventTypeInfo::class.java) {
            http.get("/webhooks/event-types")
        }
        return result.data
    }

    /**
     * Lists past webhook dispatches using legacy common-list pagination values.
     *
     * Only [ListParams.page] and [ListParams.perPage] are sent because the webhook endpoint does not
     * accept the document-specific filters in [ListParams].
     *
     * @param params Pagination values; all other [ListParams] fields are ignored.
     * @param accountId Account override; otherwise the client's default account is used.
     * @return Dispatch attempts and optional pagination-header metadata.
     */
    @Deprecated("Use listDispatches(WebhookDispatchParams, accountId)")
    suspend fun listDispatches(
        params: ListParams = ListParams(),
        accountId: String? = null,
    ): PaginatedResult<WebhookDispatch> {
        val id = accountId(accountId)
        val query = WebhookDispatchParams(page = params.page, perPage = params.perPage).toQueryMap()
        return callList("Failed to list webhook dispatches", WebhookDispatch::class.java) {
            http.get("/accounts/${pathSegment(id)}/webhooks", query)
        }
    }

    /**
     * Lists dispatch history with every current API filter.
     *
     * @param params Event, delivery-state, timestamp-range, and pagination filters.
     * @param accountId Account override; otherwise the client's default account is used.
     * @return Matching dispatch attempts and optional pagination-header metadata.
     * @throws ValidationException for an invalid timestamp range or pagination value.
     */
    suspend fun listDispatches(
        params: WebhookDispatchParams,
        accountId: String? = null,
    ): PaginatedResult<WebhookDispatch> {
        val id = accountId(accountId)
        return callList("Failed to list webhook dispatches", WebhookDispatch::class.java) {
            http.get("/accounts/${pathSegment(id)}/webhooks", params.toQueryMap())
        }
    }

    /**
     * Retries a failed webhook dispatch.
     *
     * @param dispatchId Stable delivery-attempt identifier.
     * @param accountId Account override; otherwise the client's default account is used.
     * @return Updated dispatch record for the retry.
     */
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
