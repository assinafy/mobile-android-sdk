package com.assinafy.sdk.resources

import com.assinafy.sdk.Logger
import com.assinafy.sdk.NoOpLogger
import com.assinafy.sdk.http.ApiHttpClient
import com.assinafy.sdk.models.PaginatedResult
import com.assinafy.sdk.models.Template
import com.assinafy.sdk.models.TemplateListItem
import com.assinafy.sdk.request.ListParams

/**
 * Read access to document templates. Creating a document from a template lives on the document
 * resource ([com.assinafy.sdk.resources.DocumentResource.createFromTemplate]).
 * API and transport failures use the SDK's typed exception hierarchy.
 */
class TemplateResource internal constructor(
    http: ApiHttpClient,
    defaultAccountId: String? = null,
    logger: Logger = NoOpLogger,
) : BaseResource(http, defaultAccountId, logger) {

    /**
     * Lists templates (`GET /accounts/{accountId}/templates`).
     *
     * @param params Search and pagination values; unsupported common filters are ignored.
     * @param accountId Account override; otherwise the client's default account is used.
     * @return Matching templates and optional pagination-header metadata.
     */
    suspend fun list(params: ListParams = ListParams(), accountId: String? = null): PaginatedResult<TemplateListItem> {
        val id = accountId(accountId)
        val query = buildMap<String, Any> {
            params.search?.let { put("search", it) }
            params.page?.let { put("page", it) }
            params.perPage?.let { put("per-page", it) }
        }
        return callList("Failed to list templates", TemplateListItem::class.java) {
            http.get("/accounts/${pathSegment(id)}/templates", query)
        }
    }

    /**
     * Fetches a template by ID, including signer roles and page fields.
     *
     * This compatibility endpoint exists on deployed Assinafy environments even though the current
     * public OpenAPI document exposes only the account template list route.
     *
     * @param templateId Stable template identifier.
     * @param accountId Account override; otherwise the client's default account is used.
     * @return Complete template.
     */
    suspend fun get(templateId: String, accountId: String? = null): Template {
        val id = accountId(accountId)
        val tmplId = requireId(templateId, "Template ID")
        return call("Failed to fetch template", Template::class.java) {
            http.get("/accounts/${pathSegment(id)}/templates/${pathSegment(tmplId)}")
        }
    }
}
