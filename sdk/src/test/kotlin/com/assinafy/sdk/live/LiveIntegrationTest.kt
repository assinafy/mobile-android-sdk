package com.assinafy.sdk.live

import com.assinafy.sdk.AssinafyClient
import com.assinafy.sdk.AssinafyClientConfig
import com.assinafy.sdk.SdkConstants
import com.assinafy.sdk.request.ListParams
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Opt-in end-to-end tests against a real Assinafy environment. They are **skipped by default** (via
 * JUnit assumptions) and only run when `ASSINAFY_API_KEY` and `ASSINAFY_ACCOUNT_ID` are set, so no
 * secrets live in the repo or CI. Point them at the sandbox:
 *
 * ```
 * ASSINAFY_API_KEY=... ASSINAFY_ACCOUNT_ID=... \
 *   ASSINAFY_BASE_URL=https://sandbox.assinafy.com.br/v1 \
 *   ./gradlew :sdk:testDebugUnitTest
 * ```
 *
 * Read-only checks always run when enabled. The upload→ready→delete write check additionally requires
 * `ASSINAFY_LIVE_WRITES=true`; it never creates an assignment, so it sends no signer emails.
 */
class LiveIntegrationTest {

    private val apiKey = System.getenv("ASSINAFY_API_KEY").orEmpty()
    private val accountId = System.getenv("ASSINAFY_ACCOUNT_ID").orEmpty()
    private val baseUrl = System.getenv("ASSINAFY_BASE_URL") ?: "https://sandbox.assinafy.com.br/v1"

    @BeforeEach
    fun requireCredentials() {
        assumeTrue(
            apiKey.isNotBlank() && accountId.isNotBlank(),
            "Set ASSINAFY_API_KEY and ASSINAFY_ACCOUNT_ID to run live integration tests",
        )
    }

    private fun client(): AssinafyClient = AssinafyClient.create(
        AssinafyClientConfig(apiKey = apiKey, accountId = accountId, baseUrl = baseUrl, timeoutMs = 60_000L),
    )

    @Test
    fun `read-only smoke test against the live API`() = runBlocking<Unit> {
        val client = client()

        val statuses = client.documents.getStatuses()
        assertThat(statuses).isNotEmpty
        assertThat(statuses.map { it.code }).contains("metadata_ready", "certificated")

        val workspace = client.workspaces.get(accountId)
        assertThat(workspace.id).isEqualTo(accountId)

        val signers = client.signers.list(ListParams(perPage = 5))
        // The call must succeed and every returned signer must carry an id (vacuously true if empty).
        assertThat(signers.data).allSatisfy { assertThat(it.id).isNotBlank() }

        val eventTypes = client.webhooks.listEventTypes()
        assertThat(eventTypes.map { it.id }).contains("document_ready")

        client.tags.list() // must not throw
    }

    @Test
    fun `upload, wait for ready, then delete a document`() = runBlocking<Unit> {
        assumeTrue(
            System.getenv("ASSINAFY_LIVE_WRITES") == "true",
            "Set ASSINAFY_LIVE_WRITES=true to run the write-path live test",
        )
        val client = client()

        val uploaded = client.documents.upload(MINIMAL_PDF, "sdk-live-test.pdf", mapOf("audit" to "true"))
        try {
            assertThat(uploaded.id).isNotBlank()
            val ready = client.documents.waitUntilReady(uploaded.id, maxWaitMs = 60_000L)
            assertThat(ready.status).isIn("metadata_ready", "pending_signature", "certificated")
        } finally {
            client.documents.delete(uploaded.id)
        }
    }

    private companion object {
        // A minimal valid single-page PDF.
        val MINIMAL_PDF: ByteArray = (
            "%PDF-1.4\n" +
                "1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n" +
                "2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n" +
                "3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]>>endobj\n" +
                "trailer<</Root 1 0 R>>\n%%EOF"
            ).toByteArray(Charsets.US_ASCII)

        init {
            // Reference the constant so build tooling never flags it as unused.
            check(MINIMAL_PDF.isNotEmpty())
            check(SdkConstants.DEFAULT_BASE_URL.isNotBlank())
        }
    }
}
