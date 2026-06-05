# Changelog

All notable changes to the Assinafy Android SDK will be documented in this file.

## [1.0.3] - 2026-06-05

Production-readiness audit verified end-to-end against the live `https://sandbox.assinafy.com.br/v1`
API. Every endpoint the SDK calls was confirmed against the live contract; no working functionality
was removed.

### Fixed
- **Consumer ProGuard/R8 rules now ship in the AAR.** The Gson keep rules were only wired via
  `proguardFiles` (the library's own, effectively no-op minification) and never reached consuming
  apps, so any app with `minifyEnabled = true` would strip the reflectively-populated model fields in
  release builds. They are now declared with `consumerProguardFiles` and hardened
  (`@SerializedName` members, Gson `TypeToken`, broader `-keepattributes`).
- **Binary error messages were swallowed.** `getBinary` passed a raw `String` error body to
  `ApiException.fromResponse`, which only understood a `Map`, so every download/thumbnail/page/signature
  failure reported the generic "API request failed". `fromResponse` now parses a JSON or plain-string
  body, and an empty successful binary response surfaces a clear error.
- **Gson null-safety.** Genuinely-optional model fields (`DocumentPage` dimensions/`download_url`,
  `Signer.fullName`, document timestamps) are now nullable so Gson cannot inject `null` into a
  non-null Kotlin field and NPE later. `ResponseHandler.handle` now throws a clear error on an empty
  body instead of returning `null as T`.
- **`findByEmail` pages through all results** instead of only the first 100, so the idempotent
  `signers.create` dedup is reliable for large signer sets.
- Pagination header parsing is now case-insensitive; `ApiException` no longer coerces a null
  `responseData` into an empty string in its context map.

### Added
- **Full request/response payload reference in the README** (real sandbox payloads) plus KDoc across
  the public API.
- Constants for stringly-typed values: `DocumentArtifact.CERTIFICATE_PAGE`/`BUNDLE`, `SignatureType`,
  and `WebhookEvent`; `RegisterWebhookRequest.DEFAULT_EVENTS` and `Logger.NONE` are now public.
- `DocumentArtifacts.thumbnail` and assignment-context fields on `Signer` (`completed`,
  `verificationMethod`, `notificationMethods`, `step`, `notified`).
- Typed `ConfirmSignerDataRequest` overload for `DocumentResource.confirmSignerData`.
- **Opt-in `LiveIntegrationTest`** that exercises the real API when `ASSINAFY_API_KEY` /
  `ASSINAFY_ACCOUNT_ID` are set (skipped by default). Unit-test coverage expanded to 140 tests,
  including the OkHttp transport, `TemplateResource`, `waitUntilReady`, and webhook 404 handling.

### Changed
- The `AssinafyClient` primary constructor is now `internal`; construct via `AssinafyClient.create(...)`.
- `entries` / `editor_fields` request fields are typed as `List<Map<String, Any?>>` (R8-safe).
- `DEFAULT_MAX_WAIT_MS` raised to 120s (decoupled from the per-request timeout) for upload readiness.
- Dependency bumps: Gson 2.11.0, coroutines 1.9.0, JUnit 5.11.4, AssertJ 3.27.3.

### Tooling
- CI hardened: first-party GitHub Actions pinned to commit SHAs, Gradle wrapper validation added,
  formatting/dependency checks run on push, and `publish-snapshot` now `needs` the verification jobs
  and publishes a unique per-run `-SNAPSHOT` coordinate. Added Dependabot for Actions and Gradle.

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
