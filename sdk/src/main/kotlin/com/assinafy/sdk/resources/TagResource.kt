package com.assinafy.sdk.resources

import com.assinafy.sdk.Logger
import com.assinafy.sdk.NoOpLogger
import com.assinafy.sdk.http.ApiHttpClient
import com.assinafy.sdk.models.Tag

/**
 * Workspace-scoped tag management. Tags are case-insensitive labels (unique per workspace) that
 * can be attached to documents and templates. Attaching/detaching tags on a specific document is
 * done via [DocumentResource.addTags], [DocumentResource.replaceTags] and [DocumentResource.detachTag].
 */
class TagResource(
    http: ApiHttpClient,
    defaultAccountId: String? = null,
    logger: Logger = NoOpLogger,
) : BaseResource(http, defaultAccountId, logger) {

    /** Lists workspace tags, ordered alphabetically, optionally filtered by a case-insensitive [search]. */
    suspend fun list(search: String? = null, accountId: String? = null): List<Tag> {
        val accId = accountId(accountId)
        val query = buildMap<String, Any?> { search?.takeIf { it.isNotBlank() }?.let { put("search", it) } }
        val result = callList("Failed to list tags", Tag::class.java) {
            http.get("/accounts/${pathSegment(accId)}/tags", query)
        }
        return result.data
    }

    /** Creates a tag. [color] is an optional 6-char hex value (with or without leading `#`). */
    suspend fun create(name: String, color: String? = null, accountId: String? = null): Tag {
        val accId = accountId(accountId)
        val tagName = requireId(name, "Tag name")
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
        val body = buildMap<String, Any?> {
            name?.takeIf { it.isNotBlank() }?.let { put("name", it) }
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
     */
    suspend fun delete(tagId: String, force: Boolean = false, accountId: String? = null) {
        val accId = accountId(accountId)
        val id = requireId(tagId, "Tag ID")
        val query = if (force) queryString("force" to "true") else ""
        callVoid("Failed to delete tag") {
            http.delete("/accounts/${pathSegment(accId)}/tags/${pathSegment(id)}$query")
        }
    }
}
