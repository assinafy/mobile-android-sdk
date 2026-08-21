# Assinafy v1 API coverage

This SDK covers every operation in the Assinafy v1 OpenAPI document fetched on **2026-08-21** from
[`https://api.assinafy.com.br/v1/docs/openapi.json`](https://api.assinafy.com.br/v1/docs/openapi.json).

- OpenAPI SHA-256: `47fe244e05d7acd9cef0561da4b8c042e1eb549b015f731233475093b5602087`
- Paths: 67
- Operations: **89 covered / 89 documented**
- API base URL: `https://api.assinafy.com.br/v1`

Authentication modes below are:

- **Account** — `X-Api-Key` or `Authorization: Bearer ...`; account-scoped calls use an explicit
  `accountId` or the client's default.
- **Signer** — no account credential; `signer-access-code` is sent in the query string.
- **Public** — no credential.

The SDK method column omits the leading `client.`. Kotlin aliases and local convenience helpers are
documented in [API_REFERENCE.md](API_REFERENCE.md).

“Covered” means the operation has a public SDK mapping; the unit suite tests resource serialization
and shared transport/response behavior. It does not mean every destructive, credential-rotation,
social-login, OTP, or signer-state success path is run against a shared live account;
[TESTING.md](TESTING.md) defines the safe live-test boundary.

## Accounts (10/10)

| Operation | SDK method | Auth | Status |
|---|---|---|---|
| `GET /v1/accounts/{accountId}` | `workspaces.get(accountId)` | Account | Covered |
| `PUT /v1/accounts/{accountId}` | `workspaces.update(accountId, request)` | Account | Covered |
| `DELETE /v1/accounts/{accountId}` | `workspaces.delete(accountId, force)` | Account | Covered |
| `GET /v1/accounts/{accountId}/theme` | `workspaces.getTheme(accountId)` | Account | Covered |
| `GET /v1/accounts/{accountId}/logo` | `workspaces.getLogo(accountId)` | Account | Covered |
| `POST /v1/accounts/{accountId}/logo` | `workspaces.uploadLogo(accountId, ...)` | Account | Covered |
| `DELETE /v1/accounts/{accountId}/logo` | `workspaces.deleteLogo(accountId)` | Account | Covered |
| `GET /v1/accounts` | `workspaces.list()` | Account | Covered |
| `POST /v1/accounts` | `workspaces.create(request)` | Account | Covered |
| `GET /v1/accounts/{accountId}/stats` | `workspaces.getStats(...)` | Account | Covered |

## Assignments (7/7)

| Operation | SDK method | Auth | Status |
|---|---|---|---|
| `GET /v1/assignments` | `assignments.list(params, accountId)` | Account | Covered |
| `POST /v1/documents/{documentId}/assignments` | `assignments.create(documentId, request)` | Account | Covered |
| `POST /v1/documents/{documentId}/assignments/estimate-cost` | `assignments.estimateCost(documentId, request)` | Account | Covered |
| `PUT /v1/documents/{documentId}/assignments/{assignmentId}/signers/{signerId}/resend` | `assignments.resendNotification(...)` | Account | Covered |
| `POST /v1/documents/{documentId}/assignments/{assignmentId}/signers/{signerId}/estimate-resend-cost` | `assignments.estimateResendCost(...)` | Account | Covered |
| `PUT /v1/documents/{documentId}/assignments/{assignmentId}/reset-expiration` | `assignments.resetExpiration(...)` | Account | Covered |
| `GET /v1/documents/{documentId}/assignments/{assignmentId}/whatsapp-notifications` | `assignments.listWhatsappNotifications(...)` | Account | Covered |

## Authentication (9/9)

| Operation | SDK method | Auth | Status |
|---|---|---|---|
| `POST /v1/login` | `authentication.login(request)` | Public | Covered |
| `PUT /v1/authentication/request-password-reset` | `authentication.requestPasswordReset(request)` | Public | Covered |
| `PUT /v1/authentication/reset-password` | `authentication.resetPassword(request)` | Public | Covered |
| `PUT /v1/authentication/change-password` | `authentication.changePassword(request)` | Account | Covered |
| `POST /v1/authentication/social-login` | `authentication.socialLogin(request)` | Public | Covered |
| `POST /v1/auth/link-social-login` | `authentication.linkSocialLogin(request)` | Account | Covered |
| `GET /v1/users/api-keys` | `authentication.getApiKey()` | Account | Covered |
| `POST /v1/users/api-keys` | `authentication.createApiKey(request)` | Account | Covered |
| `DELETE /v1/users/api-keys` | `authentication.deleteApiKey()` | Account | Covered |

## Documents (18/18)

| Operation | SDK method | Auth | Status |
|---|---|---|---|
| `GET /v1/documents/{documentId}/activities` | `documents.activities(documentId)` | Account | Covered |
| `GET /v1/accounts/{accountId}/documents` | `documents.list(params, accountId)` | Account | Covered |
| `POST /v1/accounts/{accountId}/documents` | `documents.upload(...)` | Account | Covered |
| `GET /v1/accounts/{accountId}/documents/search` | `documents.search(...)` | Account | Covered |
| `GET /v1/documents/statuses` | `documents.getStatuses()` | Account | Covered |
| `GET /v1/documents/{documentId}` | `documents.details(documentId)` | Account | Covered |
| `DELETE /v1/documents/{documentId}` | `documents.delete(documentId)` | Account | Covered |
| `PATCH /v1/documents/{documentId}` | `documents.rename(documentId, name)` | Account | Covered |
| `GET /v1/documents/{documentId}/download/{artifactName}` | `documents.download(documentId, artifactName)` | Account | Covered |
| `GET /v1/documents/{documentSignatureHash}/verify` | `documents.verify(hash)` | Public | Covered |
| `GET /v1/accounts/{accountId}/documents/{documentId}/tags` | `documents.listTags(documentId, accountId)` | Account | Covered |
| `PUT /v1/accounts/{accountId}/documents/{documentId}/tags` | `documents.replaceTags(documentId, tagIds, accountId)` | Account | Covered |
| `POST /v1/accounts/{accountId}/documents/{documentId}/tags` | `documents.addTags(documentId, tagIds, accountId)` | Account | Covered |
| `DELETE /v1/accounts/{accountId}/documents/{documentId}/tags/{tagId}` | `documents.detachTag(...)` | Account | Covered |
| `GET /v1/documents/{documentId}/thumbnail` | `documents.thumbnail(documentId)` | Account | Covered |
| `GET /v1/documents/{documentId}/pages/{pageId}/download` | `documents.downloadPage(documentId, pageId)` | Account | Covered |
| `POST /v1/accounts/{accountId}/templates/{templateId}/documents` | `documents.createFromTemplate(...)` | Account | Covered |
| `POST /v1/accounts/{accountId}/templates/{templateId}/documents/estimate-cost` | `documents.estimateCostFromTemplate(...)` | Account | Covered |

## Fields (8/8)

| Operation | SDK method | Auth | Status |
|---|---|---|---|
| `GET /v1/accounts/{accountId}/fields` | `fields.list(...)` | Account | Covered |
| `POST /v1/accounts/{accountId}/fields` | `fields.create(request, accountId)` | Account | Covered |
| `GET /v1/accounts/{accountId}/fields/{fieldId}` | `fields.get(fieldId, accountId)` | Account | Covered |
| `PUT /v1/accounts/{accountId}/fields/{fieldId}` | `fields.update(fieldId, request, accountId)` | Account | Covered |
| `DELETE /v1/accounts/{accountId}/fields/{fieldId}` | `fields.delete(fieldId, accountId)` | Account | Covered |
| `POST /v1/accounts/{accountId}/fields/{fieldId}/validate` | `fields.validate(fieldId, value, accountId)` | Account | Covered |
| `POST /v1/accounts/{accountId}/fields/validate-multiple` | `fields.validateMultiple(entries, accountId)` | Account | Covered |
| `GET /v1/field-types` | `fields.listTypes()` | Account | Covered |

## Signers (5/5)

| Operation | SDK method | Auth | Status |
|---|---|---|---|
| `GET /v1/accounts/{accountId}/signers` | `signers.list(params, accountId)` | Account | Covered |
| `POST /v1/accounts/{accountId}/signers` | `signers.create(request, accountId)` | Account | Covered |
| `GET /v1/accounts/{accountId}/signers/{signerId}` | `signers.get(signerId, accountId)` | Account | Covered |
| `PUT /v1/accounts/{accountId}/signers/{signerId}` | `signers.update(signerId, request, accountId)` | Account | Covered |
| `DELETE /v1/accounts/{accountId}/signers/{signerId}` | `signers.delete(signerId, accountId)` | Account | Covered |

## Signing (17/17)

| Operation | SDK method | Auth | Status |
|---|---|---|---|
| `GET /v1/signers/self` | `signerDocuments.self(accessCode)` | Signer | Covered |
| `GET /v1/signers/{signerId}/document` | `signerDocuments.getCurrent(...)` | Signer | Covered |
| `GET /v1/sign` | `signerDocuments.getAssignment(...)` | Signer | Covered |
| `POST /v1/documents/{documentId}/assignments/{assignmentId}` | `signerDocuments.sign(...)` | Signer | Covered |
| `PUT /v1/documents/{documentId}/assignments/{assignmentId}/reject` | `signerDocuments.decline(...)` | Signer | Covered |
| `PUT /v1/signers/documents/sign-multiple` | `signerDocuments.signMultiple(...)` | Signer | Covered |
| `PUT /v1/signers/documents/decline-multiple` | `signerDocuments.declineMultiple(...)` | Signer | Covered |
| `POST /v1/verify` | `signerDocuments.verifyEmail(...)` | Signer | Covered |
| `PUT /v1/documents/{documentId}/signers/confirm-data` | `signerDocuments.confirmData(...)` | Signer | Covered |
| `PUT /v1/signers/accept-terms` | `signerDocuments.acceptTerms(accessCode)` | Signer | Covered |
| `POST /v1/signature` | `signerDocuments.uploadSignature(...)` | Signer | Covered |
| `GET /v1/signature/{signatureType}` | `signerDocuments.downloadSignature(...)` | Signer | Covered |
| `GET /v1/signers/{signerId}/documents` | `signerDocuments.list(...)` | Signer | Covered |
| `GET /v1/signers/{signerId}/documents/search` | `signerDocuments.search(...)` | Signer | Covered |
| `GET /v1/signers/{signerId}/documents/{documentId}/download/{artifactName}` | `signerDocuments.download(...)` | Public | Covered |
| `GET /v1/public/documents/{documentId}` | `documents.getPublic(documentId)` | Public | Covered |
| `PUT /v1/public/documents/{documentId}/send-token` | `documents.sendToken(documentId, email)` | Public | Covered |

## Tags (4/4)

| Operation | SDK method | Auth | Status |
|---|---|---|---|
| `GET /v1/accounts/{accountId}/tags` | `tags.list(search, accountId)` | Account | Covered |
| `POST /v1/accounts/{accountId}/tags` | `tags.create(name, color, accountId)` | Account | Covered |
| `PUT /v1/accounts/{accountId}/tags/{tagId}` | `tags.update(...)` | Account | Covered |
| `DELETE /v1/accounts/{accountId}/tags/{tagId}` | `tags.delete(tagId, force, accountId)` | Account | Covered |

## Templates (1/1)

| Operation | SDK method | Auth | Status |
|---|---|---|---|
| `GET /v1/accounts/{accountId}/templates` | `templates.list(params, accountId)` | Account | Covered |

## Users (4/4)

| Operation | SDK method | Auth | Status |
|---|---|---|---|
| `GET /v1/users/self` | `users.getCurrent()` | Account | Covered |
| `GET /v1/users/self/stats` | `users.getStats(query)` | Account | Covered |
| `GET /v1/users/self/notification-preferences` | `users.getNotificationPreferences()` | Account | Covered |
| `PUT /v1/users/self/notification-preferences` | `users.updateNotificationPreferences(request)` | Account | Covered |

## Webhooks (6/6)

| Operation | SDK method | Auth | Status |
|---|---|---|---|
| `GET /v1/accounts/{accountId}/webhooks/subscriptions` | `webhooks.get(accountId)` | Account | Covered |
| `PUT /v1/accounts/{accountId}/webhooks/subscriptions` | `webhooks.register(request, accountId)` | Account | Covered |
| `PUT /v1/accounts/{accountId}/webhooks/inactivate` | `webhooks.inactivate(accountId)` | Account | Covered |
| `GET /v1/webhooks/event-types` | `webhooks.listEventTypes()` | Account | Covered |
| `GET /v1/accounts/{accountId}/webhooks` | `webhooks.listDispatches(...)` | Account | Covered |
| `POST /v1/accounts/{accountId}/webhooks/{historyId}/retry` | `webhooks.retryDispatch(historyId, accountId)` | Account | Covered |

## Retained compatibility route

`templates.get(templateId, accountId)` calls
`GET /v1/accounts/{accountId}/templates/{templateId}`. This live Assinafy route predates the
2026-08-21 OpenAPI snapshot and is retained for source and deployed-service compatibility. It is
not counted among the 89 OpenAPI operations. No other undocumented HTTP route is added by the SDK.

The SDK also retains seven deployed-service wire compatibilities without adding operations:

- document upload adds multipart `name` and JSON-string `metadata` beside the OpenAPI `file` part
  only when the caller explicitly supplies metadata;
- assignment listing adds the deployed API's `accountId` query to OpenAPI's `page`/`per-page` only
  when the caller explicitly passes that override;
- assignment expiration reset sends `{"expires_at":null}` only when the caller explicitly passes
  null or blank to clear an expiration on deployments that support that extension;
- notification resend adds the deployed API's `channel` JSON body only when the caller explicitly
  passes that compatibility parameter;
- public token delivery supports the deployed `recipient`/`channel` body explicitly and as a
  narrowly gated retry when the server rejects the OpenAPI `email` body for those missing fields;
- document tag mutations pass the supplied strings unchanged: use IDs for the current OpenAPI, or
  names only when targeting an older deployment that still requires its legacy tag-name contract;
- account create/update may send the deprecated six-digit `primary_color` and `secondary_color`
  fields when callers explicitly use them.
