package com.assinafy.sdk.resources

import com.assinafy.sdk.Logger
import com.assinafy.sdk.NoOpLogger
import com.assinafy.sdk.exceptions.AssinafyException
import com.assinafy.sdk.exceptions.ValidationException
import com.assinafy.sdk.http.ApiHttpClient
import com.assinafy.sdk.models.AuthenticatedUser
import com.assinafy.sdk.models.DocumentStatsGranularity
import com.assinafy.sdk.models.DocumentStatsQuery
import com.assinafy.sdk.models.DocumentStatsRow
import com.assinafy.sdk.models.NotificationPreferences
import com.assinafy.sdk.request.UpdateNotificationPreferencesRequest

/**
 * Profile, cross-account statistics, and notification settings for the authenticated user.
 *
 * @param http Authenticated API transport.
 * @param defaultAccountId Optional default account retained for consistency with other resources.
 * @param logger SDK logger.
 */
class UserResource internal constructor(
    http: ApiHttpClient,
    defaultAccountId: String? = null,
    logger: Logger = NoOpLogger,
) : BaseResource(http, defaultAccountId, logger) {

    /**
     * Retrieves the authenticated profile with `GET /users/self`.
     *
     * Request body/query: none. Response `data`:
     * ```json
     * {
     *   "id":"user-1", "name":"Example User", "email":"user@example.com",
     *   "telephone":null, "government_id":null, "is_email_verified":true,
     *   "has_accepted_terms":true, "created_at":"2026-01-01T00:00:00Z",
     *   "to_be_deleted_at":null
     * }
     * ```
     * The older sandbox `{ "user": {...}, "accounts": [...] }` data variant is normalized to the same model.
     *
     * @return Complete authenticated-user profile.
     */
    suspend fun getCurrent(): AuthenticatedUser {
        val result = callMap("Failed to fetch current user") { http.get("/users/self") }
        val user = (result["user"] as? Map<*, *>) ?: result
        return AuthenticatedUser(
            id = user.requiredString("id"),
            name = user.requiredString("name"),
            email = user.requiredString("email"),
            telephone = user["telephone"] as? String,
            governmentId = user["government_id"] as? String,
            isEmailVerified = user.requiredBoolean("is_email_verified"),
            hasAcceptedTerms = user.requiredBoolean("has_accepted_terms"),
            createdAt = user.requiredString("created_at"),
            toBeDeletedAt = user["to_be_deleted_at"] as? String,
        )
    }

    /**
     * Retrieves cross-account KPIs with `GET /users/self/stats`.
     *
     * Query shape: optional `granularity=monthly|daily` and `month=YYYY-MM`; request body: none.
     * Response `data`:
     * ```json
     * [{
     *   "period":"2026-06", "documents_uploaded":42, "documents_sent":37,
     *   "signature_requests":61, "signature_requests_notification_email":55,
     *   "signature_requests_notification_whatsapp":18, "signature_requests_notification_bypass":3,
     *   "signature_requests_verification_email":48, "signature_requests_verification_whatsapp":6,
     *   "signature_requests_verification_bypass":3,
     *   "signature_requests_verification_digital_certificate":4,
     *   "signature_requests_viewed":44, "signature_requests_completed":52,
     *   "documents_certified":30
     * }]
     * ```
     *
     * @param query Monthly/daily granularity and optional target month.
     * @return Zero-filled KPI periods, most recent first.
     * @throws ValidationException when daily granularity lacks a month or month is not `YYYY-MM`.
     */
    suspend fun getStats(query: DocumentStatsQuery = DocumentStatsQuery()): List<DocumentStatsRow> {
        if (query.granularity == DocumentStatsGranularity.DAILY && query.month == null) {
            throw ValidationException("Month is required when granularity is daily")
        }
        if (query.month != null && !MONTH_PATTERN.matches(query.month)) {
            throw ValidationException("Month must use YYYY-MM format")
        }
        val params = buildMap<String, Any?> {
            query.granularity?.let { put("granularity", it.wireValue) }
            query.month?.let { put("month", it) }
        }
        return callList("Failed to fetch current-user document statistics", DocumentStatsRow::class.java) {
            http.get("/users/self/stats", params)
        }.data
    }

    /**
     * Retrieves settings with `GET /users/self/notification-preferences`.
     *
     * Request body/query: none. Response `data` always contains all nine booleans:
     * ```json
     * {
     *   "DocumentCompleted":true, "SignerDeclined":true, "DocumentCancelled":true,
     *   "DocumentAboutToExpire":true, "DocumentExpired":true, "DocumentExpirationReset":true,
     *   "DocumentProcessingFailed":true, "TemplateProcessingFailed":true,
     *   "SignerWhatsappFailed":true
     * }
     * ```
     *
     * @return Complete owner-facing email notification preferences.
     */
    suspend fun getNotificationPreferences(): NotificationPreferences = call(
        "Failed to fetch notification preferences",
        NotificationPreferences::class.java,
    ) { http.get("/users/self/notification-preferences") }

    /**
     * Merges settings with `PUT /users/self/notification-preferences`.
     *
     * Request body is a non-empty subset such as `{"DocumentCompleted":false,"SignerDeclined":true}`.
     * Omitted keys retain their current value. Response `data` is the complete nine-boolean map documented by
     * [getNotificationPreferences].
     *
     * @param request One or more owner-facing email preferences to merge.
     * @return Complete preferences after the merge.
     * @throws ValidationException when no preference is supplied.
     */
    suspend fun updateNotificationPreferences(
        request: UpdateNotificationPreferencesRequest,
    ): NotificationPreferences {
        if (!request.hasChanges()) throw ValidationException("At least one notification preference is required")
        return call("Failed to update notification preferences", NotificationPreferences::class.java) {
            http.put("/users/self/notification-preferences", toJson(request))
        }
    }

    private fun Map<*, *>.requiredString(key: String): String = this[key] as? String
        ?: throw AssinafyException("Failed to parse current user: missing $key")

    private fun Map<*, *>.requiredBoolean(key: String): Boolean = this[key] as? Boolean
        ?: throw AssinafyException("Failed to parse current user: missing $key")

    private companion object {
        val MONTH_PATTERN = Regex("^\\d{4}-(0[1-9]|1[0-2])$")
    }
}
