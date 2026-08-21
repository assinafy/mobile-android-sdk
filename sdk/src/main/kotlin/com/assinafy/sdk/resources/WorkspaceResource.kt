package com.assinafy.sdk.resources

import com.assinafy.sdk.Logger
import com.assinafy.sdk.NoOpLogger
import com.assinafy.sdk.exceptions.ValidationException
import com.assinafy.sdk.http.ApiHttpClient
import com.assinafy.sdk.models.AccountTheme
import com.assinafy.sdk.models.DocumentStatsRow
import com.assinafy.sdk.models.PaginatedResult
import com.assinafy.sdk.models.Workspace
import com.assinafy.sdk.models.WorkspaceListItem
import com.assinafy.sdk.request.CreateWorkspaceRequest
import com.assinafy.sdk.request.UpdateWorkspaceRequest

/**
 * Workspace (account) management. A "workspace" and an "account" are the same API entity.
 * API and transport failures use the SDK's typed exception hierarchy.
 */
class WorkspaceResource internal constructor(
    http: ApiHttpClient,
    defaultAccountId: String? = null,
    logger: Logger = NoOpLogger,
) : BaseResource(http, defaultAccountId, logger) {

    /**
     * Creates a workspace (`POST /accounts`).
     *
     * @param request Name, sender identity, and optional legacy branding colors.
     * @return Complete created account.
     * @throws ValidationException for a blank name, unsupported sender type, or invalid color.
     */
    suspend fun create(request: CreateWorkspaceRequest): Workspace {
        if (request.name.isBlank()) {
            throw ValidationException("Workspace name is required")
        }
        validateSenderType(request.notificationSenderType)
        @Suppress("DEPRECATION")
        validateColor(request.primaryColor, "Primary color")
        @Suppress("DEPRECATION")
        validateColor(request.secondaryColor, "Secondary color")
        val body = buildMap<String, Any> {
            put("name", request.name.trim())
            request.notificationSenderType?.let { put("notification_sender_type", it) }
            @Suppress("DEPRECATION")
            request.primaryColor?.let { put("primary_color", it) }
            @Suppress("DEPRECATION")
            request.secondaryColor?.let { put("secondary_color", it) }
        }
        return call("Failed to create workspace", Workspace::class.java) {
            http.post("/accounts", toJson(body))
        }
    }

    /**
     * Lists workspaces accessible to the credential (`GET /accounts`).
     *
     * @return Accounts and optional pagination-header metadata.
     */
    suspend fun list(): PaginatedResult<WorkspaceListItem> = callList("Failed to list workspaces", WorkspaceListItem::class.java) {
        http.get("/accounts")
    }

    /**
     * Fetches a workspace by ID (`GET /accounts/{accountId}`).
     *
     * @param accountId Stable account identifier.
     * @return Complete account record.
     */
    suspend fun get(accountId: String): Workspace {
        val id = requireId(accountId, "Account ID")
        return call("Failed to fetch workspace", Workspace::class.java) {
            http.get("/accounts/${pathSegment(id)}")
        }
    }

    /**
     * Updates a workspace (`PUT /accounts/{accountId}`).
     *
     * @param accountId Stable account identifier.
     * @param request Non-empty set of account fields to replace.
     * @return Complete updated account.
     * @throws ValidationException for blank IDs/names, empty updates, unsupported sender types, or invalid colors.
     */
    suspend fun update(accountId: String, request: UpdateWorkspaceRequest): Workspace {
        val id = requireId(accountId, "Account ID")
        if (request.name?.isBlank() == true) {
            throw ValidationException("Workspace name is required")
        }
        validateSenderType(request.notificationSenderType)
        @Suppress("DEPRECATION")
        validateColor(request.primaryColor, "Primary color")
        @Suppress("DEPRECATION")
        validateColor(request.secondaryColor, "Secondary color")
        val body = buildMap<String, Any> {
            request.name?.let { put("name", it.trim()) }
            request.notificationSenderType?.let { put("notification_sender_type", it) }
            @Suppress("DEPRECATION")
            request.primaryColor?.let { put("primary_color", it) }
            @Suppress("DEPRECATION")
            request.secondaryColor?.let { put("secondary_color", it) }
        }
        if (body.isEmpty()) throw ValidationException("At least one workspace field is required")
        return call("Failed to update workspace", Workspace::class.java) {
            http.put("/accounts/${pathSegment(id)}", toJson(body))
        }
    }

    /**
     * Deletes a workspace (`DELETE /accounts/{accountId}`).
     *
     * @param accountId Stable account identifier.
     * @param force Optional server-side force flag; omit it to use normal deletion safeguards.
     */
    suspend fun delete(accountId: String, force: Boolean? = null) {
        val id = requireId(accountId, "Account ID")
        callVoid("Failed to delete workspace") {
            http.delete("/accounts/${pathSegment(id)}", force?.let { toJson(mapOf("force" to it)) })
        }
    }

    /**
     * Fetches the account's branding theme (`GET /accounts/{accountId}/theme`).
     *
     * @param accountId Stable account identifier.
     * @return Account name, colors, and optional logo URL.
     */
    suspend fun getTheme(accountId: String): AccountTheme {
        val id = requireId(accountId, "Account ID")
        return call("Failed to fetch account theme", AccountTheme::class.java) {
            http.get("/accounts/${pathSegment(id)}/theme")
        }
    }

    /**
     * Downloads the account logo image as raw bytes (`GET /accounts/{accountId}/logo`).
     * Returns `null` when the account has no logo set (the API responds `404`).
     *
     * @param accountId Stable account identifier.
     * @return Unmodified image bytes, or `null` for HTTP 404.
     */
    suspend fun getLogo(accountId: String): ByteArray? {
        val id = requireId(accountId, "Account ID")
        return callBinaryOptional("Failed to download account logo") {
            http.getBinary("/accounts/${pathSegment(id)}/logo")
        }
    }

    /**
     * Uploads or replaces the account logo as a single multipart `file` part.
     *
     * @param accountId Stable account identifier.
     * @param fileData Non-empty image bytes.
     * @param fileName Non-blank multipart file name.
     * @param contentType Image MIME type, defaulting to `image/png`.
     * @throws ValidationException for blank identifiers/names, empty bytes, or a non-image MIME type.
     */
    suspend fun uploadLogo(
        accountId: String,
        fileData: ByteArray,
        fileName: String,
        contentType: String = "image/png",
    ) {
        val id = requireId(accountId, "Account ID")
        val name = requireId(fileName, "Logo file name")
        if (fileData.isEmpty()) throw ValidationException("Logo file data is empty")
        if (!contentType.startsWith("image/")) throw ValidationException("Logo content type must be an image")
        callVoid("Failed to upload account logo") {
            http.postMultipartFile("/accounts/${pathSegment(id)}/logo", "file", name, fileData, contentType)
        }
    }

    /**
     * Deletes the account's current logo.
     *
     * @param accountId Stable account identifier.
     */
    suspend fun deleteLogo(accountId: String) {
        val id = requireId(accountId, "Account ID")
        callVoid("Failed to delete account logo") { http.delete("/accounts/${pathSegment(id)}/logo") }
    }

    /**
     * Returns account document and signature KPIs.
     *
     * @param accountId Stable account identifier.
     * @param granularity `monthly`, `daily`, or `null` for the API default.
     * @param month Required `YYYY-MM` target when [granularity] is `daily`.
     * @return Zero-filled KPI periods returned by the API.
     * @throws ValidationException for a blank account, unsupported granularity, or invalid/missing month.
     */
    suspend fun getStats(
        accountId: String,
        granularity: String? = null,
        month: String? = null,
    ): List<DocumentStatsRow> {
        val id = requireId(accountId, "Account ID")
        if (granularity != null && granularity !in setOf("monthly", "daily")) {
            throw ValidationException("Stats granularity must be monthly or daily")
        }
        if (granularity == "daily" && month == null) {
            throw ValidationException("Month is required for daily statistics")
        }
        if (month != null && !MONTH.matches(month)) {
            throw ValidationException("Month must use YYYY-MM format")
        }
        return callList("Failed to fetch account statistics", DocumentStatsRow::class.java) {
            http.get(
                "/accounts/${pathSegment(id)}/stats",
                mapOf("granularity" to granularity, "month" to month),
            )
        }.data
    }

    private fun validateSenderType(value: String?) {
        if (value != null && value !in setOf("User", "Account")) {
            throw ValidationException("Notification sender type must be User or Account")
        }
    }

    private fun validateColor(value: String?, name: String) {
        if (value != null && !HEX_COLOR.matches(value)) {
            throw ValidationException("$name must be exactly six hexadecimal characters without #")
        }
    }

    /** Account-input validation constants. */
    companion object {
        private val HEX_COLOR = Regex("^[0-9a-fA-F]{6}$")
        private val MONTH = Regex("^\\d{4}-(0[1-9]|1[0-2])$")
    }
}
