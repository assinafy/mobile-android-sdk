package com.assinafy.sdk.live

import com.assinafy.sdk.AssinafyClient
import com.assinafy.sdk.AssinafyClientConfig
import com.assinafy.sdk.DocumentArtifact
import com.assinafy.sdk.DocumentStatus
import com.assinafy.sdk.SdkConstants
import com.assinafy.sdk.exceptions.ApiException
import com.assinafy.sdk.models.DocumentDetails
import com.assinafy.sdk.request.CreateAssignmentRequest
import com.assinafy.sdk.request.CreateDocumentFromTemplateRequest
import com.assinafy.sdk.request.CreateFieldRequest
import com.assinafy.sdk.request.CreateSignerRequest
import com.assinafy.sdk.request.FieldValidationEntry
import com.assinafy.sdk.request.ListParams
import com.assinafy.sdk.request.SignerReference
import com.assinafy.sdk.request.TemplateSigner
import com.assinafy.sdk.request.UpdateFieldRequest
import com.assinafy.sdk.request.UpdateNotificationPreferencesRequest
import com.assinafy.sdk.request.UpdateSignerRequest
import com.assinafy.sdk.request.UploadAndRequestSignaturesRequest
import com.assinafy.sdk.request.WebhookDispatchParams
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Opt-in end-to-end checks against an Assinafy sandbox. Base credentials enable the mandatory
 * read-only contract test; mutating tests additionally require `ASSINAFY_LIVE_WRITES=true` and use
 * only unique disposable records or state restored after each scenario.
 *
 * No secret or test identity is embedded in source. Optional signer-link checks require a
 * disposable `ASSINAFY_SIGNER_ACCESS_CODE`; tests that need existing documents or templates state
 * that fixture requirement through a JUnit assumption.
 */
class LiveIntegrationTest {

    private val apiKey = System.getenv("ASSINAFY_API_KEY").orEmpty()
    private val accountId = System.getenv("ASSINAFY_ACCOUNT_ID").orEmpty()
    private val baseUrl = System.getenv("ASSINAFY_BASE_URL") ?: "https://sandbox.assinafy.com.br/v1"
    private val liveRequired = System.getenv("ASSINAFY_REQUIRE_LIVE") == "true"
    private val writesEnabled = System.getenv("ASSINAFY_LIVE_WRITES") == "true"
    private val testEmail = System.getenv("ASSINAFY_TEST_EMAIL").orEmpty()
    private val secondTestEmail = System.getenv("ASSINAFY_TEST_EMAIL_2").orEmpty()
    private val signerAccessCode = System.getenv("ASSINAFY_SIGNER_ACCESS_CODE").orEmpty()

    @BeforeEach
    fun requireCredentials() {
        val credentialsSupplied = apiKey.isNotBlank() && accountId.isNotBlank()
        check(!liveRequired || credentialsSupplied) {
            "The required live suite is missing ASSINAFY_API_KEY or ASSINAFY_ACCOUNT_ID"
        }
        assumeTrue(
            credentialsSupplied,
            "Set ASSINAFY_API_KEY and ASSINAFY_ACCOUNT_ID to run live integration tests",
        )
    }

    private fun client(): AssinafyClient = AssinafyClient.create(
        AssinafyClientConfig(apiKey = apiKey, accountId = accountId, baseUrl = baseUrl, timeoutMs = 60_000L),
    )

    @Suppress("DEPRECATION")
    @Test
    fun `authenticated catalog and account reads match the live API`() = runBlocking<Unit> {
        val sdk = client()

        val workspaces = sdk.workspaces.list()
        assertThat(workspaces.data.any { it.id == accountId }).isTrue()
        assertThat(sdk.workspaces.get(accountId).id.isNotBlank()).isTrue()
        sdk.workspaces.getTheme(accountId)
        sdk.workspaces.getLogo(accountId)

        val documents = sdk.documents.list(ListParams(perPage = 5))
        assertThat(documents.data.all { it.id.isNotBlank() }).isTrue()
        sdk.documents.search(query = "sdk", perPage = 5)
        val statuses = sdk.documents.getStatuses()
        assertThat(statuses.isNotEmpty() && statuses.all { it.code.isNotBlank() }).isTrue()

        val signers = sdk.signers.list(ListParams(perPage = 5))
        assertThat(signers.data.all { it.id.isNotBlank() }).isTrue()
        val assignments = sdk.assignments.list(ListParams(perPage = 5), accountId)
        assertThat(assignments.data.all { it.id.isNotBlank() }).isTrue()

        val fields = sdk.fields.list(includeInactive = true, includeStandard = true)
        assertThat(fields.all { it.id.isNotBlank() }).isTrue()
        val fieldTypes = sdk.fields.listTypes()
        assertThat(fieldTypes.isNotEmpty() && fieldTypes.all { it.type.isNotBlank() }).isTrue()

        val templates = sdk.templates.list(ListParams(perPage = 5))
        assertThat(templates.data.all { it.id.isNotBlank() }).isTrue()
        sdk.tags.list()
        sdk.tags.list(search = "sdk")

        assertThat(sdk.users.getCurrent().id.isNotBlank()).isTrue()
        sdk.authentication.getApiKey()

        sdk.webhooks.get()
        val eventTypes = sdk.webhooks.listEventTypes()
        assertThat(eventTypes.isNotEmpty() && eventTypes.all { it.id.isNotBlank() }).isTrue()
        sdk.webhooks.listDispatches(ListParams(perPage = 5))
        sdk.webhooks.listDispatches(WebhookDispatchParams(perPage = 5))

        try {
            assertThat(sdk.documents.verify("sdk-live-invalid-${UUID.randomUUID()}").isValid).isFalse()
        } catch (error: ApiException) {
            assertThat(error.statusCode).isEqualTo(404)
        }
    }

    @Test
    fun `account statistics are readable when deployed in the sandbox`() = runBlocking<Unit> {
        val rows = sandboxEndpoint("Account statistics") { client().workspaces.getStats(accountId) }
        assertThat(rows.all { it.period.isNotBlank() }).isTrue()
    }

    @Test
    fun `user statistics are readable when deployed in the sandbox`() = runBlocking<Unit> {
        val rows = sandboxEndpoint("User statistics") { client().users.getStats() }
        assertThat(rows.all { it.period.isNotBlank() }).isTrue()
    }

    @Test
    fun `notification preferences are readable when deployed in the sandbox`() = runBlocking<Unit> {
        sandboxEndpoint("Notification preferences") { client().users.getNotificationPreferences() }
    }

    @Test
    fun `existing signer detail and email lookup are readable when present`() = runBlocking<Unit> {
        val sdk = client()
        val signer = sdk.signers.list(ListParams(perPage = 25)).data.firstOrNull()
        assumeTrue(signer != null, "Sandbox account needs an existing signer fixture")

        assertThat(sdk.signers.get(signer!!.id).id.isNotBlank()).isTrue()
        val signerWithEmail = sdk.signers.list(ListParams(perPage = 100)).data.firstOrNull { !it.email.isNullOrBlank() }
        assumeTrue(signerWithEmail != null, "Sandbox account needs an existing signer with an email fixture")
        assertThat(sdk.signers.findByEmail(signerWithEmail!!.email!!)?.id?.isNotBlank()).isTrue()
    }

    @Test
    fun `existing field validation reads are operational when present`() = runBlocking<Unit> {
        val sdk = client()
        val field = sdk.fields.list(includeInactive = true, includeStandard = true).firstOrNull()
        assumeTrue(field != null, "Sandbox account needs an existing field fixture")

        assertThat(sdk.fields.get(field!!.id).id.isNotBlank()).isTrue()
        sdk.fields.validate(field.id, null)
        val results = sdk.fields.validateMultiple(listOf(FieldValidationEntry(field.id, null)))
        assertThat(results.size).isEqualTo(1)
    }

    @Test
    fun `existing document projections are readable when present`() = runBlocking<Unit> {
        val sdk = client()
        val listed = sdk.documents.list(ListParams(perPage = 25)).data.firstOrNull()
        assumeTrue(listed != null, "Sandbox account needs an existing document fixture")

        val details = sdk.documents.details(listed!!.id)
        assertThat(details.id.isNotBlank()).isTrue()
        assertThat(sdk.documents.get(details.id).id.isNotBlank()).isTrue()
        sdk.documents.activities(details.id)
        sdk.documents.listTags(details.id)
        sdk.documents.getPublic(details.id)
        sdk.documents.isFullySigned(details.id)
        sdk.documents.getSigningProgress(details.id)
    }

    @Test
    fun `existing ready document artifacts are downloadable when present`() = runBlocking<Unit> {
        val sdk = client()
        var ready: DocumentDetails? = null
        for (document in sdk.documents.list(ListParams(perPage = 25)).data) {
            val details = sdk.documents.details(document.id)
            if (details.status in DocumentStatus.READY && !details.pages.isNullOrEmpty()) {
                ready = details
                break
            }
        }
        assumeTrue(ready != null, "Sandbox account needs a ready document with a rendered page fixture")

        val original = sdk.documents.download(ready!!.id, DocumentArtifact.ORIGINAL)
        assertThat(original.copyOf(PDF_MAGIC.size).contentEquals(PDF_MAGIC)).isTrue()
        assertThat(sdk.documents.thumbnail(ready.id).isNotEmpty()).isTrue()
        assertThat(sdk.documents.downloadPage(ready.id, requireNotNull(ready.pages).first().id).isNotEmpty()).isTrue()
    }

    @Test
    fun `template detail and estimate are operational when a template exists`() = runBlocking<Unit> {
        val sdk = client()
        val listed = sdk.templates.list(ListParams(perPage = 25)).data.firstOrNull()
        assumeTrue(listed != null, "Sandbox account needs an existing template fixture")

        val template = sdk.templates.get(listed!!.id)
        assertThat(template.id.isNotBlank()).isTrue()
        val roles = template.roles.orEmpty()
        assumeTrue(roles.isNotEmpty(), "Sandbox template fixture needs at least one signer role")
        sdk.documents.estimateCostFromTemplate(
            template.id,
            roles.map {
                TemplateSigner(
                    roleId = it.id,
                    verificationMethod = "Email",
                    notificationMethods = listOf("Email"),
                )
            },
        )
    }

    @Test
    fun `signer-link reads are operational with a disposable access code`() = runBlocking<Unit> {
        assumeTrue(
            signerAccessCode.isNotBlank(),
            "Set ASSINAFY_SIGNER_ACCESS_CODE for a disposable signer-link fixture",
        )
        val sdk = client()

        val signer = sdk.signerDocuments.self(signerAccessCode)
        assertThat(signer.id.isNotBlank()).isTrue()
        assertThat(sdk.signers.getSelf(signerAccessCode).id.isNotBlank()).isTrue()
        val documents = sdk.signerDocuments.list(signer.id, signerAccessCode, ListParams(perPage = 5))
        assumeTrue(documents.data.isNotEmpty(), "Signer access-code fixture needs at least one document")
        assertThat(sdk.signerDocuments.getCurrent(signer.id, signerAccessCode).id.isNotBlank()).isTrue()
        sdk.signerDocuments.search(signer.id, signerAccessCode, search = "sdk")
    }

    @Test
    fun `signer-link document artifact is downloadable when present`() = runBlocking<Unit> {
        assumeTrue(
            signerAccessCode.isNotBlank(),
            "Set ASSINAFY_SIGNER_ACCESS_CODE for a disposable signer-link fixture",
        )
        val sdk = client()
        val signer = sdk.signerDocuments.self(signerAccessCode)
        val documents = sdk.signerDocuments.list(signer.id, signerAccessCode, ListParams(perPage = 25))

        val downloadable = documents.data.firstOrNull { it.artifacts?.original != null }
        assumeTrue(downloadable != null, "Signer access-code fixture needs a document with an original artifact")
        assertThat(
            sdk.signerDocuments.download(signer.id, downloadable!!.id, DocumentArtifact.ORIGINAL)
                .copyOf(PDF_MAGIC.size)
                .contentEquals(PDF_MAGIC),
        ).isTrue()
    }

    @Test
    fun `signer-link signature is downloadable when present`() = runBlocking<Unit> {
        assumeTrue(
            signerAccessCode.isNotBlank(),
            "Set ASSINAFY_SIGNER_ACCESS_CODE for a disposable signer-link fixture",
        )
        val sdk = client()
        val signer = sdk.signerDocuments.self(signerAccessCode)
        assumeTrue(signer.hasSignature == true, "Signer access-code fixture needs a stored signature")

        assertThat(sdk.signerDocuments.downloadSignature(signerAccessCode).isNotEmpty()).isTrue()
        assertThat(sdk.signers.downloadSignature(signerAccessCode, "signature").isNotEmpty()).isTrue()
    }

    @Test
    fun `notification preference update is restored`() = runBlocking<Unit> {
        requireWrites()
        val sdk = client()
        val original = sandboxEndpoint("Notification preferences") { sdk.users.getNotificationPreferences() }

        reversible(
            block = {
                val updated = sdk.users.updateNotificationPreferences(
                    UpdateNotificationPreferencesRequest(documentCompleted = !original.documentCompleted),
                )
                assertThat(updated.documentCompleted).isNotEqualTo(original.documentCompleted)
            },
            cleanup = {
                sdk.users.updateNotificationPreferences(
                    UpdateNotificationPreferencesRequest(documentCompleted = original.documentCompleted),
                )
            },
        )
    }

    @Test
    fun `disposable field lifecycle is reversible`() = runBlocking<Unit> {
        requireWrites()
        val sdk = client()
        val suffix = UUID.randomUUID().toString().take(8)
        val fieldTypes = sdk.fields.listTypes()
        val fieldType = fieldTypes.firstOrNull { it.type !in NON_CUSTOM_FIELD_TYPES } ?: fieldTypes.first()
        val names = listOf("SDK live field $suffix", "SDK live field updated $suffix")
        var fieldId: String? = null

        reversible(
            block = {
                val created = sdk.fields.create(
                    CreateFieldRequest(name = names.first(), type = fieldType.type, isRequired = false),
                )
                fieldId = created.id
                assertThat(sdk.fields.get(created.id).id.isNotBlank()).isTrue()
                sdk.fields.validate(created.id, "sdk-live")
                val results = sdk.fields.validateMultiple(listOf(FieldValidationEntry(created.id, "sdk-live")))
                assertThat(results.size).isEqualTo(1)
                val updated = sdk.fields.update(
                    created.id,
                    UpdateFieldRequest(name = names.last()),
                )
                assertThat(updated.name.endsWith(suffix)).isTrue()
                assertThat(
                    sdk.fields.list(includeInactive = true, includeStandard = true).any { it.id == created.id },
                ).isTrue()
            },
            cleanup = {
                val ids = sdk.fields.list(includeInactive = true, includeStandard = true)
                    .filter { it.id == fieldId || it.name in names }
                    .map { it.id }
                    .toSet()
                cleanUpAll(*ids.map { id -> suspend { sdk.fields.delete(id) } }.toTypedArray())
            },
        )
    }

    @Test
    fun `disposable signer lifecycle is reversible`() = runBlocking<Unit> {
        requireWrites()
        val sdk = client()
        val suffix = UUID.randomUUID().toString().take(8)
        val names = listOf("SDK live signer $suffix", "SDK live signer updated $suffix")
        var signerId: String? = null

        reversible(
            block = {
                val created = sdk.signers.create(CreateSignerRequest(fullName = names.first()))
                signerId = created.id
                assertThat(sdk.signers.get(created.id).id.isNotBlank()).isTrue()
                val updated = sdk.signers.update(
                    created.id,
                    UpdateSignerRequest(fullName = names.last()),
                )
                assertThat(updated.fullName?.endsWith(suffix)).isTrue()
                assertThat(sdk.signers.list(ListParams(search = suffix, perPage = 5)).data.isNotEmpty()).isTrue()
            },
            cleanup = {
                val ids = sdk.signers.list(ListParams(search = suffix, perPage = 100)).data
                    .filter { it.id == signerId || it.fullName?.let(names::contains) == true }
                    .map { it.id }
                    .toSet()
                cleanUpAll(*ids.map { id -> suspend { sdk.signers.delete(id) } }.toTypedArray())
            },
        )
    }

    @Test
    fun `disposable document upload and rename lifecycle is reversible`() = runBlocking<Unit> {
        requireWrites()
        val sdk = client()
        val suffix = UUID.randomUUID().toString().take(8)
        val fileName = "sdk-live-$suffix.pdf"
        val renamedFileName = "sdk-live-renamed-$suffix.pdf"
        val documentIds = linkedSetOf<String>()

        reversible(
            block = {
                val uploaded = sdk.documents.upload(minimalPdf(suffix), fileName)
                documentIds += uploaded.id
                val ready = sdk.documents.waitUntilReady(uploaded.id)
                assertThat(ready.status in DocumentStatus.READY).isTrue()

                val renamed = sdk.documents.rename(ready.id, renamedFileName)
                assertThat(renamed.name).isEqualTo(renamedFileName)
                assertThat(
                    sdk.documents.download(renamed.id, DocumentArtifact.ORIGINAL)
                        .copyOf(PDF_MAGIC.size)
                        .contentEquals(PDF_MAGIC),
                ).isTrue()
                assertThat(sdk.documents.thumbnail(renamed.id).isNotEmpty()).isTrue()
                val page = sdk.documents.details(renamed.id).pages?.firstOrNull()
                assertThat(page != null).withFailMessage("Uploaded sandbox PDF did not produce a rendered page").isTrue()
                assertThat(sdk.documents.downloadPage(renamed.id, page!!.id).isNotEmpty()).isTrue()
            },
            cleanup = {
                recoverDocuments(sdk, documentIds, fileName, renamedFileName)
                    .forEach { deleteDocumentWhenReady(sdk, it) }
            },
        )
    }

    @Test
    fun `disposable upload assignment tag and notification lifecycle is reversible`() = runBlocking<Unit> {
        requireWritesAndEmails()
        val sdk = client()
        val suffix = UUID.randomUUID().toString().take(8)
        val fileName = "sdk-live-$suffix.pdf"
        val estimateFileName = "sdk-live-estimate-$suffix.pdf"
        val emails = listOf(testEmail, secondTestEmail)
        val signerNames = listOf("SDK live signer one $suffix", "SDK live signer two $suffix")
        val preexistingSignerIds = emails.map { sdk.signers.findByEmail(it)?.id }
        val documentIds = linkedSetOf<String>()
        var workflowSignerIds = emptyList<String>()
        var tagId: String? = null

        reversible(
            block = {
                workflowSignerIds = emails.indices.map { index ->
                    sdk.signers.create(CreateSignerRequest(signerNames[index], emails[index])).id
                }
                val signerRefs = workflowSignerIds.map { SignerReference.ofId(it) }
                val estimateDocument = sdk.documents.upload(minimalPdf("estimate-$suffix"), estimateFileName)
                documentIds += estimateDocument.id
                sdk.documents.waitUntilReady(estimateDocument.id)
                sdk.assignments.estimateCost(estimateDocument.id, CreateAssignmentRequest(signers = signerRefs))

                val workflow = sdk.uploadAndRequestSignatures(
                    UploadAndRequestSignaturesRequest(
                        fileData = minimalPdf(suffix),
                        fileName = fileName,
                        signers = emails.indices.map {
                            UploadAndRequestSignaturesRequest.SignerEntry(signerNames[it], emails[it])
                        },
                        message = "Automated sandbox SDK verification",
                        accountId = accountId,
                    ),
                )
                documentIds += workflow.document.id
                workflowSignerIds = workflow.signerIds
                assertThat(workflow.signerIds.size).isEqualTo(2)

                val document = sdk.documents.waitUntilReady(workflow.document.id)
                assertThat(document.status in DocumentStatus.READY).isTrue()

                assertThat(
                    sdk.documents.download(document.id, DocumentArtifact.ORIGINAL)
                        .copyOf(PDF_MAGIC.size)
                        .contentEquals(PDF_MAGIC),
                ).isTrue()
                assertThat(sdk.documents.thumbnail(document.id).isNotEmpty()).isTrue()
                val page = sdk.documents.details(document.id).pages?.firstOrNull()
                assertThat(page != null).withFailMessage("Uploaded sandbox PDF did not produce a rendered page").isTrue()
                assertThat(sdk.documents.downloadPage(document.id, page!!.id).isNotEmpty()).isTrue()
                sdk.documents.activities(document.id)
                sdk.documents.getPublic(document.id)
                sdk.documents.getSigningProgress(document.id)
                sdk.documents.isFullySigned(document.id)

                val assignment = workflow.assignment
                sdk.assignments.resetExpiration(document.id, assignment.id, null)
                sdk.assignments.listWhatsappNotifications(document.id, assignment.id)
                sdk.assignments.estimateResendCost(document.id, assignment.id, workflow.signerIds.first())
                sdk.assignments.resendNotification(document.id, assignment.id, workflow.signerIds.first(), "email")
                sdk.documents.sendToken(document.id, testEmail, "email")

                val tag = sdk.tags.create("SDK live tag $suffix", "336699")
                tagId = tag.id
                val updatedTag = sdk.tags.update(tag.id, name = "SDK live tag updated $suffix", color = "993366")
                assertThat(sdk.tags.list(search = suffix).any { it.id == tag.id }).isTrue()
                sdk.documents.addTags(document.id, listOf(updatedTag.name))
                assertThat(sdk.documents.listTags(document.id).any { it.id == tag.id }).isTrue()
                sdk.documents.replaceTags(document.id, listOf(updatedTag.name))
                assertThat(sdk.documents.listTags(document.id).any { it.id == tag.id }).isTrue()
                sdk.documents.detachTag(document.id, tag.id)
                sdk.documents.replaceTags(document.id, emptyList())
                assertThat(sdk.documents.listTags(document.id)).isEmpty()
            },
            cleanup = {
                cleanUpAll(
                    {
                        val tagIds = sdk.tags.list(search = suffix)
                            .filter {
                                it.id == tagId ||
                                    it.name == "SDK live tag $suffix" ||
                                    it.name == "SDK live tag updated $suffix"
                            }
                            .map { it.id }
                            .toSet()
                        tagIds.forEach { tag ->
                            documentIds.forEach { document ->
                                runCatching { sdk.documents.detachTag(document, tag) }
                            }
                            sdk.tags.delete(tag, force = true)
                        }
                    },
                    {
                        recoverDocuments(sdk, documentIds, fileName, estimateFileName)
                            .forEach { deleteDocumentWhenReady(sdk, it) }
                    },
                    {
                        emails.indices.forEach { index ->
                            if (preexistingSignerIds[index] == null) {
                                val signer = workflowSignerIds.getOrNull(index)?.let { id ->
                                    runCatching { sdk.signers.get(id) }.getOrNull()
                                } ?: sdk.signers.findByEmail(emails[index])
                                if (signer?.fullName == signerNames[index]) sdk.signers.delete(signer.id)
                            }
                        }
                    },
                )
            },
        )
    }

    @Test
    fun `template document creation is reversible when a compatible template exists`() = runBlocking<Unit> {
        requireWritesAndEmails()
        val sdk = client()
        val listedTemplate = sdk.templates.list(ListParams(perPage = 25)).data.firstOrNull()
        assumeTrue(listedTemplate != null, "Sandbox account needs an existing template fixture")
        val template = sdk.templates.get(listedTemplate!!.id)
        assumeTrue(
            !template.roles.isNullOrEmpty() && template.roles!!.size <= 2,
            "Sandbox account needs a compatible template fixture with one or two signer roles",
        )
        val suffix = UUID.randomUUID().toString().take(8)
        val emails = listOf(testEmail, secondTestEmail)
        val signerNames = listOf("SDK template signer one $suffix", "SDK template signer two $suffix")
        val preexistingSignerIds = emails.map { sdk.signers.findByEmail(it)?.id }
        val resolvedSignerIds = mutableListOf<String>()
        val documentName = "SDK live template document $suffix"
        var documentId: String? = null

        reversible(
            block = {
                template.roles!!.indices.forEach { index ->
                    resolvedSignerIds += sdk.signers.create(
                        CreateSignerRequest(fullName = signerNames[index], email = emails[index]),
                    ).id
                }
                val signers = template.roles!!.mapIndexed { index, role ->
                    TemplateSigner(
                        roleId = role.id,
                        id = resolvedSignerIds[index],
                        verificationMethod = "Email",
                        notificationMethods = listOf("Email"),
                        step = 1,
                    )
                }
                sdk.documents.estimateCostFromTemplate(template.id, signers)
                val created = sdk.documents.createFromTemplate(
                    template.id,
                    signers,
                    CreateDocumentFromTemplateRequest(
                        signers = signers,
                        name = documentName,
                        message = "Automated sandbox SDK verification",
                    ),
                )
                documentId = created.id
                assertThat(sdk.documents.waitUntilReady(created.id).id).isEqualTo(created.id)
            },
            cleanup = {
                cleanUpAll(
                    {
                        recoverDocuments(sdk, setOfNotNull(documentId), documentName)
                            .forEach { deleteDocumentWhenReady(sdk, it) }
                    },
                    {
                        template.roles!!.indices.forEach { index ->
                            if (preexistingSignerIds[index] == null) {
                                val signer = resolvedSignerIds.getOrNull(index)?.let { id ->
                                    runCatching { sdk.signers.get(id) }.getOrNull()
                                } ?: sdk.signers.findByEmail(emails[index])
                                if (signer?.fullName == signerNames[index]) sdk.signers.delete(signer.id)
                            }
                        }
                    },
                )
            },
        )
    }

    private fun requireWrites() {
        assumeTrue(writesEnabled, "Set ASSINAFY_LIVE_WRITES=true to run reversible sandbox write tests")
    }

    private fun requireWritesAndEmails() {
        requireWrites()
        assertThat(testEmail.isNotBlank() && secondTestEmail.isNotBlank())
            .withFailMessage("ASSINAFY_TEST_EMAIL and ASSINAFY_TEST_EMAIL_2 are required when live writes are enabled")
            .isTrue()
    }

    private suspend fun recoverDocuments(
        sdk: AssinafyClient,
        knownIds: Set<String>,
        vararg exactNames: String,
    ): Set<String> {
        val result = knownIds.toMutableSet()
        exactNames.forEach { name ->
            sdk.documents.search(query = name, perPage = 100).data
                .filter { it.name == name }
                .forEach { result += it.id }
        }
        return result
    }

    private suspend fun deleteDocumentWhenReady(sdk: AssinafyClient, documentId: String) {
        runCatching { sdk.documents.waitUntilReady(documentId) }
        sdk.documents.delete(documentId)
    }

    private suspend fun reversible(
        block: suspend () -> Unit,
        cleanup: suspend () -> Unit,
    ) {
        var failure: Throwable? = null
        try {
            block()
        } catch (caught: Throwable) {
            failure = caught
        }
        try {
            cleanup()
        } catch (caught: Throwable) {
            if (failure == null) failure = caught else failure?.addSuppressed(caught)
        }
        failure?.let { throw it }
    }

    private suspend fun cleanUpAll(vararg actions: suspend () -> Unit) {
        var failure: Throwable? = null
        actions.forEach { action ->
            try {
                action()
            } catch (caught: Throwable) {
                if (failure == null) failure = caught else failure?.addSuppressed(caught)
            }
        }
        failure?.let { throw it }
    }

    private suspend fun <T> sandboxEndpoint(
        name: String,
        block: suspend () -> T,
    ): T = try {
        block()
    } catch (error: ApiException) {
        assumeTrue(error.statusCode != 404, "$name is not deployed in this sandbox")
        throw error
    }

    private companion object {
        val PDF_MAGIC = "%PDF-".toByteArray(Charsets.US_ASCII)
        val NON_CUSTOM_FIELD_TYPES = setOf("signature", "initial", "signature_date")

        fun minimalPdf(marker: String): ByteArray {
            val objects = listOf(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] /Contents 4 0 R >>",
                "<< /Length 0 >>\nstream\n\nendstream",
            )
            val pdf = StringBuilder("%PDF-1.4\n% sdk-live-$marker\n")
            val offsets = objects.mapIndexed { index, body ->
                pdf.length.also { pdf.append("${index + 1} 0 obj\n$body\nendobj\n") }
            }
            val xrefOffset = pdf.length
            pdf.append("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n")
            offsets.forEach { pdf.append(it.toString().padStart(10, '0')).append(" 00000 n \n") }
            pdf.append("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n")
            return pdf.toString().toByteArray(Charsets.US_ASCII)
        }

        init {
            check(minimalPdf("self-check").copyOf(PDF_MAGIC.size).contentEquals(PDF_MAGIC))
            check(SdkConstants.DEFAULT_BASE_URL.isNotBlank())
        }
    }
}
