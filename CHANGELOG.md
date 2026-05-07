# Changelog

All notable changes to the Assinafy Android SDK will be documented in this file.

## [1.0.0] - 2026-05-06

### Added
- Initial release of the Assinafy Android SDK (Kotlin)
- `AssinafyClient` — primary entry point with configurable API key / token authentication
- `DocumentResource` — upload, list, details, download, thumbnail, activities, delete, `waitUntilReady`, `isFullySigned`, `getSigningProgress`, `createFromTemplate`, `estimateCostFromTemplate`, `verify`
- `SignerResource` — create (idempotent by email), get, list, update, delete, `findByEmail`
- `AssignmentResource` — create, `estimateCost`, `resetExpiration`, `resendNotification`, `estimateResendCost`, cancel
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
