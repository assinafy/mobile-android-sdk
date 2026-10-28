package com.assinafy.smoke

import android.app.Application
import com.assinafy.sdk.AssinafyClient
import com.assinafy.sdk.AssinafyClientConfig
import com.assinafy.sdk.request.CreateAssignmentRequest
import com.assinafy.sdk.request.ListParams
import com.assinafy.sdk.request.SignerReference

class SmokeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val client = AssinafyClient.create(AssinafyClientConfig(baseUrl = "http://127.0.0.1"))
        client.webhookVerifier.extractEvent("{\"event\":\"smoke\"}")
        CreateAssignmentRequest(signers = listOf(SignerReference.ofId("signer")))
    }

    @Suppress("unused")
    private suspend fun compilePublicApi(client: AssinafyClient) {
        client.documents.list(ListParams(perPage = 1))
        client.users.getCurrent()
        client.contracts.list(com.assinafy.sdk.request.ContractListParams(endingWithinDays = 30))
        client.contracts.get("document")
        client.contracts.update("document", com.assinafy.sdk.request.UpdateContractRequest(status = com.assinafy.sdk.ContractStatus.ACTIVE))
        client.contracts.related("document")
        client.contracts.renew("document", com.assinafy.sdk.request.RenewContractRequest(term = 1))
        client.contracts.terminate("document")
        client.webhooks.listEventTypes()
        client.webhooks.listEndpoints(url = "https://example.com/hook")
        client.signerDocuments.getIdentityVerification("signer-code")
        client.assignments.getIdentityVerification("document", "assignment", "signer")
    }
}
