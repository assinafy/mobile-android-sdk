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
     * Lists templates with `GET /accounts/{accountId}/templates`.
     *
     * Request body: none. Optional query: `search`, `page`, `per-page`. Response `data` is an array
     * of templates; a list entry omits the page/role detail a single template carries:
     * ```json
     * [{
     *   "resource": "template", "id": "fa88b732db84d01427d4cdd1092", "name": "template.pdf",
     *   "document_name": "Service agreement", "message": "Please review and sign",
     *   "status": "ready",
     *   "roles": [{ "id": "role-1", "name": "Editor", "assignment_type": "Editor",
     *               "created_at": "2026-05-14T12:00:00Z", "updated_at": "2026-05-14T12:00:00Z" }],
     *   "tags": [{ "id": "tag-1", "name": "Contracts" }],
     *   "default_document_tags": [{ "id": "tag-1", "name": "Contracts" }],
     *   "created_at": "2026-05-14T12:00:00Z", "updated_at": "2026-05-14T12:00:00Z"
     * }]
     * ```
     * `X-Pagination-*` headers are exposed through [PaginatedResult.meta].
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
     * Sends `GET /accounts/{accountId}/templates/{templateId}` with no request body. Response
     * `data` is the complete template, including every page with its placed fields:
     * ```json
     * {
     *   "resource": "template", "id": "fa88b732db84d01427d4cdd1092", "name": "template.pdf",
     *   "document_name": "Service agreement", "message": "Please review and sign",
     *   "status": "ready",
     *   "pages": [{
     *     "id": "page-1", "number": 1, "height": 2100, "width": 1275,
     *     "download_url": "https://api.assinafy.com.br/v1/documents/doc1/pages/page-1/download",
     *     "fields": [{
     *       "id": "tf-1", "field_id": "field-1", "role_id": "role-1", "label": "Signature",
     *       "display_settings": { "left": 72, "top": 640, "width": 180, "height": 40, "fontSize": 12 },
     *       "created_at": "2026-05-14T12:00:00Z", "updated_at": "2026-05-14T12:00:00Z"
     *     }]
     *   }],
     *   "roles": [{ "id": "role-1", "name": "Editor", "assignment_type": "Editor",
     *               "created_at": "2026-05-14T12:00:00Z", "updated_at": "2026-05-14T12:00:00Z" }],
     *   "tags": [{ "id": "tag-1", "name": "Contracts" }],
     *   "default_document_tags": [{ "id": "tag-1", "name": "Contracts" }],
     *   "created_at": "2026-05-14T12:00:00Z", "updated_at": "2026-05-14T12:00:00Z"
     * }
     * ```
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
