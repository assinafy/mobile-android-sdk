# Assinafy Android SDK

*[Leia em português](README.md) · English*

Coroutine-first Kotlin client for the [Assinafy API v1](https://api.assinafy.com.br/v1/docs). It covers documents, signers, assignments, public signing, OAuth 2.1/PKCE, biometrics, ICP-Brasil A1/A3 certificates, templates, contracts, users, tags, fields, and webhooks. Every network method is `suspend`.

## Requirements and installation

- Android API 21+, Java 17 consumer bytecode, and TLS 1.2+.
- SDK build: Kotlin 2.4.21, Gradle 9.5, AGP 9.3.3, and JDK 25 LTS.
- Runtime dependencies: OkHttp 5, Gson, and kotlinx-coroutines-core.

```kotlin
// settings.gradle.kts: google(); mavenCentral()
dependencies { implementation("com.assinafy:assinafy-android-sdk:2.9.0") }
```

The AAR declares `INTERNET` and ships consumer R8 rules. See [API_REFERENCE.md](docs/API_REFERENCE.md) for every operation and payload, [API_COVERAGE.md](docs/API_COVERAGE.md) for the endpoint map, and [TESTING.md](docs/TESTING.md) for test tiers.

## Credentials and clients

API keys and account tokens are backend secrets: never put them in an APK, logs, analytics, or device storage. A distributed app either uses public OAuth with PKCE to act in the user's workspace or receives only application-specific data from your backend. A signer access code is also a credential and belongs only to the signing session.

```kotlin
val backend = AssinafyClient.create(
    apiKey = apiKey,
    accountId = accountId,
    baseUrl = "https://api.assinafy.com.br/v1",
)

val signerClient = AssinafyClient.create(
    AssinafyClientConfig(baseUrl = "https://api.assinafy.com.br/v1"),
)
```

The client keeps authenticated, login/OAuth, and public-signing transports separate. Credentials are stripped on cross-origin redirects; mutations do not follow redirects, do not retry automatically, and use one-shot bodies. Reuse one client per configuration.

## Public OAuth 2.1 with PKCE

Register a **Public** application, an exact HTTPS redirect URI, and the required scopes. Android cannot keep a `client_secret`; the SDK rejects any non-null value.

```kotlin
val oauthClient = AssinafyClient.create(
    AssinafyClientConfig(
        baseUrl = "https://api.assinafy.com.br/v1",
        oauth = OAuthConfig(
            clientId = clientId,
            redirectUri = "https://app.example/oauth/callback",
            scopes = listOf(OAuthScope.DOCUMENTS_READ, OAuthScope.DOCUMENTS_WRITE, OAuthScope.OFFLINE_ACCESS),
        ),
    ),
)
val attempt = oauthClient.oauth.authorizationRequest() // open attempt.url in a Custom Tab
val code = oauthClient.oauth.parseCallback(callbackUri, attempt) // validates URI, state, and iss
val tokens = oauthClient.oauth.exchangeCode(code, attempt.pkce.codeVerifier)
val authorized = AssinafyClient.create(AssinafyClientConfig(token = tokens.accessToken, accountId = accountId))
```

The code expires in 60 seconds. Keep `state` and the verifier only until exchange. Access tokens last one hour. When `offline_access` is granted, each `refresh(latestRefreshToken)` returns a new refresh token and retires the old one; persist the new pair atomically and never replay an uncertain refresh. `revoke` ends the connection. `ApiException.challenge` reports a missing scope; reconnect with that scope instead of retrying.

For sandbox, read the issuer through `protectedResourceMetadata()`; production uses `https://auth.assinafy.com.br`. Login with MFA throws `MfaRequiredException`; finish with `authentication.verifyMfa(...)`.

## Complete flow: PDF to certificate

Run these account operations on a trusted backend or with an authorized OAuth token.

```kotlin
val uploaded = backend.documents.upload(pdfBytes, "agreement.pdf")
val document = backend.documents.waitUntilReady(uploaded.id)
val signer = backend.signers.create(
    CreateSignerRequest(
        fullName = "Example Person",
        email = "person@example.com",
        whatsappPhoneNumber = "+5511999999999",
        governmentId = "00000000000",
    ),
)
val request = CreateAssignmentRequest(
    method = AssignmentMethod.VIRTUAL,
    signers = listOf(SignerReference.over(signer.id, NotificationMethod.EMAIL)),
)
val estimate = backend.assignments.estimateCost(document.id, request)
require(estimate.hasSufficientResources != false)
val assignment = backend.assignments.create(document.id, request)
val invitationUrl = assignment.signingUrls.orEmpty().single().url // Email: resend page; otherwise null
```

For `collect`, use IDs from `document.pages` and `fields.list()` in `entries`; every signer must own at least one field. For `virtual`, confirm signer data and submit `entries = emptyList()` when signing. Estimate first whenever cost or entitlement can block creation.

| Verification | Notification | Requirements and completion |
|---|---|---|
| `Email` | `Email` | Email OTP; free default |
| `Whatsapp` | `Whatsapp` | WhatsApp OTP; paid plan and notification charge |
| `IdDocumentFaceMatchBasic` | `Email` or `Whatsapp` | Valid CPF, entitlement, consent, and hosted capture |
| `DigitalCertificate` | `Email` or `Whatsapp` | CPF/CNPJ, entitlement, `virtual`, signer alone in the step; ICP-Brasil A1/A3 on the hosted page |

Send one method, both, or neither; the API infers the missing side and defaults both to Email. Each signer has exactly one notification channel. `estimateCost` is the source of the final price.

The returned Email URL is an invitation-resend page, without document access. Use the `accessCode` from the delivered invitation without logging it. This example is for Email/Whatsapp; biometric and certificate signing follow the flows below:

```kotlin
val self = signerClient.signerDocuments.self(accessCode)
signerClient.signerDocuments.verifyEmail(accessCode, VerifySignerEmailRequest(otp))
val documentToSign = signerClient.signerDocuments.getCurrent(self.id, accessCode)
signerClient.signerDocuments.confirmData(documentToSign.id, accessCode, ConfirmSignerDataRequest(email = "person@example.com"))
signerClient.signerDocuments.acceptTerms(accessCode)
val assignmentToSign = requireNotNull(signerClient.signerDocuments.getAssignment(accessCode).assignment)
signerClient.signerDocuments.sign(documentToSign.id, assignmentToSign.id, accessCode, fieldValues)
```

For biometrics, read `getIdentityVerification(accessCode)`. Only when `canStart == true`, obtain explicit consent, call `startIdentityVerification(accessCode, consent = true)`, and open the hosted URL; never log or store it. Pending/InReview/Approved/Declined/Abandoned sessions cannot be replaced. The account reviews with `assignments.getIdentityVerification`, downloads only the named images for temporary use, and calls `reviewIdentityVerification`; rejection cancels the document.

For `DigitalCertificate`, open the delivered invitation in a compatible browser. Assinafy runs the A1/A3 handshake; the app never receives the private key or calls the browser-extension routes. After every signature and review completes:

```kotlin
val finalState = backend.documents.details(document.id) // after the DOCUMENT_READY webhook
require(finalState.status == DocumentStatus.CERTIFICATED)
val pdf = backend.documents.download(document.id, DocumentArtifact.CERTIFICATED)
val verification = backend.documents.verify(requireNotNull(finalState.verificationCode))
```

Prefer webhooks over polling for `DOCUMENT_READY`, failures, signatures, identity, and contracts.

## Envelopes and embedded signing

To send multiple PDFs together, upload and wait for `metadata_ready` on each, then estimate and create:

```kotlin
val envelopeRequest = CreateEnvelopeRequest("Example", listOf(SignerReference.ofId(signer.id)), readyDocumentIds.map { EnvelopeDocumentRequest(it, listOf(signer.id)) }, idempotencyKey = requestId)
val envelopeCost = backend.envelopes.estimateCost(envelopeRequest)
val envelope = backend.envelopes.create(envelopeRequest.copy(maxTotalCredits = BigDecimal(requireNotNull(envelopeCost.totalCredits).toString())))
backend.envelopes.activities(envelope.id, ListParams(page = 1, perPage = 20))
```

Creation does not guarantee delivery: inspect notification history. Use `get`, `update`, `addDocument`, `removeDocument`, `resendNotification`, `cancel`, `download` and `delete`; deleting a closed envelope preserves documents. Reuse the key only for an explicit retry of the same creation. Templates also accept `idempotencyKey` and `maxTotalCredits`.

For embedding, register an HTTPS origin through `embeddedSigning.addOrigin` on your backend and create the assignment signer with `embedded=true` (Email/Whatsapp). Mint `createSession(documentId, assignmentId, signerId)` for each opening; load the URL once within five minutes in an iframe on that origin. Never log or prefetch it. A native app opens this hosted integration page; Assinafy exchanges its token internally.

`workspaces.get(accountId, expandSigningFeatures=true)` exposes plan entitlements; `getUsage` exposes balances. Alert policy and personal push preferences have separate methods unavailable to third-party OAuth. Signer downloads require the code and support only `original`; after signing, read `certifiedCopy` and use `resendCertifiedCopy` for delivery through the original channel.

## Webhooks

Create an HTTPS endpoint with `signingEnabled = true`, retain the one-time returned `secret`, and verify the raw body bytes. OAuth may create or rotate a secret but cannot read an existing one.

```kotlin
val endpoint = backend.webhooks.createEndpoint(
    CreateWebhookEndpointRequest("https://hooks.example/assinafy", "ops@example.com", events, signingEnabled = true),
)
val verifier = WebhookVerifier(requireNotNull(endpoint.secret))
require(verifier.verifySignature(rawBody, headers["webhook-id"], headers["webhook-timestamp"], headers["webhook-signature"]))
```

`testEndpoint` sends a test delivery. For overlap during rotation, use `rotateEndpointSecret(id, keepPrevious=true)` and switch before `previousSecretExpiresAt` (24 hours). Verification uses HMAC-SHA256, constant-time checking, and a 300-second replay window. Process the event only afterward and make consumers idempotent by `webhook-id`.

## Contract management

Upload the instrument, retain its ID, and save its terms. Money uses `BigDecimal`; send the last `updatedAt` with each mutation so stale writes return `409`.

```kotlin
val contract = backend.contracts.update(
    document.id,
    UpdateContractRequest(counterpartyName = "Example Company", value = BigDecimal("1250.00"), currency = "BRL", endDate = "2027-12-31"),
) as DocumentContract
val renewed = backend.contracts.renew(document.id, RenewContractRequest(term = contract.term, updatedAt = contract.updatedAt))
backend.contracts.terminate(document.id, TerminateContractRequest("Ended by agreement", renewed.updatedAt))
```

For amendments, send `parentDocumentId`, `relationType`, and only the delta, then use the regular signing flow. `related` returns the root and instruments; references replace the entire list; `clearFields` sends explicit null. Never replay mutations automatically.

## Resources, responses, and errors

`client` exposes `authentication`, `oauth`, `workspaces`, `documents`, `signers`, `signerDocuments`, `assignments`, `templates`, `fields`, `tags`, `contracts`, `users`, and `webhooks`. Normal responses unwrap `{status,message,data}`; OAuth uses flat RFC objects. Lists return `PaginatedResult`, with header metadata and the `per-page` query.

- `ValidationException`: input rejected before network I/O.
- `ApiException`: non-2xx HTTP/envelope response, including data, challenge, and rate limits.
- `OAuthException`: flat OAuth error.
- `NetworkException`: I/O failure. Coroutine cancellation is preserved.

Only `GET`/`HEAD`/`OPTIONS` retry `429`, at most twice and for up to 30 seconds. Use structured scopes (`viewModelScope`, `lifecycleScope`) and never block the main thread.

## Build and tests

Docker is the reproducible build path:

```shell
docker compose build build
docker compose run --rm build ./gradlew :sdk:assembleRelease :sdk:test :sdk:lintDebug :sdk:ktlintCheck :sdk:dokkaGeneratePublicationHtml :sdk:publishReleasePublicationToMavenLocal :consumer-smoke:assembleRelease -Pversion=0.0.0-ci-SNAPSHOT --no-daemon
```

Live tests are opt-in, use the `ASSINAFY_*` variables documented in [TESTING.md](docs/TESTING.md), and should target sandbox with writes disabled by default. The project follows SemVer; see [CHANGELOG.md](CHANGELOG.md).
