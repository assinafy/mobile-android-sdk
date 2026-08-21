package com.assinafy.sdk.resources

import com.assinafy.sdk.Logger
import com.assinafy.sdk.NoOpLogger
import com.assinafy.sdk.exceptions.ValidationException
import com.assinafy.sdk.http.ApiHttpClient
import com.assinafy.sdk.models.FieldDefinition
import com.assinafy.sdk.models.FieldType
import com.assinafy.sdk.models.FieldValidationResult
import com.assinafy.sdk.request.CreateFieldRequest
import com.assinafy.sdk.request.FieldValidationEntry
import com.assinafy.sdk.request.UpdateFieldRequest

/**
 * Account-scoped field definitions and value validation, plus the global field-type catalogue.
 *
 * @param http Authenticated API transport.
 * @param defaultAccountId Account used when a method does not receive an explicit override.
 * @param logger SDK logger.
 */
class FieldResource internal constructor(
    http: ApiHttpClient,
    defaultAccountId: String? = null,
    logger: Logger = NoOpLogger,
) : BaseResource(http, defaultAccountId, logger) {

    /**
     * Creates a definition with `POST /accounts/{accountId}/fields`.
     *
     * Request body:
     * `{"name":"Employee CPF","type":"cpf","regex":null,"is_required":true}`.
     * Response `data`:
     * ```json
     * {
     *   "resource":"field", "id":"field-1", "name":"Employee CPF", "type":"cpf",
     *   "regex":null, "is_pre_defined":false, "is_active":true, "is_required":true,
     *   "is_standard":false, "is_read_only":false, "is_visible":true
     * }
     * ```
     *
     * @param request Required name/type and optional regex/required state.
     * @param accountId Account override; otherwise the resource default is used.
     * @return Created field definition.
     * @throws ValidationException when the account, name, or type is blank.
     */
    suspend fun create(request: CreateFieldRequest, accountId: String? = null): FieldDefinition {
        val id = accountId(accountId)
        val normalized = request.copy(
            name = requireId(request.name, "Field name"),
            type = requireId(request.type, "Field type"),
        )
        return call("Failed to create field definition", FieldDefinition::class.java) {
            http.post("/accounts/${pathSegment(id)}/fields", toJson(normalized))
        }
    }

    /**
     * Lists definitions with `GET /accounts/{accountId}/fields`.
     *
     * Query shape: optional `include_inactive` and `include_standard` booleans; request body: none.
     * Response `data` is an unpaginated array of complete [FieldDefinition] objects:
     * ```json
     * [{
     *   "resource":"field", "id":"field-1", "name":"CPF", "type":"cpf", "regex":null,
     *   "is_pre_defined":true, "is_active":true, "is_required":false,
     *   "is_standard":false, "is_read_only":false, "is_visible":true
     * }]
     * ```
     *
     * @param includeInactive Whether inactive definitions should be included.
     * @param includeStandard Whether built-in signature, initial, and signature-date definitions should be included.
     * @param accountId Account override; otherwise the resource default is used.
     * @return Field definitions matching the query.
     */
    suspend fun list(
        includeInactive: Boolean? = null,
        includeStandard: Boolean? = null,
        accountId: String? = null,
    ): List<FieldDefinition> {
        val id = accountId(accountId)
        val query = buildMap<String, Any?> {
            includeInactive?.let { put("include_inactive", it) }
            includeStandard?.let { put("include_standard", it) }
        }
        return callList("Failed to list field definitions", FieldDefinition::class.java) {
            http.get("/accounts/${pathSegment(id)}/fields", query)
        }.data
    }

    /**
     * Retrieves one definition with `GET /accounts/{accountId}/fields/{fieldId}`.
     *
     * Request body/query: none. Response `data` contains `resource`, `id`, `name`, `type`, nullable `regex`,
     * `is_pre_defined`, `is_active`, `is_required`, `is_standard`, `is_read_only`, and `is_visible`.
     *
     * @param fieldId Field definition identifier.
     * @param accountId Account override; otherwise the resource default is used.
     * @return Complete field definition.
     * @throws ValidationException when the account or field identifier is blank.
     */
    suspend fun get(fieldId: String, accountId: String? = null): FieldDefinition {
        val id = accountId(accountId)
        val fid = requireId(fieldId, "Field ID")
        return call("Failed to fetch field definition", FieldDefinition::class.java) {
            http.get("/accounts/${pathSegment(id)}/fields/${pathSegment(fid)}")
        }
    }

    /**
     * Updates a definition with `PUT /accounts/{accountId}/fields/{fieldId}`.
     *
     * Request body is a non-empty subset of `{"name":"New name","regex":null,"is_active":false}`.
     * Response `data` is the complete updated [FieldDefinition] shape documented by [create].
     *
     * @param fieldId Field definition identifier.
     * @param request Name, regex, explicit regex removal, and/or active-state changes.
     * @param accountId Account override; otherwise the resource default is used.
     * @return Updated field definition.
     * @throws ValidationException for a blank field ID/name, conflicting regex changes, or an empty update.
     */
    suspend fun update(
        fieldId: String,
        request: UpdateFieldRequest,
        accountId: String? = null,
    ): FieldDefinition {
        val id = accountId(accountId)
        val fid = requireId(fieldId, "Field ID")
        if (request.name?.isBlank() == true) throw ValidationException("Field name is required")
        if (request.clearRegex && request.regex != null) {
            throw ValidationException("Set regex or clearRegex, not both")
        }
        val body = buildMap<String, Any?> {
            request.name?.let { put("name", it.trim()) }
            when {
                request.clearRegex -> put("regex", null)
                request.regex != null -> put("regex", request.regex)
            }
            request.isActive?.let { put("is_active", it) }
        }
        if (body.isEmpty()) throw ValidationException("At least one field change is required")
        return call("Failed to update field definition", FieldDefinition::class.java) {
            http.put(
                "/accounts/${pathSegment(id)}/fields/${pathSegment(fid)}",
                toJsonAllowNulls(body),
            )
        }
    }

    /**
     * Deletes a definition with `DELETE /accounts/{accountId}/fields/{fieldId}`.
     *
     * Request body/query: none. Response is the standard success envelope without a typed `data` payload.
     * Definitions already used by assignments may need to be deactivated with [update] instead.
     *
     * @param fieldId Field definition identifier.
     * @param accountId Account override; otherwise the resource default is used.
     * @throws ValidationException when the account or field identifier is blank.
     */
    suspend fun delete(fieldId: String, accountId: String? = null) {
        val id = accountId(accountId)
        val fid = requireId(fieldId, "Field ID")
        callVoid("Failed to delete field definition") {
            http.delete("/accounts/${pathSegment(id)}/fields/${pathSegment(fid)}")
        }
    }

    /**
     * Validates one value with `POST /accounts/{accountId}/fields/{fieldId}/validate`.
     *
     * Request body: `{"value":"400.676.228-36"}`; request query: none.
     * Response `data`: `{"type":"cpf","success":true,"error_message":""}`.
     *
     * @param fieldId Field definition whose type/regex validates the value.
     * @param value JSON-compatible input value; the `value` key is retained when this is `null`.
     * @param accountId Account override; otherwise the resource default is used.
     * @return Type, success flag, and error message from the validator.
     * @throws ValidationException when the account or field identifier is blank.
     */
    suspend fun validate(
        fieldId: String,
        value: Any?,
        accountId: String? = null,
    ): FieldValidationResult {
        val id = accountId(accountId)
        val fid = requireId(fieldId, "Field ID")
        return call("Failed to validate field value", FieldValidationResult::class.java) {
            http.post(
                "/accounts/${pathSegment(id)}/fields/${pathSegment(fid)}/validate",
                toJsonAllowNulls(mapOf("value" to value)),
            )
        }
    }

    /**
     * Validates values in one request with `POST /accounts/{accountId}/fields/validate-multiple`.
     *
     * Request body: `[{"field_id":"field-1","value":"123"}]`; request query: none.
     * Response `data`:
     * `[{"field_id":"field-1","type":"cpf","success":false,"error_message":"Invalid CPF."}]`.
     *
     * @param entries Non-empty field identifier/value array sent directly as the JSON body.
     * @param accountId Account override; otherwise the resource default is used.
     * @return One validation result for each request entry.
     * @throws ValidationException when the account is blank, the array is empty, or an entry has a blank field ID.
     */
    suspend fun validateMultiple(
        entries: List<FieldValidationEntry>,
        accountId: String? = null,
    ): List<FieldValidationResult> {
        val id = accountId(accountId)
        if (entries.isEmpty()) throw ValidationException("At least one field value is required")
        entries.forEach { requireId(it.fieldId, "Field ID") }
        return callList("Failed to validate field values", FieldValidationResult::class.java) {
            http.post(
                "/accounts/${pathSegment(id)}/fields/validate-multiple",
                toJsonAllowNulls(entries),
            )
        }.data
    }

    /**
     * Lists supported types with `GET /field-types`.
     *
     * Request body/query: none. Response `data`: `[{"type":"cpf","name":"CPF"}]`.
     *
     * @return Machine-readable type codes and human-readable names.
     */
    suspend fun listTypes(): List<FieldType> = callList(
        "Failed to list field types",
        FieldType::class.java,
    ) { http.get("/field-types") }.data
}
