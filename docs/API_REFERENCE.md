# Android SDK API reference

This reference describes the public Kotlin surface and its Assinafy v1 wire contract. All network
functions are `suspend` functions. Paths below include `/v1`; configure the client with a base URL
that already ends in `/v1`. Request routes, query parameters, and bodies follow the 2026-08-21
OpenAPI document unless a deployed-service compatibility is explicitly labelled. Response models
also accept the explicitly labelled deployed/legacy superset described below.

For the operation-by-operation conformance ledger, see [API_COVERAGE.md](API_COVERAGE.md).

## Transport, authentication, and errors

Production is `https://api.assinafy.com.br/v1`; sandbox is
`https://sandbox.assinafy.com.br/v1`.

```kotlin
val client = AssinafyClient.create(
    AssinafyClientConfig(
        apiKey = BuildConfig.ASSINAFY_API_KEY,
        accountId = BuildConfig.ASSINAFY_ACCOUNT_ID,
        baseUrl = "https://sandbox.assinafy.com.br/v1",
    )
)
```

Use exactly one account credential:

- `apiKey` sends `X-Api-Key: ...`.
- `token` sends `Authorization: Bearer ...`.
- Neither is required for login, password-reset, document verification, public-document, or signer
  flows. The client uses a separate credentialless transport for those calls, so account secrets are
  not forwarded to public URLs or redirects.
- Signer calls send the one-time code only as the `signer-access-code` query parameter.

Do not put API keys, bearer tokens, signer codes, passwords, or webhook secrets in an Android APK,
source control, logs, crash reports, or analytics. Account operations normally belong on a trusted
backend. Public/signer operations are suitable for a client application when the application
receives the short-lived signer code through the intended signing flow.

JSON responses use the envelope:

```json
{"status":200,"message":"OK","data":{}}
```

The SDK retries HTTP 429 responses up to twice for GET, HEAD, and OPTIONS, honoring
`Retry-After`/`X-Rate-Limit-Reset` with a 30-second cap. Mutation requests are not replayed. It then
validates both the HTTP status and the envelope `status`, preserves the complete error envelope in
`ApiException.responseData`, and unwraps `data`. Successes without a data model return `Unit`. A list endpoint
returns `PaginatedResult<T>` when pagination applies; metadata comes from `X-Pagination-Current-Page`,
`X-Pagination-Page-Count`, `X-Pagination-Per-Page`, and `X-Pagination-Total-Count` headers. Binary
endpoints return the response bytes unchanged.

Failures are:

| Exception | Meaning |
|---|---|
| `ValidationException` | A local required-field, format, range, file, or configuration check failed. No request is sent. |
| `ApiException` | HTTP or envelope status was not 2xx. Inspect `statusCode` and `responseData`; do not branch on human-readable text, and redact response data before logging because servers can echo sensitive input. |
| `NetworkException` | DNS, TLS, connection, timeout, or response-read failure. |
| `AssinafyException` | Common SDK base exception and response-decoding failures. |

Coroutine cancellation cancels the underlying OkHttp call and propagates cancellation; do not turn
it into an automatic retry.

## Client and resource map

`AssinafyClient.create(config)` validates the URL and positive timeout. Credentials require HTTPS,
except for loopback test servers. `apiKey` and `token` are mutually exclusive. The convenience
`create(apiKey, accountId, baseUrl, webhookSecret, timeoutMs, logger)` builds the same client.

| Property | Operations |
|---|---|
| `authentication` | Login, passwords, social identity, personal API keys |
| `workspaces` | Accounts, themes, logos, account statistics |
| `documents` | Documents, artifacts, public document access, template instantiation, document tags |
| `signers` | Account-scoped signer CRUD and compatibility signer-flow aliases |
| `signerDocuments` | Complete credentialless signer-facing signing flow |
| `assignments` | Assignment creation, pricing, expiration, resend, notification history |
| `fields` | Field definitions, field types, server-side validation |
| `users` | Authenticated profile, cross-account statistics, notification preferences |
| `tags` | Account tag CRUD |
| `templates` | Template reads |
| `webhooks` | Subscription and dispatch management |
| `webhookVerifier` | Optional backend HMAC verification and payload parsing |

`uploadAndRequestSignatures(request)` is a local orchestration helper. It validates signer input,
uploads the PDF, always waits for metadata readiness, finds or creates each signer by email, then
creates a virtual assignment. `waitForReady` controls only the final document refresh. It returns
`UploadAndRequestSignaturesResult(document, assignment, signerIds)`. A failure after upload can leave
the uploaded document in the account; callers that require rollback should delete that document
explicitly after deciding that deletion is safe.

`SignerReference.ofId(signerId)` is shorthand for `SignerReference(id = signerId)`.
`ListParams.toQueryMap()` returns its non-null values using the API's exact query names and joins tag
IDs with commas. A custom `Logger` receives `debug`, `info`, `warn`, and `error` calls as
`(message, context)`; the SDK does not include credential values in its contexts.

Document tag mutation values are sent unchanged. The current OpenAPI defines them as tag IDs; older
deployments may still require tag names on the same routes.

## AuthenticationResource

| Kotlin function | Exact request | JSON body | Return (`data`) |
|---|---|---|---|
| `login(LoginRequest)` | `POST /v1/login` (public) | `{"email":string,"password":string}` | `AuthenticationSession` |
| `requestPasswordReset(RequestPasswordResetRequest)` | `PUT /v1/authentication/request-password-reset` (public) | `{"email":string}` | `AuthenticationEmailResponse` |
| `resetPassword(ResetPasswordRequest)` | `PUT /v1/authentication/reset-password` (public) | `{"email":string,"new_password":string,"token":string?}` | `AuthenticationEmailResponse` |
| `changePassword(ChangePasswordRequest)` | `PUT /v1/authentication/change-password` | `{"email":string,"password":string,"new_password":string}` | `AuthenticationEmailResponse` |
| `socialLogin(SocialLoginRequest)` | `POST /v1/authentication/social-login` (public) | `{"provider":"google","token":string,"has_accepted_terms":boolean}` | `AuthenticationSession` |
| `linkSocialLogin(LinkSocialLoginRequest)` | `POST /v1/auth/link-social-login` | `{"provider":"google","token":string}` | `Unit` |
| `getApiKey()` | `GET /v1/users/api-keys` | none | `ApiKeyResponse?` (`api_key` may also be null) |
| `createApiKey(CreateApiKeyRequest)` | `POST /v1/users/api-keys` | `{"password":string}` | `ApiKeyResponse` containing the newly generated key |
| `deleteApiKey()` | `DELETE /v1/users/api-keys` | none | `Unit` |

Creating an API key rotates the previous key; deletion revokes it. Password reset/change and API-key
rotation are state-changing security operations and should never be used as health checks.

## WorkspaceResource

An Assinafy workspace is an API account. `notification_sender_type` accepts `"User"` or
`"Account"`. Compatibility color values, when used, are exactly six hexadecimal characters without
`#`, for example `2072b9`.

| Kotlin function | Exact request | Query/body | Return (`data`) |
|---|---|---|---|
| `create(CreateWorkspaceRequest)` | `POST /v1/accounts` | `{"name":string,"notification_sender_type":string?,"primary_color":string?,"secondary_color":string?}` | `Workspace` |
| `list()` | `GET /v1/accounts` | none | `PaginatedResult<Workspace>` |
| `get(accountId)` | `GET /v1/accounts/{accountId}` | none | `Workspace` |
| `update(accountId, UpdateWorkspaceRequest)` | `PUT /v1/accounts/{accountId}` | Non-empty subset of create fields | `Workspace` |
| `delete(accountId, force)` | `DELETE /v1/accounts/{accountId}` | Optional JSON `{"force":boolean}` | `Unit` |
| `getTheme(accountId)` | `GET /v1/accounts/{accountId}/theme` | none | `AccountTheme` |
| `getLogo(accountId)` | `GET /v1/accounts/{accountId}/logo` | none | Raw `ByteArray?`; `null` on 404 |
| `uploadLogo(accountId, fileData, fileName, contentType)` | `POST /v1/accounts/{accountId}/logo` | `multipart/form-data`; one `file` part | `Unit` |
| `deleteLogo(accountId)` | `DELETE /v1/accounts/{accountId}/logo` | none | `Unit` |
| `getStats(accountId, granularity, month)` | `GET /v1/accounts/{accountId}/stats` | `granularity=monthly\|daily`; `month=YYYY-MM` is required for daily | `List<DocumentStatsRow>` |

## DocumentResource

| Kotlin function | Exact request | Query/body | Return (`data`) |
|---|---|---|---|
| `upload(fileData, fileName, metadata, accountId)` | `POST /v1/accounts/{accountId}/documents` | Multipart `file` (`application/pdf`). Only when `metadata` is non-null, opt in to live compatibility `name` and JSON-string `metadata` parts | `DocumentUploadResponse` |
| `list(ListParams, accountId)` | `GET /v1/accounts/{accountId}/documents` | Optional `status`, `method`, `search`, comma-separated tag IDs in `tags`, `sort`, `page`, `per-page` | `PaginatedResult<DocumentListItem>`; alias of full `DocumentDetails` |
| `search(query, status, page, perPage, accountId)` | `GET /v1/accounts/{accountId}/documents/search` | Optional `search`, `status`, `page`, `per-page` | `PaginatedResult<DocumentListItem>`; alias of full `DocumentDetails` |
| `details(documentId)` | `GET /v1/documents/{documentId}` | none | `DocumentDetails` |
| `get(documentId)` | Same as `details` | none | `DocumentDetails` |
| `waitUntilReady(documentId, maxWaitMs, pollIntervalMs)` | Repeats `GET /v1/documents/{documentId}` | Local timing arguments only | First `DocumentDetails` in a ready state |
| `download(documentId, artifactName)` | `GET /v1/documents/{documentId}/download/{artifactName}` | none | Raw PDF/ZIP `ByteArray` |
| `thumbnail(documentId)` | `GET /v1/documents/{documentId}/thumbnail` | none | Raw image `ByteArray` |
| `downloadPage(documentId, pageId)` | `GET /v1/documents/{documentId}/pages/{pageId}/download` | none | Raw page image `ByteArray` |
| `activities(documentId)` | `GET /v1/documents/{documentId}/activities` | none | `List<DocumentActivity>` |
| `delete(documentId)` | `DELETE /v1/documents/{documentId}` | none | `Unit` |
| `rename(documentId, name)` | `PATCH /v1/documents/{documentId}` | `{"name":string}`; maximum 255 characters | `DocumentDetails` |
| `createFromTemplate(templateId, signers, options, accountId)` | `POST /v1/accounts/{accountId}/templates/{templateId}/documents` | `CreateDocumentFromTemplateRequest`; function `signers` replaces `options.signers` | `DocumentDetails` |
| `estimateCostFromTemplate(templateId, signers, accountId)` | `POST /v1/accounts/{accountId}/templates/{templateId}/documents/estimate-cost` | `{"signers":[{"role_id":string,"verification_method":string?,"notification_methods":[string]?}]}` | `CostEstimate` |
| `verify(hash)` | `GET /v1/documents/{documentSignatureHash}/verify` (public) | none | `DocumentVerification` |
| `getPublic(documentId)` | `GET /v1/public/documents/{documentId}` (public) | none | `PublicDocumentInfo` |
| `sendToken(documentId, email?, channel?)` | `PUT /v1/public/documents/{documentId}/send-token` (public) | OpenAPI: none or `{"email":string}`; explicit deployed compatibility: `{"recipient":string,"channel":"email"\|"whatsapp"}` | `Unit` |
| `isFullySigned(documentId)` | Calls `details` | local derivation | `Boolean` |
| `getSigningProgress(documentId)` | Calls `details` | local derivation | `SigningProgress` |
| `getStatuses()` | `GET /v1/documents/statuses` | none | `List<DocumentStatusInfo>` |
| `confirmSignerData(documentId, accessCode, request)` | `PUT /v1/documents/{documentId}/signers/confirm-data?signer-access-code=...` | Official subset `full_name`, `email`, `government_id`; deprecated compatibility properties are sent only when explicitly populated | `Signer` |
| `confirmSignerData(documentId, accessCode, data)` | Same endpoint; compatibility overload | Caller-supplied non-empty JSON object | `Signer` |
| `listTags(documentId, accountId)` | `GET /v1/accounts/{accountId}/documents/{documentId}/tags` | none | `List<Tag>` |
| `replaceTags(documentId, tagNames, accountId)` | `PUT /v1/accounts/{accountId}/documents/{documentId}/tags` | `{"tags":["tag_id",...]}`; parameter name is retained for source compatibility | `List<Tag>` |
| `addTags(documentId, tagNames, accountId)` | `POST /v1/accounts/{accountId}/documents/{documentId}/tags` | `{"tags":["tag_id",...]}`; parameter name is retained for source compatibility | `List<Tag>` |
| `detachTag(documentId, tagId, accountId)` | `DELETE /v1/accounts/{accountId}/documents/{documentId}/tags/{tagId}` | none | `Unit` |

Uploads must be non-empty PDF content, have a `.pdf` name, begin with `%PDF-`, and be no larger than
25 MiB. Artifact names are `original`, `certificated`, `certificate-page`, `pades`, and `bundle`;
`bundle` is ZIP rather than PDF. Readiness stops at `metadata_ready`, `pending_signature`, or
`certificated`, and fails immediately for terminal processing/rejection/expiration states.

The current OpenAPI multipart schema declares only `file`, which is exactly what the default
`metadata=null` call sends. Supplying metadata explicitly opts into the deployed API's legacy `name`
and `metadata` parts retained by earlier SDK releases.

In 2.0, `DocumentListItem`, `DocumentUploadResponse`, `WorkspaceListItem`, and `TemplateListItem` are
Kotlin type aliases of their complete models. Their distinct 1.x JVM classes no longer exist, so 1.x
consumers must recompile for 2.0. The document aliases expose the full `DocumentDetails` model,
including typed `Assignment?`, pages, artifacts, tags, and activities. Fields that list, search, or
upload projections may omit—including `accountId`, `tags`, `pages`, and `isClosed`—are nullable and
must be checked before use.

Template creation body:

```json
{
  "signers": [{
    "role_id": "role_example",
    "id": "signer_example",
    "verification_method": "Email",
    "notification_methods": ["Email"],
    "step": 1
  }],
  "name": "Agreement.pdf",
  "message": "Please review and sign",
  "expires_at": "2026-12-31T23:59:59Z",
  "editor_fields": [{"field_id":"field_example","value":"Example value"}],
  "tags": ["Contracts"]
}
```

## SignerResource

Account CRUD is the preferred use of this resource. The last five methods are compatibility aliases
for signer-flow endpoints; new code should use `signerDocuments`, whose return types match the
current OpenAPI more precisely.

| Kotlin function | Exact request | Query/body | Return (`data`) |
|---|---|---|---|
| `create(CreateSignerRequest, accountId)` | `POST /v1/accounts/{accountId}/signers` | `{"full_name":string,"email":string?,"whatsapp_phone_number":string?}` | `Signer`; exact existing email is reused |
| `get(signerId, accountId)` | `GET /v1/accounts/{accountId}/signers/{signerId}` | none | `Signer` |
| `list(ListParams, accountId)` | `GET /v1/accounts/{accountId}/signers` | Optional `search`, `page`, `per-page`; other common-list fields are not sent | `PaginatedResult<Signer>` |
| `update(signerId, UpdateSignerRequest, accountId)` | `PUT /v1/accounts/{accountId}/signers/{signerId}` | Subset `full_name`, `email`, `whatsapp_phone_number`, `government_id` | `Signer` |
| `delete(signerId, accountId)` | `DELETE /v1/accounts/{accountId}/signers/{signerId}` | none | `Unit` |
| `findByEmail(email, accountId)` | Pages through signer list search | `search=email`, `page`, `per-page=100` | Exact case-insensitive `Signer?` |
| `getSelf(accessCode)` | `GET /v1/signers/self?signer-access-code=...` | none | Compatibility `Signer`; prefer `signerDocuments.self` |
| `acceptTerms(accessCode)` | `PUT /v1/signers/accept-terms?signer-access-code=...` | none | Compatibility `Map<String,Any>` |
| `verifyEmail(accessCode, verificationCode)` | `POST /v1/verify?signer-access-code=...` | `{"verification-code":string}` | Compatibility `Map<String,Any>` |
| `uploadSignature(accessCode, type, imageData, contentType, reuse)` | `POST /v1/signature?signer-access-code=...&type=...&reuse=...` | Raw image body; current API specifies PNG | `Unit` |
| `downloadSignature(accessCode, type)` | `GET /v1/signature/{type}?signer-access-code=...` | none | Raw `ByteArray` |

Deprecated `cpf`/`metadata` create fields and `cpf` update are sent only for compatibility with older
deployments; they are not part of the 2026-08-21 create-signer OpenAPI schema.

## SignerDocumentResource

All methods use a client transport that contains no account API key or bearer token. Except for the
public artifact download, `accessCode` becomes the query key `signer-access-code` and never appears
in a JSON body.

| Kotlin function | Exact request | Body | Return (`data`) |
|---|---|---|---|
| `self(accessCode)` | `GET /v1/signers/self?signer-access-code=...` | none | `SignerSelf` |
| `getCurrent(signerId, accessCode)` | `GET /v1/signers/{signerId}/document?signer-access-code=...` | none | `DocumentDetails` |
| `getAssignment(accessCode, hasAcceptedTerms)` | `GET /v1/sign?signer-access-code=...&has_accepted_terms=...` | none | `DocumentDetails` |
| `sign(documentId, assignmentId, accessCode, entries)` | `POST /v1/documents/{documentId}/assignments/{assignmentId}?signer-access-code=...` | Array of `SignAssignmentItemRequest` | API result `Map<String,Any>` |
| `decline(documentId, assignmentId, accessCode, declineReason)` | `PUT /v1/documents/{documentId}/assignments/{assignmentId}/reject?signer-access-code=...` | `{"decline_reason":string}` | `Unit` |
| `signMultiple(documentIds, accessCode)` | `PUT /v1/signers/documents/sign-multiple?signer-access-code=...` | `{"document_ids":[string,...]}` | `Unit` |
| `declineMultiple(documentIds, declineReason, accessCode)` | `PUT /v1/signers/documents/decline-multiple?signer-access-code=...` | `{"document_ids":[string,...],"decline_reason":string}` | `Unit` |
| `verifyEmail(accessCode, VerifySignerEmailRequest)` | `POST /v1/verify?signer-access-code=...` | `{"verification-code":string}` | `Unit` |
| `confirmData(documentId, accessCode, ConfirmSignerDataRequest)` | `PUT /v1/documents/{documentId}/signers/confirm-data?signer-access-code=...` | Subset `full_name`, `email`, `government_id` | `Signer` |
| `acceptTerms(accessCode)` | `PUT /v1/signers/accept-terms?signer-access-code=...` | none | `Unit` |
| `uploadSignature(accessCode, imageData, type, reuse)` | `POST /v1/signature?signer-access-code=...&type=...&reuse=...` | Raw PNG bytes, `Content-Type: image/png` | `Unit` |
| `downloadSignature(accessCode, type)` | `GET /v1/signature/{type}?signer-access-code=...` | none | Raw PNG `ByteArray` |
| `list(signerId, accessCode, ListParams)` | `GET /v1/signers/{signerId}/documents?signer-access-code=...` | Query uses only `page`, `per-page` | `PaginatedResult<DocumentDetails>` |
| `search(signerId, accessCode, search)` | `GET /v1/signers/{signerId}/documents/search?signer-access-code=...&search=...` | none | `PaginatedResult<DocumentDetails>` |
| `download(signerId, documentId, artifactName)` | `GET /v1/signers/{signerId}/documents/{documentId}/download/{artifactName}` (public) | none and no access code | Raw PDF/ZIP `ByteArray` |

Signing item array:

```json
[
  {
    "itemId": "item_example",
    "fieldId": "field_example",
    "pageId": "page_example",
    "value": "Approved"
  }
]
```

## AssignmentResource

| Kotlin function | Exact request | Query/body | Return (`data`) |
|---|---|---|---|
| `list(ListParams, accountId)` | `GET /v1/assignments` | Optional `page`, `per-page`; explicitly passing `accountId` opts into the deployed-service compatibility `accountId` query | `PaginatedResult<Assignment>` |
| `create(documentId, CreateAssignmentRequest)` | `POST /v1/documents/{documentId}/assignments` | Full assignment request below | `Assignment` |
| `estimateCost(documentId, CreateAssignmentRequest)` | `POST /v1/documents/{documentId}/assignments/estimate-cost` | Projected `method`, `signers`, and `entries`; signer IDs/steps omitted | `CostEstimate` |
| `resetExpiration(documentId, assignmentId, expiresAt)` | `PUT /v1/documents/{documentId}/assignments/{assignmentId}/reset-expiration` | `{"expires_at":string}`; explicit null/blank opts into deployed clear compatibility `{"expires_at":null}` | `Assignment` |
| `decline(documentId, assignmentId, accessCode, reason)` | Compatibility alias for signer reject | `{"decline_reason":string}` and signer query code | `Unit` |
| `listWhatsappNotifications(documentId, assignmentId)` | `GET /v1/documents/{documentId}/assignments/{assignmentId}/whatsapp-notifications` | none | `List<WhatsappNotification>` |
| `resendNotification(documentId, assignmentId, signerId, channel?)` | `PUT /v1/documents/{documentId}/assignments/{assignmentId}/signers/{signerId}/resend` | none; optional deployed-service `{"channel":"email"\|"whatsapp"}` | `ResendEmailResponse` |
| `estimateResendCost(documentId, assignmentId, signerId)` | `POST /v1/documents/{documentId}/assignments/{assignmentId}/signers/{signerId}/estimate-resend-cost` | none | `CostEstimate` |

Create body:

```json
{
  "method": "virtual",
  "signers": [{
    "id": "signer_example",
    "verification_method": "Email",
    "notification_methods": ["Email"],
    "step": 1
  }],
  "message": "Please review and sign",
  "expires_at": "2026-12-31T23:59:59Z",
  "copy_receivers": ["signer_copy_example"],
  "entries": [{
    "page_id": "page_example",
    "fields": [{
      "signer_id": "signer_example",
      "field_id": "field_example",
      "display_settings": {
        "left": 10.0, "top": 20.0, "width": 180.0, "height": 40.0,
        "fontSize": 12.0, "fontFamily": "sans-serif", "backgroundColor": "ffffff"
      }
    }]
  }]
}
```

`method` is `virtual` or `collect`. Create requires an ID for every signer. Steps, if present, form a
contiguous positive sequence; signers on a shared step act in parallel. A digital-certificate signer
must be alone in its step. Collect assignments require page/field entries.

## FieldResource

| Kotlin function | Exact request | Query/body | Return (`data`) |
|---|---|---|---|
| `create(CreateFieldRequest, accountId)` | `POST /v1/accounts/{accountId}/fields` | `{"name":string,"type":string,"regex":string?,"is_required":boolean?}` | `FieldDefinition` |
| `list(includeInactive, includeStandard, accountId)` | `GET /v1/accounts/{accountId}/fields` | Optional `include_inactive`, `include_standard` | `List<FieldDefinition>` |
| `get(fieldId, accountId)` | `GET /v1/accounts/{accountId}/fields/{fieldId}` | none | `FieldDefinition` |
| `update(fieldId, UpdateFieldRequest, accountId)` | `PUT /v1/accounts/{accountId}/fields/{fieldId}` | Non-empty subset `name`, `regex` (including explicit null), `is_active` | `FieldDefinition` |
| `delete(fieldId, accountId)` | `DELETE /v1/accounts/{accountId}/fields/{fieldId}` | none | `Unit` |
| `validate(fieldId, value, accountId)` | `POST /v1/accounts/{accountId}/fields/{fieldId}/validate` | `{"value":any|null}` | `FieldValidationResult` |
| `validateMultiple(entries, accountId)` | `POST /v1/accounts/{accountId}/fields/validate-multiple` | `[ {"field_id":string,"value":any|null}, ... ]` | `List<FieldValidationResult>` |
| `listTypes()` | `GET /v1/field-types` | none | `List<FieldType>` |

`UpdateFieldRequest.clearRegex=true` sends an explicit JSON null. It cannot be combined with a new
`regex` value.

## UserResource

| Kotlin function | Exact request | Query/body | Return (`data`) |
|---|---|---|---|
| `getCurrent()` | `GET /v1/users/self` | none | `AuthenticatedUser` |
| `getStats(DocumentStatsQuery)` | `GET /v1/users/self/stats` | Optional `granularity=monthly\|daily`; daily requires `month=YYYY-MM` | `List<DocumentStatsRow>` |
| `getNotificationPreferences()` | `GET /v1/users/self/notification-preferences` | none | `NotificationPreferences` |
| `updateNotificationPreferences(request)` | `PUT /v1/users/self/notification-preferences` | Non-empty subset of the nine PascalCase preference keys | Complete `NotificationPreferences` |

## TagResource

Tag identifiers, not names, are attached to documents. Tag colors accept six hexadecimal
characters, optionally prefixed by `#`; explicit color clearing is supported on update.

| Kotlin function | Exact request | Query/body | Return (`data`) |
|---|---|---|---|
| `list(search, accountId)` | `GET /v1/accounts/{accountId}/tags` | Optional `search` | `List<Tag>` |
| `create(name, color, accountId)` | `POST /v1/accounts/{accountId}/tags` | `{"name":string,"color":string?}` | `Tag` |
| `update(tagId, name, color, clearColor, accountId)` | `PUT /v1/accounts/{accountId}/tags/{tagId}` | Non-empty subset `name`, `color`; `clearColor` sends `color:null` | `Tag` |
| `delete(tagId, force, accountId)` | `DELETE /v1/accounts/{accountId}/tags/{tagId}` | Optional `force=true` query | `Unit` |

## TemplateResource

| Kotlin function | Exact request | Query/body | Return (`data`) |
|---|---|---|---|
| `list(ListParams, accountId)` | `GET /v1/accounts/{accountId}/templates` | Optional `search`, `page`, `per-page`; other `ListParams` fields are not sent | `PaginatedResult<Template>` |
| `get(templateId, accountId)` | `GET /v1/accounts/{accountId}/templates/{templateId}` | none | `Template` |

`get` is a retained live compatibility route and is not present in the 2026-08-21 OpenAPI snapshot.
The list method deliberately ignores document-only `status`, `method`, `tags`, and `sort` fields.

## WebhookResource

| Kotlin function | Exact request | Query/body | Return (`data`) |
|---|---|---|---|
| `register(RegisterWebhookRequest, accountId)` | `PUT /v1/accounts/{accountId}/webhooks/subscriptions` | `{"url":string,"email":string,"events":[string],"is_active":boolean}` | `WebhookSubscription` |
| `get(accountId)` | `GET /v1/accounts/{accountId}/webhooks/subscriptions` | none | `WebhookSubscription?`; null on 404 |
| `inactivate(accountId)` | `PUT /v1/accounts/{accountId}/webhooks/inactivate` | none | `WebhookSubscription` |
| `listEventTypes()` | `GET /v1/webhooks/event-types` | none | `List<WebhookEventTypeInfo>` |
| Deprecated `listDispatches(ListParams, accountId)` | `GET /v1/accounts/{accountId}/webhooks` | Optional `page`, `per-page`; other `ListParams` fields are ignored | `PaginatedResult<WebhookDispatch>` |
| `listDispatches(WebhookDispatchParams, accountId)` | Same endpoint | Optional `event`, `delivered`, Unix-second `from`/`to`, `page`, `per-page` | `PaginatedResult<WebhookDispatch>` |
| `retryDispatch(dispatchId, accountId)` | `POST /v1/accounts/{accountId}/webhooks/{historyId}/retry` | none | `WebhookDispatch` |

If `events` is absent or empty, the SDK subscribes to its documented default event set. Retrieve the
server's current event catalog with `listEventTypes()` before offering a selection to users.

### WebhookVerifier

`WebhookVerifier` is an optional server-side helper, not an assertion about Assinafy's delivery
contract. `verify(payload: ByteArray|String, signature)` performs constant-time HMAC-SHA256 checking
when `webhookSecret` is configured; it returns `false` without a configured secret.
`extractEvent(payload: ByteArray|String)` parses `WebhookPayload?`. `getEventType(event)` returns
`event` then legacy `type`; `getEventData(event)` returns `payload` or an empty map. Never embed a
webhook shared secret in an Android application.

## Request types

`?` means nullable/omittable. Wire names are shown exactly.

| Kotlin type | Complete fields |
|---|---|
| `AssinafyClientConfig` | `apiKey:String?`, `token:String?`, `accountId:String?`, `baseUrl:String`, `webhookSecret:String?`, `timeoutMs:Long`, `logger:Logger?` |
| `ListParams` | query `page:Int?`, `per-page:Int?`, `search:String?`, `sort:String?`, `status:String?`, `method:String?`, `tags:List<String>?` (comma-separated tag IDs) |
| `LoginRequest` | `email:String`, `password:String` |
| `RequestPasswordResetRequest` | `email:String` |
| `ResetPasswordRequest` | `email:String`, `new_password:String`, `token:String?` |
| `ChangePasswordRequest` | `email:String`, `password:String`, `new_password:String` |
| `SocialLoginRequest` | `provider:"google"`, `token:String`, `has_accepted_terms:Boolean` |
| `LinkSocialLoginRequest` | `provider:"google"`, `token:String` |
| `CreateApiKeyRequest` | `password:String` |
| `CreateWorkspaceRequest` | `name:String`, `notification_sender_type:String?`; compatibility `primary_color:String?`, `secondary_color:String?` |
| `UpdateWorkspaceRequest` | `name:String?`, `notification_sender_type:String?`; compatibility `primary_color:String?`, `secondary_color:String?` |
| `CreateSignerRequest` | `full_name:String`, `email:String?`, `whatsapp_phone_number:String?`; deprecated compatibility `cpf:String?`, `metadata:Map?` |
| `UpdateSignerRequest` | `full_name:String?`, `email:String?`, `whatsapp_phone_number:String?`, `government_id:String?`; deprecated compatibility `cpf:String?` |
| `ConfirmSignerDataRequest` | `full_name:String?`, `email:String?`, `government_id:String?`; deprecated fields are not sent by `signerDocuments.confirmData` |
| `SignerReference` | `id:String?` (required for create), `verification_method:String?`, `notification_methods:List<String>?`, `step:Int?` |
| `CreateAssignmentRequest` | `method:String`, `signers:List<SignerReference>`, `message:String?`, `expires_at:String?`, `copy_receivers:List<String>?` (signer IDs), `entries:List<AssignmentEntry>?` |
| `AssignmentEntry` | `page_id:String`, `fields:List<AssignmentFieldPlacement>` |
| `AssignmentFieldPlacement` | `signer_id:String`, `field_id:String`, `display_settings:DisplaySettings?` |
| `DisplaySettings` | `left:Float`, `top:Float`, `width:Float`, `height:Float`, `fontSize:Float`, `fontFamily:String?`, `backgroundColor:String?` |
| `TemplateSigner` | `role_id:String`, `id:String?` (required for create), `verification_method:String?`, `notification_methods:List<String>?`, `step:Int?` |
| `CreateDocumentFromTemplateRequest` | `signers:List<TemplateSigner>`, `name:String?`, `message:String?`, `expires_at:String?`, `editor_fields:List<TemplateEditorField>?`, `tags:List<String>?` (tag names; missing names are created and merged with template defaults) |
| `TemplateEditorField` | `field_id:String`, `value:String` |
| `CreateFieldRequest` | `name:String`, `type:String`, `regex:String?`, `is_required:Boolean?` |
| `UpdateFieldRequest` | local `name:String?`, `regex:String?`, `clearRegex:Boolean`, `isActive:Boolean?`; serialized as `name`, `regex`, `is_active` |
| `FieldValidationEntry` | `field_id:String`, `value:Any?` |
| `SignAssignmentItemRequest` | `itemId:String`, `fieldId:String`, `pageId:String`, `value:String` |
| `VerifySignerEmailRequest` | `verification-code:String` |
| `DocumentStatsQuery` | query `granularity:DocumentStatsGranularity?` (`monthly` or `daily`), `month:String?` (`YYYY-MM`, required for daily) |
| `UpdateNotificationPreferencesRequest` | Nullable booleans: `DocumentCompleted`, `SignerDeclined`, `DocumentCancelled`, `DocumentAboutToExpire`, `DocumentExpired`, `DocumentExpirationReset`, `DocumentProcessingFailed`, `TemplateProcessingFailed`, `SignerWhatsappFailed` |
| `RegisterWebhookRequest` | `url:String`, `email:String`, `events:List<String>?`, `is_active:Boolean` |
| `WebhookDispatchParams` | query `event:String?`, `delivered:Boolean?`, `from:Long?`, `to:Long?`, `page:Int?`, `per-page:Int?` |
| `UploadAndRequestSignaturesRequest` | Local workflow: `fileData:ByteArray`, `fileName:String`, `signers:List<SignerEntry>`, `message:String?`, `metadata:Map?`, `waitForReady:Boolean`, `expiresAt:String?`, `copyReceivers:List<String>?` (signer IDs), `accountId:String?` |
| `UploadAndRequestSignaturesRequest.SignerEntry` | `name:String`, `email:String`, `whatsappPhoneNumber:String?`, deprecated compatibility `cpf:String?`, `metadata:Map?` |

## Response types

The following tables are the complete serialized fields accepted by the public response models.
Kotlin property names differ only where shown after `→`. Nullable fields use `?`; list defaults do
not imply that the server always returns a key. The frozen OpenAPI omits `required` arrays from its
response component schemas globally. The SDK keeps stable endpoint invariants such as resource IDs
non-null, while fields that list, search, upload, and other endpoint projections are proven to omit
are nullable. Labelled deployed/legacy response fields make decoding tolerant without changing the
snapshot-exact request contract.

### Authentication and account models

| Kotlin type | Complete wire fields and Kotlin types |
|---|---|
| `AuthenticationSession` | `access_token→accessToken:String`, `user:AuthenticatedUser`, `accounts:List<AuthenticatedAccount>` |
| `AuthenticatedUser` | `id:String`, `name:String`, `email:String`, `telephone:String?`, `government_id→governmentId:String?`, `is_email_verified→isEmailVerified:Boolean`, `has_accepted_terms→hasAcceptedTerms:Boolean`, `created_at→createdAt:String`, `to_be_deleted_at→toBeDeletedAt:String?` |
| `AuthenticatedAccount` | `id:String`, `name:String`, `roles:List<String>`, `is_delete_allowed→isDeleteAllowed:Boolean`, `created_at→createdAt:String` |
| `AuthenticationEmailResponse` | `email:String` |
| `ApiKeyResponse` | `api_key→apiKey:String?` |
| `Workspace` / `WorkspaceListItem` | `resource:String?`, `id:String`, `name:String`, `primary_color→primaryColor:String?`, `secondary_color→secondaryColor:String?`, `notification_sender_type→notificationSenderType:String?`, `is_delete_allowed→isDeleteAllowed:Boolean?`, `roles:List<String>?`, `created_at→createdAt:String?` |
| `AccountTheme` | `account_name→accountName:String?`, `primary_color→primaryColor:String?`, `secondary_color→secondaryColor:String?`, `logo:String?` |

### Documents, assignments, and signers

| Kotlin type | Complete wire fields and Kotlin types |
|---|---|
| `DocumentArtifacts` | `original:String?`, `thumbnail:String?`, `certificated:String?`, `certificate-page→certificatePage:String?`, `bundle:String?`, `pades:String?` |
| `DocumentPage` | `id:String`, `number:Int?`, `height:Int?`, `width:Int?`, `download_url→downloadUrl:String?` |
| `DocumentListItem` / `DocumentUploadResponse` | 2.0 Kotlin aliases of `DocumentDetails`; recompile 1.x consumers because the distinct JVM classes were removed, and handle nullable projection fields |
| `DocumentDetails` | `resource:String?`, `id:String`, `account_id→accountId:String?`, `template_id→templateId:String?`, `name:String`, `status:String`, `assignment:Assignment?`, deployed response `download_url→downloadUrl:String?` and `download_final_url→downloadFinalUrl:String?`, `signing_url→signingUrl:String?`, `artifacts:DocumentArtifacts?`, `tags:List<Tag>?`, `pages:List<DocumentPage>?`, `created_at→createdAt:String?`, `updated_at→updatedAt:String?`, `is_closed→isClosed:Boolean?`, `decline_reason→declineReason:String?`, `declined_by→declinedBy:Signer?`, deployed response `activities:List<DocumentActivity>?` |
| `PublicDocumentInfo` | `id:String`, `name:String`, `resource:String?`, `account_id→accountId:String?`, `template_id→templateId:String?`, `status:String?`, `artifacts:DocumentArtifacts?`, `is_closed→isClosed:Boolean?`, `signing_url→signingUrl:String?`, `decline_reason→declineReason:String?`, `declined_by→declinedBy:Signer?`, `tags:List<Tag>?`, `assignment:Assignment?`, `pages:List<DocumentPage>?`, `created_at→createdAt:String?`, `updated_at→updatedAt:String?`; deployed responses `page_count→pageCount:Number?`, `created_by→createdBy:String?` |
| `DocumentActivity` | `id:Long`, `event:String`, `message:String?`, deployed-widened `payload:Any?` (the frozen schema declares an object), `origin:Map<String,Any>?`, `created_at→createdAt:String?` |
| `DocumentVerification` | `hash:String`, `id:String?`, `status:String?`, `page_count→pageCount:String?`, `signer_count→signerCount:String?`, `completed_count→completedCount:Int?`, `completed_at→completedAt:String?`, `verified_at→verifiedAt:String`, `is_valid→isValid:Boolean`, `message:String` |
| `DocumentStatusInfo` | `code:String`, `deletable:Boolean?` |
| `SigningProgress` | Local `signed:Int`, `total:Int`, `pending:Int`, `percentage:Double` |
| `Assignment` | `resource:String?`, `id:String`, `sender_email→senderEmail:String?`, `method:String?`, `expires_at→expiresAt:String?`, compatibility `expiration:String?`, `message:String?`, `signers:List<Signer>`, `copy_receivers→copyReceivers:List<Signer>?`, `items:List<AssignmentItem>?`, `summary:AssignmentSummary?`, `signing_urls→signingUrls:List<SigningUrl>?` |
| `AssignmentSummary` | `signer_count→signerCount:Int`, `completed_count→completedCount:Int`, `signers:List<Signer>` |
| `SigningUrl` | `signer_id→signerId:String`, `url:String` |
| `Signer` | `id:String`, `full_name→fullName:String?`, `email:String?`, `whatsapp_phone_number→whatsappPhoneNumber:String?`, legacy `cpf:String?`, deployed response `government_id→governmentId:String?`, `has_accepted_terms→hasAcceptedTerms:Boolean?`, legacy `metadata:Map<String,Any>?`; assignment expansions `completed:Boolean?`, `verification_method→verificationMethod:String?`, `notification_methods→notificationMethods:List<String>?`, `step:Int?`, `notified:Boolean?`, `notification_history→notificationHistory:List<NotificationHistoryEntry>?`; signer-self expansions `has_signature→hasSignature:Boolean?`, `has_initial→hasInitial:Boolean?`, `is_signature_reusable→isSignatureReusable:Boolean?`; `resource:String?` |
| `SignerSelf` | `resource:String?`, `id:String`, `full_name→fullName:String?`, `email:String?`, `whatsapp_phone_number→whatsappPhoneNumber:String?`, `has_accepted_terms→hasAcceptedTerms:Boolean?`, `has_signature→hasSignature:Boolean?`, `has_initial→hasInitial:Boolean?`, `is_signature_reusable→isSignatureReusable:Boolean?` |
| `NotificationHistoryEntry` | `event:String?`, `status:String?`, `error_code→errorCode:String?`, `error_message→errorMessage:String?`, `sent_at→sentAt:String?`, `failed_at→failedAt:String?` |
| `ResendEmailResponse` | `is_sent→isSent:Boolean?`, `document_id→documentId:String?`, `signer_id→signerId:String?` |
| `WhatsappNotification` | `sent_at→sentAt:Long?`, `header:String?`, `body:String?`, `buttons:List<WhatsappNotificationButton>`, `phone_number→phoneNumber:String?`, `signer_id→signerId:String?` |
| `WhatsappNotificationButton` | `text:String`, deployed response `url:String?` |

Assignment item objects inside `Assignment.items` follow the API schema:
`id:String?`, `signer:Signer?`, `field:FieldDefinition?`, `page:DocumentPage?`,
`display_settings:Any?`, `value:Any?`, and `completed:Boolean`. The two dynamic values remain opaque
because their JSON shapes vary by assignment and field type.

### Pricing, fields, tags, and templates

| Kotlin type | Complete wire fields and Kotlin types |
|---|---|
| `CostEstimate` | `documents:Int?`, `credits:Double?`, `needs_extra_document→needsExtraDocument:Boolean?`, `extra_document_cost→extraDocumentCost:Double?`, `total_credits→totalCredits:Double?`, `breakdown:List<CostEstimateBreakdownItem>`, `document_balance→documentBalance:Double?`, `credit_balance→creditBalance:Double?`, `has_sufficient_resources→hasSufficientResources:Boolean?`, `blocking_reason→blockingReason:String?`, `message:String?`; compatibility `total→legacyTotal:Double?`, `has_sufficient_credits→legacyHasSufficientCredits:Boolean?` |
| `CostEstimateBreakdownItem` | `code:String`, `name:String`, `cost:Double`, `quantity:Int?`, `unit_cost→unitCost:Double?` |
| `FieldDefinition` | `resource:String?`, `id:String`, `name:String`, `type:String`, `regex:String?`, `is_pre_defined→isPreDefined:Boolean?`, `is_active→isActive:Boolean`, `is_required→isRequired:Boolean?`, `is_standard→isStandard:Boolean?`, `is_read_only→isReadOnly:Boolean?`, `is_visible→isVisible:Boolean?` |
| `FieldType` | `type:String`, `name:String` |
| `FieldValidationResult` | `field_id→fieldId:String?`, `type:String?`, `success:Boolean`, `error_message→errorMessage:String` |
| `Tag` | `resource:String?`, `id:String`, `name:String`, `color:String?`, `created_at→createdAt:String?`, `updated_at→updatedAt:String?` |
| `Template` / `TemplateListItem` | `resource:String?`, `id:String`, `name:String`, `document_name→documentName:String?`, `message:String?`, `status:String`, deployed response `account_id→accountId:String?`, `pages:List<TemplatePage>?`, `roles:List<TemplateRole>?`, `tags:List<Tag>?`, `default_document_tags→defaultDocumentTags:List<Tag>?`, `created_at→createdAt:String`, `updated_at→updatedAt:String?` |
| `TemplateRole` | `id:String`, `name:String`, `assignment_type→assignmentType:String?`, `created_at→createdAt:String?`, `updated_at→updatedAt:String?` |
| `TemplatePage` | `id:String`, `number:Int?`, `height:Int?`, `width:Int?`, `download_url→downloadUrl:String?`, `fields:List<TemplateFieldPlacement>?` |
| `TemplateFieldPlacement` | `id:String`, `field_id→fieldId:String?`, `role_id→roleId:String?`, `label:String?`, `display_settings→displaySettings:Any?`, `created_at→createdAt:String?`, `updated_at→updatedAt:String?` |

### Statistics, preferences, webhooks, and pagination

| Kotlin type | Complete wire fields and Kotlin types |
|---|---|
| `DocumentStatsRow` | `period:String`, then `Long`: `documents_uploaded`, `documents_sent`, `signature_requests`, `signature_requests_notification_email`, `signature_requests_notification_whatsapp`, `signature_requests_notification_bypass`, `signature_requests_verification_email`, `signature_requests_verification_whatsapp`, `signature_requests_verification_bypass`, `signature_requests_verification_digital_certificate`, `signature_requests_viewed`, `signature_requests_completed`, `documents_certified` |
| `NotificationPreferences` | Nine required `Boolean` fields: `DocumentCompleted`, `SignerDeclined`, `DocumentCancelled`, `DocumentAboutToExpire`, `DocumentExpired`, `DocumentExpirationReset`, `DocumentProcessingFailed`, `TemplateProcessingFailed`, `SignerWhatsappFailed` |
| `WebhookSubscription` | `url:String?`, `email:String?`, `events:List<String>`, `is_active→isActive:Boolean`, `updated_at→updatedAt:String?` |
| `WebhookEventTypeInfo` | `id:String`, `description:String?` |
| `WebhookDispatch` | `resource:String?`, `id:String`, `event:String`, `activity_id→activityId:Long?`, `endpoint:String?`, `payload:Map<String,Any>?`, `delivered:Boolean`, `http_status→httpStatus:Int?`, `response_body→responseBody:String?`, `error:String?`, `created_at→createdAt:String?`, `updated_at→updatedAt:String?` |
| `WebhookPayload` | `id:Long?`, `event:String?`, compatibility `type:String?`, `message:String?`, `payload:Map<String,Any>?`, `subject:Map<String,Any>?`, `object→obj:Map<String,Any>?`, `origin:Map<String,Any>?`, `created_at→createdAt:Long?`, `account_id→accountId:String?` |
| `PaginatedResult<T>` | Local `data:List<T>`, `meta:PaginationMeta?` |
| `PaginationMeta` | Local `currentPage:Int?`, `lastPage:Int?`, `perPage:Int?`, `total:Int?` populated from headers |

## Constants

- `DocumentArtifact`: `original`, `certificated`, `certificate-page`, `pades`, `bundle`.
- `SignatureType`: `signature`, `initial`.
- `AssignmentMethod`: `virtual`, `collect`.
- `DocumentStatus.READY`: `metadata_ready`, `pending_signature`, `certificated`.
- `DocumentStatus.FAILED`: `failed`, `rejected_by_signer`, `rejected_by_user`, `expired`.
- `WebhookEvent` contains the current SDK event IDs; treat `webhooks.listEventTypes()` as the
  authoritative runtime catalog.
