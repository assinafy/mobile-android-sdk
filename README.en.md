# Assinafy Android SDK

*[Leia em português](README.md) · English*

A coroutine-first Kotlin client for the [Assinafy v1 API](https://api.assinafy.com.br/v1/docs). It
covers every published v1 operation: account administration, document upload and retrieval,
signers, signature assignments, the signer-facing signing flow, password and OAuth 2.1
authentication, field definitions, tags, templates, users, and webhooks.

This document reads front to back: set up a client, walk one document through its full signature
lifecycle, then handle the surrounding concerns — errors, pagination, testing, and releases. Two
companion documents go deeper:

- [API reference](docs/API_REFERENCE.md) — every SDK function with its exact route, query, request
  body, response model, and error semantics.
- [Operation index](docs/API_COVERAGE.md) — every published v1 operation mapped to SDK methods,
  plus the places where the deployed service and the published schema differ.
- [Building and testing](docs/TESTING.md) — the supported build environment and the live test
  boundary.

## Contents

1. [Requirements](#requirements)
2. [Installation](#installation)
3. [Creating a client](#creating-a-client)
4. [Where each credential belongs](#where-each-credential-belongs)
5. [OAuth 2.1](#oauth-21)
6. [The document lifecycle](#the-document-lifecycle)
   - [Step 1 — Upload a PDF](#step-1--upload-a-pdf)
   - [Step 2 — Resolve the signers](#step-2--resolve-the-signers)
   - [Step 3 — Estimate the cost](#step-3--estimate-the-cost)
   - [Step 4 — Request signatures](#step-4--request-signatures)
   - [Step 5 — Sign, as the signer](#step-5--sign-as-the-signer)
   - [Step 6 — Track completion](#step-6--track-completion)
   - [Step 7 — Download and verify](#step-7--download-and-verify)
7. [Verification and notification methods](#verification-and-notification-methods)
8. [The one-call shortcut](#the-one-call-shortcut)
9. [Everything else on the client](#everything-else-on-the-client)
10. [Responses, pagination, and errors](#responses-pagination-and-errors)
11. [Coroutines and lifecycle](#coroutines-and-lifecycle)
12. [Building and testing](#building-and-testing)
13. [Versioning](#versioning)
14. [License](#license)

## Requirements

| Requirement | Value |
|---|---|
| Runtime | Android API 21 or newer |
| Compiled against | Stable Android 17 / API 37.0 |
| Consumer bytecode | Java 17 |
| Build JDK | JDK 25 LTS, with a Java 17 toolchain for compilation |
| Language | Kotlin with coroutines |

The consuming application owns `targetSdk`. The AAR declares the Android `INTERNET` permission and
carries a consumer ProGuard file, so a minified release build needs no extra configuration: the SDK's
serialized model fields are annotated with `@SerializedName` and survive Gson's own R8 rules.

## Installation

The coordinate is `com.assinafy:assinafy-android-sdk`.

To build against a checkout, publish it to Maven Local first:

```shell
./gradlew :sdk:publishReleasePublicationToMavenLocal \
  -Pversion=2.1.0-local-SNAPSHOT \
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
    implementation("com.assinafy:assinafy-android-sdk:2.1.0-local-SNAPSHOT")
}
```

Published releases are public on Maven Central and require no package credentials:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

dependencies {
    implementation("com.assinafy:assinafy-android-sdk:2.1.0")
}
```

## Creating a client

`AssinafyClient.create` validates the configuration and returns a client whose resources are exposed
as properties. Build one per configuration and reuse it for the process lifetime so its OkHttp
connection pool and dispatcher are shared.

```kotlin
fun accountClient(apiKey: String, accountId: String): AssinafyClient =
    AssinafyClient.create(
        apiKey = apiKey,
        accountId = accountId,
        baseUrl = SdkConstants.DEFAULT_BASE_URL, // https://api.assinafy.com.br/v1
    )
```

`AssinafyClientConfig` exposes the full surface when you need more than the four common settings:

```kotlin
val client = AssinafyClient.create(
    AssinafyClientConfig(
        apiKey = apiKey,          // sent as X-Api-Key
        token = null,             // or a bearer token; the two are mutually exclusive
        accountId = accountId,    // default account for account-scoped calls
        baseUrl = "https://api.assinafy.com.br/v1",
        webhookSecret = null,     // local HMAC verification only; never sent to Assinafy
        timeoutMs = 30_000L,      // connect, read, and write
        logger = null,            // Logger.NONE by default; secrets are never logged
        oauth = null,             // registered OAuth application; see OAuth 2.1
    ),
)
```

`baseUrl` is the complete API prefix that SDK routes are appended to. Assinafy-hosted URLs include
`/v1`; a reverse proxy may use another path prefix. A trailing slash is accepted. User information,
queries, and fragments are rejected, and supplying a credential requires HTTPS unless the host is
`localhost`, `127.0.0.1`, or `::1`.

Every account-scoped method takes an optional `accountId` that overrides the client default, so one
client can serve several workspaces. `client.workspaces` is the exception: its methods always name
their account explicitly, because `list()` spans every reachable account and `delete()` is
irreversible.

## Where each credential belongs

The API has four credential kinds, and the SDK keeps them on separate transports so a long-lived
account secret can never reach a public signing URL or a cross-origin redirect.

| Credential | Sent as | Belongs in | Used by |
|---|---|---|---|
| API key | `X-Api-Key` header | A trusted backend or controlled JVM process | Every account resource |
| Access token | `Authorization: Bearer` | Same as an API key; obtained from `authentication.login` | The same account resources |
| OAuth 2.1 token | `Authorization: Bearer` | An app acting in **someone else's** workspace with their permission | Whatever the approved scopes allow |
| Signer access code | `signer-access-code` query parameter | The signer's own app or browser session, for the duration of one signing | `signerDocuments` |

An account API key is a long-lived backend credential with full workspace access. Do not ship it in a
distributed APK, log it, or persist it on a device. Run account operations in a trusted backend and
expose only the application-specific result to the Android app — or use [OAuth 2.1](#oauth-21),
which exists for exactly this case. A signer-facing app needs no account credential at all —
construct a credentialless client and pass the short-lived access code per call:

```kotlin
val signerClient = AssinafyClient.create(
    AssinafyClientConfig(baseUrl = "https://api.assinafy.com.br/v1"),
)
```

## OAuth 2.1

Use OAuth when your application acts **in someone else's workspace, with their permission**, without
ever touching that person's password or API key. If you automate your own workspace, keep using your
API key — nothing in this section applies.

| | API key | OAuth |
|---|---|---|
| Acts on | **Your own** workspace | **Someone else's** workspace, with permission |
| Can do | Everything your account can do | Only what the user approved |
| The user can switch it off | No | Yes, at any time |

An Android app is a **public client**: it runs on the user's device and cannot keep a secret.
Register it as `Public`, leave `clientSecret` null, and authenticate with PKCE alone. A client secret
shipped inside an APK is extractable by anyone who unpacks it.

Register the application in the Assinafy app under **Settings → OAuth applications → New
application**. You receive a `client_id` and register one or more redirect URIs — they are matched
character for character, must be `https://` without a fragment, and `…/callback` differs from
`…/callback/`. Register one per environment.

### Configuration

```kotlin
val client = AssinafyClient.create(
    AssinafyClientConfig(
        baseUrl = SdkConstants.DEFAULT_BASE_URL,
        oauth = OAuthConfig(
            clientId = "your-client-id",
            redirectUri = "https://myapp.example/oauth/callback",
            scopes = listOf(
                OAuthScope.DOCUMENTS_READ,
                OAuthScope.DOCUMENTS_WRITE,
                OAuthScope.OFFLINE_ACCESS,
            ),
        ),
    ),
)
```

| Scope | Lets your app |
|---|---|
| `OAuthScope.DOCUMENTS_READ` | Read documents, pages, tags, signers, assignments, and activity |
| `OAuthScope.DOCUMENTS_WRITE` | Create documents and send them for signature (spends notification credits) |
| `OAuthScope.TEMPLATES_READ` / `TEMPLATES_WRITE` | Read / maintain templates, roles, fields, and tags |
| `OAuthScope.ACCOUNT_READ` | Read the workspace profile, theme, and logo |
| `OAuthScope.OPENID` · `PROFILE` · `EMAIL` | Identify the user and receive their name / email |
| `OAuthScope.OFFLINE_ACCESS` | Receive a refresh token and keep working while the user is away |

Request the minimum: the user approves everything you asked for, or nothing. Billing, subscriptions,
workspace membership, credentials, and administration are **never** reachable by an OAuth token,
whatever its scopes.

### 1. Prepare the attempt and open the browser

Every connection attempt needs a fresh PKCE pair and a fresh `state`. `authorizationRequest()`
generates both and builds the URL; nothing is sent over the network.

```kotlin
val attempt = client.oauth.authorizationRequest()

// Keep both in the user's session: steps 2 and 3 need them.
session.save(state = attempt.state, verifier = attempt.pkce.codeVerifier)

CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(attempt.url))
```

Use a full page navigation — a Custom Tab or the system browser. Never an embedded WebView, never an
AJAX call.

If `client_id` or `redirect_uri` is wrong the user is **not** returned to your app: the authorization
server shows the error on its own page, because redirecting to an unverified address would be unsafe.
Users stuck on an Assinafy error page almost always mean one of those two values is wrong.

### 2. Validate the return

`parseCallback` checks `state` and `iss` before anything else and returns the code. A mismatch in
either means the response is not yours, and the code is discarded rather than exchanged.

```kotlin
val code = try {
    client.oauth.parseCallback(callbackUri.toString(), session.state)
} catch (error: OAuthException) {
    if (error.isAccessDenied) return showDeclinedByUser() else throw error
}
```

### 3. Exchange the code for tokens

The code is single-use and expires **60 seconds** after approval.

```kotlin
val tokens = client.oauth.exchangeCode(code, session.verifier)

store.save(
    accessToken = tokens.accessToken,   // valid for 1 hour
    refreshToken = tokens.refreshToken, // present only if offline_access was approved
    scopes = tokens.scopes,             // read it; do not assume you got everything you asked for
)
```

### 4. Find the workspace, then call the API

A token belongs to **one workspace** — the one the user picked. With an OAuth token, the workspace
list returns exactly that one.

```kotlin
val workspaceClient = AssinafyClient.create(
    AssinafyClientConfig(token = tokens.accessToken, baseUrl = SdkConstants.DEFAULT_BASE_URL),
)
val workspaceId = workspaceClient.workspaces.list().data.first().id
store.save(workspaceId)

val documents = workspaceClient.documents.list(accountId = workspaceId)
```

> **One connection, one workspace.** Calling any other workspace returns `403`, even one the same
> user belongs to. If your customer uses several workspaces, connect each one separately and keep
> tokens per workspace.

### 5. Refresh

Access tokens last 1 hour. With `offline_access`, renew them without the user:

```kotlin
val renewed = client.oauth.refresh(store.refreshToken)
store.save(refreshToken = renewed.refreshToken) // save before using the new access token
```

> **Every refresh rotates the token.** Each renewal returns a new refresh token and retires the
> previous one. A reused refresh token cannot be told apart from a stolen one being replayed, so it
> ends the whole connection and the user must connect again. Save the new token before doing anything
> else, treat a timeout as "it may have worked" (re-read the stored token rather than retrying
> blindly), and refresh one at a time per connection.
>
> A connection lasts **30 days from approval**. Refreshing does not extend it.

### 6. Disconnect

Revoke rather than only deleting the local token. The endpoint always answers `200`.

```kotlin
client.oauth.revoke(store.refreshToken, tokenTypeHint = "refresh_token")
store.clear()
```

### Signing users in (OpenID Connect)

With the `openid` scope the exchange returns an `id_token` (an RS256 JWT) and the claims endpoint
becomes available. Validate the `id_token` with any OIDC library against
`https://auth.assinafy.com.br/.well-known/jwks.json`, checking `iss`, `aud` (your `client_id`),
`exp`, and the `nonce` you sent.

```kotlin
val info = workspaceClient.oauth.userInfo() // needs openid; name needs profile, email needs email
println("${info.sub} · ${info.name} · ${info.email}")
```

### Discovery

The URLs are stable, but they can be read from the metadata documents — that is how you point the
flow at an environment other than production.

```kotlin
val resource = client.oauth.protectedResourceMetadata()       // RFC 9728, served by this API
val issuer = requireNotNull(resource.authorizationServer)
val server = client.oauth.authorizationServerMetadata(issuer) // RFC 8414, served by the issuer
```

The `resource` indicator and the metadata URL are derived from the client's `baseUrl`, so pointing
the client already targets the flow. Only `authorizationServerUrl` needs setting
outside production — read it from `resource.authorizationServer` rather than guessing.

### OAuth errors

Unlike the rest of the API, the OAuth endpoints answer with flat objects (`{error,
error_description}`), not the envelope. The SDK turns them into `OAuthException`, which carries the
standard code.

| Situation | Handling |
|---|---|
| `OAuthException.isAccessDenied` | The user declined on the approval screen |
| `OAuthException.isInvalidGrant` | Code expired or reused, mismatched `code_verifier` or `redirect_uri`, refresh token already used or permissions changed — reconnect |
| `OAuthException.INVALID_CLIENT` | Wrong `client_id`, or the application is disabled |
| `ApiException` with status `401` | Token expired or revoked — refresh; if that fails, ask the user to reconnect |
| `ApiException` with status `403` | Missing scope, another workspace, or an area OAuth never reaches |

A call made without the scope it requires answers `403` with
`WWW-Authenticate: Bearer error="insufficient_scope", scope="documents:write"`. That is a prompt to
reconnect with that scope, **not** to retry the call. `OAuthChallenge.parse` reads the header.

### Before you go live

- A fresh PKCE pair and `state` for every connection attempt
- `state` and `iss` checked on the redirect URI — `parseCallback` does both
- `client_secret` only on a server, never in an app, browser code, or a repository
- The new refresh token saved before use, and one refresh at a time per connection
- `401` handled: refresh, and if that fails, ask the user to reconnect
- The workspace id stored per connection, and the returned `scope` actually read
- Every production redirect URI registered, `https://` and exact
- Only the scopes you need
- Tokens revoked when a user disconnects

## The document lifecycle

A signature request moves through seven stages. The sections below follow one document all the way
through; [The one-call shortcut](#the-one-call-shortcut) collapses stages 1, 2, and 4 into a single
call when the defaults suit you.

```
upload ──▶ metadata_ready ──▶ pending_signature ──▶ certificating ──▶ certificated
   │              │                    │                                    │
 step 1        step 3-4              step 5                             step 7
 upload      estimate + request     signer signs                     download + verify
```

### Step 1 — Upload a PDF

The document must be a real PDF (the SDK checks the `%PDF-` signature), at most 25 MB, and at most
2,000 pages. Read Android assets with `use` so the stream is always closed.

```kotlin
val pdfBytes = context.assets.open("agreement.pdf").use { it.readBytes() }
val uploaded = client.documents.upload(pdfBytes, "agreement.pdf")
```

Upload returns immediately with a document in `uploaded` status while the service extracts page
metadata. A collect assignment needs that metadata, so wait for it:

```kotlin
val ready = client.documents.waitUntilReady(uploaded.id)   // metadata_ready
```

`waitUntilReady` polls `GET /v1/documents/{id}` every two seconds for up to two minutes by default,
returns as soon as the status is in `DocumentStatus.READY`, and throws a `ValidationException` if the
document reaches a terminal failure state or the budget elapses. A virtual assignment may be created
before metadata processing finishes, so this wait is optional for that method.

The uploaded name can be corrected while the document is still in `uploaded` or `metadata_ready` and
has no signers. The service normalizes diacritics and unsupported characters:

```kotlin
val renamed = client.documents.rename(uploaded.id, "Service agreement 2026.pdf")
```

### Step 2 — Resolve the signers

Signers live on the account, not on the document, so the same person is reused across documents.
`signers.create` is idempotent by email: it returns the existing record instead of creating a
duplicate, and recovers from a concurrent create that loses the race.

```kotlin
val signer = client.signers.create(
    CreateSignerRequest(
        fullName = "Ada Lovelace",
        email = "ada@example.com",
        whatsappPhoneNumber = null,
    ),
)
```

Some verification methods have prerequisites. WhatsApp verification requires
`whatsappPhoneNumber` and a paid subscription; digital-certificate verification requires the account
entitlement and a CPF in the signer's `government_id`, which is set after creation:

```kotlin
client.signers.update(signer.id, UpdateSignerRequest(governmentId = "12345678909"))
```

The service refuses to change a verified email or WhatsApp number while the signer has an in-flight
document on that channel, answering `400` and naming the document.

### Step 3 — Estimate the cost

Assignments consume plan documents and credits. Price the request before sending it — signer IDs are
not needed, only the channels:

```kotlin
val estimate = client.assignments.estimateCost(
    ready.id,
    CreateAssignmentRequest(
        method = AssignmentMethod.VIRTUAL,
        signers = listOf(SignerReference(notificationMethods = listOf("Whatsapp"))),
    ),
)

if (!(estimate.hasSufficientResources ?: true)) {
    error("Blocked: ${estimate.blockingReason} — ${estimate.message}")
}
```

`CostEstimate` reports `totalCredits`, an itemized `breakdown`, the current `documentBalance` and
`creditBalance`, and a `blockingReason` of `PendingPayment`, `InsufficientDocuments`, or
`InsufficientCredits`.

### Step 4 — Request signatures

An assignment is the signature request. `virtual` collects a signature with no input fields;
`collect` places fields on specific pages.

```kotlin
val assignment = client.assignments.create(
    ready.id,
    CreateAssignmentRequest(
        method = AssignmentMethod.VIRTUAL,
        signers = listOf(SignerReference.ofId(signer.id)),
        message = "Please review and sign",
        expiresAt = "2026-12-31T23:59:59Z",
        copyReceivers = listOf(observerSignerId),
    ),
)
```

Each signer may set a verification method and one notification method; see
[Verification and notification methods](#verification-and-notification-methods) for the pairings,
their costs, and the ICP-Brasil A1/A3 option.

`step` orders the signing. Signers sharing a step sign in parallel, and a later step is notified only
after every signer in the previous step has finished. If any signer has a step, all must, and the
values must be contiguous from 1. A digital-certificate signer must be alone in its step.

```kotlin
signers = listOf(
    SignerReference(id = firstId, step = 1),
    SignerReference(id = secondId, step = 2),
)
```

A `collect` assignment adds field placements, which is why it requires `metadata_ready` — each entry
targets a real page:

```kotlin
val page = requireNotNull(ready.pages).first()
client.assignments.create(
    ready.id,
    CreateAssignmentRequest(
        method = AssignmentMethod.COLLECT,
        signers = listOf(SignerReference.ofId(signer.id)),
        entries = listOf(
            AssignmentEntry(
                pageId = page.id,
                fields = listOf(
                    AssignmentFieldPlacement(
                        signerId = signer.id,
                        fieldId = fieldDefinitionId,
                        displaySettings = DisplaySettings(
                            left = 72f, top = 640f, width = 180f, height = 40f, fontSize = 12f,
                        ),
                    ),
                ),
            ),
        ),
    ),
)
```

The response carries `signingUrls`, one per signer. Deliver the intended URL through a trusted
channel. Its final `/sign/{accessCode}` path segment is a credential: do not log it, persist it, or
put it in analytics.

If a notification needs to go out again, `assignments.resendNotification` re-sends to one signer, and
`assignments.estimateResendCost` prices that first. `assignments.resetExpiration` moves the deadline;
passing `null` clears it where the deployment supports that.

### Step 5 — Sign, as the signer

This is the only part that belongs in an end-user app, and it uses the credentialless client from
[Where each credential belongs](#where-each-credential-belongs). Pass the access code, the one-time
verification code, and the signature image explicitly; do not read them from long-lived storage.

```kotlin
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
        ConfirmSignerDataRequest(fullName = signer.fullName, email = signer.email),
    )
    client.signerDocuments.uploadSignature(
        signerAccessCode = signerAccessCode,
        imageData = signaturePng,
        type = SignatureType.SIGNATURE,
        reuse = true,
    )

    // A virtual assignment has no collect-field items, so the contract body is exactly [].
    return client.signerDocuments.sign(
        documentId = document.id,
        assignmentId = assignment.id,
        signerAccessCode = signerAccessCode,
        entries = emptyList(),
    )
}
```

Notes on this flow:

- `getAssignment` marks the document as viewed and answers `409` while preparation is still running;
  retry with backoff.
- `verifyEmail` handles the code delivered over WhatsApp as well; the wire key is
  `verification-code` either way.
- `confirmData` is mandatory before `sign` on a virtual assignment — the service answers `400`
  otherwise.
- A `collect` assignment passes `SignAssignmentItemRequest` values instead of an empty list.
- A digital-certificate signer must confirm data and accept terms before `getAssignment`. The v1
  OpenAPI does not define the certificate start/complete routes its prose refers to, so this SDK does
  not guess those schemas.
- To decline instead, call `signerDocuments.decline(documentId, assignmentId, accessCode, reason)`.
  `signMultiple` and `declineMultiple` act on several documents at once.

A signer who has not received their link can request a fresh one-time token for a public document:

```kotlin
client.documents.sendToken(documentId, "ada@example.com")            // channel defaults to email
client.documents.sendToken(documentId, "+5511999998888", "whatsapp") // recipient is the phone number
```

### Step 6 — Track completion

On a backend, prefer webhooks over polling. Register the subscription once per account:

```kotlin
client.webhooks.register(
    RegisterWebhookRequest(
        url = "https://example.com/hooks/assinafy",
        email = "ops@example.com",
        events = listOf(
            WebhookEvent.DOCUMENT_READY,
            WebhookEvent.SIGNER_SIGNED_DOCUMENT,
            WebhookEvent.SIGNER_REJECTED_DOCUMENT,
            WebhookEvent.DOCUMENT_PROCESSING_FAILED,
        ),
    ),
)
```

`WebhookEvent` lists every event identifier, and `webhooks.listEventTypes()` returns the live
catalog. `webhooks.listDispatches(WebhookDispatchParams(...))` is the delivery history — filter by
event, delivery state, or timestamp range — and `webhooks.retryDispatch(id)` replays one failed
delivery. There is no delete: `webhooks.inactivate()` stops delivery, and `register` overwrites.

Webhooks belong on a backend, not on a device. `WebhookVerifier` is an optional HMAC-SHA256 helper
for gateways configured to sign deliveries; `webhookSecret` is only ever used locally.

```kotlin
if (client.webhookVerifier.verify(rawBodyBytes, signatureHeader)) {
    val event = client.webhookVerifier.extractEvent(rawBodyBytes)
    when (client.webhookVerifier.getEventType(event)) {
        WebhookEvent.DOCUMENT_READY -> handleReady(client.webhookVerifier.getEventData(event))
    }
}
```

Where a webhook is unavailable, poll the account side instead:

```kotlin
val progress = client.documents.getSigningProgress(documentId) // signed, total, pending, percentage
val complete = client.documents.isFullySigned(documentId)
val activity = client.documents.activities(documentId)         // full activity log
```

`isFullySigned` can return `true` during `certificating`, before the immutable artifact exists.

### Step 7 — Download and verify

Wait for `certificated` before fetching the final PDF:

```kotlin
suspend fun downloadCompletedPdf(client: AssinafyClient, documentId: String): ByteArray {
    val document = client.documents.details(documentId)
    check(document.status == DocumentStatus.CERTIFICATED) { "Document is not certificated" }
    return client.documents.download(documentId, DocumentArtifact.CERTIFICATED)
}
```

| `DocumentArtifact` | Contents |
|---|---|
| `ORIGINAL` | The PDF as uploaded |
| `CERTIFICATED` | The signed PDF with its certificate page |
| `CERTIFICATE_PAGE` | The certificate page alone |
| `PADES` | PAdES signatures; present only with digital-certificate signers |
| `BUNDLE` | A ZIP of the above |

`documents.thumbnail(id)` and `documents.downloadPage(id, pageId)` return page imagery. All binary
endpoints return the response bytes unchanged.

Anyone holding the hash printed on a signed document can verify it without credentials:

```kotlin
val result = client.documents.verify(signatureHash)
if (result.isValid) println("Signed at ${result.completedAt}")
```

This call always answers HTTP `200`. An unknown hash or unsigned document sets `isValid = false`,
leaves the other details null, and explains why in `message`.

## Verification and notification methods

Set per signer on `SignerReference` when creating the assignment. The verification method (how the
person proves identity) and the notification method (how they are invited) are **coupled**: send one,
both, or neither — the missing side is inferred, and sending neither defaults both to Email.

| Verification | How it works | Allowed notification | Cost per signer |
| --- | --- | --- | --- |
| `VerificationMethod.EMAIL` *(default)* | One-time code (OTP) by email, required before signing | `NotificationMethod.EMAIL` | Free |
| `VerificationMethod.WHATSAPP` | One-time code (OTP) over WhatsApp | `NotificationMethod.WHATSAPP` | 0.45 credits, paid plans only |
| `VerificationMethod.DIGITAL_CERTIFICATE` | The signer signs with their **own ICP-Brasil certificate (A1 or A3)** through the Web PKI browser extension, producing a **qualified PAdES signature** | `EMAIL` **or** `WHATSAPP` | 2 credits + the notification |

Exactly one notification method per signer. The SDK validates the pairing locally, so an invalid
combination fails with `ValidationException` before the request leaves.

```kotlin
// Verify and notify over the same channel.
SignerReference.over(signer.id, NotificationMethod.WHATSAPP)

// ICP-Brasil A1/A3 certificate, invited by email, alone in step 2.
SignerReference.withDigitalCertificate(signer.id, notifyBy = NotificationMethod.EMAIL, step = 2)
```

No verification method carries a price of its own: what is billed is the **notification** it travels
with. Because choosing WhatsApp verification also chooses WhatsApp notification, such a signer costs
0.45 credits against 0 for an email-verified one. Digital certificate is the exception — the
signature itself costs 2 credits per signer on top of the notification, charged when the assignment
is created and shown in the estimate `breakdown` under the `SignatureDigitalCertificate` code.

Digital certificate requires the account feature (Standard and Pro plans), a CPF or CNPJ in
`government_id`, and the signer to be **alone in their step**. A CPF requires that person's
certificate (an e-CPF, or an e-CNPJ naming them as legal representative); a CNPJ requires an e-CNPJ
for that company, from any of its representatives. Because certificate signing runs through a
handshake with the Web PKI browser extension, it is not completed by this SDK's native signing flow —
send the signer to the web signing page.

## The one-call shortcut

When the defaults fit — a virtual assignment, email verification, signers identified by email —
`uploadAndRequestSignatures` performs stages 1, 2, and 4 in one call. It validates every signer,
uploads the PDF, waits for metadata readiness, reuses or creates each signer by exact email, and
creates the assignment.

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

The result holds the `DocumentDetails`, the created `Assignment` with its per-signer `signingUrls`,
and the resolved signer IDs. A failure after the upload leaves the document in the account; delete it
explicitly if your flow requires a rollback. Use the individual resources for collect fields,
sequential signing, WhatsApp, or digital-certificate verification.

## Everything else on the client

| Resource | What it does |
|---|---|
| `client.authentication` | Login, password reset and change, social login and linking, personal API keys |
| `client.oauth` | OAuth 2.1 with PKCE: authorization URL, exchange, refresh, revoke, userinfo, discovery |
| `client.workspaces` | Account CRUD, theme, logo upload/download/delete, per-account KPI statistics |
| `client.documents` | Upload, list, search, details, rename, delete, artifacts, activities, template instantiation, public access, per-document tags |
| `client.signers` | Account signer CRUD and `findByEmail` |
| `client.signerDocuments` | The complete credentialless signer-facing flow |
| `client.assignments` | Create, estimate, reset expiration, resend, WhatsApp notification history |
| `client.fields` | Field definitions, the field-type catalog, single and batch server-side validation |
| `client.users` | Authenticated profile, cross-account KPI statistics, notification preferences |
| `client.tags` | Workspace tag CRUD, with force-detach delete |
| `client.templates` | Template reads; instantiate through `documents.createFromTemplate` |
| `client.webhooks` | Subscription, delivery history, retry |
| `client.webhookVerifier` | Local HMAC verification and payload parsing |

Templates produce a document and its assignment in one call. Map one signer to each template role;
the signers must already exist:

```kotlin
val template = client.templates.list().data.first { it.status == "ready" }
val role = requireNotNull(template.roles).first()

val document = client.documents.createFromTemplate(
    templateId = template.id,
    signers = listOf(TemplateSigner(roleId = role.id, id = signer.id)),
    options = CreateDocumentFromTemplateRequest(
        signers = emptyList(), // replaced by the signers argument
        name = "Agreement for Ada.pdf",
        message = "Please review and sign",
        editorFields = listOf(TemplateEditorField(fieldId = fieldId, value = "Example value")),
    ),
)
```

Tags are workspace labels, unique per workspace and case-insensitive. Create them on the account,
then attach them by ID:

```kotlin
val tag = client.tags.create(name = "Contracts", color = "1f6feb")
client.documents.addTags(documentId, listOf(tag.id))
client.documents.replaceTags(documentId, listOf(tag.id))  // full replacement
client.documents.detachTag(documentId, tag.id)
```

## Responses, pagination, and errors

Every JSON response uses one envelope:

```json
{"status": 200, "message": "OK", "data": {}}
```

The SDK validates both the HTTP status and the envelope `status`, unwraps `data` into the documented
type, and returns `Unit` where an operation has no payload. Binary endpoints return the bytes
unchanged. The OAuth endpoints are the exception: they answer with flat objects, and the SDK handles
them separately.

List endpoints return `PaginatedResult<T>`, whose `meta` comes from the `X-Pagination-Current-Page`,
`X-Pagination-Page-Count`, `X-Pagination-Per-Page`, and `X-Pagination-Total-Count` response headers.
Page numbers are one-based, and `perPage` must be between 1 and 100:

```kotlin
var page = 1
do {
    val result = client.documents.list(ListParams(page = page, perPage = 100))
    result.data.forEach(::process)
    page++
} while (result.meta?.lastPage?.let { page <= it } == true)
```

HTTP 429 is retried at most twice, only for safe reads, honoring `Retry-After` and
`X-Rate-Limit-Reset` up to a 30-second cap. Mutations are never replayed.

Five exception types cover every failure. Catch them at the application boundary:

```kotlin
try {
    client.documents.details(documentId)
} catch (error: ValidationException) {
    // Local check failed; no request was sent. error.errors names the field.
    showInputError(error.message)
} catch (error: OAuthException) {
    // A flat OAuth error. Branch on error.error, never on message text.
    handleOAuth(error.error)
} catch (error: ApiException) {
    // Non-2xx HTTP or envelope status. Branch on statusCode, never on message text.
    reportApiStatus(error.statusCode) // Never log responseData without redaction.
} catch (error: NetworkException) {
    // DNS, TLS, connection, timeout, or response-read failure.
    showRetryableNetworkError()
} catch (error: AssinafyException) {
    // Base type; also covers response-decoding failures.
    reportUnexpected(error)
}
```

`ApiException.responseData` preserves the complete error envelope, which can echo values the caller
sent. Redact it before logging or forwarding it anywhere.

## Coroutines and lifecycle

Every network method is a `suspend` function and is safe to call from any dispatcher; the underlying
OkHttp calls are asynchronous, so they do not block the calling thread. Cancelling the coroutine
cancels the in-flight HTTP call — treat cancellation as cancellation, never as a signal to retry.

```kotlin
viewModelScope.launch {
    val details = runCatching { client.documents.details(documentId) }
    // Leaving this scope cancels the request.
}
```

Timeouts are per request and configured on the client. `waitUntilReady` spans several requests and
has its own independent budget.

## Building and testing

The repository builds with the Gradle wrapper. When the host lacks JDK 25 and the Android SDK, Docker
is the shortest path:

```shell
docker compose build --pull
docker compose run --rm test    # SDK unit tests
docker compose run --rm build   # full module build: compile, test, lint, package
```

Locally, with the toolchain installed:

```shell
./gradlew \
  :sdk:assembleRelease \
  :sdk:test \
  :sdk:lintDebug \
  :sdk:ktlintCheck \
  :sdk:dokkaGeneratePublicationHtml \
  :sdk:publishReleasePublicationToMavenLocal \
  :consumer-smoke:assembleRelease \
  --no-daemon
```

Live tests are skipped unless their environment is supplied, and mutating tests need a second
explicit opt-in. [Building and testing](docs/TESTING.md) documents the full environment, the safe
live-test boundary, and the release checklist.

## Versioning

Releases follow semantic versioning and are tagged `vMAJOR.MINOR.PATCH`. The
[changelog](CHANGELOG.md) records every release.

Recompile every 1.x consumer against 2.x. `DocumentListItem`, `DocumentUploadResponse`,
`WorkspaceListItem`, and `TemplateListItem` are Kotlin type aliases of their complete models, so
their distinct 1.x JVM classes no longer exist. Fields that a list, search, or upload projection
omits are nullable and must be checked before use.

Members marked `@Deprecated` are wire-compatible aliases retained for older integrations. New code
should use the replacement named in each deprecation message.

## License

MIT. See [LICENSE](LICENSE).
