package com.assinafy.sdk.resources

import com.assinafy.sdk.Logger
import com.assinafy.sdk.NoOpLogger
import com.assinafy.sdk.exceptions.ValidationException
import com.assinafy.sdk.http.ApiHttpClient
import com.assinafy.sdk.models.PaginatedResult
import com.assinafy.sdk.models.Workspace
import com.assinafy.sdk.models.WorkspaceListItem
import com.assinafy.sdk.request.CreateWorkspaceRequest
import com.assinafy.sdk.request.UpdateWorkspaceRequest

class WorkspaceResource(
    http: ApiHttpClient,
    defaultAccountId: String? = null,
    logger: Logger = NoOpLogger,
) : BaseResource(http, defaultAccountId, logger) {

    suspend fun create(request: CreateWorkspaceRequest): Workspace {
        if (request.name.isBlank()) {
            throw ValidationException("Workspace name is required")
        }
        return call("Failed to create workspace", Workspace::class.java) {
            http.post("/accounts", toJson(request))
        }
    }

    suspend fun list(): PaginatedResult<WorkspaceListItem> = callList("Failed to list workspaces", WorkspaceListItem::class.java) {
        http.get("/accounts")
    }

    suspend fun get(accountId: String): Workspace {
        val id = requireId(accountId, "Account ID")
        return call("Failed to fetch workspace", Workspace::class.java) {
            http.get("/accounts/${pathSegment(id)}")
        }
    }

    suspend fun update(accountId: String, request: UpdateWorkspaceRequest): Workspace {
        val id = requireId(accountId, "Account ID")
        if (request.name?.isBlank() == true) {
            throw ValidationException("Workspace name is required")
        }
        return call("Failed to update workspace", Workspace::class.java) {
            http.put("/accounts/${pathSegment(id)}", toJson(request))
        }
    }

    suspend fun delete(accountId: String) {
        val id = requireId(accountId, "Account ID")
        callVoid("Failed to delete workspace") { http.delete("/accounts/${pathSegment(id)}") }
    }
}
