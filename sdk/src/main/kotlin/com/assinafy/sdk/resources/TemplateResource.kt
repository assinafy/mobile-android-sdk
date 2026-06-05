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
 */
class TemplateResource(
    http: ApiHttpClient,
    defaultAccountId: String? = null,
    logger: Logger = NoOpLogger,
) : BaseResource(http, defaultAccountId, logger) {

    /** Lists templates (`GET /accounts/{accountId}/templates`), supporting `status`/`search`/`tags`/`sort`/`page`/`per-page`. */
    suspend fun list(params: ListParams = ListParams(), accountId: String? = null): PaginatedResult<TemplateListItem> {
        val id = accountId(accountId)
        return callList("Failed to list templates", TemplateListItem::class.java) {
            http.get("/accounts/${pathSegment(id)}/templates", params.toQueryMap())
        }
    }

    /** Fetches a template by id (`GET /accounts/{accountId}/templates/{templateId}`), including its signer `roles`. */
    suspend fun get(templateId: String, accountId: String? = null): Template {
        val id = accountId(accountId)
        val tmplId = requireId(templateId, "Template ID")
        return call("Failed to fetch template", Template::class.java) {
            http.get("/accounts/${pathSegment(id)}/templates/${pathSegment(tmplId)}")
        }
    }
}
