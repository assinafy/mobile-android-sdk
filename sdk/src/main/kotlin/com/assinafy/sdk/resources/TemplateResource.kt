package com.assinafy.sdk.resources

import com.assinafy.sdk.Logger
import com.assinafy.sdk.NoOpLogger
import com.assinafy.sdk.http.ApiHttpClient
import com.assinafy.sdk.models.PaginatedResult
import com.assinafy.sdk.models.Template
import com.assinafy.sdk.models.TemplateListItem
import com.assinafy.sdk.request.ListParams

class TemplateResource(
    http: ApiHttpClient,
    defaultAccountId: String? = null,
    logger: Logger = NoOpLogger,
) : BaseResource(http, defaultAccountId, logger) {

    suspend fun list(params: ListParams = ListParams(), accountId: String? = null): PaginatedResult<TemplateListItem> {
        val id = accountId(accountId)
        return callList("Failed to list templates", TemplateListItem::class.java) {
            http.get("/accounts/$id/templates", params.toQueryMap())
        }
    }

    suspend fun get(templateId: String, accountId: String? = null): Template {
        val id = accountId(accountId)
        val tmplId = requireId(templateId, "Template ID")
        return call("Failed to fetch template", Template::class.java) {
            http.get("/accounts/$id/templates/$tmplId")
        }
    }
}
