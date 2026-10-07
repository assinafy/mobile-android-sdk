package com.assinafy.sdk.resources

import com.assinafy.sdk.Logger
import com.assinafy.sdk.NoOpLogger
import com.assinafy.sdk.exceptions.ValidationException
import com.assinafy.sdk.http.ApiHttpClient
import com.assinafy.sdk.models.PaginatedResult
import com.assinafy.sdk.models.WebhookDispatch
import com.assinafy.sdk.models.WebhookEndpoint
import com.assinafy.sdk.models.WebhookEndpointSecret
import com.assinafy.sdk.models.WebhookEventTypeInfo
import com.assinafy.sdk.models.WebhookSubscription
import com.assinafy.sdk.request.CreateWebhookEndpointRequest
import com.assinafy.sdk.request.ListParams
import com.assinafy.sdk.request.RegisterWebhookRequest
import com.assinafy.sdk.request.UpdateWebhookEndpointRequest
import com.assinafy.sdk.request.WebhookDispatchParams
import com.assinafy.sdk.util.requireValidEmail
import java.net.URI

/**
 * Webhook endpoints, delivery history and signing secrets. An account has 1 endpoint, or up to 3 on
 * paid plans; every active endpoint subscribed to an event receives it. The `*Endpoint*` methods
 * manage each endpoint; [register], [get] and [inactivate] act on the account's oldest endpoint.
 * Event ids are listed in [com.assinafy.sdk.WebhookEvent]; verify signed deliveries with
 * [com.assinafy.sdk.support.WebhookVerifier].
 * API and transport failures use the SDK's typed exception hierarchy.
 */
class WebhookResource internal constructor(
    http: ApiHttpClient,
    defaultAccountId: String? = null,
    logger: Logger = NoOpLogger,
) : BaseResource(http, defaultAccountId, logger) {

    /**
     * Updates the account's oldest webhook endpoint, creating it when the account has none
     * (`PUT /accounts/{accountId}/webhooks/subscriptions`). Use [updateEndpoint] to target one
     * endpoint of several. When [RegisterWebhookRequest.events] is
     * null, [RegisterWebhookRequest.DEFAULT_EVENTS] is used; an explicit empty list is preserved.
     *
     * Request body:
     * Response `data` is the complete stored subscription:
     *
     * Wire operation: `PUT /v1/accounts/{accountId}/webhooks/subscriptions`.
     *
     * Request body (`application/json`; optional members may be omitted):
     * ```json
     * {
     *   "events": ["example"],
     *   "is_active": true,
     *   "url": "https://example.com/resource",
     *   "email": "person@example.com"
     * }
     * ```
     *
     * Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
     * ```json
     * {
     *   "events": ["example"],
     *   "is_active": true,
     *   "url": null,
     *   "email": null,
     *   "updated_at": null
     * }
     * ```
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
            "events" to (request.events ?: RegisterWebhookRequest.DEFAULT_EVENTS),
            "is_active" to request.isActive,
        )
        logger.info("Registering webhook", mapOf("eventCount" to (request.events?.size ?: RegisterWebhookRequest.DEFAULT_EVENTS.size)))
        return call("Failed to register webhook", WebhookSubscription::class.java) {
            http.put("/accounts/${pathSegment(id)}/webhooks/subscriptions", toJson(body))
        }
    }

    /**
     * Fetches the account's oldest webhook endpoint
     * (`GET /accounts/{accountId}/webhooks/subscriptions`). Use [listEndpoints] to see all of them.
     *
     * Request body: none. Response `data`:
     *
     * Wire operation: `GET /v1/accounts/{accountId}/webhooks/subscriptions`.
     *
     * Request body: none.
     *
     * Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
     * ```json
     * {
     *   "events": ["example"],
     *   "is_active": true,
     *   "url": null,
     *   "email": null,
     *   "updated_at": null
     * }
     * ```
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
     * Deactivates the account's oldest endpoint without deleting it
     * (`PUT /accounts/{accountId}/webhooks/inactivate`); other endpoints are unaffected. To remove an
     * endpoint and free its slot, use [deleteEndpoint].
     *
     * Request body: none. Response `data` is the complete subscription with `is_active` now false,
     * in the shape documented by [get].
     *
     * Wire operation: `PUT /v1/accounts/{accountId}/webhooks/inactivate`.
     *
     * Request body: none.
     *
     * Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
     * ```json
     * {
     *   "events": ["example"],
     *   "is_active": true,
     *   "url": null,
     *   "email": null,
     *   "updated_at": null
     * }
     * ```
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
     * Request body/query: none. Response `data`:
     * Reading this catalog lets an integration accept new server events without an SDK update;
     * [com.assinafy.sdk.WebhookEvent] holds the identifiers known at release time.
     *
     * Wire operation: `GET /v1/webhooks/event-types`.
     *
     * Request body: none.
     *
     * Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
     * ```json
     * [
     *   {
     *     "id": "id-placeholder",
     *     "description": "example"
     *   }
     * ]
     * ```
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
     * Request body: none. Response `data` is an array of dispatch records in the shape documented
     * by [retryDispatch].
     *
     * Wire operation: `GET /v1/accounts/{accountId}/webhooks`.
     *
     * Request body: none.
     *
     * Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
     * ```json
     * [
     *   {
     *     "resource": "activity_dispatching_history",
     *     "id": "id-placeholder",
     *     "event": "example",
     *     "activity_id": 1,
     *     "endpoint_id": "id-placeholder",
     *     "endpoint": "https://example.com/webhooks/assinafy",
     *     "payload": {},
     *     "delivered": true,
     *     "http_status": 1,
     *     "response_body": null,
     *     "error": null,
     *     "created_at": "2026-01-01T00:00:00Z",
     *     "updated_at": "2026-01-01T00:00:00Z"
     *   }
     * ]
     * ```
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
     * Lists dispatch history with every current API filter
     * (`GET /accounts/{accountId}/webhooks`).
     *
     * Request body: none. Query: `endpoint_id`, `event`, `delivered`, `from`, `to`, `page`, `per-page`. Response
     * `data` is an array of dispatch records in the shape documented by [retryDispatch], and
     * `X-Pagination-*` headers are exposed through [PaginatedResult.meta].
     *
     * Wire operation: `GET /v1/accounts/{accountId}/webhooks`.
     *
     * Request body: none.
     *
     * Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
     * ```json
     * [
     *   {
     *     "resource": "activity_dispatching_history",
     *     "id": "id-placeholder",
     *     "event": "example",
     *     "activity_id": 1,
     *     "endpoint_id": "id-placeholder",
     *     "endpoint": "https://example.com/webhooks/assinafy",
     *     "payload": {},
     *     "delivered": true,
     *     "http_status": 1,
     *     "response_body": null,
     *     "error": null,
     *     "created_at": "2026-01-01T00:00:00Z",
     *     "updated_at": "2026-01-01T00:00:00Z"
     *   }
     * ]
     * ```
     *
     * @param params Endpoint, event, delivery-state, timestamp-range, and pagination filters.
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
     * Retries a failed webhook dispatch
     * (`POST /accounts/{accountId}/webhooks/{historyId}/retry`).
     *
     * Request body: none. Response `data` is the updated delivery record:
     *
     * Wire operation: `POST /v1/accounts/{accountId}/webhooks/{historyId}/retry`.
     *
     * Request body: none.
     *
     * Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
     * ```json
     * {
     *   "resource": "activity_dispatching_history",
     *   "id": "id-placeholder",
     *   "event": "example",
     *   "activity_id": 1,
     *   "endpoint_id": "id-placeholder",
     *   "endpoint": "https://example.com/webhooks/assinafy",
     *   "payload": {},
     *   "delivered": true,
     *   "http_status": 1,
     *   "response_body": null,
     *   "error": null,
     *   "created_at": "2026-01-01T00:00:00Z",
     *   "updated_at": "2026-01-01T00:00:00Z"
     * }
     * ```
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

    /**
     * Lists the account's webhook endpoints, oldest first.
     *
     * Wire operation: `GET /v1/accounts/{accountId}/webhooks/endpoints`.
     *
     * Request body: none.
     *
     * Response 200 `data` payload (inside `{status,message,data}`) is an array of endpoints:
     * ```json
     * [
     *   {
     *     "id": "id-placeholder",
     *     "name": "ERP",
     *     "url": "https://example.com/webhooks/assinafy",
     *     "email": "ops@example.com",
     *     "events": ["document_ready"],
     *     "is_active": true,
     *     "signing_enabled": true,
     *     "created_at": "2026-01-01T00:00:00Z",
     *     "updated_at": "2026-01-01T00:00:00Z"
     *   }
     * ]
     * ```
     *
     * @param accountId Account override; otherwise the client's default account is used.
     * @return Every endpoint of the account, oldest first.
     */
    suspend fun listEndpoints(accountId: String? = null): List<WebhookEndpoint> {
        val id = accountId(accountId)
        return callList("Failed to list webhook endpoints", WebhookEndpoint::class.java) {
            http.get(endpointsPath(id))
        }.data
    }

    /**
     * Registers a new URL to receive the account's webhook events. An account can have 1 endpoint,
     * or up to 3 on paid plans; one past the limit answers `403`, and a `url` another endpoint of
     * the workspace already uses answers `400`. With `signing_enabled: true` a secret is generated;
     * read it with [getEndpointSecret].
     *
     * Wire operation: `POST /v1/accounts/{accountId}/webhooks/endpoints`.
     *
     * Request body (`application/json`; `name`, `is_active` and `signing_enabled` are optional):
     * ```json
     * {
     *   "url": "https://example.com/webhooks/assinafy",
     *   "email": "ops@example.com",
     *   "events": ["document_ready", "signer_signed_document"],
     *   "name": "ERP",
     *   "is_active": true,
     *   "signing_enabled": true
     * }
     * ```
     *
     * Response 200 `data` payload (inside `{status,message,data}`) is the created endpoint:
     * ```json
     * {
     *   "id": "id-placeholder",
     *   "name": "ERP",
     *   "url": "https://example.com/webhooks/assinafy",
     *   "email": "ops@example.com",
     *   "events": ["document_ready", "signer_signed_document"],
     *   "is_active": true,
     *   "signing_enabled": true,
     *   "created_at": "2026-01-01T00:00:00Z",
     *   "updated_at": "2026-01-01T00:00:00Z"
     * }
     * ```
     *
     * @param request URL, contact email, events and optional label, state and signing flag.
     * @param accountId Account override; otherwise the client's default account is used.
     * @return The created endpoint.
     * @throws ValidationException on an invalid URL or email, or an empty event list.
     * @throws com.assinafy.sdk.exceptions.ApiException with status 403 when the plan's endpoint limit is reached.
     */
    suspend fun createEndpoint(request: CreateWebhookEndpointRequest, accountId: String? = null): WebhookEndpoint {
        val normalized = request.copy(
            url = requireHttpUrl(request.url),
            email = requireValidEmail(request.email, "Webhook email"),
            events = requireEvents(request.events),
        )
        val id = accountId(accountId)
        logger.info("Creating webhook endpoint", mapOf("eventCount" to normalized.events.size))
        return call("Failed to create webhook endpoint", WebhookEndpoint::class.java) {
            http.post(endpointsPath(id), toJson(normalized))
        }
    }

    /**
     * Fetches one webhook endpoint.
     *
     * Wire operation: `GET /v1/accounts/{accountId}/webhooks/endpoints/{endpointId}`.
     *
     * Request body: none.
     *
     * Response 200 `data` payload (inside `{status,message,data}`):
     * ```json
     * {
     *   "id": "id-placeholder",
     *   "name": "ERP",
     *   "url": "https://example.com/webhooks/assinafy",
     *   "email": "ops@example.com",
     *   "events": ["document_ready", "signer_signed_document"],
     *   "is_active": true,
     *   "signing_enabled": true,
     *   "created_at": "2026-01-01T00:00:00Z",
     *   "updated_at": "2026-01-01T00:00:00Z"
     * }
     * ```
     *
     * @param endpointId Endpoint identifier.
     * @param accountId Account override; otherwise the client's default account is used.
     * @return The endpoint, or `null` when the API returns HTTP 404.
     */
    suspend fun getEndpoint(endpointId: String, accountId: String? = null): WebhookEndpoint? {
        val path = endpointPath(accountId(accountId), endpointId)
        return callOptional("Failed to fetch webhook endpoint", WebhookEndpoint::class.java) { http.get(path) }
    }

    /**
     * Changes a webhook endpoint; only the non-null fields of [request] are sent and updated. A
     * `url` another endpoint of the workspace already uses answers `400`. `signing_enabled: true`
     * generates a secret when the endpoint has none and keeps it otherwise; `false` discards it.
     *
     * Wire operation: `PUT /v1/accounts/{accountId}/webhooks/endpoints/{endpointId}`.
     *
     * Request body (`application/json`; every member is optional, at least one is required):
     * ```json
     * {
     *   "url": "https://example.com/webhooks/assinafy",
     *   "email": "ops@example.com",
     *   "events": ["document_ready"],
     *   "name": "ERP",
     *   "is_active": false,
     *   "signing_enabled": true
     * }
     * ```
     *
     * Response 200 `data` payload (inside `{status,message,data}`) is the updated endpoint:
     * ```json
     * {
     *   "id": "id-placeholder",
     *   "name": "ERP",
     *   "url": "https://example.com/webhooks/assinafy",
     *   "email": "ops@example.com",
     *   "events": ["document_ready", "signer_signed_document"],
     *   "is_active": true,
     *   "signing_enabled": true,
     *   "created_at": "2026-01-01T00:00:00Z",
     *   "updated_at": "2026-01-01T00:00:00Z"
     * }
     * ```
     *
     * @param endpointId Endpoint identifier.
     * @param request Fields to change.
     * @param accountId Account override; otherwise the client's default account is used.
     * @return The updated endpoint.
     * @throws ValidationException when no field is supplied, or on an invalid URL, email or empty event list.
     */
    suspend fun updateEndpoint(
        endpointId: String,
        request: UpdateWebhookEndpointRequest,
        accountId: String? = null,
    ): WebhookEndpoint {
        if (request == UpdateWebhookEndpointRequest()) throw ValidationException("Webhook endpoint update requires at least one field")
        val normalized = request.copy(
            url = request.url?.let(::requireHttpUrl),
            email = request.email?.let { requireValidEmail(it, "Webhook email") },
            events = request.events?.let(::requireEvents),
        )
        val path = endpointPath(accountId(accountId), endpointId)
        return call("Failed to update webhook endpoint", WebhookEndpoint::class.java) {
            http.put(path, toJson(normalized))
        }
    }

    /**
     * Stops delivering events to an endpoint and frees its slot.
     *
     * Wire operation: `DELETE /v1/accounts/{accountId}/webhooks/endpoints/{endpointId}`.
     *
     * Request body: none.
     *
     * Response 200 `data` payload (inside `{status,message,data}`):
     * ```json
     * []
     * ```
     *
     * @param endpointId Endpoint identifier.
     * @param accountId Account override; otherwise the client's default account is used.
     */
    suspend fun deleteEndpoint(endpointId: String, accountId: String? = null) {
        val path = endpointPath(accountId(accountId), endpointId)
        logger.info("Deleting webhook endpoint")
        callVoid("Failed to delete webhook endpoint") { http.delete(path) }
    }

    /**
     * Returns the secret that signs deliveries to this endpoint; pass it to
     * [com.assinafy.sdk.support.WebhookVerifier]. Answers `400` when signing is disabled. Not
     * available to OAuth applications, so call it with an API key.
     *
     * Wire operation: `GET /v1/accounts/{accountId}/webhooks/endpoints/{endpointId}/secret`.
     *
     * Request body: none.
     *
     * Response 200 `data` payload (inside `{status,message,data}`):
     * ```json
     * {
     *   "secret": "whsec_<base64-key>"
     * }
     * ```
     *
     * @param endpointId Endpoint identifier.
     * @param accountId Account override; otherwise the client's default account is used.
     * @return The current signing secret.
     */
    suspend fun getEndpointSecret(endpointId: String, accountId: String? = null): WebhookEndpointSecret {
        val path = endpointPath(accountId(accountId), endpointId) + "/secret"
        return call("Failed to fetch webhook endpoint secret", WebhookEndpointSecret::class.java) { http.get(path) }
    }

    /**
     * Replaces the endpoint's signing secret and returns the new one. The old secret stops working
     * immediately, so update the receiver right away. Answers `400` when signing is disabled. Not
     * available to OAuth applications, so call it with an API key.
     *
     * Wire operation: `POST /v1/accounts/{accountId}/webhooks/endpoints/{endpointId}/secret/rotate`.
     *
     * Request body: none.
     *
     * Response 200 `data` payload (inside `{status,message,data}`):
     * ```json
     * {
     *   "secret": "whsec_<base64-key>"
     * }
     * ```
     *
     * @param endpointId Endpoint identifier.
     * @param accountId Account override; otherwise the client's default account is used.
     * @return The new signing secret.
     */
    suspend fun rotateEndpointSecret(endpointId: String, accountId: String? = null): WebhookEndpointSecret {
        val path = endpointPath(accountId(accountId), endpointId) + "/secret/rotate"
        logger.info("Rotating webhook endpoint secret")
        return call("Failed to rotate webhook endpoint secret", WebhookEndpointSecret::class.java) { http.post(path) }
    }

    private fun endpointsPath(accountId: String) = "/accounts/${pathSegment(accountId)}/webhooks/endpoints"

    private fun endpointPath(accountId: String, endpointId: String) =
        endpointsPath(accountId) + "/" + pathSegment(requireId(endpointId, "Endpoint ID"))

    private fun requireEvents(events: List<String>): List<String> {
        if (events.isEmpty() || events.any(String::isBlank)) throw ValidationException("Webhook events must be a nonempty list of event IDs")
        return events
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
