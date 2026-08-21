# Assinafy Android SDK

Coroutine-first Kotlin client for the
[Assinafy v1 API](https://api.assinafy.com.br/v1/docs). The SDK covers all 89 operations in the
current OpenAPI document, including account administration, documents, signers, signing,
assignments, authentication, fields, users, tags, templates, and webhooks.

- [Complete API reference](docs/API_REFERENCE.md)
- [89/89 operation coverage](docs/API_COVERAGE.md)
- [Build, unit, and sandbox testing](docs/TESTING.md)

## Requirements

- Android API 21 or newer at runtime; compiled against Android API 36
- Java 17-compatible consumer bytecode
- JDK 25 LTS to run the build and an installed Java 17 toolchain for compilation
- Kotlin coroutines

The consuming application owns `targetSdk`. The AAR supplies the required Android `INTERNET`
permission and consumer R8/ProGuard rules.

## Installation

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("com.assinafy:assinafy-android-sdk:2.0.0")
}
```

If the artifact is resolved from GitHub Packages, add the package repository and read its token from
the environment rather than committing credentials:

```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/assinafy/mobile-android-sdk")
        credentials {
            username = providers.environmentVariable("GITHUB_ACTOR").orNull
            password = providers.environmentVariable("GITHUB_TOKEN").orNull
        }
    }
}
```

## Migrating from 1.x to 2.0

Recompile every 1.x consumer against 2.0. `DocumentListItem`, `DocumentUploadResponse`,
`WorkspaceListItem`, and `TemplateListItem` are now Kotlin type aliases of their complete models, so
their distinct 1.x JVM classes were removed. Document fields omitted by list, search, or upload
projections—including `accountId`, `tags`, `pages`, and `isClosed`—are nullable and must be checked
before use.

## Account-authenticated quick start

Account API keys are long-lived secrets. For a distributed Android application, call account
operations from your backend and expose only the minimum application-specific API to the device.
The direct client shown here is appropriate for a trusted Android/JVM environment and local sandbox
testing.

```kotlin
val client = AssinafyClient.create(
    AssinafyClientConfig(
        apiKey = BuildConfig.ASSINAFY_API_KEY,
        accountId = BuildConfig.ASSINAFY_ACCOUNT_ID,
        baseUrl = "https://sandbox.assinafy.com.br/v1",
    )
)

// Call suspend functions from a lifecycle-aware scope.
viewModelScope.launch {
    val documents = client.documents.list(ListParams(page = 1, perPage = 25))
    documents.data.forEach { document -> println(document.name) }
}
```

Use either `apiKey` (`X-Api-Key`) or `token` (`Authorization: Bearer`), never both. Production uses
`https://api.assinafy.com.br/v1` by default.

## Upload and request signatures

```kotlin
val result = client.uploadAndRequestSignatures(
    UploadAndRequestSignaturesRequest(
        fileData = context.assets.open("agreement.pdf").readBytes(),
        fileName = "agreement.pdf",
        signers = listOf(
            UploadAndRequestSignaturesRequest.SignerEntry(
                name = "Example Signer",
                email = "signer@example.com",
            )
        ),
        message = "Please review and sign",
    )
)

println(result.document.id)
println(result.assignment.id)
```

The helper uploads a PDF, waits for metadata processing, reuses or creates signers by email, and
creates a virtual assignment. For custom verification, sequential signing, collect fields, or
transaction-specific recovery, call the resource methods directly.

## Public and signer-facing use

A credentialless client can perform login/password-reset and public/signer operations. It cannot
perform account operations.

```kotlin
val publicClient = AssinafyClient.create(
    AssinafyClientConfig(baseUrl = "https://sandbox.assinafy.com.br/v1")
)

val publicDocument = publicClient.documents.getPublic("doc_example")
publicClient.documents.sendToken("doc_example", "signer@example.com")
val verification = publicClient.documents.verify("document_signature_hash")

val signer = publicClient.signerDocuments.self(signerAccessCode)
val document = publicClient.signerDocuments.getAssignment(signerAccessCode)
publicClient.signerDocuments.acceptTerms(signerAccessCode)
publicClient.signerDocuments.verifyEmail(
    signerAccessCode,
    VerifySignerEmailRequest(verificationCode = oneTimeCode),
)
publicClient.signerDocuments.uploadSignature(
    signerAccessCode = signerAccessCode,
    imageData = pngBytes,
    type = SignatureType.SIGNATURE,
    reuse = true,
)
```

The SDK places `signer-access-code` in the query string exactly as required by the API. Signature
uploads are raw PNG bodies, not JSON or multipart. Never log or persist the signer code.

## Common operations

```kotlin
// Signer CRUD
val signer = client.signers.create(
    CreateSignerRequest(
        fullName = "Example Signer",
        email = "signer@example.com",
    )
)

// Sequential virtual assignment
val assignment = client.assignments.create(
    "doc_example",
    CreateAssignmentRequest(
        signers = listOf(
            SignerReference(id = signer.id, step = 1),
            SignerReference(id = "signer_second", step = 2),
        ),
        message = "Please sign in order",
    )
)

// Tags: create first, then attach IDs—not names.
val tag = client.tags.create("Contracts", color = "2072b9")
client.documents.addTags("doc_example", listOf(tag.id))

// Account sender identity and compatibility colors (six hex digits, no '#').
client.workspaces.update(
    "acc_example",
    UpdateWorkspaceRequest(
        notificationSenderType = "Account",
        primaryColor = "2072b9",
        secondaryColor = "f2f5f8",
    )
)
```

Every JSON response is an envelope such as
`{"status":200,"message":"OK","data":{...}}`; the SDK unwraps `data` into a model when the schema
is fixed and a documented `Map`/`Any` where the API payload is dynamic. List pagination is read from
`X-Pagination-*` response headers and returned as `PaginatedResult.meta`. The complete
request/response field tables, binary behavior, authentication mode, and error semantics are in the
[API reference](docs/API_REFERENCE.md).

## Resource map

| Resource | Purpose |
|---|---|
| `client.authentication` | Login, password management, social login, personal API keys |
| `client.workspaces` | Accounts, themes, logos, account KPIs |
| `client.documents` | Upload/search/download, public access, templates, document tags |
| `client.signers` | Account signer CRUD |
| `client.signerDocuments` | Complete signer-facing document and signing flow |
| `client.assignments` | Signature requests, pricing, expiration, resend history |
| `client.fields` | Field definitions, types, and validation |
| `client.users` | Profile, cross-account KPIs, notification preferences |
| `client.tags` | Tag CRUD |
| `client.templates` | Template reads |
| `client.webhooks` | Subscription and delivery history |

All network methods are cancellable `suspend` functions. Handle `ValidationException`,
`ApiException`, and `NetworkException` at the application boundary. Do not log raw
`ApiException.responseData`; an API may echo sensitive request values in validation errors.

## Webhook verification

Webhooks are delivered to a backend, not directly to an Android device. `WebhookVerifier` is an
optional server-side HMAC-SHA256 helper for deployments whose gateway supplies a shared signature.
Keep `webhookSecret` exclusively on the backend. See the
[webhook section](docs/API_REFERENCE.md#webhookresource) for the exact behavior.

## Verification

```shell
./gradlew :sdk:assembleRelease :sdk:test :sdk:lintDebug :sdk:ktlintCheck --no-daemon
```

Live sandbox tests are opt-in and secret-driven. See [TESTING.md](docs/TESTING.md) before enabling
write tests.

## License

MIT. See [LICENSE](LICENSE).
