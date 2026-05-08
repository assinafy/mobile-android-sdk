# Assinafy Android SDK

Kotlin SDK for the [Assinafy API](https://api.assinafy.com.br/v1/docs) — a Brazilian digital signature platform.

Covers documents, signers, assignments, webhooks, workspaces, templates, and the high-level `uploadAndRequestSignatures` helper. All operations use Kotlin coroutines (`suspend` functions) for non-blocking I/O.

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
    implementation("com.assinafy:assinafy-android-sdk:1.0.0")
}
```

### Gradle (Groovy DSL)

```groovy
dependencies {
    implementation 'com.assinafy:assinafy-android-sdk:1.0.0'
}
```

Add the GitHub Packages repository if needed:

```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/assinafy/android-sdk")
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

val client = AssinafyClient(
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
AssinafyClient(AssinafyClientConfig(apiKey = "k_xxx", accountId = "acc_xxx"))

// Legacy: Authorization: Bearer <token>
AssinafyClient(AssinafyClientConfig(token = "jwt_xxx", accountId = "acc_xxx"))
```

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
client.documents.delete(doc.id)
```

Uploads are validated locally: only `.pdf` files up to 25 MB are accepted.

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
```

`signers.create()` is idempotent by email: it reuses an existing signer when the same email is already in the workspace.

### Assignments

```kotlin
val assignment = client.assignments.create(
    documentId,
    CreateAssignmentRequest(
        method = "virtual",
        signers = listOf(SignerReference.ofId("signer-1"), SignerReference.ofId("signer-2")),
        message = "Please review and sign",
        expiresAt = "2024-12-31T23:59:00Z",
        copyReceivers = listOf("observer-id"),
    )
)

client.assignments.estimateCost(documentId, CreateAssignmentRequest(
    signers = listOf(SignerReference(verificationMethod = "Whatsapp"))
))
client.assignments.resetExpiration(documentId, assignmentId, "2025-06-30T00:00:00Z")
client.assignments.resendNotification(documentId, assignmentId, signerId)
client.assignments.estimateResendCost(documentId, assignmentId, signerId)
client.assignments.cancel(documentId, "No longer needed")
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

Webhook payloads are signed with HMAC-SHA256. Assinafy sends the hex digest in the `X-Assinafy-Signature` header.

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

### Templates

```kotlin
val (data, meta) = client.templates.list(ListParams(search = "NDA", perPage = 20))
val template = client.templates.get(templateId)
val role = requireNotNull(template.roles?.firstOrNull()) { "Template has no signer roles" }

client.documents.createFromTemplate(
    templateId,
    listOf(
        TemplateSigner(
            roleId = role.id,
            id = signerId,
            verificationMethod = "Email",
            notificationMethods = listOf("Email"),
        )
    ),
    CreateDocumentFromTemplateRequest(
        signers = emptyList(),
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
