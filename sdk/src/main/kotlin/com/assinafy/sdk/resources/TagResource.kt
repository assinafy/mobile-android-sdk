package com.assinafy.sdk.resources

import com.assinafy.sdk.Logger
import com.assinafy.sdk.NoOpLogger
import com.assinafy.sdk.http.ApiHttpClient
import com.assinafy.sdk.models.Tag

/**
 * Workspace-scoped tag management. Tags are case-insensitive labels (unique per workspace) that
 * can be attached to documents and templates. Attaching/detaching tags on a specific document is
 * done via [DocumentResource.addTags], [DocumentResource.replaceTags] and [DocumentResource.detachTag].
 * API and transport failures use the SDK's typed exception hierarchy.
 */
class TagResource internal constructor(
    http: ApiHttpClient,
    defaultAccountId: String? = null,
    logger: Logger = NoOpLogger,
) : BaseResource(http, defaultAccountId, logger) {

    /**
     * Lists workspace tags, ordered alphabetically.
     *
     * @param search Optional case-insensitive partial name filter.
     * @param accountId Account override; otherwise the client's default account is used.
     * @return Matching tags.
     */
    suspend fun list(search: String? = null, accountId: String? = null): List<Tag> {
        val accId = accountId(accountId)
        val query = search?.takeIf { it.isNotBlank() }?.let { mapOf("search" to it) } ?: emptyMap()
        val result = callList("Failed to list tags", Tag::class.java) {
            http.get("/accounts/${pathSegment(accId)}/tags", query)
        }
        return result.data
    }

    /**
     * Creates a workspace tag.
     *
     * @param name Non-blank name, collapsed to single spaces and limited to 64 characters.
     * @param color Optional six-digit hexadecimal value, with or without a leading `#`.
     * @param accountId Account override; otherwise the client's default account is used.
     * @return Created tag.
     * @throws com.assinafy.sdk.exceptions.ValidationException for an invalid account, name, or color.
     */
    suspend fun create(name: String, color: String? = null, accountId: String? = null): Tag {
        val accId = accountId(accountId)
        val tagName = normalizeName(name)
        validateColor(color)
        val body = buildMap<String, Any?> {
            put("name", tagName)
            color?.let { put("color", it) }
        }
        return call("Failed to create tag", Tag::class.java) {
            http.post("/accounts/${pathSegment(accId)}/tags", toJson(body))
        }
    }

    /**
     * Updates a tag's [name] and/or [color]. Omit a parameter to leave it unchanged. Pass
     * [clearColor] = `true` to remove the color.
     *
     * @param tagId Stable tag identifier.
     * @param name Optional replacement name.
     * @param color Optional replacement six-digit hexadecimal color.
     * @param clearColor Whether to send an explicit JSON `null` and remove the current color.
     * @param accountId Account override; otherwise the client's default account is used.
     * @return Complete updated tag.
     * @throws com.assinafy.sdk.exceptions.ValidationException for an empty update or invalid input.
     */
    suspend fun update(
        tagId: String,
        name: String? = null,
        color: String? = null,
        clearColor: Boolean = false,
        accountId: String? = null,
    ): Tag {
        val accId = accountId(accountId)
        val id = requireId(tagId, "Tag ID")
        if (name == null && color == null && !clearColor) {
            throw com.assinafy.sdk.exceptions.ValidationException("At least one tag field is required")
        }
        val tagName = name?.let(::normalizeName)
        validateColor(color)
        val body = buildMap<String, Any?> {
            tagName?.let { put("name", it) }
            when {
                clearColor -> put("color", null)
                color != null -> put("color", color)
            }
        }
        return call("Failed to update tag", Tag::class.java) {
            http.put("/accounts/${pathSegment(accId)}/tags/${pathSegment(id)}", toJsonAllowNulls(body))
        }
    }

    /**
     * Deletes a tag. By default fails with a 409 if the tag is still attached to anything; pass
     * [force] = `true` to detach it from all documents/templates first.
     *
     * @param tagId Stable tag identifier.
     * @param force Whether the server should detach all uses before deletion.
     * @param accountId Account override; otherwise the client's default account is used.
     */
    suspend fun delete(tagId: String, force: Boolean = false, accountId: String? = null) {
        val accId = accountId(accountId)
        val id = requireId(tagId, "Tag ID")
        val query = if (force) queryString("force" to "true") else ""
        callVoid("Failed to delete tag") {
            http.delete("/accounts/${pathSegment(accId)}/tags/${pathSegment(id)}$query")
        }
    }

    private fun normalizeName(value: String): String {
        val name = requireId(value, "Tag name").replace(Regex("\\s+"), " ")
        if (name.length > 64) {
            throw com.assinafy.sdk.exceptions.ValidationException("Tag name must not exceed 64 characters")
        }
        return name
    }

    private fun validateColor(value: String?) {
        if (value != null && !COLOR.matches(value)) {
            throw com.assinafy.sdk.exceptions.ValidationException("Tag color must be a six-character hex value")
        }
    }

    /** Tag-value validation constants. */
    companion object {
        private val COLOR = Regex("^#?[0-9a-fA-F]{6}$")
    }
}
