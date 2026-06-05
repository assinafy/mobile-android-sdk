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

/** Workspace (account) management. A "workspace" and an "account" are the same entity in the API. */
class WorkspaceResource(
    http: ApiHttpClient,
    defaultAccountId: String? = null,
    logger: Logger = NoOpLogger,
) : BaseResource(http, defaultAccountId, logger) {

    /** Creates a workspace (`POST /accounts`). */
    suspend fun create(request: CreateWorkspaceRequest): Workspace {
        if (request.name.isBlank()) {
            throw ValidationException("Workspace name is required")
        }
        return call("Failed to create workspace", Workspace::class.java) {
            http.post("/accounts", toJson(request))
        }
    }

    /** Lists the workspaces accessible to the credential (`GET /accounts`). */
    suspend fun list(): PaginatedResult<WorkspaceListItem> = callList("Failed to list workspaces", WorkspaceListItem::class.java) {
        http.get("/accounts")
    }

    /** Fetches a workspace by id (`GET /accounts/{accountId}`). */
    suspend fun get(accountId: String): Workspace {
        val id = requireId(accountId, "Account ID")
        return call("Failed to fetch workspace", Workspace::class.java) {
            http.get("/accounts/${pathSegment(id)}")
        }
    }

    /** Updates a workspace (`PUT /accounts/{accountId}`). */
    suspend fun update(accountId: String, request: UpdateWorkspaceRequest): Workspace {
        val id = requireId(accountId, "Account ID")
        if (request.name?.isBlank() == true) {
            throw ValidationException("Workspace name is required")
        }
        return call("Failed to update workspace", Workspace::class.java) {
            http.put("/accounts/${pathSegment(id)}", toJson(request))
        }
    }

    /** Deletes a workspace (`DELETE /accounts/{accountId}`). */
    suspend fun delete(accountId: String) {
        val id = requireId(accountId, "Account ID")
        callVoid("Failed to delete workspace") { http.delete("/accounts/${pathSegment(id)}") }
    }
}
