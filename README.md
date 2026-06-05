# Assinafy Android SDK

Kotlin SDK for the [Assinafy API](https://api.assinafy.com.br/v1/docs) — a Brazilian digital signature platform.

Covers documents, signers, assignments, webhooks, workspaces, templates, tags, and the high-level `uploadAndRequestSignatures` helper. All operations use Kotlin coroutines (`suspend` functions) for non-blocking I/O.

## Requirements

- Android SDK API 21+ at runtime; built against Android 15 (API 35)
- JDK 17+ (for building)
- Kotlin 2.3+
- Kotlin Coroutines

Consumer apps still own their `targetSdkVersion` and Google Play compliance; this library only sets its runtime floor and compile SDK.

## Installation

### Gradle (Kotlin DSL)

Add the dependency to your module `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.assinafy:assinafy-android-sdk:1.1.0")
}
```

### Gradle (Groovy DSL)

```groovy
dependencies {
    implementation 'com.assinafy:assinafy-android-sdk:1.1.0'
}
```

Add the GitHub Packages repository if needed:

```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/assinafy/mobile-android-sdk")
        credentials {
            username = System.getenv("GITHUB_ACTOR")
            password = System.getenv("GITHUB_TOKEN")
        }
    }
}
```

## Quick Start

```kotlin
import com.assinafy.sdk.AssinafyClient
import com.assinafy.sdk.AssinafyClientConfig
import com.assinafy.sdk.request.UploadAndRequestSignaturesRequest

val client = AssinafyClient.create(
    AssinafyClientConfig(
        apiKey = BuildConfig.ASSINAFY_API_KEY,
        accountId = BuildConfig.ASSINAFY_ACCOUNT_ID,
    )
)

// In a coroutine scope (e.g. viewModelScope or lifecycleScope):
val pdfBytes = context.assets.open("contract.pdf").readBytes()

val result = client.uploadAndRequestSignatures(
    UploadAndRequestSignaturesRequest(
        fileData = pdfBytes,
        fileName = "contract.pdf",
        signers = listOf(
            UploadAndRequestSignaturesRequest.SignerEntry(
                name = "John Doe",
                email = "john@example.com",
            ),
            UploadAndRequestSignaturesRequest.SignerEntry(
                name = "Jane Smith",
                email = "jane@example.com",
                whatsappPhoneNumber = "+5548999990000",
            ),
        ),
        message = "Please sign this contract",
    )
)

println("Document ID: ${result.document.id}")
println("Assignment ID: ${result.assignment.id}")
```

## Authentication

```kotlin
// Preferred: X-Api-Key header
AssinafyClient.create(AssinafyClientConfig(apiKey = "k_xxx", accountId = "acc_xxx"))

// Legacy: Authorization: Bearer <token>
AssinafyClient.create(AssinafyClientConfig(token = "jwt_xxx", accountId = "acc_xxx"))
```

> Construct the client through the `AssinafyClient.create(...)` factory (it validates the config and
> wires the resources). The constructor is internal.

## Configuration

| Option           | Type      | Default                           | Description                                    |
|------------------|-----------|-----------------------------------|------------------------------------------------|
| `apiKey`         | `String?` | —                                 | Preferred credential (sent as `X-Api-Key`).    |
| `token`          | `String?` | —                                 | Legacy access token (sent as `Bearer`).        |
| `accountId`      | `String?` | —                                 | Default workspace/account ID.                  |
| `baseUrl`        | `String`  | `https://api.assinafy.com.br/v1`  | Override to point to sandbox.                  |
| `webhookSecret`  | `String?` | —                                 | Shared secret used by `WebhookVerifier`.       |
| `timeoutMs`      | `Long`    | `30000`                           | Request timeout in milliseconds.               |
| `logger`         | `Logger?` | no-op                             | Optional `Logger` implementation.              |

### Factory

```kotlin
val client = AssinafyClient.create("api-key", "account-id")
```

## Resources

All resource methods are `suspend` functions and must be called from a coroutine context.

### Documents

```kotlin
val pdfBytes = File("contract.pdf").readBytes()
val doc = client.documents.upload(pdfBytes, "contract.pdf", metadata = mapOf("type" to "service"))

val (data, meta) = client.documents.list(ListParams(page = 1, perPage = 20))
client.documents.details(doc.id)
client.documents.activities(doc.id)
client.documents.waitUntilReady(doc.id, maxWaitMs = 30_000L)
client.documents.download(doc.id, "certificated")
client.documents.thumbnail(doc.id)
client.documents.downloadPage(doc.id, pageId)
client.documents.isFullySigned(doc.id)
client.documents.getSigningProgress(doc.id)
client.documents.getStatuses()
client.documents.delete(doc.id)

// Tags attached to a document
client.documents.listTags(doc.id)
client.documents.addTags(doc.id, listOf("Contracts", "2026-Q1"))   // append (unknown names auto-created)
client.documents.replaceTags(doc.id, listOf("Archived"))           // replace whole set ([] detaches all)
client.documents.detachTag(doc.id, tagId)                          // remove one link (tag itself kept)
```

Uploads are validated locally: only `.pdf` files up to 25 MB are accepted.
`isFullySigned()` reports `true` only once the document is `certificated` (or every signer in the assignment summary has completed).

### Signers

```kotlin
val signer = client.signers.create(
    CreateSignerRequest(
        fullName = "John Doe",
        email = "john@example.com",
        whatsappPhoneNumber = "+5548999990000",
        cpf = "123.456.789-00",
    )
)

client.signers.get(signerId)
client.signers.list(ListParams(page = 1, perPage = 50, search = "john"))
client.signers.update(signerId, UpdateSignerRequest(fullName = "Johnny Doe"))
client.signers.delete(signerId)

val existing = client.signers.findByEmail("john@example.com")

// Signer-facing flows (authenticated by the signer access code, not the API key):
client.signers.getSelf(signerAccessCode)
client.signers.acceptTerms(signerAccessCode)
client.signers.verifyEmail(signerAccessCode, verificationCode)
client.signers.uploadSignature(signerAccessCode, type = SignatureType.SIGNATURE, imageData = pngBytes)        // image/png by default
client.signers.uploadSignature(signerAccessCode, type = SignatureType.INITIAL, imageData = jpegBytes, contentType = "image/jpeg")
client.signers.downloadSignature(signerAccessCode, type = SignatureType.SIGNATURE)

// Signer confirms their contact data + terms (also access-code authenticated):
client.documents.confirmSignerData(
    documentId,
    signerAccessCode,
    ConfirmSignerDataRequest(email = "john@example.com", whatsappPhoneNumber = "+5548999990000", hasAcceptedTerms = true),
)
```

`signers.create()` is idempotent by email: it reuses an existing signer when the same email is already in the workspace (and recovers from the API's duplicate-email error if a concurrent create wins the race).

### Assignments

```kotlin
val assignment = client.assignments.create(
    documentId,
    CreateAssignmentRequest(
        method = "virtual",
        signers = listOf(
            // `step` controls sequential signing: step 2 is notified only after step 1 finishes.
            SignerReference(id = "signer-1", verificationMethod = "Email", notificationMethods = listOf("Email"), step = 1),
            SignerReference(id = "signer-2", verificationMethod = "Whatsapp", notificationMethods = listOf("Whatsapp"), step = 2),
        ),
        message = "Please review and sign",
        expiresAt = "2024-12-31T23:59:00Z",
        copyReceivers = listOf("observer-id"),
    )
)

client.assignments.estimateCost(documentId, CreateAssignmentRequest(
    signers = listOf(SignerReference(verificationMethod = "Whatsapp"))
))
client.assignments.resetExpiration(documentId, assignmentId, "2025-06-30T00:00:00Z")
client.assignments.resetExpiration(documentId, assignmentId, null)   // clear expiration (never expires)
client.assignments.resendNotification(documentId, assignmentId, signerId)
client.assignments.estimateResendCost(documentId, assignmentId, signerId)
client.assignments.listWhatsappNotifications(documentId, assignmentId)
client.assignments.decline(documentId, assignmentId, signerAccessCode, reason = "Terms not acceptable")
```

### Webhooks

```kotlin
client.webhooks.register(
    RegisterWebhookRequest(
        url = "https://example.com/webhooks/assinafy",
        email = "admin@example.com",
        events = listOf(
            "document_ready",
            "document_prepared",
            "signer_signed_document",
            "signer_rejected_document",
            "document_processing_failed",
        ),
    )
)

client.webhooks.get()
client.webhooks.inactivate()
client.webhooks.delete()
client.webhooks.listEventTypes()
client.webhooks.listDispatches(ListParams(perPage = 20))
client.webhooks.retryDispatch(dispatchId)
```

### Webhook Verification

> Note: webhooks are delivered to your **backend**, not the device. Run `WebhookVerifier` on a
> JVM/server receiver (this same artifact works server-side) and keep `webhookSecret` on the server
> only — never bundle it in a shipped Android app.

Assinafy delivers each event as an HTTP `POST` of JSON to your endpoint and expects any `2xx`
response (see the [delivery contract](https://api.assinafy.com.br/v1/docs)). `WebhookVerifier` is an
optional convenience helper for deployments that protect that endpoint with a shared-secret
HMAC-SHA256 signature: configure `webhookSecret` on the client and pass the raw request body plus the
signature header your gateway sends. It also parses the event envelope regardless of whether a secret
is configured.

```kotlin
// In your server endpoint handler:
val signature = request.getHeader("X-Assinafy-Signature") ?: ""
val rawBody = request.body.readBytes()

if (!client.webhookVerifier.verify(rawBody, signature)) {
    // respond 401
}

val event = client.webhookVerifier.extractEvent(rawBody)
val type = client.webhookVerifier.getEventType(event)
val data = client.webhookVerifier.getEventData(event)

when (type) {
    "document_ready"            -> { /* ... */ }
    "signer_signed_document"    -> { /* ... */ }
    "signer_rejected_document"  -> { /* ... */ }
    "document_processing_failed"-> { /* ... */ }
}
```

`getEventData(event)` returns the event's `payload` field, which the live API leaves empty for most
event types — so it usually yields an empty map. For the real event content read the envelope fields
on the `WebhookPayload` returned by `extractEvent(rawBody)`: `event?.subject`, `event?.obj` (the
JSON `object` field), `event?.origin`, and `event?.createdAt`. Event ids are available as constants
in `WebhookEvent` (and `RegisterWebhookRequest.DEFAULT_EVENTS` is the SDK's default subscription).

### Templates

```kotlin
val (data, meta) = client.templates.list(ListParams(search = "NDA", perPage = 20))
val template = client.templates.get(templateId)
val role = requireNotNull(template.roles?.firstOrNull()) { "Template has no signer roles" }

val templateSigners = listOf(
    TemplateSigner(
        roleId = role.id,
        id = signerId,
        verificationMethod = "Email",
        notificationMethods = listOf("Email"),
    )
)

// Simplest form (uses the default options object):
client.documents.createFromTemplate(templateId, templateSigners)

// With a custom name/message — pass the SAME signer list in the options object:
client.documents.createFromTemplate(
    templateId,
    templateSigners,
    CreateDocumentFromTemplateRequest(
        signers = templateSigners,
        name = "NDA - John Doe",
        message = "Please sign at your earliest convenience.",
    ),
)

client.documents.estimateCostFromTemplate(templateId, listOf(TemplateSigner(roleId = "role_id", id = signerId)))
client.documents.verify(signatureHash)
```

### Workspaces

```kotlin
client.workspaces.create(CreateWorkspaceRequest(name = "My Workspace", primaryColor = "#ff0066"))
client.workspaces.list()
client.workspaces.get(accountId)
client.workspaces.update(accountId, UpdateWorkspaceRequest(name = "Renamed"))
client.workspaces.delete(accountId)
```

### Tags

Workspace-scoped labels (unique, case-insensitive) that can be attached to documents and templates.

```kotlin
val tag = client.tags.create("Contracts", color = "ff8800")
client.tags.list(search = "contract")
client.tags.update(tag.id, name = "Sales Contracts")
client.tags.update(tag.id, clearColor = true)        // remove the color
client.tags.delete(tag.id, force = true)             // detach from everything, then delete
```

Per-document attachment lives on the document resource (`listTags`, `addTags`, `replaceTags`, `detachTag`).

## High-Level Helper

Uploads a PDF, waits for processing, reuses or creates signers by email, and kicks off a virtual assignment.

```kotlin
val result = client.uploadAndRequestSignatures(
    UploadAndRequestSignaturesRequest(
        fileData = pdfBytes,
        fileName = "contract.pdf",
        signers = listOf(
            UploadAndRequestSignaturesRequest.SignerEntry(name = "John", email = "john@example.com"),
            UploadAndRequestSignaturesRequest.SignerEntry(name = "Jane", email = "jane@example.com", whatsappPhoneNumber = "+5548999990000"),
        ),
        message = "Please sign",
        metadata = mapOf("year" to 2026),
        waitForReady = true,
        expiresAt = "2026-12-31T00:00:00Z",
    )
)

result.document    // DocumentUploadResponse
result.assignment  // Assignment
result.signerIds   // List<String>
```

## API Reference — request & response payloads

Every JSON response is wrapped in an envelope: `{"status": <int>, "message": <string>, "data": <object|array>}`.
The SDK unwraps `data` for you and returns the typed model. **List pagination is carried in
`X-Pagination-*` response headers** (not the body) and exposed as `PaginatedResult.meta`. The page-size
query parameter is the hyphenated **`per-page`**. All examples below are real sandbox payloads.

Base URLs: production `https://api.assinafy.com.br/v1`, sandbox `https://sandbox.assinafy.com.br/v1`.
Auth header: `X-Api-Key: <key>` (or `Authorization: Bearer <jwt>`).

### Documents

**`upload(fileData, fileName, metadata?, accountId?)`** → `POST /accounts/{accountId}/documents` (multipart)

Request parts: `file` (`application/pdf`), `name` (string), `metadata` (JSON string, optional). Response:

```json
{ "status": 200, "message": "", "data": {
  "resource": "document", "id": "1031fc8e022e1b772886ec81543c", "account_id": "102d25a489f34a275d31a16045fd",
  "template_id": null, "name": "contract.pdf", "status": "uploaded",
  "artifacts": { "original": "https://.../documents/1031.../download/original" },
  "tags": [], "pages": [], "is_closed": false, "created_at": "2026-06-05T19:26:54Z", "updated_at": "2026-06-05T19:26:54Z"
} }
```

**`list(params, accountId?)`** → `GET /accounts/{accountId}/documents?status&method&search&tags&sort&page&per-page`
→ `data: [DocumentListItem]`; meta from `X-Pagination-Current-Page|Per-Page|Total-Count|Page-Count`.

**`details(id)` / `get(id)`** → `GET /documents/{id}` → `DocumentDetails` (adds `assignment`, `pages`, `download_url`/`download_final_url` once certificated).

**`waitUntilReady(id, maxWaitMs=120_000, pollIntervalMs=2_000)`** — polls `details` through `uploaded → metadata_processing → metadata_ready`; throws on `failed`/`rejected_*`/`expired` or timeout.

**`activities(id)`** → `GET /documents/{id}/activities` →

```json
{ "status": 200, "data": [
  { "id": 42, "event": "document_uploaded", "message": "Documento criado.",
    "payload": [], "origin": { "ip": "1.2.3.4", "user-agent": "assinafy-android-sdk/1.1.0" },
    "created_at": "2026-05-11T23:58:21Z" } ] }
```

**`getStatuses()`** → `GET /documents/statuses` → `data: [{ "code": "metadata_ready", "deletable": true }, ...]`
(codes: `uploading, uploaded, metadata_processing, metadata_ready, certificating, certificated, pending_signature, expired, rejected_by_signer, rejected_by_user, failed`).

**`download(id, artifact=DocumentArtifact.CERTIFICATED)`** → `GET /documents/{id}/download/{artifact}` → raw bytes.
**`thumbnail(id)`** → `GET /documents/{id}/thumbnail`. **`downloadPage(id, pageId)`** → `GET /documents/{id}/pages/{pageId}/download`.

**Document tags** — `listTags`/`addTags`/`replaceTags` use `GET`/`POST`/`PUT /accounts/{acc}/documents/{id}/tags`
with body `{"tags": ["Contracts", "2026-Q1"]}` (unknown names auto-created); `detachTag` →
`DELETE /accounts/{acc}/documents/{id}/tags/{tagId}` → `{"detached": true}`. All tag responses return the resulting set:

```json
{ "status": 200, "data": [ { "id": "1031...", "name": "Contracts", "color": "ff8800",
  "created_at": "2026-06-05T19:27:45Z", "updated_at": "2026-06-05T19:27:45Z" } ] }
```

### Signers

**`create(CreateSignerRequest, accountId?)`** → `POST /accounts/{acc}/signers`
Request `{"full_name": "John Doe", "email": "john@example.com", "whatsapp_phone_number": "+5548999990000", "cpf": "12345678900"}`
(idempotent by email — reuses an existing signer and recovers from the API's duplicate-email `400`). Response:

```json
{ "status": 200, "data": { "resource": "signer", "id": "19e6b92e7895332ed9708535d8c",
  "full_name": "John Doe", "email": "john@example.com", "whatsapp_phone_number": null, "has_accepted_terms": false } }
```

**`list` / `get` / `update` / `delete`** → `GET|GET|PUT|DELETE /accounts/{acc}/signers[/{id}]`. **`findByEmail(email)`** pages through `search` results.

Signer-facing (authenticated by `signer-access-code`, not the API key): **`getSelf`** `GET /signers/self?signer-access-code=`,
**`acceptTerms`** `PUT /signers/accept-terms`, **`verifyEmail`** `POST /verify`, **`uploadSignature`**
`POST /signature?signer-access-code=&type=` (raw `image/png`/`image/jpeg` body — not multipart), **`downloadSignature`** `GET /signature/{type}?signer-access-code=`.

### Assignments

**`create(documentId, CreateAssignmentRequest)`** → `POST /documents/{documentId}/assignments`

```json
// request
{ "method": "virtual",
  "signers": [ { "id": "19e6...", "verification_method": "Email", "notification_methods": ["Email"], "step": 1 },
               { "id": "1030...", "verification_method": "Email", "notification_methods": ["Email"], "step": 1 } ],
  "message": "Please sign", "expires_at": "2026-12-31T23:59:00Z", "copy_receivers": ["observer-id"] }
```
```json
// response data
{ "resource": "assignment", "id": "1031fc9ea9afe7ffdea898dff174", "sender_email": "bill@febacapital.com",
  "method": "virtual", "expires_at": null, "message": "Please sign",
  "signers": [ { "id": "19e6...", "full_name": "Bill M", "email": "bill@febacapital.com",
                 "completed": false, "verification_method": "Email", "notification_methods": ["Email"], "step": 1, "notified": true } ],
  "copy_receivers": [], "items": [...], "summary": { "signer_count": 2, "completed_count": 0 },
  "signing_urls": [ { "signer_id": "19e6...", "url": "https://app-sandbox.assinafy.com.br/sign/1031...?email=bill%40febacapital.com" } ] }
```

**`estimateCost(documentId, request)`** → `POST /documents/{id}/assignments/estimate-cost` →

```json
{ "status": 200, "data": { "documents": 1, "credits": 0, "needs_extra_document": false, "extra_document_cost": 0,
  "total_credits": 0, "breakdown": [], "document_balance": 72, "credit_balance": 0,
  "has_sufficient_resources": true, "blocking_reason": null } }
```

**`resetExpiration(documentId, assignmentId, expiresAt?)`** → `PUT .../reset-expiration` with `{"expires_at": "2026-12-31T00:00:00Z"}` or `{"expires_at": null}` (explicit null clears it — never expires).
**`resendNotification(documentId, assignmentId, signerId)`** → `PUT .../signers/{signerId}/resend` → `{"is_sent": true, "document_id": "...", "signer_id": "..."}`.
**`estimateResendCost(...)`** → `POST .../signers/{signerId}/estimate-resend-cost` → `{"total": 0, "breakdown": [{"code": "NotificationEmailResend", "name": "Email Notification Resend", "cost": 0}], "credit_balance": 0, "has_sufficient_credits": true}`.
**`decline(documentId, assignmentId, signerAccessCode, reason)`** → `PUT .../reject?signer-access-code=` with `{"decline_reason": "..."}`.
**`listWhatsappNotifications(...)`** → `GET .../whatsapp-notifications`.

### Templates

**`list` / `get`** → `GET /accounts/{acc}/templates[?status&search&tags&sort&page&per-page]` / `GET /accounts/{acc}/templates/{id}` (response includes `roles: [{id, name}]`).
Create-document-from-template lives on the documents resource: **`createFromTemplate`** → `POST /accounts/{acc}/templates/{templateId}/documents`
with `{"signers": [{"role_id": "...", "id": "...", "verification_method": "Email", "notification_methods": ["Email"]}], "name": "...", "message": "...", "expires_at": "..."}`.

### Tags

**`create`** `POST /accounts/{acc}/tags` `{"name": "Contracts", "color": "ff8800"}`; **`list`** `GET .../tags?search`; **`update`** `PUT .../tags/{id}` (`{"color": null}` clears the color); **`delete`** `DELETE .../tags/{id}?force=true` → `{"deleted": true}`.

### Webhooks (account-scoped subscription)

**`register(RegisterWebhookRequest)`** → `PUT /accounts/{acc}/webhooks/subscriptions`
`{"url": "https://...", "email": "ops@example.com", "events": ["document_ready", ...], "is_active": true}`.
**`get()`** → `GET .../webhooks/subscriptions` (`null` on 404). **`inactivate()`** → `PUT .../webhooks/inactivate`. **`delete()`** → `DELETE .../webhooks/subscriptions`.
**`listEventTypes()`** → `GET /webhooks/event-types` → `data: [{ "id": "document_ready", "description": "..." }, ...]`.
**`listDispatches(params)`** → `GET /accounts/{acc}/webhooks`. **`retryDispatch(id)`** → `POST /accounts/{acc}/webhooks/{id}/retry`.

### Workspaces (accounts)

**`list`** `GET /accounts` → `data: [{ "id": "...", "name": "MT", "roles": ["owner"], "is_delete_allowed": true, "created_at": "..." }]`.
**`get`** `GET /accounts/{id}` (adds `primary_color`, `secondary_color`). **`create`** `POST /accounts` `{"name": "...", "primary_color": "#ff0066"}`. **`update`/`delete`** `PUT|DELETE /accounts/{id}`.

## Error Handling

```kotlin
import com.assinafy.sdk.exceptions.ApiException
import com.assinafy.sdk.exceptions.AssinafyException
import com.assinafy.sdk.exceptions.NetworkException
import com.assinafy.sdk.exceptions.ValidationException

try {
    client.documents.upload(pdfBytes, "contract.pdf")
} catch (e: ValidationException) {
    Log.e("Assinafy", "Validation failed: ${e.message}")
} catch (e: ApiException) {
    Log.e("Assinafy", "API error ${e.statusCode}: ${e.message}")
} catch (e: NetworkException) {
    Log.e("Assinafy", "Network error: ${e.message}")
} catch (e: AssinafyException) {
    Log.e("Assinafy", "SDK error: ${e.message}")
}
```

## Android Integration Example

```kotlin
class DocumentViewModel(
    private val client: AssinafyClient,
) : ViewModel() {

    fun uploadDocument(uri: Uri, context: Context) {
        viewModelScope.launch {
            try {
                val pdfBytes = context.contentResolver.openInputStream(uri)?.readBytes()
                    ?: return@launch
                val doc = client.documents.upload(pdfBytes, "document.pdf")
                // handle success
            } catch (e: AssinafyException) {
                // handle error
            }
        }
    }
}
```

## Development

### Requirements

- JDK 17+
- Android SDK Platform 35 for building
- Set `ANDROID_HOME` environment variable to your Android SDK path

### Building with Docker (Recommended)

```bash
# Build the SDK (includes tests)
docker build -t assinafy-sdk .
docker run --rm --platform linux/amd64 assinafy-sdk ./gradlew :sdk:build --no-daemon

# Run tests only
docker run --rm --platform linux/amd64 assinafy-sdk ./gradlew :sdk:test --no-daemon
```

### Building Locally

```bash
# Configure Android SDK path
export ANDROID_HOME=~/Android/Sdk

# Build and test
./gradlew :sdk:build
./gradlew :sdk:test
```

### Project Structure

```
sdk/
└── src/
    ├── main/kotlin/com/assinafy/sdk/
    │   ├── AssinafyClient.kt
    │   ├── AssinafyClientConfig.kt
    │   ├── Logger.kt
    │   ├── exceptions/
    │   ├── http/
    │   ├── models/
    │   ├── request/
    │   ├── resources/
    │   ├── support/
    │   └── util/
    └── test/kotlin/com/assinafy/sdk/
        ├── helper/
        ├── resources/
        ├── support/
        └── util/
```

## License

MIT
