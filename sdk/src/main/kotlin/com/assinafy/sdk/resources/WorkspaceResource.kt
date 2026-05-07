package com.assinafy.sdk.resources

import com.assinafy.sdk.Logger
import com.assinafy.sdk.NoOpLogger
import com.assinafy.sdk.http.ApiHttpClient
import com.assinafy.sdk.models.PaginatedResult
import com.assinafy.sdk.models.Workspace
import com.assinafy.sdk.models.WorkspaceListItem
import com.assinafy.sdk.request.CreateWorkspaceRequest
import com.assinafy.sdk.request.UpdateWorkspaceRequest
import com.assinafy.sdk.util.ResponseHandler

class WorkspaceResource(
    http: ApiHttpClient,
    defaultAccountId: String? = null,
    logger: Logger = NoOpLogger,
) : BaseResource(http, defaultAccountId, logger) {

    suspend fun create(request: CreateWorkspaceRequest): Workspace {
        return call("Failed to create workspace", Workspace::class.java) {
            http.post("/accounts", ResponseHandler.GSON.toJson(request))
        }
    }

    suspend fun list(): PaginatedResult<WorkspaceListItem> {
        return callList("Failed to list workspaces", WorkspaceListItem::class.java) {
            http.get("/accounts")
        }
    }

    suspend fun get(accountId: String): Workspace {
        val id = requireId(accountId, "Account ID")
        return call("Failed to fetch workspace", Workspace::class.java) {
            http.get("/accounts/$id")
        }
    }

    suspend fun update(accountId: String, request: UpdateWorkspaceRequest): Workspace {
        val id = requireId(accountId, "Account ID")
        return call("Failed to update workspace", Workspace::class.java) {
            http.put("/accounts/$id", ResponseHandler.GSON.toJson(request))
        }
    }

    suspend fun delete(accountId: String) {
        val id = requireId(accountId, "Account ID")
        callVoid("Failed to delete workspace") { http.delete("/accounts/$id") }
    }
}
