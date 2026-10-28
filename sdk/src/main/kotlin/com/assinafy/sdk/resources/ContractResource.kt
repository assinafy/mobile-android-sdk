package com.assinafy.sdk.resources

import com.assinafy.sdk.Logger
import com.assinafy.sdk.NoOpLogger
import com.assinafy.sdk.exceptions.AssinafyException
import com.assinafy.sdk.exceptions.ValidationException
import com.assinafy.sdk.http.ApiHttpClient
import com.assinafy.sdk.http.HttpRawResponse
import com.assinafy.sdk.models.ContractRecord
import com.assinafy.sdk.models.DocumentContract
import com.assinafy.sdk.models.DocumentContractAmendment
import com.assinafy.sdk.models.PaginatedResult
import com.assinafy.sdk.request.ContractListParams
import com.assinafy.sdk.request.RenewContractRequest
import com.assinafy.sdk.request.TerminateContractRequest
import com.assinafy.sdk.request.UpdateContractRequest
import com.assinafy.sdk.util.ContractValidation
import com.assinafy.sdk.util.ResponseHandler
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Document-scoped contract tracking. Reads need documents:read; writes need documents:write and
 * a plan with contract tracking. Upload the document first; a failed terms write leaves it intact.
 * Lifecycle processing uses Brasilia calendar days. HTTP 400 exposes field/code/message entries in
 * ApiException.responseData; stale updated_at preconditions return 409. Mutations are never replayed.
 */
class ContractResource internal constructor(
    http: ApiHttpClient,
    defaultAccountId: String? = null,
    logger: Logger = NoOpLogger,
) : BaseResource(http, defaultAccountId, logger) {

    /**
     * Lists contracts, most recently updated first by default.
     *
     * Filters include status (comma-separated), counterparty, search, end_date_from/to, ending_within_days,
     * external_system/id, sort, expand=document, page and per-page. X-Pagination-* headers supply metadata.
     * For reconciliation, page by updated_at until records predate the last sync.
     *
     * Wire operation: `GET /v1/accounts/{accountId}/contracts`.
     *
     * Request body: none.
     *
     * Response 200 `data`, with optional document expansion (inside `{status,message,data}`):
     * ```json
     * [
     *   {
     *     "resource": "document_contract",
     *     "document_id": "document-placeholder",
     *     "status": "active",
     *     "counterparty_name": "Acme Ltda",
     *     "counterparty_taxpayer_id": "11222333000181",
     *     "value": 120000.5,
     *     "currency": "BRL",
     *     "start_date": "2026-11-01",
     *     "end_date": "2027-10-31",
     *     "auto_renew": true,
     *     "renewal_term_months": 12,
     *     "notice_period_days": 30,
     *     "term": 1,
     *     "owner_id": "owner-placeholder",
     *     "metadata": {
     *       "crm_stage": "won"
     *     },
     *     "external_references": [
     *       {
     *         "system": "hubspot",
     *         "id": "98765"
     *       }
     *     ],
     *     "created_at": "2026-10-23T12:00:00Z",
     *     "updated_at": "2026-10-23T12:00:00Z",
     *     "document": {
     *       "id": "document-placeholder",
     *       "account_id": "document-placeholder",
     *       "template_id": null,
     *       "name": "Contrato de prestação de serviços.pdf",
     *       "status": "certificated",
     *       "artifacts": {
     *         "original": "https://example.com/resource"
     *       },
     *       "is_closed": true,
     *       "signing_url": "https://example.com/resource",
     *       "verification_code": "document-placeholder",
     *       "verification_url": "https://example.com/resource",
     *       "decline_reason": null,
     *       "declined_by": null,
     *       "tags": [],
     *       "created_at": "2026-10-20T14:00:00Z",
     *       "updated_at": "2026-10-22T09:00:00Z"
     *     }
     *   }
     * ]
     * ```
     *
     * @param params Validated filters and optional document expansion.
     * @param accountId Workspace override; defaults to the configured workspace.
     * @return Contract page and pagination headers.
     */
    suspend fun list(params: ContractListParams = ContractListParams(), accountId: String? = null): PaginatedResult<DocumentContract> {
        val account = pathSegment(accountId(accountId))
        val query = params.toQueryMap()
        return callList("Failed to list contracts", DocumentContract::class.java) {
            http.get("/accounts/$account/contracts", query)
        }
    }

    /**
     * Reads a document's own contract terms or its amendment link.
     *
     * The concrete ContractRecord type distinguishes terms from an instrument. A missing contract returns
     * HTTP 404; reads remain available when the workspace plan lapses.
     *
     * Wire operation: `GET /v1/accounts/{accountId}/documents/{documentId}/contract`.
     *
     * Request body: none.
     *
     * Response 200 `data` for a root contract (inside `{status,message,data}`):
     * ```json
     * {
     *   "resource": "document_contract",
     *   "document_id": "document-placeholder",
     *   "status": "active",
     *   "counterparty_name": "Acme Ltda",
     *   "counterparty_taxpayer_id": "11222333000181",
     *   "value": 130000,
     *   "currency": "BRL",
     *   "start_date": "2026-11-01",
     *   "end_date": "2028-06-30",
     *   "auto_renew": true,
     *   "renewal_term_months": 12,
     *   "notice_period_days": 30,
     *   "term": 2,
     *   "owner_id": "owner-placeholder",
     *   "metadata": {
     *     "crm_stage": "won"
     *   },
     *   "external_references": [
     *     {
     *       "system": "hubspot",
     *       "id": "98765"
     *     }
     *   ],
     *   "created_at": "2026-10-23T12:00:00Z",
     *   "updated_at": "2027-05-12T18:20:00Z",
     *   "amendments": [
     *     {
     *       "document_id": "document-placeholder",
     *       "document_name": "Aditivo 1 - Contrato de prestação de serviços.pdf",
     *       "parent_document_id": "document-placeholder",
     *       "relation_type": "amendment",
     *       "number": 1,
     *       "status": "applied",
     *       "document_status": "certificated",
     *       "apply_error": null,
     *       "currency": "BRL",
     *       "terms": {
     *         "value": 130000,
     *         "end_date": "2028-06-30"
     *       },
     *       "changes": {
     *         "value": {
     *           "from": 120000.5,
     *           "to": 130000
     *         },
     *         "end_date": {
     *           "from": "2027-10-31",
     *           "to": "2028-06-30"
     *         },
     *         "term": {
     *           "from": 1,
     *           "to": 2
     *         }
     *       },
     *       "applied_at": "2027-05-12T18:20:00Z",
     *       "created_at": "2027-05-10T15:00:00Z",
     *       "updated_at": "2027-05-12T18:20:00Z"
     *     }
     *   ]
     * }
     * ```
     *
     * Alternative response 200 `data` for an instrument (inside `{status,message,data}`):
     * ```json
     * {
     *   "resource": "document_contract_amendment",
     *   "document_id": "document-placeholder",
     *   "document_name": "Aditivo 1 - Contrato de prestação de serviços.pdf",
     *   "parent_document_id": "document-placeholder",
     *   "relation_type": "amendment",
     *   "number": null,
     *   "status": "pending",
     *   "document_status": "pending_signature",
     *   "apply_error": null,
     *   "currency": "BRL",
     *   "terms": {
     *     "value": 130000,
     *     "end_date": "2028-06-30"
     *   },
     *   "changes": {
     *     "value": {
     *       "from": 120000.5,
     *       "to": 130000
     *     },
     *     "end_date": {
     *       "from": "2027-10-31",
     *       "to": "2028-06-30"
     *     },
     *     "term": {
     *       "from": 1,
     *       "to": 2
     *     }
     *   },
     *   "applied_at": null,
     *   "created_at": "2027-05-10T15:00:00Z",
     *   "updated_at": "2027-05-10T15:00:00Z"
     * }
     * ```
     *
     * @param documentId Contract or instrument document identifier.
     * @param accountId Workspace override.
     * @return DocumentContract or DocumentContractAmendment.
     */
    suspend fun get(documentId: String, accountId: String? = null): ContractRecord {
        val path = contractPath(documentId, accountId)
        return record { http.get(path) }
    }

    /**
     * Creates terms or changes only the supplied attributes.
     *
     * Use clearFields for explicit nulls and externalReferences for whole-list replacement. A pending
     * instrument null removes that term from its delta. Send parent_document_id and relation_type to link;
     * clear parent_document_id to unlink a pending instrument. Applied instruments cannot change.
     * An amendment may change the value, currency, dates, auto-renew, renewal/notice periods, counterparty
     * and metadata; a renewal only end_date/value; a termination only end_date. The API applies signed
     * instruments in certification order. Send the last-read updated_at to refuse stale writes with 409.
     * Link request example: {"parent_document_id":"parent-placeholder","relation_type":"amendment","value":130000}.
     *
     * Wire operation: `PUT /v1/accounts/{accountId}/documents/{documentId}/contract`.
     *
     * Request body (`application/json`; omit unchanged fields):
     * ```json
     * {
     *   "status": "active",
     *   "counterparty_name": "Acme Ltda",
     *   "counterparty_taxpayer_id": "11.222.333/0001-81",
     *   "value": 120000.5,
     *   "currency": "BRL",
     *   "start_date": "2026-11-01",
     *   "end_date": "2027-10-31",
     *   "auto_renew": true,
     *   "renewal_term_months": 12,
     *   "notice_period_days": 30,
     *   "owner_id": "owner-placeholder",
     *   "metadata": {
     *     "crm_stage": "won"
     *   },
     *   "external_references": [
     *     {
     *       "system": "hubspot",
     *       "id": "98765"
     *     }
     *   ],
     *   "updated_at": "2026-10-23T12:00:00Z"
     * }
     * ```
     *
     * Response 200 `data` for contract terms (inside `{status,message,data}`):
     * ```json
     * {
     *   "resource": "document_contract",
     *   "document_id": "document-placeholder",
     *   "status": "active",
     *   "counterparty_name": "Acme Ltda",
     *   "counterparty_taxpayer_id": "11222333000181",
     *   "value": 120000.5,
     *   "currency": "BRL",
     *   "start_date": "2026-11-01",
     *   "end_date": "2027-10-31",
     *   "auto_renew": true,
     *   "renewal_term_months": 12,
     *   "notice_period_days": 30,
     *   "term": 1,
     *   "owner_id": "owner-placeholder",
     *   "metadata": {
     *     "crm_stage": "won"
     *   },
     *   "external_references": [
     *     {
     *       "system": "hubspot",
     *       "id": "98765"
     *     }
     *   ],
     *   "created_at": "2026-10-23T12:00:00Z",
     *   "updated_at": "2026-10-23T12:00:00Z"
     * }
     * ```
     *
     * Alternative response 200 `data` for an instrument (inside `{status,message,data}`):
     * ```json
     * {
     *   "resource": "document_contract_amendment",
     *   "document_id": "document-placeholder",
     *   "document_name": "Aditivo 1 - Contrato de prestação de serviços.pdf",
     *   "parent_document_id": "document-placeholder",
     *   "relation_type": "amendment",
     *   "number": null,
     *   "status": "pending",
     *   "document_status": "pending_signature",
     *   "apply_error": null,
     *   "currency": "BRL",
     *   "terms": {
     *     "value": 130000,
     *     "end_date": "2028-06-30"
     *   },
     *   "changes": {
     *     "value": {
     *       "from": 120000.5,
     *       "to": 130000
     *     },
     *     "end_date": {
     *       "from": "2027-10-31",
     *       "to": "2028-06-30"
     *     },
     *     "term": {
     *       "from": 1,
     *       "to": 2
     *     }
     *   },
     *   "applied_at": null,
     *   "created_at": "2027-05-10T15:00:00Z",
     *   "updated_at": "2027-05-10T15:00:00Z"
     * }
     * ```
     *
     * @param documentId Existing document identifier.
     * @param request Partial terms, explicit clears or instrument link/delta.
     * @param accountId Workspace override.
     * @return Saved contract or instrument; no signing operation is performed.
     * @throws ValidationException for an empty update, conflicting clears or invalid field values.
     */
    suspend fun update(documentId: String, request: UpdateContractRequest, accountId: String? = null): ContractRecord {
        val path = contractPath(documentId, accountId)
        ContractValidation.update(request)
        val body = JsonParser.parseString(toJson(request)).asJsonObject
        request.clearFields.forEach { field ->
            if (body.has(field.wireValue)) throw ValidationException("Set or clear ${field.wireValue}, not both")
            body.add(field.wireValue, JsonNull.INSTANCE)
        }
        if (body.size() == 0) throw ValidationException("At least one contract field is required")
        return record { http.put(path, toJsonAllowNulls(body)) }
    }

    /**
     * Reads the root contract and every linked instrument from any document in its family.
     *
     * Applied instruments are ordered by number, followed by pending instruments. A missing family returns 404.
     *
     * Wire operation: `GET /v1/accounts/{accountId}/documents/{documentId}/contract/related`.
     *
     * Request body: none.
     *
     * Response 200 `data` (inside `{status,message,data}`):
     * ```json
     * {
     *   "resource": "document_contract",
     *   "document_id": "document-placeholder",
     *   "status": "active",
     *   "counterparty_name": "Acme Ltda",
     *   "counterparty_taxpayer_id": "11222333000181",
     *   "value": 130000,
     *   "currency": "BRL",
     *   "start_date": "2026-11-01",
     *   "end_date": "2028-06-30",
     *   "auto_renew": true,
     *   "renewal_term_months": 12,
     *   "notice_period_days": 30,
     *   "term": 2,
     *   "owner_id": "owner-placeholder",
     *   "metadata": {
     *     "crm_stage": "won"
     *   },
     *   "external_references": [
     *     {
     *       "system": "hubspot",
     *       "id": "98765"
     *     }
     *   ],
     *   "created_at": "2026-10-23T12:00:00Z",
     *   "updated_at": "2027-05-12T18:20:00Z",
     *   "amendments": [
     *     {
     *       "document_id": "document-placeholder",
     *       "document_name": "Aditivo 1 - Contrato de prestação de serviços.pdf",
     *       "parent_document_id": "document-placeholder",
     *       "relation_type": "amendment",
     *       "number": 1,
     *       "status": "applied",
     *       "document_status": "certificated",
     *       "apply_error": null,
     *       "currency": "BRL",
     *       "terms": {
     *         "value": 130000,
     *         "end_date": "2028-06-30"
     *       },
     *       "changes": {
     *         "value": {
     *           "from": 120000.5,
     *           "to": 130000
     *         },
     *         "end_date": {
     *           "from": "2027-10-31",
     *           "to": "2028-06-30"
     *         },
     *         "term": {
     *           "from": 1,
     *           "to": 2
     *         }
     *       },
     *       "applied_at": "2027-05-12T18:20:00Z",
     *       "created_at": "2027-05-10T15:00:00Z",
     *       "updated_at": "2027-05-12T18:20:00Z"
     *     }
     *   ]
     * }
     * ```
     *
     * @param documentId Root or instrument identifier.
     * @param accountId Workspace override.
     * @return Root contract with amendments.
     */
    suspend fun related(documentId: String, accountId: String? = null): DocumentContract {
        val path = contractPath(documentId, accountId)
        return call("Failed to read contract family", DocumentContract::class.java) { http.get("$path/related") }
    }

    /**
     * Starts the next term and reopens an expired contract.
     *
     * Supply the current term or an explicit end date later than the current one. With no end date the
     * server advances by renewal_term_months (12 when unset), until the date is in the future. Repeated
     * preconditions are refused, so a renewal cannot advance twice. Terminated contracts cannot renew.
     * Omit value to retain it; clearValue sends null. A stale updated_at returns 409.
     *
     * Wire operation: `POST /v1/accounts/{accountId}/documents/{documentId}/contract/renew`.
     *
     * Request body (`application/json`; omit unchanged fields):
     * ```json
     * {
     *   "term": 1,
     *   "end_date": "2029-06-30",
     *   "value": 130000,
     *   "updated_at": "2026-10-23T12:00:00Z"
     * }
     * ```
     *
     * Response 200 `data` (inside `{status,message,data}`):
     * ```json
     * {
     *   "resource": "document_contract",
     *   "document_id": "document-placeholder",
     *   "status": "active",
     *   "counterparty_name": "Acme Ltda",
     *   "counterparty_taxpayer_id": "11222333000181",
     *   "value": 130000,
     *   "currency": "BRL",
     *   "start_date": "2026-11-01",
     *   "end_date": "2029-06-30",
     *   "auto_renew": true,
     *   "renewal_term_months": 12,
     *   "notice_period_days": 30,
     *   "term": 2,
     *   "owner_id": "owner-placeholder",
     *   "metadata": {
     *     "crm_stage": "won"
     *   },
     *   "external_references": [
     *     {
     *       "system": "hubspot",
     *       "id": "98765"
     *     }
     *   ],
     *   "created_at": "2026-10-23T12:00:00Z",
     *   "updated_at": "2027-10-15T09:30:00Z"
     * }
     * ```
     *
     * @param documentId Root contract document identifier.
     * @param request Renewal preconditions and optional value.
     * @param accountId Workspace override.
     * @return Contract after renewal.
     */
    suspend fun renew(documentId: String, request: RenewContractRequest, accountId: String? = null): DocumentContract {
        val path = contractPath(documentId, accountId)
        ContractValidation.renew(request)
        val body = JsonParser.parseString(toJson(request)).asJsonObject
        if (request.clearValue) body.add("value", JsonNull.INSTANCE)
        return call("Failed to renew contract", DocumentContract::class.java) {
            http.post("$path/renew", toJsonAllowNulls(body))
        }
    }

    /**
     * Closes a contract and stops its lifecycle alerts.
     *
     * The reason is recorded in document_contract_terminated. The server strips HTML. A contract already
     * terminated is refused; stale updated_at returns 409. The signed document itself is preserved.
     *
     * Wire operation: `POST /v1/accounts/{accountId}/documents/{documentId}/contract/terminate`.
     *
     * Request body (`application/json`; omit unchanged fields):
     * ```json
     * {
     *   "reason": "Supplier replaced",
     *   "updated_at": "2026-10-23T12:00:00Z"
     * }
     * ```
     *
     * Response 200 `data` (inside `{status,message,data}`):
     * ```json
     * {
     *   "resource": "document_contract",
     *   "document_id": "document-placeholder",
     *   "status": "terminated",
     *   "counterparty_name": "Acme Ltda",
     *   "counterparty_taxpayer_id": "11222333000181",
     *   "value": 120000.5,
     *   "currency": "BRL",
     *   "start_date": "2026-11-01",
     *   "end_date": "2027-10-31",
     *   "auto_renew": true,
     *   "renewal_term_months": 12,
     *   "notice_period_days": 30,
     *   "term": 1,
     *   "owner_id": "owner-placeholder",
     *   "metadata": {
     *     "crm_stage": "won"
     *   },
     *   "external_references": [
     *     {
     *       "system": "hubspot",
     *       "id": "98765"
     *     }
     *   ],
     *   "created_at": "2026-10-23T12:00:00Z",
     *   "updated_at": "2027-03-02T14:00:00Z"
     * }
     * ```
     *
     * @param documentId Root contract document identifier.
     * @param request Optional reason and version precondition.
     * @param accountId Workspace override.
     * @return Terminated contract.
     */
    suspend fun terminate(
        documentId: String,
        request: TerminateContractRequest = TerminateContractRequest(),
        accountId: String? = null,
    ): DocumentContract {
        val path = contractPath(documentId, accountId)
        ContractValidation.terminate(request)
        return call("Failed to terminate contract", DocumentContract::class.java) { http.post("$path/terminate", toJson(request)) }
    }

    private fun contractPath(documentId: String, accountId: String?): String =
        "/accounts/${pathSegment(accountId(accountId))}/documents/${pathSegment(requireId(documentId, "Document ID"))}/contract"

    private suspend fun record(request: suspend () -> HttpRawResponse): ContractRecord {
        val data = call("Failed to read contract record", JsonObject::class.java, request)
        try {
            if (listOf("document_id", "status").any { data[it]?.isJsonPrimitive != true || data[it].asString.isBlank() }) {
                throw AssinafyException("Contract response is missing required fields")
            }
            return when (data["resource"]?.asString) {
                CONTRACT -> ResponseHandler.fromJson(data, DocumentContract::class.java)
                AMENDMENT -> {
                    if (listOf("parent_document_id", "relation_type").any { data[it]?.isJsonPrimitive != true || data[it].asString.isBlank() }) {
                        throw AssinafyException("Amendment response is missing required fields")
                    }
                    ResponseHandler.fromJson(data, DocumentContractAmendment::class.java)
                }
                else -> throw AssinafyException("Unknown contract response resource")
            }
        } catch (error: AssinafyException) {
            throw error
        } catch (error: Exception) {
            throw AssinafyException("Malformed contract response", cause = error)
        }
    }

    private companion object {
        const val CONTRACT = "document_contract"
        const val AMENDMENT = "document_contract_amendment"
    }
}
