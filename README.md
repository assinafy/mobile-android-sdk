# Assinafy Android SDK

Coroutine-first Kotlin client for the
[Assinafy v1 API](https://api.assinafy.com.br/v1/docs), covering account administration,
documents, signers, signing, assignments, authentication, fields, users, tags, templates, and
webhooks.

[API reference](docs/API_REFERENCE.md) documents every SDK method, request body, response model, and
error.

## Requirements

- Android API 21 or newer at runtime; compiled against stable Android 17 / API 37.0
- Java 17-compatible consumer bytecode
- JDK 25 LTS to run the build, with a Java 17 toolchain for compilation
- Kotlin coroutines

The consuming application owns `targetSdk`. The AAR supplies the Android `INTERNET` permission and
consumer R8/ProGuard rules.

## Installation

To use the checked-out source immediately, publish it to Maven Local:

```shell
./gradlew :sdk:publishReleasePublicationToMavenLocal \
  -Pversion=2.0.1-local-SNAPSHOT \
  --no-daemon
```

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
    }
}

// app/build.gradle.kts
dependencies {
    implementation("com.assinafy:assinafy-android-sdk:2.0.1-local-SNAPSHOT")
}
```

Published releases use the same coordinate in GitHub Packages. Configure the repository only when
that version exists there, and read credentials from the environment:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/assinafy/mobile-android-sdk")
            credentials {
                username = providers.environmentVariable("GITHUB_ACTOR").orNull
                password = providers.environmentVariable("GITHUB_TOKEN").orNull
            }
        }
    }
}
```

Never commit package credentials.

## Security and client configuration

An account API key is a long-lived backend credential. Do not ship it in a distributed APK, log it,
or persist it on a device. Run account operations in a trusted backend or controlled JVM process,
then expose only the application-specific result to the Android app. Signer-facing operations use a
short-lived signer access code and a separate credentialless transport.

```kotlin
fun accountClient(apiKey: String, accountId: String, sandbox: Boolean): AssinafyClient =
    AssinafyClient.create(
        apiKey = apiKey,
        accountId = accountId,
        baseUrl = if (sandbox) {
            "https://sandbox.assinafy.com.br/v1"
        } else {
            SdkConstants.DEFAULT_BASE_URL
        },
    )
```

Use either `apiKey` (`X-Api-Key`) or `token` (`Authorization: Bearer`), never both. `baseUrl` is the
full API prefix against which SDK routes are appended. Assinafy-hosted URLs include `/v1`; reverse
proxies may use another path prefix. A trailing slash is accepted. User information, queries, and
fragments are rejected, and credentials require HTTPS except for loopback hosts.
Create one client per configuration and reuse it for the process lifetime so its OkHttp connection
pools and dispatchers are reused.

## Document signing flow

### 1. Upload, resolve the signer, and request a virtual signature

The high-level helper validates every signer, uploads the PDF, waits for metadata processing,
reuses or creates each signer by exact email, and creates the assignment. Read Android assets with
`use` so the stream is always closed.

```kotlin
suspend fun requestSignature(
    client: AssinafyClient,
    context: Context,
    signerName: String,
    signerEmail: String,
): UploadAndRequestSignaturesResult {
    val pdfBytes = context.assets.open("agreement.pdf").use { it.readBytes() }

    return client.uploadAndRequestSignatures(
        UploadAndRequestSignaturesRequest(
            fileData = pdfBytes,
            fileName = "agreement.pdf",
            signers = listOf(
                UploadAndRequestSignaturesRequest.SignerEntry(
                    name = signerName,
                    email = signerEmail,
                ),
            ),
            message = "Please review and sign",
        ),
    )
}
```

The result contains the complete `DocumentDetails`, created `Assignment`, resolved signer IDs, and
the assignment's per-signer `signingUrls`. Deliver the intended signing URL through a trusted
channel. Do not log or persist the access code embedded in its final `/sign/{accessCode}` path;
deployments can append recipient metadata in the query.

For collect fields, sequential signing, WhatsApp, or digital-certificate verification, use
`signers.create`, `documents.upload`/`waitUntilReady`, and `assignments.create` directly. The exact
payloads and verification/notification coupling rules are in the
[API reference](docs/API_REFERENCE.md#assignmentresource).

### 2. Complete a virtual signature in the signer-facing app

Construct a credentialless client. Pass the access code, one-time verification code, and PNG bytes
into the signing function explicitly; do not source them from long-lived application storage.

```kotlin
val signerClient = AssinafyClient.create(
    AssinafyClientConfig(baseUrl = "https://sandbox.assinafy.com.br/v1"),
)

suspend fun signVirtualDocument(
    client: AssinafyClient,
    signerAccessCode: String,
    verificationCode: String,
    signaturePng: ByteArray,
): Map<String, Any> {
    val signer = client.signerDocuments.self(signerAccessCode)
    val document = client.signerDocuments.getAssignment(signerAccessCode)
    val assignment = requireNotNull(document.assignment) { "Document has no assignment" }

    client.signerDocuments.verifyEmail(
        signerAccessCode,
        VerifySignerEmailRequest(verificationCode),
    )
    client.signerDocuments.acceptTerms(signerAccessCode)
    client.signerDocuments.confirmData(
        document.id,
        signerAccessCode,
        ConfirmSignerDataRequest(
            fullName = signer.fullName,
            email = signer.email,
        ),
    )
    client.signerDocuments.uploadSignature(
        signerAccessCode = signerAccessCode,
        imageData = signaturePng,
        type = SignatureType.SIGNATURE,
        reuse = true,
    )

    // Virtual assignments have no collect-field items, so the contract body is exactly [].
    return client.signerDocuments.sign(
        documentId = document.id,
        assignmentId = assignment.id,
        signerAccessCode = signerAccessCode,
        entries = emptyList(),
    )
}
```

`verifyEmail` also handles the code delivered for the API's WhatsApp verification flow. Collect
assignments pass their `SignAssignmentItemRequest` values instead of an empty list. The v1 OpenAPI
does not define the certificate start/complete routes referenced by its digital-certificate prose,
so this SDK does not guess those request or response schemas.

### 3. Observe completion and download the certificated PDF

Use a backend webhook or poll account-side details until the status is `certificated`.
`isFullySigned` can become true during the preceding `certificating` state, before the immutable
artifact is ready:

```kotlin
suspend fun downloadCompletedPdf(client: AssinafyClient, documentId: String): ByteArray {
    val document = client.documents.details(documentId)
    check(document.status == DocumentStatus.CERTIFICATED) { "Document is not certificated" }
    return client.documents.download(documentId, DocumentArtifact.CERTIFICATED)
}
```

Webhooks belong on a backend, not an Android device. `WebhookVerifier` is an optional HMAC-SHA256
helper only for gateways that supply a shared signature; `webhookSecret` is never sent to Assinafy.

## Responses and errors

Assinafy JSON responses use an envelope such as:

```json
{"status":200,"message":"OK","data":{}}
```

The SDK validates HTTP and envelope status, unwraps `data` into the documented type, and reads
pagination from `X-Pagination-*` headers. Binary endpoints return unmodified bytes. Network methods
are cancellable `suspend` functions; cancellation cancels the underlying OkHttp call.

Handle typed failures at the application boundary:

```kotlin
try {
    client.documents.details(documentId)
} catch (error: ValidationException) {
    showInputError(error.message)
} catch (error: ApiException) {
    reportApiStatus(error.statusCode) // Never log responseData without redaction.
} catch (error: NetworkException) {
    showRetryableNetworkError()
}
```

Full request fields, response fields, authentication modes, and error semantics are centralized in
the [API reference](docs/API_REFERENCE.md).

## Resource map

| Resource | Purpose |
|---|---|
| `client.authentication` | Login, password management, social login, personal API keys |
| `client.workspaces` | Accounts, themes, logos, and account statistics |
| `client.documents` | Upload, search, artifacts, public access, templates, and document tags |
| `client.signers` | Account signer CRUD |
| `client.signerDocuments` | Signer-facing documents, verification, signatures, and decisions |
| `client.assignments` | Signature requests, pricing, expiration, resend, and delivery history |
| `client.fields` | Field definitions, types, and validation |
| `client.users` | Profile, cross-account statistics, and notification preferences |
| `client.tags` | Tag CRUD |
| `client.templates` | Template reads |
| `client.webhooks` | Subscription and delivery management |

## Migrating from 1.x to 2.0

Recompile every 1.x consumer against 2.0. `DocumentListItem`, `DocumentUploadResponse`,
`WorkspaceListItem`, and `TemplateListItem` are now Kotlin type aliases of their complete models, so
their distinct 1.x JVM classes were removed. Fields omitted by list, search, or upload projections
are nullable and must be checked before use.

## License

MIT. See [LICENSE](LICENSE).
