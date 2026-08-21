package com.assinafy.sdk.resources

import com.assinafy.sdk.DocumentArtifact
import com.assinafy.sdk.SignatureType
import com.assinafy.sdk.exceptions.ValidationException
import com.assinafy.sdk.http.ApiHttpClient
import com.assinafy.sdk.models.DocumentDetails
import com.assinafy.sdk.models.PaginatedResult
import com.assinafy.sdk.models.Signer
import com.assinafy.sdk.models.SignerSelf
import com.assinafy.sdk.request.ConfirmSignerDataRequest
import com.assinafy.sdk.request.ListParams
import com.assinafy.sdk.request.SignAssignmentItemRequest
import com.assinafy.sdk.request.VerifySignerEmailRequest
import com.assinafy.sdk.util.requireValidEmail

private val SIGNER_ARTIFACT_NAMES = setOf(
    DocumentArtifact.ORIGINAL,
    DocumentArtifact.CERTIFICATED,
    DocumentArtifact.CERTIFICATE_PAGE,
    DocumentArtifact.PADES,
    DocumentArtifact.BUNDLE,
)

/**
 * Signer-facing document and signing operations from the Assinafy v1 API.
 *
 * The supplied [http] client must be auth-free: these endpoints authenticate with the
 * `signer-access-code` query parameter, except [download], whose OpenAPI security list is empty.
 * This separation prevents a workspace API key or bearer token from being sent to public signing
 * links or artifact URLs.
 *
 * @param http Auth-free transport configured with an Assinafy v1 base URL.
 */
class SignerDocumentResource internal constructor(
    http: ApiHttpClient,
) : BaseResource(http) {

    /**
     * Fetches the signer identified by an access code.
     *
     * Wire request: `GET /signers/self?signer-access-code={code}`, with no body or credential
     * header. The typed [SignerSelf] response represents:
     * ```json
     * {
     *   "resource": "signer", "id": "signer_123", "full_name": "Example Signer",
     *   "email": "signer@example.com", "whatsapp_phone_number": null,
     *   "has_accepted_terms": false, "has_signature": true, "has_initial": false,
     *   "is_signature_reusable": true
     * }
     * ```
     * Signature-state flags remain nullable because older sandbox responses can omit them.
     *
     * @param signerAccessCode One-time code from the signer's signing link.
     * @return The signer's identity and stored-signature state.
     */
    suspend fun self(signerAccessCode: String): SignerSelf =
        call("Failed to fetch signer profile", SignerSelf::class.java) {
            http.get(withSignerCode("/signers/self", signerAccessCode))
        }

    /**
     * Fetches the current document for a known signer.
     *
     * Wire request: `GET /signers/{signerId}/document?signer-access-code={code}`, with both path
     * and query values URL-encoded. The [DocumentDetails] response has the full document shape:
     * ```json
     * {
     *   "resource": "document", "id": "doc_123", "account_id": "account_123",
     *   "template_id": null, "name": "Agreement.pdf", "status": "pending_signature",
     *   "assignment": { "id": "assignment_123", "method": "collect", "signers": [],
     *     "copy_receivers": [], "items": [], "summary": null, "signing_urls": [] },
     *   "artifacts": { "original": "https://example.com/original.pdf" },
     *   "signing_url": "https://example.com/sign", "tags": [], "pages": [],
     *   "created_at": "2026-08-21T12:00:00Z", "updated_at": "2026-08-21T12:01:00Z",
     *   "is_closed": false, "decline_reason": null, "declined_by": null
     * }
     * ```
     *
     * @param signerId Signer ID placed in the URL path.
     * @param signerAccessCode One-time code sent only as `signer-access-code` in the query.
     * @return The current document, including any assignment and signer-visible items.
     */
    suspend fun getCurrent(signerId: String, signerAccessCode: String): DocumentDetails {
        val sid = requireId(signerId, "Signer ID")
        return call("Failed to fetch current signer document", DocumentDetails::class.java) {
            http.get(withSignerCode("/signers/${pathSegment(sid)}/document", signerAccessCode))
        }
    }

    /**
     * Opens the assignment as the signer sees it and records the document as viewed.
     *
     * Wire request: `GET /sign?signer-access-code={code}&has_accepted_terms={boolean}`. The terms
     * query is omitted when [hasAcceptedTerms] is null and preserves an explicit false. A `409`
     * means document preparation is still in progress. The [DocumentDetails] response contains the
     * same complete document shape as [getCurrent], with signer-facing assignment content such as:
     * ```json
     * {
     *   "id": "doc_123", "account_id": "account_123", "name": "Agreement.pdf",
     *   "status": "pending_signature", "artifacts": {}, "tags": [], "pages": [],
     *   "assignment": { "id": "assignment_123", "method": "collect",
     *     "signers": [{ "id": "signer_123", "verification_method": "Email" }],
     *     "items": [{ "id": "item_123", "completed": false }],
     *     "summary": { "signer_count": 1, "completed_count": 0 }, "signing_urls": [] },
     *   "created_at": "2026-08-21T12:00:00Z", "updated_at": "2026-08-21T12:01:00Z",
     *   "is_closed": false
     * }
     * ```
     *
     * @param signerAccessCode One-time signer code sent as a query parameter.
     * @param hasAcceptedTerms Optional terms-acceptance value sent as `has_accepted_terms`.
     * @return The document, pages, assignment, signers, and signer-visible assignment items.
     */
    suspend fun getAssignment(
        signerAccessCode: String,
        hasAcceptedTerms: Boolean? = null,
    ): DocumentDetails = call("Failed to fetch signer assignment", DocumentDetails::class.java) {
        http.get(
            withSignerCode(
                "/sign",
                signerAccessCode,
                "has_accepted_terms" to hasAcceptedTerms,
            ),
        )
    }

    /**
     * Submits signer field values and signs one assignment.
     *
     * Wire request:
     * `POST /documents/{documentId}/assignments/{assignmentId}?signer-access-code={code}` with a
     * JSON-array body. Every [SignAssignmentItemRequest] is emitted in the exact API shape:
     * ```json
     * [{"itemId":"item_123","fieldId":"field_123","pageId":"page_123","value":"Approved"}]
     * ```
     * The typed result preserves the operation-defined response object, for example
     * `{"signed": true}`, as a `Map<String, Any>` without inventing undocumented keys.
     *
     * @param documentId Document ID placed in the URL path.
     * @param assignmentId Assignment ID placed in the URL path.
     * @param signerAccessCode One-time signer code sent only in the query.
     * @param entries Non-empty assignment-item values sent as the complete JSON request body.
     * @return The API's signing-result object.
     */
    suspend fun sign(
        documentId: String,
        assignmentId: String,
        signerAccessCode: String,
        entries: List<SignAssignmentItemRequest>,
    ): Map<String, Any> {
        val did = requireId(documentId, "Document ID")
        val aid = requireId(assignmentId, "Assignment ID")
        if (entries.isEmpty()) throw ValidationException("At least one assignment item is required")
        entries.forEachIndexed { index, entry ->
            requireId(entry.itemId, "Item ID at index $index")
            requireId(entry.fieldId, "Field ID at index $index")
            requireId(entry.pageId, "Page ID at index $index")
        }
        return callMap("Failed to sign document") {
            http.post(
                withSignerCode(
                    "/documents/${pathSegment(did)}/assignments/${pathSegment(aid)}",
                    signerAccessCode,
                ),
                toJson(entries),
            )
        }
    }

    /**
     * Declines one assignment as the signer.
     *
     * Wire request:
     * `PUT /documents/{documentId}/assignments/{assignmentId}/reject?signer-access-code={code}`
     * with the complete JSON body `{"decline_reason":"Reason shown to the sender"}`. The API's
     * successful envelope is `{"status":200,"data":[]}` and is intentionally returned as [Unit].
     *
     * @param documentId Document ID placed in the URL path.
     * @param assignmentId Assignment ID placed in the URL path.
     * @param signerAccessCode One-time signer code sent only in the query.
     * @param declineReason Required free-text reason sent as `decline_reason`.
     */
    suspend fun decline(
        documentId: String,
        assignmentId: String,
        signerAccessCode: String,
        declineReason: String,
    ) {
        val did = requireId(documentId, "Document ID")
        val aid = requireId(assignmentId, "Assignment ID")
        val reason = requireId(declineReason, "Decline reason")
        callVoid("Failed to decline assignment") {
            http.put(
                withSignerCode(
                    "/documents/${pathSegment(did)}/assignments/${pathSegment(aid)}/reject",
                    signerAccessCode,
                ),
                toJson(mapOf("decline_reason" to reason)),
            )
        }
    }

    /**
     * Signs several virtual-method documents for the same signer.
     *
     * Wire request: `PUT /signers/documents/sign-multiple?signer-access-code={code}` with the
     * complete body `{"document_ids":["doc_1","doc_2"]}`. The documented success payload is an
     * empty data array, `{"status":200,"data":[]}`, so this function returns [Unit].
     *
     * @param documentIds Non-empty document-ID list serialized as `document_ids`.
     * @param signerAccessCode One-time signer code sent only in the query.
     */
    suspend fun signMultiple(documentIds: List<String>, signerAccessCode: String) {
        val ids = requireDocumentIds(documentIds)
        callVoid("Failed to sign multiple documents") {
            http.put(
                withSignerCode("/signers/documents/sign-multiple", signerAccessCode),
                toJson(mapOf("document_ids" to ids)),
            )
        }
    }

    /**
     * Declines several documents with one shared reason.
     *
     * Wire request: `PUT /signers/documents/decline-multiple?signer-access-code={code}` with the
     * complete body
     * `{"document_ids":["doc_1","doc_2"],"decline_reason":"Unfavorable terms"}`. The documented
     * `{"status":200,"data":[]}` success response is returned as [Unit].
     *
     * @param documentIds Non-empty document-ID list serialized as `document_ids`.
     * @param declineReason Required reason serialized as `decline_reason`.
     * @param signerAccessCode One-time signer code sent only in the query.
     */
    suspend fun declineMultiple(
        documentIds: List<String>,
        declineReason: String,
        signerAccessCode: String,
    ) {
        val ids = requireDocumentIds(documentIds)
        val reason = requireId(declineReason, "Decline reason")
        callVoid("Failed to decline multiple documents") {
            http.put(
                withSignerCode("/signers/documents/decline-multiple", signerAccessCode),
                toJson(mapOf("document_ids" to ids, "decline_reason" to reason)),
            )
        }
    }

    /**
     * Verifies the one-time password delivered to a signer.
     *
     * Wire request: `POST /verify?signer-access-code={code}`. [request] is the entire JSON body,
     * `{"verification-code":"123456"}`; the body never contains `signer-access-code`. The API's
     * bare success envelope is validated and returned as [Unit].
     *
     * @param signerAccessCode Signing-link access code sent only in the query.
     * @param request OTP body whose exact wire key is `verification-code`.
     */
    suspend fun verifyEmail(signerAccessCode: String, request: VerifySignerEmailRequest) {
        val otp = requireId(request.verificationCode, "Verification code")
        callVoid("Failed to verify signer email") {
            http.post(
                withSignerCode("/verify", signerAccessCode),
                toJson(request.copy(verificationCode = otp)),
            )
        }
    }

    /**
     * Confirms or corrects signer identity data before signing.
     *
     * Wire request:
     * `PUT /documents/{documentId}/signers/confirm-data?signer-access-code={code}`. Null fields are
     * omitted from the complete request body; a full request is:
     * ```json
     * {"full_name":"Example Signer","email":"signer@example.com",
     *  "government_id":"12345678900"}
     * ```
     * The [Signer] response represents `{"id":"signer_123","full_name":"Example Signer",
     * "email":"signer@example.com","whatsapp_phone_number":null,
     * "has_accepted_terms":true}`.
     *
     * @param documentId Document whose signer data is being confirmed.
     * @param signerAccessCode One-time signer code sent only in the query.
     * @param request Optional official identity fields sent as JSON. Deprecated compatibility
     * fields on the shared request type are deliberately not sent by this current endpoint.
     * @return The updated signer record.
     */
    suspend fun confirmData(
        documentId: String,
        signerAccessCode: String,
        request: ConfirmSignerDataRequest,
    ): Signer {
        val did = requireId(documentId, "Document ID")
        val body = buildMap {
            request.fullName?.let { put("full_name", requireId(it, "Full name")) }
            request.email?.let { put("email", requireValidEmail(it)) }
            request.governmentId?.let { put("government_id", requireId(it, "Government ID")) }
        }
        return call("Failed to confirm signer data", Signer::class.java) {
            http.put(
                withSignerCode(
                    "/documents/${pathSegment(did)}/signers/confirm-data",
                    signerAccessCode,
                ),
                toJson(body),
            )
        }
    }

    /**
     * Records the signer's terms acceptance.
     *
     * Wire request: `PUT /signers/accept-terms?signer-access-code={code}` with no JSON body. The
     * bare success envelope, typically `{"status":200,"message":"Terms accepted"}`, is validated
     * and returned as [Unit]. Putting the access code in a body would leave this request
     * unauthenticated, so this function always sends it in the query.
     *
     * @param signerAccessCode One-time signer code sent only as `signer-access-code`.
     */
    suspend fun acceptTerms(signerAccessCode: String) {
        callVoid("Failed to accept terms") {
            http.put(withSignerCode("/signers/accept-terms", signerAccessCode))
        }
    }

    /**
     * Uploads the signer's signature or initials as raw PNG bytes.
     *
     * Wire request: `POST /signature?signer-access-code={code}&type={type}&reuse={boolean}` with
     * `Content-Type: image/png` and [imageData] as the complete raw body (not JSON or multipart).
     * `type` defaults to `signature`; `reuse` is omitted when null. The bare success envelope is
     * validated and returned as [Unit].
     *
     * @param signerAccessCode One-time signer code sent only in the query.
     * @param imageData Non-empty PNG file bytes sent without multipart framing.
     * @param type Image kind, normally [SignatureType.SIGNATURE] or [SignatureType.INITIAL].
     * @param reuse Optional instruction to update the signer's reusable-signature preference.
     */
    suspend fun uploadSignature(
        signerAccessCode: String,
        imageData: ByteArray,
        type: String = SignatureType.SIGNATURE,
        reuse: Boolean? = null,
    ) {
        if (imageData.isEmpty()) throw ValidationException("Signature image data is empty")
        val imageType = requireId(type, "Signature type")
        callVoid("Failed to upload signer signature") {
            http.postSignature(
                withSignerCode(
                    "/signature",
                    signerAccessCode,
                    "type" to imageType,
                    "reuse" to reuse,
                ),
                imageData,
                "image/png",
            )
        }
    }

    /**
     * Downloads the signer's stored signature or initials.
     *
     * Wire request: `GET /signature/{type}?signer-access-code={code}` with no body. The path type
     * and access code are URL-encoded. The response is returned unchanged as PNG [ByteArray]
     * content; a missing stored image surfaces as the API's `404` exception.
     *
     * @param signerAccessCode One-time signer code sent only in the query.
     * @param type Stored image kind, normally [SignatureType.SIGNATURE] or [SignatureType.INITIAL].
     * @return Raw signature/initial image bytes.
     */
    suspend fun downloadSignature(
        signerAccessCode: String,
        type: String = SignatureType.SIGNATURE,
    ): ByteArray {
        val imageType = requireId(type, "Signature type")
        return callBinary("Failed to download signer signature") {
            http.getBinary(
                withSignerCode(
                    "/signature/${pathSegment(imageType)}",
                    signerAccessCode,
                ),
            )
        }
    }

    /**
     * Lists documents belonging to a signer.
     *
     * Wire request: `GET /signers/{signerId}/documents?signer-access-code={code}&page={page}`
     * `&per-page={perPage}`. Only the OpenAPI-documented pagination fields from [params] are sent.
     * The response body is a `data` array of full [DocumentDetails] values, for example
     * `{"data":[{"id":"doc_123","account_id":"account_123","name":"Agreement.pdf",
     * "status":"pending_signature","artifacts":{},"tags":[],"pages":[],
     * "is_closed":false}]}`. `X-Pagination-*` headers become [PaginatedResult.meta], such as
     * `{currentPage=1, perPage=25, total=1, lastPage=1}`.
     *
     * @param signerId Signer ID placed in the URL path.
     * @param signerAccessCode One-time signer code sent only in the query.
     * @param params Pagination; `page` is 1-based and `perPage` is between 1 and 100.
     * @return Full signer documents plus optional pagination metadata.
     */
    suspend fun list(
        signerId: String,
        signerAccessCode: String,
        params: ListParams = ListParams(),
    ): PaginatedResult<DocumentDetails> {
        val sid = requireId(signerId, "Signer ID")
        return callList("Failed to list signer documents", DocumentDetails::class.java) {
            http.get(
                withSignerCode(
                    "/signers/${pathSegment(sid)}/documents",
                    signerAccessCode,
                    "page" to params.page,
                    "per-page" to params.perPage,
                ),
            )
        }
    }

    /**
     * Searches documents belonging to a signer by name.
     *
     * Wire request:
     * `GET /signers/{signerId}/documents/search?signer-access-code={code}&search={term}`; the
     * `search` query is omitted when null or blank. The typed [PaginatedResult] contains the same
     * complete [DocumentDetails] elements as [list], for example
     * `{"data":[{"id":"doc_123","account_id":"account_123","name":"Agreement.pdf",
     * "status":"pending_signature","artifacts":{},"tags":[],"pages":[],
     * "is_closed":false}]}`, plus pagination metadata when the server returns pagination headers.
     *
     * @param signerId Signer ID placed in the URL path.
     * @param signerAccessCode One-time signer code sent only in the query.
     * @param search Optional free-text document-name query.
     * @return Matching full signer documents and any response pagination metadata.
     */
    suspend fun search(
        signerId: String,
        signerAccessCode: String,
        search: String? = null,
    ): PaginatedResult<DocumentDetails> {
        val sid = requireId(signerId, "Signer ID")
        val term = search?.trim()?.takeIf { it.isNotEmpty() }
        return callList("Failed to search signer documents", DocumentDetails::class.java) {
            http.get(
                withSignerCode(
                    "/signers/${pathSegment(sid)}/documents/search",
                    signerAccessCode,
                    "search" to term,
                ),
            )
        }
    }

    /**
     * Downloads an artifact from the documented public signer-link route.
     *
     * Wire request:
     * `GET /signers/{signerId}/documents/{documentId}/download/{artifactName}`. This operation has
     * an empty OpenAPI security list: it deliberately sends no signer access code, API key, bearer
     * token, query parameters, or body. [artifactName] accepts `original`, `certificated`,
     * `certificate-page`, `pades`, or `bundle`. The response is raw PDF bytes, except `bundle`,
     * which is a ZIP archive.
     *
     * @param signerId Signer ID placed in the public URL path.
     * @param documentId Document ID placed in the public URL path.
     * @param artifactName Artifact kind; defaults to [DocumentArtifact.ORIGINAL].
     * @return Unmodified PDF or ZIP response bytes.
     */
    suspend fun download(
        signerId: String,
        documentId: String,
        artifactName: String = DocumentArtifact.ORIGINAL,
    ): ByteArray {
        val sid = requireId(signerId, "Signer ID")
        val did = requireId(documentId, "Document ID")
        val artifact = requireId(artifactName, "Artifact name")
        if (artifact !in SIGNER_ARTIFACT_NAMES) {
            throw ValidationException(
                "Artifact name must be one of: ${SIGNER_ARTIFACT_NAMES.joinToString()}",
            )
        }
        return callBinary("Failed to download signer document artifact") {
            http.getBinary(
                "/signers/${pathSegment(sid)}/documents/${pathSegment(did)}/download/" +
                    pathSegment(artifact),
            )
        }
    }

    private fun withSignerCode(
        path: String,
        signerAccessCode: String,
        vararg query: Pair<String, Any?>,
    ): String = path + queryString(
        "signer-access-code" to requireId(signerAccessCode, "Signer access code"),
        *query,
    )

    private fun requireDocumentIds(documentIds: List<String>): List<String> {
        if (documentIds.isEmpty()) throw ValidationException("At least one document ID is required")
        return documentIds.mapIndexed { index, id -> requireId(id, "Document ID at index $index") }
    }
}
