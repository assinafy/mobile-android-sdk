# Changelog

All notable changes to the Assinafy Android SDK will be documented in this file.

## [1.0.2] - 2026-05-27

Full audit against the live `https://api.assinafy.com.br/v1` contract.

### Fixed
- **Duplicate-signer recovery.** `SignerResource.create` now treats the live duplicate-email error
  (HTTP **400**, previously only 409 was handled) as a signal to return the existing signer, keeping
  the call idempotent even under a create race.
- **`DocumentResource.isFullySigned`** no longer reports `true` for documents that are merely
  `metadata_ready` / `pending_signature`. It now requires the `certificated` status (or a complete
  assignment summary).
- **`AssignmentResource.resetExpiration`** accepts `null` to clear the expiration. The body is
  serialized with an explicit `{"expires_at": null}` (the default serializer dropped the key, so the
  clear never reached the API).
- **Signature upload** now matches the documented contract: the image is sent as a raw binary body
  with `Content-Type: image/png` or `image/jpeg` (was multipart form data). `uploadSignature` gained
  a `contentType` parameter (defaults to `image/png`).

### Added
- **Tags.** New `client.tags` resource (`list`, `create`, `update`, `delete`, with case-insensitive
  names and `force` delete) plus per-document attachment on `DocumentResource`
  (`listTags`, `addTags`, `replaceTags`, `detachTag`). `Tag` model added.
- **Assignment lifecycle.** `AssignmentResource.decline` and `listWhatsappNotifications`;
  `SignerReference.step` for sequential signing.
- **List filters.** `ListParams` gained `status`, `method`, and `tags` (serialized as a comma-separated
  `tags` value) for the documents/templates list endpoints.
- **Model completeness.** `DocumentDetails`/`DocumentListItem`/`DocumentUploadResponse` now surface
  `tags`, `signing_url`, and `template_id` as returned by the live API.

### Changed
- `AssinafyClient` constructor gained a `tags: TagResource` parameter (the factory wires it
  automatically). `ApiHttpClient.postSignature` takes a `contentType` argument.

### Tooling
- Added a project `.editorconfig` pinning ktlint to the IntelliJ IDEA code style so formatting is
  deterministic across ktlint versions (the CI `ktlintCheck` gate previously had no config and was
  non-deterministic). Version constant and Gradle `version` aligned to `1.0.2`. Removed the unused
  `targetSdk` from the library module (consumers own their own `targetSdkVersion`).

## [1.0.1] - 2026-05-11

### Fixed
- **Pagination silently ignored.** `ListParams.perPage` now serialises as the documented `per-page` query key (was `per_page`, which the API discards).
- **`Assignment.signing_urls`** modelled as a `List<SigningUrl>` (was an incorrect `Map<String, String>`); `Assignment.copy_receivers` modelled as `List<Signer>` on responses (matches the documented signer-object shape).
- **`DocumentActivity`**: `created_at` is an ISO 8601 string (was `Long`), `origin` is an object (was `String`), and `payload` is now exposed.
- **`WebhookDispatch`**: `created_at` / `updated_at` are ISO 8601 strings (were `Long`).
- **`WebhookSubscription`**: `url` / `email` are nullable (live API returns null for empty subscriptions); spurious `id` field removed (not present in the response).
- **`Workspace` / `WorkspaceListItem`**: surface `primary_color` and `secondary_color`.
- **`WebhookPayload`**: surface the `subject`, `object`, `origin`, and `created_at` envelope fields so consumers can read the actual event subject/object (the documented `payload` field is `null` for most events).
- **`TemplateSigner.id`** is optional — required only for `create-from-template`, not for `estimate-cost-from-template`.

### Removed
- `AssignmentResource.cancel()` (the endpoint `/accounts/{account_id}/signature-requests/{document_id}/cancel` returns 404 against the live API and is not in the public docs).

## [1.0.0] - 2026-05-06

### Added
- Initial release of the Assinafy Android SDK (Kotlin)
- `AssinafyClient` — primary entry point with configurable API key / token authentication
- `DocumentResource` — upload, list, details, download, thumbnail, activities, delete, `waitUntilReady`, `isFullySigned`, `getSigningProgress`, `createFromTemplate`, `estimateCostFromTemplate`, `verify`
- `SignerResource` — create (idempotent by email), get, list, update, delete, `findByEmail`
- `AssignmentResource` — create, `estimateCost`, `resetExpiration`, `resendNotification`, `estimateResendCost`
- `WebhookResource` — register, get, delete, inactivate, `listEventTypes`, `listDispatches`, `retryDispatch`
- `WorkspaceResource` — create, list, get, update, delete
- `TemplateResource` — list, get
- `WebhookVerifier` — HMAC-SHA256 signature verification, event parsing
- `uploadAndRequestSignatures` high-level helper
- Full Kotlin coroutines support (`suspend` functions throughout)
- Typed exceptions: `AssinafyException`, `ApiException`, `NetworkException`, `ValidationException`
- Pagination metadata parsed from `X-Pagination-*` response headers
- OkHttp-based HTTP client (`OkHttpApiClient`) with configurable timeout and interceptors
- Gson-based JSON serialisation / deserialisation
- Gradle Kotlin DSL build configuration
- Docker Compose test environment
