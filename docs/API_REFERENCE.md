# Android SDK API reference

This reference describes the public Kotlin surface and its Assinafy v1 wire contract. All network
functions are `suspend` functions. Paths below include `/v1`; Assinafy-hosted base URLs must include
that prefix. Request routes, query parameters, bodies, response fields, and authentication modes are
listed for every supported operation. Labelled optional behaviors are retained for installations
that expose them.

See [Supported Assinafy v1 operations](API_COVERAGE.md) for the operation index.

## Transport, authentication, and errors

Production is `https://api.assinafy.com.br/v1`; sandbox is `https://sandbox.assinafy.com.br/v1`. Use credentials issued for the selected environment.

```kotlin
val client = AssinafyClient.create(
    AssinafyClientConfig(
        token = securelyStoredOAuthAccessToken,
        accountId = selectedWorkspaceId,
        baseUrl = "https://api.assinafy.com.br/v1",
    )
)
```

Use exactly one account credential:

- `apiKey` sends `X-Api-Key: ...`.
- `token` sends `Authorization: Bearer ...`.
- Neither is required for login, password-reset, document verification, public-document, or signer
  flows. The client uses a separate credentialless transport for those calls, so account secrets are
  not forwarded to public URLs or redirects.
- Signer calls send the one-time code only as the `signer-access-code` query parameter.

Do not put API keys, bearer tokens, signer codes, passwords, or webhook secrets in an Android APK,
source control, logs, crash reports, or analytics. Account operations normally belong on a trusted
backend. Public/signer operations are suitable for a client application when the application
receives the short-lived signer code through the intended signing flow.

JSON responses use the envelope:

```json
{"status":200,"message":"OK","data":{}}
```

Error responses use the same structure; `data` may contain field-specific details:

```json
{
  "status": 422,
  "message": "Validation failed",
  "data": {"email": ["Email is invalid"]}
}
```

The SDK retries HTTP 429 responses up to twice for GET, HEAD, and OPTIONS, honoring
`Retry-After`/`X-Rate-Limit-Reset` with a 30-second cap. Mutation requests disable redirects and automatic connection retries and are sent at most once. It then
validates both the HTTP status and the envelope `status`, preserves the complete error envelope in
`ApiException.responseData`, and unwraps `data`. Successes without a data model return `Unit`. A list endpoint
returns `PaginatedResult<T>` when pagination applies; metadata comes from `X-Pagination-Current-Page`,
`X-Pagination-Page-Count`, `X-Pagination-Per-Page`, and `X-Pagination-Total-Count` headers. Binary
endpoints return the response bytes unchanged.

Failures are:

| Exception | Meaning |
|---|---|
| `ValidationException` | A local required-field, format, range, file, or configuration check failed. No request is sent. |
| `ApiException` | HTTP or envelope status was not 2xx. Inspect `statusCode`, `responseData`, and `challenge` (the parsed `WWW-Authenticate: Bearer` challenge, if any); do not branch on human-readable text, and redact response data before logging because servers can echo sensitive input. |
| `NetworkException` | DNS, TLS, connection, timeout, or response-read failure. |
| `OAuthException` | An OAuth endpoint or authorization redirect reported a flat `{error, error_description}` failure. Branch on `error`, never on message text. |
| `AssinafyException` | Common SDK base exception and response-decoding failures. |

Coroutine cancellation cancels the underlying OkHttp call and propagates cancellation; do not turn
it into an automatic retry.

### Low-level HTTP surface

Most consumers should use `AssinafyClient`. The public transport types remain available for custom
resource integration and diagnostics:

| Type/member | Contract |
|---|---|
| `ApiHttpClient` | Suspend transport interface for JSON verbs, form posts, multipart uploads, raw signature upload, binary GET, and absolute-URL GET. Paths are relative to the configured API prefix; `getAbsolute(url)` takes a full URL and is used only for OAuth discovery documents; `postForm(path, fields)` sends `application/x-www-form-urlencoded` for the OAuth token and revocation endpoints and is never replayed. Its default implementation sends nothing and throws `UnsupportedOperationException`, so an implementation written for an earlier version still compiles and links. |
| `OkHttpApiClient(baseUrl, apiKey, token, timeoutMs)` | Default implementation. It negotiates TLS 1.2 or 1.3 for HTTPS, URL-encodes query values, applies credentials only on the configured origin, retries only safe reads after 429, sends `postForm` bodies at most once (never retransmitted after a dropped connection), and returns `HttpRawResponse` without unwrapping it. |
| `HttpRawResponse` | `statusCode:Int`, UTF-8 `body:String?`, and lower-cased `headers:Map<String,String>`. |
| `ApiException.fromResponse(statusCode, responseData)` | Creates a typed exception from a parsed map, raw JSON/text, or empty body; extracts `message`/`error` when present and preserves the source in `responseData`. |

The full API prefix may end in a trailing slash. It must not contain user information, a query, or
a fragment. Credentials require HTTPS except on `localhost`, `127.0.0.1`, and `::1`.

## Client and resource map

`AssinafyClient.create(config)` validates the full API prefix and positive timeout. Assinafy-hosted
prefixes include `/v1`; reverse proxies may use another path. A trailing slash is accepted, while
user information, a query, or a fragment is rejected. Credentials require HTTPS except for loopback
loopback development hosts. `apiKey` and `token` are mutually exclusive. The convenience
`create(apiKey, accountId, baseUrl, webhookSecret, timeoutMs, logger)` builds the same client.

| Property | Operations |
|---|---|
| `authentication` | Login, passwords, social identity, personal API keys |
| `oauth` | OAuth 2.1 authorization-code flow with PKCE, refresh, revocation, userinfo, discovery |
| `workspaces` | Accounts, themes, logos, account statistics |
| `documents` | Documents, artifacts, public document access, template instantiation, document tags |
| `signers` | Account-scoped signer CRUD and compatibility signer-flow aliases |
| `signerDocuments` | Complete credentialless signer-facing signing flow |
| `assignments` | Assignment creation, pricing, expiration, resend, notification history |
| `fields` | Field definitions, field types, server-side validation |
| `users` | Authenticated profile, cross-account statistics, notification preferences |
| `tags` | Account tag CRUD |
| `templates` | Template reads |
| `webhooks` | Subscription and dispatch management |
| `webhookVerifier` | Optional backend HMAC verification and payload parsing |

`uploadAndRequestSignatures(request)` is a local orchestration helper. It validates signer input,
uploads the PDF, always waits for metadata readiness, finds or creates each signer by email, then
creates a virtual assignment. `waitForReady` controls only the final document refresh. It returns
`UploadAndRequestSignaturesResult(document, assignment, signerIds)`. A failure after upload can leave
the uploaded document in the account; callers that require rollback should delete that document
explicitly after deciding that deletion is safe.

`SignerReference.ofId(signerId)` is shorthand for `SignerReference(id = signerId)`;
`SignerReference.over(signerId, channel, step)` pairs a verification and notification channel, and
`SignerReference.withDigitalCertificate(signerId, notifyBy, step)` requests ICP-Brasil A1/A3 signing.
`ListParams.toQueryMap()` returns its non-null values using the API's exact query names and joins tag
IDs with commas. A custom `Logger` receives `debug`, `info`, `warn`, and `error` calls as
`(message, context)`; the SDK does not include credential values in its contexts.

Document tag mutation values are sent unchanged. The current OpenAPI defines them as tag IDs; older
deployments may still require tag names on the same routes.

## AuthenticationResource

| Kotlin function | Exact request | JSON body | Return (`data`) |
|---|---|---|---|
| `login(LoginRequest)` | `POST /v1/login` (public) | `{"email":string,"password":string}` | `AuthenticationSession`; throws `MfaRequiredException` when a second factor is required |
| `requestPasswordReset(RequestPasswordResetRequest)` | `PUT /v1/authentication/request-password-reset` (public) | `{"email":string}` | `AuthenticationEmailResponse` |
| `resetPassword(ResetPasswordRequest)` | `PUT /v1/authentication/reset-password` (public) | `{"email":string,"new_password":string,"token":string?}` | `AuthenticationEmailResponse` |
| `changePassword(ChangePasswordRequest)` | `PUT /v1/authentication/change-password` | `{"email":string,"password":string,"new_password":string}` | `AuthenticationEmailResponse` |
| `socialLogin(SocialLoginRequest)` | `POST /v1/authentication/social-login` (public) | `{"provider":"google","token":string,"has_accepted_terms":boolean}` | `AuthenticationSession`; throws `MfaRequiredException` when a second factor is required |
| `linkSocialLogin(LinkSocialLoginRequest)` | `POST /v1/auth/link-social-login` | `{"provider":"google","token":string}` | `Unit` |
| `getApiKey()` | `GET /v1/users/api-keys` | none | `ApiKeyResponse?` (`api_key` may also be null) |
| `createApiKey(CreateApiKeyRequest)` | `POST /v1/users/api-keys` | `{"password":string}` | `ApiKeyResponse` containing the newly generated key |
| `deleteApiKey()` | `DELETE /v1/users/api-keys` | none | `Unit` |
| `verifyMfa(MfaVerifyRequest)` | `POST /v1/authentication/mfa/verify` (public) | `{"mfa_token":string,"code":string}` | `AuthenticationSession` |
| `listMfaMethods()` | `GET /v1/users/self/mfa` | none | `MfaStatus` |
| `startTotpEnrollment(label)` | `POST /v1/users/self/mfa/totp` | `{"label":string?}` | `TotpEnrollment` (secret shown once) |
| `confirmTotpEnrollment(ConfirmTotpRequest)` | `PUT /v1/users/self/mfa/totp/confirm` | `{"id":string,"code":string,"password":string?,"reauth_code":string?}` | `MfaRecoveryCodes` (shown once) |
| `regenerateRecoveryCodes(MfaReauthRequest)` | `POST /v1/users/self/mfa/recovery-codes` | `{"password":string?,"code":string?}`; one is required | `MfaRecoveryCodes` |
| `removeMfaMethod(methodId, MfaReauthRequest)` | `DELETE /v1/users/self/mfa/{customId}` | `{"password":string?,"code":string?}`; one is required | `MfaRemoval` |

A user with two-factor authentication enabled gets no session from `login` or `socialLogin`: the SDK
throws `MfaRequiredException`, whose `mfaToken` is a single-use challenge valid for five minutes.
Pass it with the user's authenticator or recovery code to `verifyMfa`:

```kotlin
val session = try {
    client.authentication.login(LoginRequest(email, password))
} catch (e: MfaRequiredException) {
    client.authentication.verifyMfa(MfaVerifyRequest(e.mfaToken, codeFromUser))
}
```

Confirming a second authenticator replaces the first and then needs `password` or `reauth_code`.
Creating an API key rotates the previous key; deletion revokes it. Password reset/change and API-key
rotation are state-changing security operations and should never be used as health checks.

## OAuthResource

`client.oauth` implements the OAuth 2.1 authorization-code flow with mandatory PKCE, for
applications acting in another user's workspace with that user's permission. It needs
`AssinafyClientConfig.oauth`; without it every method except discovery raises `ValidationException`.

An Android application is a **public client**: `OAuthConfig.clientSecret` stays `null` and PKCE alone
authenticates the client. The SDK never sends `client_secret`; a non-null `clientSecret` raises
`ValidationException` before any request, because a secret shipped in an APK is extractable.

An application's type cannot be changed after it is created, so an app registered as `Confidential`
moves to a new `Public` application: configure its `client_id` without `clientSecret`, discard the
tokens issued to the old application (the new `client_id` can neither refresh nor revoke them), and
have each user connect again. Once no supported version uses the old application, disable or delete
it; its secret shipped inside APKs.

These four endpoints do not use the `{status, message, data}` envelope. They answer with flat
RFC 6749, OpenID Connect, and RFC 9728 objects, and failures raise `OAuthException` (carrying
`error`, `errorDescription`, `statusCode`) rather than `ApiException`.

| Function | Route and contract |
|---|---|
| `authorizationRequest(scopes, nonce, pkce, state)` | Local; no request. Builds `GET {authorizationServer}/oauth/authorize` with `response_type=code`, `client_id`, `redirect_uri`, space-separated `scope`, `state`, `code_challenge`, `code_challenge_method=S256`, `resource`, and `nonce` when supplied. Returns `AuthorizationRequest(url, state, pkce)`. Generates a fresh PKCE pair and `state` per call. |
| `parseCallback(callbackUri, request \| expectedState)` | Local; no request. Verifies `state` matches and that `iss` equals the configured authorization server — a missing `iss` is a mismatch — before reading anything else, then returns `code`. Raises `OAuthException` when the redirect carries `error`, and `ValidationException` on a `state`/`iss` mismatch or a response with neither `code` nor `error`. |
| `exchangeCode(code, codeVerifier, redirectUri)` | `POST /v1/oauth/token`, form-encoded (`application/x-www-form-urlencoded`) and sent once, with `grant_type=authorization_code`, `code`, `redirect_uri`, `client_id`, `code_verifier`, `resource`. Returns `OAuthTokens`. |
| `refresh(refreshToken)` | `POST /v1/oauth/token`, form-encoded and sent once, with `grant_type=refresh_token`, `refresh_token`, `client_id`. Returns a new `OAuthTokens`, including a **new** refresh token that retires the old one. A 2xx whose `refresh_token` is missing, blank, or the one sent raises `OAuthException` `invalid_response`. |
| `revoke(token, tokenTypeHint)` | `POST /v1/oauth/revoke`, form-encoded, with `token`, `client_id` and optional `token_type_hint` of `access_token` or `refresh_token`. Answers `200` for every token outcome; only failed client authentication answers `401`. |
| `userInfo()` | `GET /v1/oauth/userinfo` using the client's bearer token. Requires the `openid` scope. Returns `UserInfo`. |
| `protectedResourceMetadata()` | `GET {apiOrigin}/.well-known/oauth-protected-resource`. The origin is derived from the client's base URL, so the document is read from the host root rather than the `/v1` prefix. Returns `ProtectedResourceMetadata`. Needs no `OAuthConfig`. |
| `authorizationServerMetadata(issuer)` | `GET {issuer}/.well-known/oauth-authorization-server`, served by the authorization server rather than this API. Returns `AuthorizationServerMetadata`. Needs no `OAuthConfig`. |

Successful token response (`exchangeCode` and `refresh`):

```json
{
  "access_token": "…",
  "token_type": "Bearer",
  "expires_in": 3600,
  "scope": "documents:read documents:write",
  "refresh_token": "…",
  "id_token": "…"
}
```

`refresh_token` is present only when `offline_access` was requested and consented; `id_token` only
when `openid` was granted. `scope` reports what was actually granted, and never contains
`offline_access`. Failure response:

```json
{"error": "invalid_grant", "error_description": "Authorization code has expired."}
```

### OAuth types

| Type | Contract |
|---|---|
| `OAuthConfig(clientId, redirectUri, scopes, clientSecret, authorizationServerUrl, resource)` | The registered application. `authorizationServerUrl` defaults to `https://auth.assinafy.com.br`; `resource` defaults to the client's base-URL origin. `clientSecret` is deprecated and must stay `null` (a non-null value is rejected, never sent); `toString()` redacts it. |
| `PkcePair(codeVerifier, codeChallenge, codeChallengeMethod)` | `PkcePair.generate(verifierLength = 64)` draws the verifier from the RFC 7636 unreserved alphabet (43-128 characters) and sets `codeChallenge` to unpadded base64url SHA-256 of it. `toString()` redacts the verifier. |
| `AuthorizationRequest(url, state, pkce)` | What to open, and what to keep in session until the redirect returns. |
| `OAuthTokens(accessToken, tokenType, expiresIn, refreshToken, scope, idToken, issuedTokenType)` | Adds `scopes: List<String>` and `hasScope(name)`. `toString()` redacts every token. |
| `UserInfo(sub, name, email, emailVerified)` | OpenID Connect claims; only `sub` is always present. |
| `ProtectedResourceMetadata(resource, authorizationServers, scopesSupported, bearerMethodsSupported)` | RFC 9728 document. Only `resource` is required by the RFC, so the rest are nullable; `authorizationServer` returns the first advertised issuer. |
| `AuthorizationServerMetadata(issuer, authorizationEndpoint, tokenEndpoint, revocationEndpoint, userinfoEndpoint, jwksUri, …)` | RFC 8414 document. Only `issuer`, `authorizationEndpoint` and `tokenEndpoint` are required by the RFC, so the rest are nullable. |
| `OAuthChallenge(error, errorDescription, scope, resourceMetadata)` | `OAuthChallenge.parse(header)` reads a `WWW-Authenticate: Bearer …` value; `isInsufficientScope` names the case where `scope` is the permission to reconnect with. |
| `OAuthException(error, errorDescription, statusCode)` | `isAccessDenied` and `isInvalidGrant` cover the two cases callers branch on; the companion holds every standard code. |
| `OAuthScope` | `DOCUMENTS_READ`, `DOCUMENTS_WRITE`, `TEMPLATES_READ`, `TEMPLATES_WRITE`, `ACCOUNT_READ`, `WEBHOOKS_WRITE`, `OPENID`, `PROFILE`, `EMAIL`, `OFFLINE_ACCESS`. |

Access tokens last one hour. A refresh token is valid for 30 days, and every refresh returns a new
one with a fresh 30 days, so a connection only expires after 30 days without a refresh. Save each
rotated refresh token and its access token before using the response, and keep the refresh and the
save together under `NonCancellable`: a refresh cancelled mid-request can lose a token the server
already rotated. Never send the same refresh token twice: after a failure that may have reached the
server, continue only if a different, newer token was saved, and otherwise ask the user to connect
again. Only a `NetworkException` caused by `UnknownHostException`, `ConnectException` or
`SSLHandshakeException` happened before anything was sent and is safe to retry with the same token. A
token is valid for exactly one workspace; any other workspace answers `403`. A call missing a scope
answers `403` with `WWW-Authenticate: Bearer error="insufficient_scope", scope="…"`, exposed as
`ApiException.challenge`, which is a prompt to reconnect with that scope rather than to retry.

## WorkspaceResource

An Assinafy workspace is an API account. `notification_sender_type` accepts `"User"` or
`"Account"`. Compatibility color values, when used, are exactly six hexadecimal characters without
`#`, for example `2072b9`.

| Kotlin function | Exact request | Query/body | Return (`data`) |
|---|---|---|---|
| `create(CreateWorkspaceRequest)` | `POST /v1/accounts` | `{"name":string,"notification_sender_type":string?,"primary_color":string?,"secondary_color":string?}` | `Workspace` |
| `list()` | `GET /v1/accounts` | none | `PaginatedResult<Workspace>` |
| `get(accountId)` | `GET /v1/accounts/{accountId}` | none | `Workspace` |
| `update(accountId, UpdateWorkspaceRequest)` | `PUT /v1/accounts/{accountId}` | Non-empty subset of create fields | `Workspace` |
| `delete(accountId, force)` | `DELETE /v1/accounts/{accountId}` | Optional JSON `{"force":boolean}` | `Unit` |
| `getTheme(accountId)` | `GET /v1/accounts/{accountId}/theme` | none | `AccountTheme` |
| `getLogo(accountId)` | `GET /v1/accounts/{accountId}/logo` | none | Raw `ByteArray?`; `null` on 404 |
| `uploadLogo(accountId, fileData, fileName, contentType)` | `POST /v1/accounts/{accountId}/logo` | `multipart/form-data`; one `file` part | `Unit` |
| `deleteLogo(accountId)` | `DELETE /v1/accounts/{accountId}/logo` | none | `Unit` |
| `getStats(accountId, granularity, month)` | `GET /v1/accounts/{accountId}/stats` | `granularity=monthly\|daily`; `month=YYYY-MM` is required for daily | `List<DocumentStatsRow>` |

Without `force`, deleting a workspace with an active paid subscription returns `400`; the response
`restrictions` array identifies each blocker as
`{"code":"ActivePaidSubscription"|"PendingDocuments","message":string,"account_ids":[string]}`.
`PendingDocuments` appears only with `ActivePaidSubscription`. `force=true` cancels the subscription
and deletes the workspace immediately.

## DocumentResource

| Kotlin function | Exact request | Query/body | Return (`data`) |
|---|---|---|---|
| `upload(fileData, fileName, metadata, accountId)` | `POST /v1/accounts/{accountId}/documents` | Multipart `file` (`application/pdf`). Only when `metadata` is non-null, opt in to live compatibility `name` and JSON-string `metadata` parts | `DocumentUploadResponse` |
| `list(ListParams, accountId)` | `GET /v1/accounts/{accountId}/documents` | Optional `status`, `method`, `search`, comma-separated tag IDs in `tags`, `sort`, `page`, `per-page` | `PaginatedResult<DocumentListItem>`; alias of full `DocumentDetails` |
| `search(query, status, page, perPage, accountId)` | `GET /v1/accounts/{accountId}/documents/search` | Optional `search`, `status`, `page`, `per-page` | `PaginatedResult<DocumentListItem>`; alias of full `DocumentDetails` |
| `details(documentId)` | `GET /v1/documents/{documentId}` | none | `DocumentDetails` |
| `get(documentId)` | Same as `details` | none | `DocumentDetails` |
| `waitUntilReady(documentId, maxWaitMs, pollIntervalMs)` | Repeats `GET /v1/documents/{documentId}` | Local timing arguments only | First `DocumentDetails` whose status is in `DocumentStatus.READY` |
| `download(documentId, artifactName)` | `GET /v1/documents/{documentId}/download/{artifactName}` | none | Raw PDF/ZIP `ByteArray` |
| `thumbnail(documentId)` | `GET /v1/documents/{documentId}/thumbnail` | none | Raw image `ByteArray` |
| `downloadPage(documentId, pageId)` | `GET /v1/documents/{documentId}/pages/{pageId}/download` | none | Raw page image `ByteArray` |
| `activities(documentId)` | `GET /v1/documents/{documentId}/activities` | none | `List<DocumentActivity>` |
| `delete(documentId)` | `DELETE /v1/documents/{documentId}` | none; allowed only when the status catalog reports `deletable=true` | `Unit` |
| `rename(documentId, name)` | `PATCH /v1/documents/{documentId}` | `{"name":string}`; maximum 255 characters; only before assignment in `uploaded`/`metadata_ready` | `DocumentDetails` |
| `createFromTemplate(templateId, signers, options, accountId)` | `POST /v1/accounts/{accountId}/templates/{templateId}/documents` | `CreateDocumentFromTemplateRequest`; function `signers` replaces `options.signers` | `DocumentDetails` |
| `estimateCostFromTemplate(templateId, signers, accountId)` | `POST /v1/accounts/{accountId}/templates/{templateId}/documents/estimate-cost` | `{"signers":[{"role_id":string,"verification_method":string?,"notification_methods":[string]?}]}` | `CostEstimate` |
| `verify(hash)` | `GET /v1/documents/{documentSignatureHash}/verify` (public) | none | `DocumentVerification` |
| `getPublic(documentId)` | `GET /v1/public/documents/{documentId}` (public) | none | `PublicDocumentInfo` |
| `sendToken(documentId, email?, channel?)` | `PUT /v1/public/documents/{documentId}/send-token` (public) | `{"recipient":string,"channel":"email"\|"whatsapp"}`; both keys are required by the service and `channel` defaults to `email` | `Unit` |
| `isFullySigned(documentId)` | Calls `details` | local derivation | `true` when every signer is complete or status is `certificated`; does not guarantee the certificated artifact is ready during `certificating` |
| `getSigningProgress(documentId)` | Calls `details` | local derivation | `SigningProgress` |
| `getStatuses()` | `GET /v1/documents/statuses` | none | `List<DocumentStatusInfo>` |
| `confirmSignerData(documentId, accessCode, request)` | `PUT /v1/documents/{documentId}/signers/confirm-data?signer-access-code=...` | Official subset `full_name`, `email`, `government_id`; deprecated compatibility properties are ignored | `Signer` |
| `confirmSignerData(documentId, accessCode, data)` | Same endpoint; compatibility overload | Caller-supplied non-empty JSON object | `Signer` |
| `listTags(documentId, accountId)` | `GET /v1/accounts/{accountId}/documents/{documentId}/tags` | none | `List<Tag>` |
| `replaceTags(documentId, tagNames, accountId)` | `PUT /v1/accounts/{accountId}/documents/{documentId}/tags` | `{"tags":["tag_id",...]}`; parameter name is retained for source compatibility | `List<Tag>` |
| `addTags(documentId, tagNames, accountId)` | `POST /v1/accounts/{accountId}/documents/{documentId}/tags` | `{"tags":["tag_id",...]}`; parameter name is retained for source compatibility | `List<Tag>` |
| `detachTag(documentId, tagId, accountId)` | `DELETE /v1/accounts/{accountId}/documents/{documentId}/tags/{tagId}` | none | `Unit` |

Uploads must be non-empty PDF content, have a `.pdf` name, begin with `%PDF-`, and be no larger than
25 MiB; the service accepts at most 2,000 pages. Artifact names are `original`, `certificated`,
`certificate-page`, `pades`, and `bundle`. The `pades` artifact exists only when the document has
digital-certificate signers and contains their ICP-Brasil signatures plus the platform certification
box. `bundle` is a ZIP containing the original,
certificated, and certificate-page artifacts, plus PAdES when available. Readiness stops at
`metadata_ready`, `pending_signature`, `certificating`, or `certificated`, and fails immediately for
the terminal `failed`, `rejected_by_signer`, `rejected_by_user`, and `expired` states.

`verify(hash)` always returns HTTP `200`. For an unknown hash or an unsigned document,
`is_valid=false`, the other nullable certification details are null, and `message` explains why.

`sendToken` diverges from the published OpenAPI schema, which shows an optional `{"email":...}` body.
The deployed service rejects that body with `400` naming `channel`, then `recipient`, as missing, so
the SDK always sends both keys. `email` is the recipient for the selected channel — an address on
`email`, the signer's phone number on `whatsapp` — and only the address form is format-validated.

The current OpenAPI multipart schema declares only `file`, which is exactly what the default
`metadata=null` call sends. Supplying metadata explicitly opts into the deployed API's legacy `name`
and `metadata` parts retained by earlier SDK releases.

Rename responses use the server-normalized name: diacritics and unsupported characters can be
removed or replaced.

In 2.0, `DocumentListItem`, `DocumentUploadResponse`, `WorkspaceListItem`, and `TemplateListItem` are
Kotlin type aliases of their complete models. Their distinct 1.x JVM classes no longer exist, so 1.x
consumers must recompile for 2.0. The document aliases expose the full `DocumentDetails` model,
including typed `Assignment?`, pages, artifacts, tags, and activities. Fields that list, search, or
upload projections may omit—including `accountId`, `tags`, `pages`, and `isClosed`—are nullable and
must be checked before use.

Template creation body:

```json
{
  "signers": [{
    "role_id": "role_example",
    "id": "signer_example",
    "verification_method": "Email",
    "notification_methods": ["Email"],
    "step": 1
  }],
  "name": "Agreement.pdf",
  "message": "Please review and sign",
  "expires_at": "2026-12-31T23:59:59Z",
  "editor_fields": [{"field_id":"field_example","value":"Example value"}],
  "tags": ["Contracts"]
}
```

## SignerResource

Account CRUD is the preferred use of this resource. The last five methods are compatibility aliases
for signer-flow endpoints; new code should use `signerDocuments`, whose return types match the
current OpenAPI more precisely.

| Kotlin function | Exact request | Query/body | Return (`data`) |
|---|---|---|---|
| `create(CreateSignerRequest, accountId)` | `POST /v1/accounts/{accountId}/signers` | `{"full_name":string,"email":string?,"whatsapp_phone_number":string?}` | `Signer`; exact existing email is reused |
| `get(signerId, accountId)` | `GET /v1/accounts/{accountId}/signers/{signerId}` | none | `Signer` |
| `list(ListParams, accountId)` | `GET /v1/accounts/{accountId}/signers` | Optional `search`, `page`, `per-page`; other common-list fields are not sent | `PaginatedResult<Signer>` |
| `update(signerId, UpdateSignerRequest, accountId)` | `PUT /v1/accounts/{accountId}/signers/{signerId}` | Subset `full_name`, `email`, `whatsapp_phone_number`, `government_id` | `Signer` |
| `delete(signerId, accountId)` | `DELETE /v1/accounts/{accountId}/signers/{signerId}` | none | `Unit` |
| `findByEmail(email, accountId)` | Pages through signer list search | `search=email`, `page`, `per-page=100` | Exact case-insensitive `Signer?` |
| `getSelf(accessCode)` | `GET /v1/signers/self?signer-access-code=...` | none | Compatibility `Signer`; prefer `signerDocuments.self` |
| `acceptTerms(accessCode)` | `PUT /v1/signers/accept-terms?signer-access-code=...` | none | Compatibility `Map<String,Any>` |
| `verifyEmail(accessCode, verificationCode)` | `POST /v1/verify?signer-access-code=...` | `{"verification-code":string}` | Compatibility `Map<String,Any>` |
| `uploadSignature(accessCode, type, imageData, contentType, reuse)` | `POST /v1/signature?signer-access-code=...&type=...&reuse=...` | Raw image body; current API specifies PNG | `Unit` |
| `downloadSignature(accessCode, type)` | `GET /v1/signature/{type}?signer-access-code=...` | none | Raw `ByteArray` |

Deprecated `cpf`/`metadata` create fields and `cpf` update are sent only when explicitly set; they
are not part of the current create-signer OpenAPI schema.

The service locks signer updates while verification is in progress. Changing an unverified email or
WhatsApp number rotates the signer access and OTP codes; resend the notification before continuing.

## SignerDocumentResource

All methods use a client transport that contains no account API key or bearer token. Except for the
public artifact download, `accessCode` becomes the query key `signer-access-code` and never appears
in a JSON body.

| Kotlin function | Exact request | Body | Return (`data`) |
|---|---|---|---|
| `self(accessCode)` | `GET /v1/signers/self?signer-access-code=...` | none | `SignerSelf` |
| `getCurrent(signerId, accessCode)` | `GET /v1/signers/{signerId}/document?signer-access-code=...` | none | `DocumentDetails` |
| `getAssignment(accessCode, hasAcceptedTerms)` | `GET /v1/sign?signer-access-code=...&has_accepted_terms=...` | none | `DocumentDetails` |
| `sign(documentId, assignmentId, accessCode, entries)` | `POST /v1/documents/{documentId}/assignments/{assignmentId}?signer-access-code=...` | Collect: array of `SignAssignmentItemRequest`; confirmed virtual: exactly `[]` | API result `Map<String,Any>` |
| `decline(documentId, assignmentId, accessCode, declineReason)` | `PUT /v1/documents/{documentId}/assignments/{assignmentId}/reject?signer-access-code=...` | `{"decline_reason":string}` | `Unit` |
| `signMultiple(documentIds, accessCode)` | `PUT /v1/signers/documents/sign-multiple?signer-access-code=...` | `{"document_ids":[string,...]}` | `Unit` |
| `declineMultiple(documentIds, declineReason, accessCode)` | `PUT /v1/signers/documents/decline-multiple?signer-access-code=...` | `{"document_ids":[string,...],"decline_reason":string}` | `Unit` |
| `verifyEmail(accessCode, VerifySignerEmailRequest)` | `POST /v1/verify?signer-access-code=...` | `{"verification-code":string}` | `Unit` |
| `confirmData(documentId, accessCode, ConfirmSignerDataRequest)` | `PUT /v1/documents/{documentId}/signers/confirm-data?signer-access-code=...` | Subset `full_name`, `email`, `government_id`; terms use `acceptTerms` | `Signer` |
| `acceptTerms(accessCode)` | `PUT /v1/signers/accept-terms?signer-access-code=...` | none | `Unit` |
| `uploadSignature(accessCode, imageData, type, reuse)` | `POST /v1/signature?signer-access-code=...&type=...&reuse=...` | Raw PNG bytes, `Content-Type: image/png` | `Unit` |
| `downloadSignature(accessCode, type)` | `GET /v1/signature/{type}?signer-access-code=...` | none | Raw PNG `ByteArray` |
| `list(signerId, accessCode, ListParams)` | `GET /v1/signers/{signerId}/documents?signer-access-code=...` | Query uses only `page`, `per-page` | `PaginatedResult<DocumentDetails>` |
| `search(signerId, accessCode, search)` | `GET /v1/signers/{signerId}/documents/search?signer-access-code=...&search=...` | none | `PaginatedResult<DocumentDetails>` |
| `download(signerId, documentId, artifactName)` | `GET /v1/signers/{signerId}/documents/{documentId}/download/{artifactName}` (public) | none and no access code | Raw PDF/ZIP `ByteArray` |

For `DigitalCertificate`, call `confirmData` and `acceptTerms` before `getAssignment`; setting
`has_accepted_terms` on `getAssignment` is too late for that verification gate.

Signing item array:

```json
[
  {
    "itemId": "item_example",
    "fieldId": "field_example",
    "pageId": "page_example",
    "value": "Approved"
  }
]
```

A virtual assignment has no items. Confirm signer data first and call `sign(..., emptyList())`, which
sends the complete JSON body `[]`. A digital-certificate signer cannot call this operation. The API
prose names certificate start/complete routes, but the current v1 OpenAPI does not define their
paths, authentication, requests, or responses; the SDK therefore does not expose guessed methods.

## AssignmentResource

| Kotlin function | Exact request | Query/body | Return (`data`) |
|---|---|---|---|
| `list(ListParams, accountId)` | `GET /v1/assignments` | Optional `page`, `per-page`; explicitly passing `accountId` opts into the deployed-service compatibility `accountId` query | `PaginatedResult<Assignment>` |
| `create(documentId, CreateAssignmentRequest)` | `POST /v1/documents/{documentId}/assignments` | Full assignment request below | `Assignment` |
| `estimateCost(documentId, CreateAssignmentRequest)` | `POST /v1/documents/{documentId}/assignments/estimate-cost` | Projected `method`, `signers`, and `entries`; signer IDs/steps omitted | `CostEstimate` |
| `resetExpiration(documentId, assignmentId, expiresAt)` | `PUT /v1/documents/{documentId}/assignments/{assignmentId}/reset-expiration` | `{"expires_at":string}`; explicit null/blank opts into deployed clear compatibility `{"expires_at":null}` | `Assignment` |
| `decline(documentId, assignmentId, accessCode, reason)` | Compatibility alias for signer reject | `{"decline_reason":string}` and signer query code | `Unit` |
| `listWhatsappNotifications(documentId, assignmentId)` | `GET /v1/documents/{documentId}/assignments/{assignmentId}/whatsapp-notifications` | none | `List<WhatsappNotification>` |
| `resendNotification(documentId, assignmentId, signerId, channel?)` | `PUT /v1/documents/{documentId}/assignments/{assignmentId}/signers/{signerId}/resend` | none; optional deployed-service `{"channel":"email"\|"whatsapp"}` | `ResendEmailResponse` |
| `estimateResendCost(documentId, assignmentId, signerId)` | `POST /v1/documents/{documentId}/assignments/{assignmentId}/signers/{signerId}/estimate-resend-cost` | none | `CostEstimate` |

Create body:

```json
{
  "method": "collect",
  "signers": [{
    "id": "signer_example",
    "verification_method": "Email",
    "notification_methods": ["Email"],
    "step": 1
  }],
  "message": "Please review and sign",
  "expires_at": "2026-12-31T23:59:59Z",
  "copy_receivers": ["signer_copy_example"],
  "entries": [{
    "page_id": "page_example",
    "fields": [{
      "signer_id": "signer_example",
      "field_id": "field_example",
      "display_settings": {
        "left": 10.0, "top": 20.0, "width": 180.0, "height": 40.0,
        "fontSize": 12.0, "fontFamily": "sans-serif", "backgroundColor": "ffffff"
      }
    }]
  }]
}
```

Verification and notification are coupled. Send one, both, or neither; the API infers the missing
side, and omitting both defaults to Email. Exactly one notification method is allowed per signer:

| `verification_method` | Allowed `notification_methods` |
|---|---|
| `Email` | `["Email"]` |
| `Whatsapp` | `["Whatsapp"]` |
| `DigitalCertificate` | `["Email"]` or `["Whatsapp"]` |

The SDK applies this table, the signing-step rules, and the collect field-placement rules locally, so
an invalid pairing raises `ValidationException` instead of costing a round trip. WhatsApp delivery
requires a paid subscription and costs 0.45 credits per signer. A digital-certificate signer must be
alone in its signing step, requires account entitlement and a CPF/CNPJ in the signer's
`government_id` (set through `signers.update` after signer creation), costs two credits in addition
to its notification, and completes through certificate-specific signing.

`method` is `virtual` or `collect`. Create requires an ID for every signer. Steps, if present, form a
contiguous positive sequence; signers on a shared step act in parallel. A digital-certificate signer
must be alone in its step. A virtual assignment may be created while the document is `uploaded`,
`metadata_processing`, or `metadata_ready`; the service promotes it to `pending_signature` after
metadata processing. A collect assignment requires `metadata_ready` because its fields target
specific pages.

Step 1 signers are notified when the assignment is created. A later step is notified only after every
signer in all preceding steps has completed. Each assignment consumes one document from the plan
allowance; when that allowance is exhausted, an extra document costs 1 credit. Email notifications
cost 0 credits, WhatsApp notifications cost 0.45 credits each, and each digital-certificate signer
costs 2 credits in addition to notification costs. The estimate uses breakdown code
`SignatureDigitalCertificate` for that signer cost and may report `PendingPayment`,
`InsufficientDocuments`, or `InsufficientCredits` in `blocking_reason`.

In non-production environments, `listWhatsappNotifications` returns simulated messages without
real delivery. Its button URLs contain signer access or verification codes; treat those URLs as
credentials and do not log, persist, or publish them.

## FieldResource

| Kotlin function | Exact request | Query/body | Return (`data`) |
|---|---|---|---|
| `create(CreateFieldRequest, accountId)` | `POST /v1/accounts/{accountId}/fields` | `{"name":string,"type":string,"regex":string?,"is_required":boolean?}` | `FieldDefinition` |
| `list(includeInactive, includeStandard, accountId)` | `GET /v1/accounts/{accountId}/fields` | Optional `include_inactive`, `include_standard` | `List<FieldDefinition>` |
| `get(fieldId, accountId)` | `GET /v1/accounts/{accountId}/fields/{fieldId}` | none | `FieldDefinition` |
| `update(fieldId, UpdateFieldRequest, accountId)` | `PUT /v1/accounts/{accountId}/fields/{fieldId}` | Non-empty subset `name`, `regex` (including explicit null), `is_active` | `FieldDefinition` |
| `delete(fieldId, accountId)` | `DELETE /v1/accounts/{accountId}/fields/{fieldId}` | none | `Unit` |
| `validate(fieldId, value, accountId)` | `POST /v1/accounts/{accountId}/fields/{fieldId}/validate` | `{"value":any|null}` | `FieldValidationResult` |
| `validateMultiple(entries, accountId)` | `POST /v1/accounts/{accountId}/fields/validate-multiple` | `[ {"field_id":string,"value":any|null}, ... ]` | `List<FieldValidationResult>` |
| `listTypes()` | `GET /v1/field-types` | none | `List<FieldType>` |

`UpdateFieldRequest.clearRegex=true` sends an explicit JSON null. It cannot be combined with a new
`regex` value.

The standard `cpf` type expects 11 digits. `cnpj` accepts 14 characters: positions 1–12 may contain
digits or uppercase `A`–`Z`, while positions 13–14 are numeric check digits. API validation ignores
punctuation. Field placement geometry uses pixels in the 150-DPI page image, measured from the
upper-left corner. Keep each rectangle within the selected page's `width` and `height`; the API does
not clamp out-of-bounds values.

## UserResource

| Kotlin function | Exact request | Query/body | Return (`data`) |
|---|---|---|---|
| `getCurrent()` | `GET /v1/users/self` | none | `AuthenticatedUser` |
| `getStats(DocumentStatsQuery)` | `GET /v1/users/self/stats` | Optional `granularity=monthly\|daily`; daily requires `month=YYYY-MM` | `List<DocumentStatsRow>` |
| `getNotificationPreferences()` | `GET /v1/users/self/notification-preferences` | none | `NotificationPreferences` |
| `updateNotificationPreferences(request)` | `PUT /v1/users/self/notification-preferences` | Non-empty subset of the nine PascalCase preference keys | Complete `NotificationPreferences` |

All nine document-notification preferences are always returned and default to `true`. They govern
owner-facing email for the authenticated user across every account they belong to; setting a key to
`false` disables that email in all of those accounts. Welcome, password-reset, invitation, and
account-deletion emails are security/account messages and are not configurable here.

## TagResource

Tag identifiers, not names, are attached to documents. Tag colors accept six hexadecimal
characters, optionally prefixed by `#`; explicit color clearing is supported on update and cannot
be combined with a replacement color.

| Kotlin function | Exact request | Query/body | Return (`data`) |
|---|---|---|---|
| `list(search, accountId)` | `GET /v1/accounts/{accountId}/tags` | Optional `search` | `List<Tag>` |
| `create(name, color, accountId)` | `POST /v1/accounts/{accountId}/tags` | `{"name":string,"color":string?}` | `Tag` |
| `update(tagId, name, color, clearColor, accountId)` | `PUT /v1/accounts/{accountId}/tags/{tagId}` | Non-empty subset `name`, `color`; `clearColor` sends `color:null` | `Tag` |
| `delete(tagId, force, accountId)` | `DELETE /v1/accounts/{accountId}/tags/{tagId}` | Optional `force=true` query | `Unit` |

## TemplateResource

| Kotlin function | Exact request | Query/body | Return (`data`) |
|---|---|---|---|
| `list(ListParams, accountId)` | `GET /v1/accounts/{accountId}/templates` | Optional `search`, `page`, `per-page`; other `ListParams` fields are not sent | `PaginatedResult<Template>` |
| `get(templateId, accountId)` | `GET /v1/accounts/{accountId}/templates/{templateId}` | none | `Template` |

`get` is an optional service route retained for source compatibility; it is not one of the current
OpenAPI operations.
The list method deliberately ignores document-only `status`, `method`, `tags`, and `sort` fields.
Template status is `uploading` while the upload is in progress, `uploaded` once transferred,
`processing` while metadata is prepared, `ready` when the template can be used, or `failed` when
processing fails. Creating a document from a template requires one signer entry for every template
role, and each signer must already exist in the account.

## WebhookResource

| Kotlin function | Exact request | Query/body | Return (`data`) |
|---|---|---|---|
| `register(RegisterWebhookRequest, accountId)` | `PUT /v1/accounts/{accountId}/webhooks/subscriptions` | `{"url":string,"email":string,"events":[string],"is_active":boolean}` | `WebhookSubscription` |
| `get(accountId)` | `GET /v1/accounts/{accountId}/webhooks/subscriptions` | none | `WebhookSubscription?`; null on 404 |
| `inactivate(accountId)` | `PUT /v1/accounts/{accountId}/webhooks/inactivate` | none | `WebhookSubscription` |
| `listEventTypes()` | `GET /v1/webhooks/event-types` | none | `List<WebhookEventTypeInfo>` |
| Deprecated `listDispatches(ListParams, accountId)` | `GET /v1/accounts/{accountId}/webhooks` | Optional `page`, `per-page`; other `ListParams` fields are ignored | `PaginatedResult<WebhookDispatch>` |
| `listDispatches(WebhookDispatchParams, accountId)` | Same endpoint | Optional `endpoint_id`, `event`, `delivered`, Unix-second `from`/`to`, `page`, `per-page` | `PaginatedResult<WebhookDispatch>` |
| `retryDispatch(dispatchId, accountId)` | `POST /v1/accounts/{accountId}/webhooks/{historyId}/retry` | none | `WebhookDispatch` |
| `listEndpoints(accountId)` | `GET /v1/accounts/{accountId}/webhooks/endpoints` | none | `List<WebhookEndpoint>`, oldest first |
| `createEndpoint(CreateWebhookEndpointRequest, accountId)` | `POST /v1/accounts/{accountId}/webhooks/endpoints` | `{"url":string,"email":string,"events":[string],"name":string?,"is_active":boolean?,"signing_enabled":boolean?}` | `WebhookEndpoint`; 403 past the plan limit |
| `getEndpoint(endpointId, accountId)` | `GET /v1/accounts/{accountId}/webhooks/endpoints/{endpointId}` | none | `WebhookEndpoint?`; null on 404 |
| `updateEndpoint(endpointId, UpdateWebhookEndpointRequest, accountId)` | `PUT /v1/accounts/{accountId}/webhooks/endpoints/{endpointId}` | Only supplied members of `{"url","email","events","name","is_active","signing_enabled"}`; at least one | `WebhookEndpoint` |
| `deleteEndpoint(endpointId, accountId)` | `DELETE /v1/accounts/{accountId}/webhooks/endpoints/{endpointId}` | none | `Unit` |
| `getEndpointSecret(endpointId, accountId)` | `GET /v1/accounts/{accountId}/webhooks/endpoints/{endpointId}/secret` (API key only) | none | `WebhookEndpointSecret`; 400 when signing is disabled |
| `rotateEndpointSecret(endpointId, accountId)` | `POST /v1/accounts/{accountId}/webhooks/endpoints/{endpointId}/secret/rotate` (API key only) | none | `WebhookEndpointSecret`; the old secret stops working at once |

An account has 1 endpoint, or up to 3 on paid plans, and every active endpoint subscribed to an event
receives it independently. `register`, `get` and `inactivate` act on the account's oldest endpoint;
the `*Endpoint*` methods address each endpoint by ID. A `url` already used by another endpoint of the
workspace answers `400`.

If `events` is `null`, the SDK subscribes to `RegisterWebhookRequest.DEFAULT_EVENTS`. An explicit
empty list is sent unchanged. Retrieve the server's current event catalog with `listEventTypes()` before
offering a selection to users.

Assinafy delivers each event as an `application/json` HTTP `POST`; any `2xx` response succeeds. A
failed event is attempted at most twice (the initial request and one retry after 3 seconds). After 10
consecutive failed events, the circuit breaker pauses normal delivery and probes about 5% of events
until one succeeds; `retryDispatch` forces another delivery. Dispatch history stores only the first
2,000 characters of the endpoint response body. Deduplicate with the `webhook-id` header, identical
on every attempt of the same event to the same endpoint.
`subject` and `object` are polymorphic resource objects, and receivers should accept unknown fields as
forward-compatible additions.

### WebhookVerifier

`WebhookVerifier(webhookSecret)` is a server-side helper; it performs no request. Endpoints created
with `signing_enabled: true` sign every delivery following
[Standard Webhooks](https://www.standardwebhooks.com). Construct the verifier with the endpoint's
`whsec_` secret from `getEndpointSecret` (the prefix is optional).

`verifySignature(payload: ByteArray|String, webhookId, webhookTimestamp, webhookSignature,
toleranceSeconds = DEFAULT_TOLERANCE_SECONDS, nowEpochSeconds = now)` computes HMAC-SHA256 over
`{webhook-id}.{webhook-timestamp}.{raw body}` with the base64-decoded secret and returns `true` when
any space-separated `v1,<base64>` entry matches in constant time and the timestamp is within the
tolerance. It returns `false` for a missing header, a missing or malformed secret, a stale or future
timestamp, or no matching signature. Pass the raw body bytes, never re-serialized JSON.

| Constant | Value |
|---|---|
| `WebhookVerifier.HEADER_ID` | `webhook-id` |
| `WebhookVerifier.HEADER_TIMESTAMP` | `webhook-timestamp` |
| `WebhookVerifier.HEADER_SIGNATURE` | `webhook-signature` |
| `WebhookVerifier.DEFAULT_TOLERANCE_SECONDS` | `300` |

```kotlin
val verifier = WebhookVerifier(endpointSecret)
if (!verifier.verifySignature(rawBody, idHeader, timestampHeader, signatureHeader)) return respond(401)
val event = verifier.extractEvent(rawBody)
```

The deprecated `verify(payload, signature)` checks a hex HMAC that Assinafy deliveries do not carry.
`toString()` redacts the secret. `extractEvent(payload: ByteArray|String)` parses `WebhookPayload?`. `getEventType(event)` returns
`event` then legacy `type`; `getEventData(event)` returns `payload` or an empty map. Never embed a
webhook shared secret in an Android application.

## Request types

`?` means nullable/omittable. Wire names are shown exactly.

| Kotlin type | Complete fields |
|---|---|
| `AssinafyClientConfig` | `apiKey:String?`, `token:String?`, `accountId:String?`, `baseUrl:String`, `webhookSecret:String?`, `timeoutMs:Long`, `logger:Logger?` |
| `ListParams` | query `page:Int?`, `per-page:Int?`, `search:String?`, `sort:String?`, `status:String?`, `method:String?`, `tags:List<String>?` (comma-separated tag IDs) |
| `LoginRequest` | `email:String`, `password:String` |
| `RequestPasswordResetRequest` | `email:String` |
| `ResetPasswordRequest` | `email:String`, `new_password:String`, `token:String?` |
| `ChangePasswordRequest` | `email:String`, `password:String`, `new_password:String` |
| `SocialLoginRequest` | `provider:"google"`, `token:String`, `has_accepted_terms:Boolean` |
| `LinkSocialLoginRequest` | `provider:"google"`, `token:String` |
| `CreateApiKeyRequest` | `password:String` |
| `CreateWorkspaceRequest` | `name:String`, `notification_sender_type:String?`; compatibility `primary_color:String?`, `secondary_color:String?` |
| `UpdateWorkspaceRequest` | `name:String?`, `notification_sender_type:String?`; compatibility `primary_color:String?`, `secondary_color:String?` |
| `CreateSignerRequest` | `full_name:String`, `email:String?`, `whatsapp_phone_number:String?`, `government_id:String?` (CPF or CNPJ; formatting accepted, normalized by the API); deprecated `cpf:String?` (sent as `government_id` when `governmentId` is null), `metadata:Map?` |
| `UpdateSignerRequest` | `full_name:String?`, `email:String?`, `whatsapp_phone_number:String?`, `government_id:String?`; deprecated compatibility `cpf:String?` |
| `ConfirmSignerDataRequest` | `full_name:String?`, `email:String?`, `government_id:String?`; deprecated `whatsapp_phone_number` and `has_accepted_terms` are not sent by `signerDocuments.confirmData` |
| `SignerReference` | `id:String?` (required for create), `verification_method:String?`, `notification_methods:List<String>?` (Email, WhatsApp, or both), `step:Int?` |
| `CreateAssignmentRequest` | `method:String`, `signers:List<SignerReference>`, `message:String?`, `expires_at:String?`, `copy_receivers:List<String>?` (signer IDs), `entries:List<AssignmentEntry>?` |
| `AssignmentEntry` | `page_id:String`, `fields:List<AssignmentFieldPlacement>` |
| `AssignmentFieldPlacement` | `signer_id:String`, `field_id:String`, `display_settings:DisplaySettings?` |
| `DisplaySettings` | `left:Float`, `top:Float`, `width:Float`, `height:Float`, `fontSize:Float`, `fontFamily:String?`, `backgroundColor:String?` |
| `TemplateSigner` | `role_id:String`, `id:String?` (required for create), `verification_method:String?`, `notification_methods:List<String>?` (exactly one when supplied), `step:Int?` |
| `CreateDocumentFromTemplateRequest` | `signers:List<TemplateSigner>`, `name:String?`, `message:String?`, `expires_at:String?`, `editor_fields:List<TemplateEditorField>?`, `tags:List<String>?` (tag names; missing names are created and merged with template defaults) |
| `TemplateEditorField` | `field_id:String`, `value:String` |
| `CreateFieldRequest` | `name:String`, `type:String`, `regex:String?`, `is_required:Boolean?` |
| `UpdateFieldRequest` | local `name:String?`, `regex:String?`, `clearRegex:Boolean`, `isActive:Boolean?`; serialized as `name`, `regex`, `is_active` |
| `FieldValidationEntry` | `field_id:String`, `value:Any?` |
| `SignAssignmentItemRequest` | `itemId:String`, `fieldId:String`, `pageId:String`, `value:String` |
| `VerifySignerEmailRequest` | `verification-code:String` |
| `DocumentStatsQuery` | query `granularity:DocumentStatsGranularity?` (`monthly` or `daily`), `month:String?` (`YYYY-MM`, required for daily) |
| `UpdateNotificationPreferencesRequest` | Nullable booleans: `DocumentCompleted`, `SignerDeclined`, `DocumentCancelled`, `DocumentAboutToExpire`, `DocumentExpired`, `DocumentExpirationReset`, `DocumentProcessingFailed`, `TemplateProcessingFailed`, `SignerWhatsappFailed` |
| `RegisterWebhookRequest` | `url:String`, `email:String`, `events:List<String>?`, `is_active:Boolean` |
| `CreateWebhookEndpointRequest` | `url:String`, `email:String`, `events:List<String>` (nonempty), `name:String?`, `is_active:Boolean?`, `signing_enabled:Boolean?` |
| `UpdateWebhookEndpointRequest` | `url:String?`, `email:String?`, `events:List<String>?`, `name:String?`, `is_active:Boolean?`, `signing_enabled:Boolean?`; only non-null members are sent |
| `MfaVerifyRequest` | `mfa_token:String`, `code:String`; `toString()` redacts both |
| `ConfirmTotpRequest` | `id:String`, `code:String`, `password:String?`, `reauth_code:String?`; `toString()` redacts secrets |
| `MfaReauthRequest` | `password:String?`, `code:String?`; `toString()` redacts both |
| `WebhookDispatchParams` | query `endpoint_id:String?`, `event:String?`, `delivered:Boolean?`, `from:Long?`, `to:Long?`, `page:Int?`, `per-page:Int?` |
| `UploadAndRequestSignaturesRequest` | Local workflow: `fileData:ByteArray`, `fileName:String`, `signers:List<SignerEntry>`, `message:String?`, `metadata:Map?`, `waitForReady:Boolean`, `expiresAt:String?`, `copyReceivers:List<String>?` (signer IDs), `accountId:String?` |
| `UploadAndRequestSignaturesRequest.SignerEntry` | `name:String`, `email:String`, `whatsappPhoneNumber:String?`, `governmentId:String?`, deprecated compatibility `cpf:String?`, `metadata:Map?` |

## Response types

The following tables list every serialized field accepted by the public response models. Kotlin
property names differ only where shown after `→`. Nullable fields use `?`; list defaults do not
imply that the server always returns a key. The OpenAPI response components omit global `required`
arrays, so the SDK keeps stable endpoint invariants such as resource IDs non-null while projection
fields that may be absent remain nullable. Labelled optional fields make decoding tolerant without
changing standard requests.

### Authentication and account models

| Kotlin type | Complete wire fields and Kotlin types |
|---|---|
| `AuthenticationSession` | `access_token→accessToken:String`, `user:AuthenticatedUser`, `accounts:List<AuthenticatedAccount>` |
| `AuthenticatedUser` | `id:String`, `name:String`, `email:String`, `telephone:String?`, `government_id→governmentId:String?`, `is_email_verified→isEmailVerified:Boolean`, `has_accepted_terms→hasAcceptedTerms:Boolean`, `created_at→createdAt:String`, `to_be_deleted_at→toBeDeletedAt:String?` |
| `AuthenticatedAccount` | `id:String`, `name:String`, `roles:List<String>`, `is_delete_allowed→isDeleteAllowed:Boolean`, `created_at→createdAt:String` |
| `AuthenticationEmailResponse` | `email:String` |
| `ApiKeyResponse` | `api_key→apiKey:String?` |
| `MfaStatus` | `methods:List<MfaMethod>`, `recovery_codes_remaining→recoveryCodesRemaining:Int` |
| `MfaMethod` | `id:String`, `type:String?`, `label:String?`, `confirmed_at→confirmedAt:String?`, `last_used_at→lastUsedAt:String?` |
| `TotpEnrollment` | `id:String`, `secret:String`, `provisioning_uri→provisioningUri:String`; `toString()` redacts the secret and URI |
| `MfaRecoveryCodes` | `recovery_codes→recoveryCodes:List<String>`; `toString()` redacts the codes |
| `MfaRemoval` | `is_mfa_enabled→isMfaEnabled:Boolean` |
| `Workspace` / `WorkspaceListItem` | `resource:String?`, `id:String`, `name:String`, `primary_color→primaryColor:String?`, `secondary_color→secondaryColor:String?`, `notification_sender_type→notificationSenderType:String?`, `is_delete_allowed→isDeleteAllowed:Boolean?`, `roles:List<String>?`, `created_at→createdAt:String?` |
| `AccountTheme` | `account_name→accountName:String?`, `primary_color→primaryColor:String?`, `secondary_color→secondaryColor:String?`, `logo:String?` |

### Documents, assignments, and signers

| Kotlin type | Complete wire fields and Kotlin types |
|---|---|
| `DocumentArtifacts` | `original:String?`, `thumbnail:String?`, `certificated:String?`, `certificate-page→certificatePage:String?`, `bundle:String?`, `pades:String?` |
| `DocumentPage` | `id:String`, `number:Int?`, `height:Int?`, `width:Int?`, `download_url→downloadUrl:String?` |
| `DocumentListItem` / `DocumentUploadResponse` | 2.0 Kotlin aliases of `DocumentDetails`; recompile 1.x consumers because the distinct JVM classes were removed, and handle nullable projection fields |
| `DocumentDetails` | `resource:String?`, `id:String`, `account_id→accountId:String?`, `template_id→templateId:String?`, `name:String`, `status:String`, `assignment:Assignment?`, deployed response `download_url→downloadUrl:String?` and `download_final_url→downloadFinalUrl:String?`, `signing_url→signingUrl:String?`, `artifacts:DocumentArtifacts?`, `tags:List<Tag>?`, `pages:List<DocumentPage>?`, `created_at→createdAt:String?`, `updated_at→updatedAt:String?`, `is_closed→isClosed:Boolean?`, `decline_reason→declineReason:String?`, `declined_by→declinedBy:Signer?`, deployed response `activities:List<DocumentActivity>?` |
| `PublicDocumentInfo` | `id:String`, `name:String`, `resource:String?`, `account_id→accountId:String?`, `template_id→templateId:String?`, `status:String?`, `artifacts:DocumentArtifacts?`, `is_closed→isClosed:Boolean?`, `signing_url→signingUrl:String?`, `decline_reason→declineReason:String?`, `declined_by→declinedBy:Signer?`, `tags:List<Tag>?`, `assignment:Assignment?`, `pages:List<DocumentPage>?`, `created_at→createdAt:String?`, `updated_at→updatedAt:String?`; deployed responses `page_count→pageCount:Number?`, `created_by→createdBy:String?` |
| `DocumentActivity` | `id:Long`, `event:String`, `message:String?`, deployed-widened `payload:Any?` (the frozen schema declares an object), `origin:Map<String,Any>?`, `created_at→createdAt:String?` |
| `DocumentVerification` | `hash:String`, `id:String?`, `status:String?`, `page_count→pageCount:String?`, `signer_count→signerCount:String?`, `completed_count→completedCount:Int?`, `completed_at→completedAt:String?`, `verified_at→verifiedAt:String`, `is_valid→isValid:Boolean`, `message:String`, `agreement_code→agreementCode:String?` |
| `DocumentStatusInfo` | `code:String`, `deletable:Boolean?` |
| `SigningProgress` | Local `signed:Int`, `total:Int`, `pending:Int`, `percentage:Double` |
| `Assignment` | `resource:String?`, `id:String`, `sender_email→senderEmail:String?`, `method:String?`, `expires_at→expiresAt:String?`, compatibility `expiration:String?`, `message:String?`, `signers:List<Signer>`, `copy_receivers→copyReceivers:List<Signer>?`, `items:List<AssignmentItem>?`, `summary:AssignmentSummary?`, `signing_urls→signingUrls:List<SigningUrl>?` |
| `AssignmentSummary` | `signer_count→signerCount:Int`, `completed_count→completedCount:Int`, `signers:List<Signer>` |
| `SigningUrl` | `signer_id→signerId:String`, `url:String` |
| `Signer` | `id:String`, `full_name→fullName:String?`, `email:String?`, `whatsapp_phone_number→whatsappPhoneNumber:String?`, legacy `cpf:String?`, deployed response `government_id→governmentId:String?`, `has_accepted_terms→hasAcceptedTerms:Boolean?`, legacy `metadata:Map<String,Any>?`; assignment expansions `completed:Boolean?`, `verification_method→verificationMethod:String?`, `notification_methods→notificationMethods:List<String>?`, `step:Int?`, `notified:Boolean?`, `notification_history→notificationHistory:List<NotificationHistoryEntry>?`; signer-self expansions `has_signature→hasSignature:Boolean?`, `has_initial→hasInitial:Boolean?`, `is_signature_reusable→isSignatureReusable:Boolean?`; `resource:String?` |
| `SignerSelf` | `resource:String?`, `id:String`, `full_name→fullName:String?`, `email:String?`, `whatsapp_phone_number→whatsappPhoneNumber:String?`, `government_id→governmentId:String?`, `has_accepted_terms→hasAcceptedTerms:Boolean?`, `has_signature→hasSignature:Boolean?`, `has_initial→hasInitial:Boolean?`, `is_signature_reusable→isSignatureReusable:Boolean?` |
| `NotificationHistoryEntry` | `event:String?`, `status:String?`, `error_code→errorCode:String?`, `error_message→errorMessage:String?`, `sent_at→sentAt:String?`, `failed_at→failedAt:String?` |
| `ResendEmailResponse` | `is_sent→isSent:Boolean?`, `document_id→documentId:String?`, `signer_id→signerId:String?` |
| `WhatsappNotification` | `sent_at→sentAt:Long?`, `header:String?`, `body:String?`, `buttons:List<WhatsappNotificationButton>`, `phone_number→phoneNumber:String?`, `signer_id→signerId:String?` |
| `WhatsappNotificationButton` | `text:String`, deployed response `url:String?` |

`AssignmentItem` objects inside `Assignment.items` follow the API schema:
`id:String?`, `signer:Signer?`, `field:FieldDefinition?`, `page:DocumentPage?`,
`display_settings:Any?`, `value:Any?`, and `completed:Boolean`. The two dynamic values remain opaque
because their JSON shapes vary by assignment and field type.

### Pricing, fields, tags, and templates

| Kotlin type | Complete wire fields and Kotlin types |
|---|---|
| `CostEstimate` | `documents:Int?`, `credits:Double?`, `needs_extra_document→needsExtraDocument:Boolean?`, `extra_document_cost→extraDocumentCost:Double?`, `total_credits→totalCredits:Double?`, `breakdown:List<CostEstimateBreakdownItem>`, `document_balance→documentBalance:Double?`, `credit_balance→creditBalance:Double?`, `has_sufficient_resources→hasSufficientResources:Boolean?`, `blocking_reason→blockingReason:String?`, `message:String?`; compatibility `total→legacyTotal:Double?`, `has_sufficient_credits→legacyHasSufficientCredits:Boolean?` |
| `CostEstimateBreakdownItem` | `code:String`, `name:String`, `cost:Double`, `quantity:Int?`, `unit_cost→unitCost:Double?` |
| `FieldDefinition` | `resource:String?`, `id:String`, `name:String`, `type:String`, `regex:String?`, `is_pre_defined→isPreDefined:Boolean?`, `is_active→isActive:Boolean`, `is_required→isRequired:Boolean?`, `is_standard→isStandard:Boolean?`, `is_read_only→isReadOnly:Boolean?`, `is_visible→isVisible:Boolean?` |
| `FieldType` | `type:String`, `name:String` |
| `FieldValidationResult` | `field_id→fieldId:String?`, `type:String?`, `success:Boolean`, `error_message→errorMessage:String` |
| `Tag` | `resource:String?`, `id:String`, `name:String`, `color:String?`, `created_at→createdAt:String?`, `updated_at→updatedAt:String?` |
| `Template` / `TemplateListItem` | `resource:String?`, `id:String`, `name:String`, `document_name→documentName:String?`, `message:String?`, `status:String`, deployed response `account_id→accountId:String?`, `pages:List<TemplatePage>?`, `roles:List<TemplateRole>?`, `tags:List<Tag>?`, `default_document_tags→defaultDocumentTags:List<Tag>?`, `created_at→createdAt:String`, `updated_at→updatedAt:String?` |
| `TemplateRole` | `id:String`, `name:String`, `assignment_type→assignmentType:String?`, `created_at→createdAt:String?`, `updated_at→updatedAt:String?` |
| `TemplatePage` | `id:String`, `number:Int?`, `height:Int?`, `width:Int?`, `download_url→downloadUrl:String?`, `fields:List<TemplateFieldPlacement>?` |
| `TemplateFieldPlacement` | `id:String`, `field_id→fieldId:String?`, `role_id→roleId:String?`, `label:String?`, `display_settings→displaySettings:Any?`, `created_at→createdAt:String?`, `updated_at→updatedAt:String?` |

### Statistics, preferences, webhooks, and pagination

| Kotlin type | Complete wire fields and Kotlin types |
|---|---|
| `DocumentStatsRow` | `period:String`, then `Long`: `documents_uploaded`, `documents_sent`, `signature_requests`, `signature_requests_notification_email`, `signature_requests_notification_whatsapp`, `signature_requests_notification_bypass`, `signature_requests_verification_email`, `signature_requests_verification_whatsapp`, `signature_requests_verification_bypass`, `signature_requests_verification_digital_certificate`, `signature_requests_viewed`, `signature_requests_completed`, `documents_certified` |
| `NotificationPreferences` | Nine required `Boolean` fields: `DocumentCompleted`, `SignerDeclined`, `DocumentCancelled`, `DocumentAboutToExpire`, `DocumentExpired`, `DocumentExpirationReset`, `DocumentProcessingFailed`, `TemplateProcessingFailed`, `SignerWhatsappFailed` |
| `WebhookSubscription` | `url:String?`, `email:String?`, `events:List<String>`, `is_active→isActive:Boolean`, `updated_at→updatedAt:String?` |
| `WebhookEventTypeInfo` | `id:String`, `description:String?` |
| `WebhookEndpoint` | `id:String`, `name:String?`, `url:String`, `email:String?`, `events:List<String>`, `is_active→isActive:Boolean`, `signing_enabled→signingEnabled:Boolean`, `created_at→createdAt:String?`, `updated_at→updatedAt:String?` |
| `WebhookEndpointSecret` | `secret:String` (`whsec_` + base64 key); `toString()` redacts it |
| `WebhookDispatch` | `resource:String?`, `id:String`, `event:String`, `activity_id→activityId:Long?`, `endpoint_id→endpointId:String?` (null once the endpoint is deleted), `endpoint:String?`, `payload:Map<String,Any>?`, `delivered:Boolean`, `http_status→httpStatus:Int?`, `response_body→responseBody:String?`, `error:String?`, `created_at→createdAt:String?`, `updated_at→updatedAt:String?` |
| `WebhookPayload` | `id:Long?`, `event:String?`, compatibility `type:String?`, `message:String?`, `payload:Map<String,Any>?`, `subject:Map<String,Any>?`, `object→obj:Map<String,Any>?`, `origin:Map<String,Any>?`, `created_at→createdAt:Long?`, `account_id→accountId:String?` |
| `PaginatedResult<T>` | Local `data:List<T>`, `meta:PaginationMeta?` |
| `PaginationMeta` | Local `currentPage:Int?`, `lastPage:Int?`, `perPage:Int?`, `total:Int?` populated from headers |

Statistics default to monthly granularity and 12 zero-filled periods; daily queries return every day
in the requested `YYYY-MM`. Rows are newest-first. Notification counters can exceed
`signature_requests` because a signer notified through multiple channels counts once in each
channel. The verification counters partition `signature_requests` and therefore sum to that total.

## Constants

- `SdkConstants`: `VERSION`/`USER_AGENT`, production `DEFAULT_BASE_URL`, 30-second
  `DEFAULT_TIMEOUT_MS`, 25 MiB `MAX_UPLOAD_BYTES`, 255-character
  `MAX_DOCUMENT_NAME_LENGTH`, 2-second `DEFAULT_POLL_INTERVAL_MS`, and 120-second
  `DEFAULT_MAX_WAIT_MS`.
- `DocumentArtifact`: `original`, `certificated`, `certificate-page`, `pades`, `bundle`.
- `SignatureType`: `signature`, `initial`.
- `AssignmentMethod`: `virtual`, `collect`.
- `VerificationMethod`: `Email`, `Whatsapp`, `DigitalCertificate` (ICP-Brasil A1/A3), plus `ALL`.
- `NotificationMethod`: `Email`, `Whatsapp`, plus `ALL`. Exactly one per signer, paired with the
  verification method.
- `OAuthScope`: `documents:read`, `documents:write`, `templates:read`, `templates:write`,
  `account:read`, `webhooks:write`, `openid`, `profile`, `email`, `offline_access`.
- `OAuthConfig.DEFAULT_AUTHORIZATION_SERVER`: `https://auth.assinafy.com.br`.
- `OAuthChallenge.INSUFFICIENT_SCOPE`: `insufficient_scope`.
- `DocumentStatus.CERTIFICATED`: `certificated`.
- `DocumentStatus.READY`: `metadata_ready`, `pending_signature`, `certificating`, `certificated`.
- `DocumentStatus.FAILED`: `failed`, `rejected_by_signer`, `rejected_by_user`, `expired`.
- `SocialLoginProvider.GOOGLE`: `google`.
- `NotificationSenderType`: `USER` (`User`) and `ACCOUNT` (`Account`), for `notification_sender_type`.
- `CostBlockingReason`: `PENDING_PAYMENT` (`PendingPayment`), `INSUFFICIENT_DOCUMENTS`
  (`InsufficientDocuments`), `INSUFFICIENT_CREDITS` (`InsufficientCredits`), for `CostEstimate.blockingReason`.
- `NotificationDeliveryStatus`: `SENT` (`sent`) and `FAILED` (`failed`), for `NotificationHistoryEntry.status`.
- `MfaMethodType.TOTP`: `totp`, for `MfaMethod.type`.
- `DocumentStatsGranularity`: `MONTHLY` (`monthly`) and `DAILY` (`daily`).
- `Logger.NONE`: no-op logger used by default.
- `RegisterWebhookRequest.DEFAULT_EVENTS`: `document_ready`, `document_prepared`,
  `signer_signed_document`, `signer_rejected_document`, `document_processing_failed`.
- `WebhookEvent`: `document_uploaded`, `document_metadata_ready`, `document_prepared`,
  `assignment_created`, `signature_requested`, `document_ready`, `signer_created`,
  `signer_email_verified`, `signer_whatsapp_verified`, `signer_data_confirmed`,
  `signer_signed_document`, `signer_viewed_document`, `signer_rejected_document`,
  `user_rejected_document`, `document_processing_failed`, `template_created`,
  `template_processed`, and `template_processing_failed`. Use `webhooks.listEventTypes()` to accept
  future server events without an SDK update.

## Complete method payloads

Examples use placeholder identities. Optional properties may be omitted by the service or the caller;
arrays show one illustrative item. A JSON example for multipart or raw image input describes its
parts; send binary bytes with the stated content type, rather than serializing that example.
Event-dependent opaque objects have no fixed member schema.

### `workspaces.get`

`GET /v1/accounts/{accountId}`

Parameters: `accountId` (path, required).

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
{
  "resource": "account",
  "id": "id-placeholder",
  "name": "Example",
  "primary_color": null,
  "secondary_color": null,
  "notification_sender_type": "User",
  "roles": ["example"],
  "is_delete_allowed": true,
  "created_at": "2026-01-01T00:00:00Z"
}
```

### `workspaces.update`

`PUT /v1/accounts/{accountId}`

Parameters: `accountId` (path, required).

Request body (`application/json`; optional members may be omitted):
```json
{
  "name": "Example",
  "notification_sender_type": "User"
}
```

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
{
  "resource": "account",
  "id": "id-placeholder",
  "name": "Example",
  "primary_color": null,
  "secondary_color": null,
  "notification_sender_type": "User",
  "roles": ["example"],
  "is_delete_allowed": true,
  "created_at": "2026-01-01T00:00:00Z"
}
```

### `workspaces.delete`

`DELETE /v1/accounts/{accountId}`

Parameters: `accountId` (path, required).

Request body (`application/json`; optional members may be omitted):
```json
{
  "force": true
}
```

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
[]
```

### `workspaces.getTheme`

`GET /v1/accounts/{accountId}/theme`

Parameters: `accountId` (path, required).

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
{
  "account_name": "example",
  "primary_color": "2072b9",
  "secondary_color": null,
  "logo": "https://example.com/resource"
}
```

### `workspaces.getLogo`

`GET /v1/accounts/{accountId}/logo`

Parameters: `accountId` (path, required).

Request body: none.

Response 200: raw binary bytes (`image/*`), without a JSON envelope.

### `workspaces.uploadLogo`

`POST /v1/accounts/{accountId}/logo`

Parameters: `accountId` (path, required).

Request body (`multipart/form-data`; optional members may be omitted):
```json
{
  "file": "<binary bytes>"
}
```

Response 200 body; optional members depend on document state and permissions:
```json
{
  "status": 200,
  "message": "example"
}
```

### `workspaces.deleteLogo`

`DELETE /v1/accounts/{accountId}/logo`

Parameters: `accountId` (path, required).

Request body: none.

Response 200 body; optional members depend on document state and permissions:
```json
{
  "status": 200,
  "message": "example"
}
```

### `workspaces.list`

`GET /v1/accounts`

Parameters: none.

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
[
  {
    "resource": "account",
    "id": "id-placeholder",
    "name": "Example",
    "primary_color": null,
    "secondary_color": null,
    "notification_sender_type": "User",
    "roles": ["example"],
    "is_delete_allowed": true,
    "created_at": "2026-01-01T00:00:00Z"
  }
]
```

### `workspaces.create`

`POST /v1/accounts`

Parameters: none.

Request body (`application/json`; optional members may be omitted):
```json
{
  "name": "Example",
  "notification_sender_type": "User"
}
```

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
{
  "resource": "account",
  "id": "id-placeholder",
  "name": "Example",
  "primary_color": null,
  "secondary_color": null,
  "notification_sender_type": "User",
  "roles": ["example"],
  "is_delete_allowed": true,
  "created_at": "2026-01-01T00:00:00Z"
}
```

### `workspaces.getStats`

`GET /v1/accounts/{accountId}/stats`

Parameters: `accountId` (path, required), `granularity` (query, optional), `month` (query, optional).

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
[
  {
    "period": "example",
    "documents_uploaded": 1,
    "documents_sent": 1,
    "signature_requests": 1,
    "signature_requests_notification_email": 1,
    "signature_requests_notification_whatsapp": 1,
    "signature_requests_notification_bypass": 1,
    "signature_requests_verification_email": 1,
    "signature_requests_verification_whatsapp": 1,
    "signature_requests_verification_bypass": 1,
    "signature_requests_verification_digital_certificate": 1,
    "signature_requests_viewed": 1,
    "signature_requests_completed": 1,
    "documents_certified": 1
  }
]
```

### `assignments.list`

`GET /v1/assignments`

Parameters: `page` (query, optional), `per-page` (query, optional).

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
[
  {
    "resource": "assignment",
    "id": "id-placeholder",
    "sender_email": "person@example.com",
    "method": "virtual",
    "expires_at": null,
    "message": null,
    "signers": [{"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true,"verification_method": null,"notification_methods": ["Email"],"step": 1,"notified": true,"completed": true,"notification_history": [{"event": "example","status": "sent","error_code": null,"error_message": null,"sent_at": null,"failed_at": null}]}],
    "copy_receivers": [{"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true}],
    "items": [{"id": "id-placeholder","page": {"id": "id-placeholder","number": 1,"height": 1,"width": 1,"download_url": "https://example.com/resource"},"signer": {"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true},"field": {"resource": "field","id": "id-placeholder","name": "Example","type": "text","regex": null,"is_pre_defined": true,"is_active": true,"is_required": true,"is_standard": true,"is_read_only": true,"is_visible": true},"display_settings": {"left": 1,"top": 1,"width": 1,"height": 1,"fontFamily": "Arial","fontSize": 1,"backgroundColor": "#D5EBFF"},"value": null,"completed": true}],
    "summary": {"signer_count": 1,"completed_count": 1,"signers": [{"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true}]},
    "signing_urls": [{"signer_id": "signer-id-placeholder","url": "https://example.com/resource"}]
  }
]
```

### `assignments.create`

`POST /v1/documents/{documentId}/assignments`

Parameters: `documentId` (path, required).

Request body (`application/json`; optional members may be omitted):
```json
{
  "method": "collect",
  "signers": [{"id": "id-placeholder","verification_method": "Email","notification_methods": ["Email"],"step": 1}],
  "entries": [{"page_id": "page-id-placeholder","fields": [{"signer_id": "signer-id-placeholder","field_id": "field-id-placeholder","display_settings": {"left": 1,"top": 1,"width": 1,"height": 1,"fontFamily": "Arial","fontSize": 1,"backgroundColor": "#D5EBFF"}}]}],
  "message": "example",
  "expires_at": "2026-01-01T00:00:00Z",
  "copy_receivers": ["example"]
}
```

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
{
  "resource": "assignment",
  "id": "id-placeholder",
  "sender_email": "person@example.com",
  "method": "virtual",
  "expires_at": null,
  "message": null,
  "signers": [{"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true,"verification_method": null,"notification_methods": ["Email"],"step": 1,"notified": true,"completed": true,"notification_history": [{"event": "example","status": "sent","error_code": null,"error_message": null,"sent_at": null,"failed_at": null}]}],
  "copy_receivers": [{"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true}],
  "items": [{"id": "id-placeholder","page": {"id": "id-placeholder","number": 1,"height": 1,"width": 1,"download_url": "https://example.com/resource"},"signer": {"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true},"field": {"resource": "field","id": "id-placeholder","name": "Example","type": "text","regex": null,"is_pre_defined": true,"is_active": true,"is_required": true,"is_standard": true,"is_read_only": true,"is_visible": true},"display_settings": {"left": 1,"top": 1,"width": 1,"height": 1,"fontFamily": "Arial","fontSize": 1,"backgroundColor": "#D5EBFF"},"value": null,"completed": true}],
  "summary": {"signer_count": 1,"completed_count": 1,"signers": [{"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true}]},
  "signing_urls": [{"signer_id": "signer-id-placeholder","url": "https://example.com/resource"}]
}
```

### `assignments.estimateCost`

`POST /v1/documents/{documentId}/assignments/estimate-cost`

Parameters: `documentId` (path, required).

Request body (`application/json`; optional members may be omitted):
```json
{
  "method": "collect",
  "signers": [{"verification_method": "Email","notification_methods": ["Email"]}],
  "entries": [{"page_id":"page-placeholder","fields":[{"signer_id":"signer-placeholder","field_id":"field-placeholder","display_settings":{"left":72,"top":640,"width":180,"height":40,"fontSize":12}}]}]
}
```

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
{
  "documents": 1,
  "credits": 1,
  "needs_extra_document": true,
  "extra_document_cost": 1,
  "total_credits": 1,
  "breakdown": [{"code": "example","name": "Example","cost": 1,"quantity": 1,"unit_cost": 1}],
  "document_balance": 1,
  "credit_balance": 1,
  "has_sufficient_resources": true,
  "blocking_reason": "PendingPayment",
  "message": null
}
```

### `assignments.resendNotification`

`PUT /v1/documents/{documentId}/assignments/{assignmentId}/signers/{signerId}/resend`

Parameters: `documentId` (path, required), `assignmentId` (path, required), `signerId` (path, required).

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
{
  "is_sent": true,
  "document_id": "document-id-placeholder",
  "signer_id": "signer-id-placeholder"
}
```

### `assignments.estimateResendCost`

`POST /v1/documents/{documentId}/assignments/{assignmentId}/signers/{signerId}/estimate-resend-cost`

Parameters: `documentId` (path, required), `assignmentId` (path, required), `signerId` (path, required).

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
{
  "documents": 1,
  "credits": 1,
  "needs_extra_document": true,
  "extra_document_cost": 1,
  "total_credits": 1,
  "breakdown": [{"code": "example","name": "Example","cost": 1,"quantity": 1,"unit_cost": 1}],
  "document_balance": 1,
  "credit_balance": 1,
  "has_sufficient_resources": true,
  "blocking_reason": "PendingPayment",
  "message": null
}
```

### `assignments.resetExpiration`

`PUT /v1/documents/{documentId}/assignments/{assignmentId}/reset-expiration`

Parameters: `documentId` (path, required), `assignmentId` (path, required).

Request body (`application/json`; optional members may be omitted):
```json
{
  "expires_at": "2026-01-01T00:00:00Z"
}
```

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
{
  "resource": "assignment",
  "id": "id-placeholder",
  "sender_email": "person@example.com",
  "method": "virtual",
  "expires_at": null,
  "message": null,
  "signers": [{"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true,"verification_method": null,"notification_methods": ["Email"],"step": 1,"notified": true,"completed": true,"notification_history": [{"event": "example","status": "sent","error_code": null,"error_message": null,"sent_at": null,"failed_at": null}]}],
  "copy_receivers": [{"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true}],
  "items": [{"id": "id-placeholder","page": {"id": "id-placeholder","number": 1,"height": 1,"width": 1,"download_url": "https://example.com/resource"},"signer": {"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true},"field": {"resource": "field","id": "id-placeholder","name": "Example","type": "text","regex": null,"is_pre_defined": true,"is_active": true,"is_required": true,"is_standard": true,"is_read_only": true,"is_visible": true},"display_settings": {"left": 1,"top": 1,"width": 1,"height": 1,"fontFamily": "Arial","fontSize": 1,"backgroundColor": "#D5EBFF"},"value": null,"completed": true}],
  "summary": {"signer_count": 1,"completed_count": 1,"signers": [{"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true}]},
  "signing_urls": [{"signer_id": "signer-id-placeholder","url": "https://example.com/resource"}]
}
```

### `assignments.listWhatsappNotifications`

`GET /v1/documents/{documentId}/assignments/{assignmentId}/whatsapp-notifications`

Parameters: `documentId` (path, required), `assignmentId` (path, required).

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
[
  {
    "sent_at": 1,
    "header": "example",
    "body": "example",
    "buttons": [{"text": "example"}],
    "phone_number": "+15555550100",
    "signer_id": "signer-id-placeholder"
  }
]
```

### `authentication.login`

`POST /v1/login`

Parameters: none.

Request body (`application/json`; optional members may be omitted):
```json
{
  "email": "person@example.com",
  "password": "<redacted-value>"
}
```

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
{
  "access_token": "<redacted-value>",
  "user": {"id": "id-placeholder","name": "Example","email": "person@example.com","telephone": null,"government_id": null,"is_email_verified": true,"has_accepted_terms": true,"created_at": "2026-01-01T00:00:00Z","to_be_deleted_at": null},
  "accounts": [{"id": "id-placeholder","name": "Example","roles": ["example"],"is_delete_allowed": true,"created_at": "2026-01-01T00:00:00Z"}]
}
```

When two-factor authentication is enabled, `data` is `{"mfa_token": "<redacted-value>"}` and the SDK
throws `MfaRequiredException`.

### `authentication.requestPasswordReset`

`PUT /v1/authentication/request-password-reset`

Parameters: none.

Request body (`application/json`; optional members may be omitted):
```json
{
  "email": "person@example.com"
}
```

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
{
  "email": "person@example.com"
}
```

### `authentication.resetPassword`

`PUT /v1/authentication/reset-password`

Parameters: none.

Request body (`application/json`; optional members may be omitted):
```json
{
  "email": "person@example.com",
  "token": "<redacted-value>",
  "new_password": "<redacted-value>"
}
```

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
{
  "email": "person@example.com"
}
```

### `authentication.changePassword`

`PUT /v1/authentication/change-password`

Parameters: none.

Request body (`application/json`; optional members may be omitted):
```json
{
  "email": "person@example.com",
  "password": "<redacted-value>",
  "new_password": "<redacted-value>"
}
```

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
{
  "email": "person@example.com"
}
```

### `authentication.socialLogin`

`POST /v1/authentication/social-login`

Parameters: none.

Request body (`application/json`; optional members may be omitted):
```json
{
  "provider": "google",
  "token": "<redacted-value>",
  "has_accepted_terms": true
}
```

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
{
  "access_token": "<redacted-value>",
  "user": {"id": "id-placeholder","name": "Example","email": "person@example.com","telephone": null,"government_id": null,"is_email_verified": true,"has_accepted_terms": true,"created_at": "2026-01-01T00:00:00Z","to_be_deleted_at": null},
  "accounts": [{"id": "id-placeholder","name": "Example","roles": ["example"],"is_delete_allowed": true,"created_at": "2026-01-01T00:00:00Z"}]
}
```

### `authentication.linkSocialLogin`

`POST /v1/auth/link-social-login`

Parameters: none.

Request body (`application/json`; optional members may be omitted):
```json
{
  "provider": "google",
  "token": "<redacted-value>"
}
```

Response 200 body; optional members depend on document state and permissions:
```json
{
  "status": 200,
  "message": "example"
}
```

### `authentication.getApiKey`

`GET /v1/users/api-keys`

Parameters: none.

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
{
  "api_key": null
}
```

### `authentication.createApiKey`

`POST /v1/users/api-keys`

Parameters: none.

Request body (`application/json`; optional members may be omitted):
```json
{
  "password": "<redacted-value>"
}
```

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
{
  "api_key": null
}
```

### `authentication.deleteApiKey`

`DELETE /v1/users/api-keys`

Parameters: none.

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
[]
```

### `authentication.verifyMfa`

`POST /v1/authentication/mfa/verify` (no credential)

Parameters: none.

Request body (`application/json`):
```json
{
  "mfa_token": "<redacted-value>",
  "code": "123456"
}
```

Response 200 `data` payload (inside `{status,message,data}`):
```json
{
  "access_token": "<redacted-value>",
  "user": {"id": "id-placeholder","name": "Example","email": "person@example.com","telephone": null,"government_id": null,"is_email_verified": true,"has_accepted_terms": true,"created_at": "2026-01-01T00:00:00Z","to_be_deleted_at": null},
  "accounts": [{"id": "id-placeholder","name": "Example","roles": ["example"],"is_delete_allowed": true,"created_at": "2026-01-01T00:00:00Z"}]
}
```

An expired, used or over-tried challenge answers `401`.

### `authentication.listMfaMethods`

`GET /v1/users/self/mfa`

Parameters: none.

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`):
```json
{
  "methods": [
    {"id": "id-placeholder", "type": "totp", "label": "Phone", "confirmed_at": "2026-01-01T00:00:00Z", "last_used_at": "2026-01-01T00:00:00Z"}
  ],
  "recovery_codes_remaining": 10
}
```

### `authentication.startTotpEnrollment`

`POST /v1/users/self/mfa/totp`

Parameters: none.

Request body (`application/json`; `label` is optional, `{}` when omitted):
```json
{
  "label": "Phone"
}
```

Response 200 `data` payload (inside `{status,message,data}`); the secret is returned only once:
```json
{
  "id": "id-placeholder",
  "secret": "<redacted-value>",
  "provisioning_uri": "otpauth://totp/Assinafy:person%40example.com?secret=<redacted-value>&issuer=Assinafy"
}
```

### `authentication.confirmTotpEnrollment`

`PUT /v1/users/self/mfa/totp/confirm`

Parameters: none.

Request body (`application/json`; `password` and `reauth_code` only when replacing a confirmed method):
```json
{
  "id": "id-placeholder",
  "code": "123456",
  "password": "<redacted-value>",
  "reauth_code": "<redacted-value>"
}
```

Response 200 `data` payload (inside `{status,message,data}`); the codes are shown only once:
```json
{
  "recovery_codes": ["ABCD-EFGH-JKMN"]
}
```

### `authentication.regenerateRecoveryCodes`

`POST /v1/users/self/mfa/recovery-codes`

Parameters: none.

Request body (`application/json`; send `password` or `code`):
```json
{
  "password": "<redacted-value>",
  "code": "123456"
}
```

Response 200 `data` payload (inside `{status,message,data}`):
```json
{
  "recovery_codes": ["ABCD-EFGH-JKMN"]
}
```

### `authentication.removeMfaMethod`

`DELETE /v1/users/self/mfa/{customId}`

Parameters: `customId` (path, required; the `methodId` argument).

Request body (`application/json`; send `password` or `code`):
```json
{
  "password": "<redacted-value>",
  "code": "123456"
}
```

Response 200 `data` payload (inside `{status,message,data}`):
```json
{
  "is_mfa_enabled": false
}
```

### `documents.activities`

`GET /v1/documents/{documentId}/activities`

Parameters: `documentId` (path, required).

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
[
  {
    "id": 1,
    "event": "example",
    "message": "example",
    "payload": {},
    "origin": {"ip": "example","user-agent": "example"},
    "created_at": "2026-01-01T00:00:00Z"
  }
]
```

### `documents.list`

`GET /v1/accounts/{accountId}/documents`

Parameters: `accountId` (path, required), `status` (query, optional), `method` (query, optional), `search` (query, optional), `tags` (query, optional), `sort` (query, optional), `page` (query, optional), `per-page` (query, optional).

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
[
  {
    "resource": "document",
    "id": "id-placeholder",
    "account_id": "account-id-placeholder",
    "template_id": null,
    "name": "Example",
    "status": "example",
    "artifacts": {},
    "is_closed": true,
    "signing_url": "https://example.com/resource",
    "decline_reason": null,
    "declined_by": {"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true},
    "tags": [{"id": "id-placeholder","name": "Example"}],
    "assignment": {"resource": "assignment","id": "id-placeholder","sender_email": "person@example.com","method": "virtual","expires_at": null,"message": null,"signers": [{"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true,"verification_method": null,"notification_methods": ["Email"],"step": 1,"notified": true,"completed": true,"notification_history": [{"event": "example","status": "sent","error_code": null,"error_message": null,"sent_at": null,"failed_at": null}]}],"copy_receivers": [{"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true}],"items": [{"id": "id-placeholder","page": {"id": "id-placeholder","number": 1,"height": 1,"width": 1,"download_url": "https://example.com/resource"},"signer": {"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true},"field": {"resource": "field","id": "id-placeholder","name": "Example","type": "text","regex": null,"is_pre_defined": true,"is_active": true,"is_required": true,"is_standard": true,"is_read_only": true,"is_visible": true},"display_settings": {"left": 1,"top": 1,"width": 1,"height": 1,"fontFamily": "Arial","fontSize": 1,"backgroundColor": "#D5EBFF"},"value": null,"completed": true}],"summary": {"signer_count": 1,"completed_count": 1,"signers": [{"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true}]},"signing_urls": [{"signer_id": "signer-id-placeholder","url": "https://example.com/resource"}]},
    "pages": [{"id": "id-placeholder","number": 1,"height": 1,"width": 1,"download_url": "https://example.com/resource"}],
    "created_at": "2026-01-01T00:00:00Z",
    "updated_at": "2026-01-01T00:00:00Z"
  }
]
```

### `documents.upload`

`POST /v1/accounts/{accountId}/documents`

Parameters: `accountId` (path, required).

Request body (`multipart/form-data`; optional members may be omitted):
```json
{
  "file": "<binary bytes>"
}
```

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
{
  "resource": "document",
  "id": "id-placeholder",
  "account_id": "account-id-placeholder",
  "template_id": null,
  "name": "Example",
  "status": "example",
  "artifacts": {},
  "is_closed": true,
  "signing_url": "https://example.com/resource",
  "decline_reason": null,
  "declined_by": {"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true},
  "tags": [{"id": "id-placeholder","name": "Example"}],
  "assignment": {"resource": "assignment","id": "id-placeholder","sender_email": "person@example.com","method": "virtual","expires_at": null,"message": null,"signers": [{"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true,"verification_method": null,"notification_methods": ["Email"],"step": 1,"notified": true,"completed": true,"notification_history": [{"event": "example","status": "sent","error_code": null,"error_message": null,"sent_at": null,"failed_at": null}]}],"copy_receivers": [{"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true}],"items": [{"id": "id-placeholder","page": {"id": "id-placeholder","number": 1,"height": 1,"width": 1,"download_url": "https://example.com/resource"},"signer": {"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true},"field": {"resource": "field","id": "id-placeholder","name": "Example","type": "text","regex": null,"is_pre_defined": true,"is_active": true,"is_required": true,"is_standard": true,"is_read_only": true,"is_visible": true},"display_settings": {"left": 1,"top": 1,"width": 1,"height": 1,"fontFamily": "Arial","fontSize": 1,"backgroundColor": "#D5EBFF"},"value": null,"completed": true}],"summary": {"signer_count": 1,"completed_count": 1,"signers": [{"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true}]},"signing_urls": [{"signer_id": "signer-id-placeholder","url": "https://example.com/resource"}]},
  "pages": [{"id": "id-placeholder","number": 1,"height": 1,"width": 1,"download_url": "https://example.com/resource"}],
  "created_at": "2026-01-01T00:00:00Z",
  "updated_at": "2026-01-01T00:00:00Z"
}
```

### `documents.search`

`GET /v1/accounts/{accountId}/documents/search`

Parameters: `accountId` (path, required), `search` (query, optional), `status` (query, optional), `page` (query, optional), `per-page` (query, optional).

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
[
  {
    "resource": "document",
    "id": "id-placeholder",
    "account_id": "account-id-placeholder",
    "template_id": null,
    "name": "Example",
    "status": "example",
    "artifacts": {},
    "is_closed": true,
    "signing_url": "https://example.com/resource",
    "decline_reason": null,
    "declined_by": {"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true},
    "tags": [{"id": "id-placeholder","name": "Example"}],
    "assignment": {"resource": "assignment","id": "id-placeholder","sender_email": "person@example.com","method": "virtual","expires_at": null,"message": null,"signers": [{"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true,"verification_method": null,"notification_methods": ["Email"],"step": 1,"notified": true,"completed": true,"notification_history": [{"event": "example","status": "sent","error_code": null,"error_message": null,"sent_at": null,"failed_at": null}]}],"copy_receivers": [{"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true}],"items": [{"id": "id-placeholder","page": {"id": "id-placeholder","number": 1,"height": 1,"width": 1,"download_url": "https://example.com/resource"},"signer": {"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true},"field": {"resource": "field","id": "id-placeholder","name": "Example","type": "text","regex": null,"is_pre_defined": true,"is_active": true,"is_required": true,"is_standard": true,"is_read_only": true,"is_visible": true},"display_settings": {"left": 1,"top": 1,"width": 1,"height": 1,"fontFamily": "Arial","fontSize": 1,"backgroundColor": "#D5EBFF"},"value": null,"completed": true}],"summary": {"signer_count": 1,"completed_count": 1,"signers": [{"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true}]},"signing_urls": [{"signer_id": "signer-id-placeholder","url": "https://example.com/resource"}]},
    "pages": [{"id": "id-placeholder","number": 1,"height": 1,"width": 1,"download_url": "https://example.com/resource"}],
    "created_at": "2026-01-01T00:00:00Z",
    "updated_at": "2026-01-01T00:00:00Z"
  }
]
```

### `documents.getStatuses`

`GET /v1/documents/statuses`

Parameters: none.

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
[
  {
    "code": "example",
    "deletable": true
  }
]
```

### `documents.details`

`GET /v1/documents/{documentId}`

Parameters: `documentId` (path, required).

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
{
  "resource": "document",
  "id": "id-placeholder",
  "account_id": "account-id-placeholder",
  "template_id": null,
  "name": "Example",
  "status": "example",
  "artifacts": {},
  "is_closed": true,
  "signing_url": "https://example.com/resource",
  "decline_reason": null,
  "declined_by": {"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true},
  "tags": [{"id": "id-placeholder","name": "Example"}],
  "assignment": {"resource": "assignment","id": "id-placeholder","sender_email": "person@example.com","method": "virtual","expires_at": null,"message": null,"signers": [{"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true,"verification_method": null,"notification_methods": ["Email"],"step": 1,"notified": true,"completed": true,"notification_history": [{"event": "example","status": "sent","error_code": null,"error_message": null,"sent_at": null,"failed_at": null}]}],"copy_receivers": [{"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true}],"items": [{"id": "id-placeholder","page": {"id": "id-placeholder","number": 1,"height": 1,"width": 1,"download_url": "https://example.com/resource"},"signer": {"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true},"field": {"resource": "field","id": "id-placeholder","name": "Example","type": "text","regex": null,"is_pre_defined": true,"is_active": true,"is_required": true,"is_standard": true,"is_read_only": true,"is_visible": true},"display_settings": {"left": 1,"top": 1,"width": 1,"height": 1,"fontFamily": "Arial","fontSize": 1,"backgroundColor": "#D5EBFF"},"value": null,"completed": true}],"summary": {"signer_count": 1,"completed_count": 1,"signers": [{"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true}]},"signing_urls": [{"signer_id": "signer-id-placeholder","url": "https://example.com/resource"}]},
  "pages": [{"id": "id-placeholder","number": 1,"height": 1,"width": 1,"download_url": "https://example.com/resource"}],
  "created_at": "2026-01-01T00:00:00Z",
  "updated_at": "2026-01-01T00:00:00Z"
}
```

### `documents.delete`

`DELETE /v1/documents/{documentId}`

Parameters: `documentId` (path, required).

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
[]
```

### `documents.rename`

`PATCH /v1/documents/{documentId}`

Parameters: `documentId` (path, required).

Request body (`application/json`; optional members may be omitted):
```json
{
  "name": "Example"
}
```

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
{
  "resource": "document",
  "id": "id-placeholder",
  "account_id": "account-id-placeholder",
  "template_id": null,
  "name": "Example",
  "status": "example",
  "artifacts": {},
  "is_closed": true,
  "signing_url": "https://example.com/resource",
  "decline_reason": null,
  "declined_by": {"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true},
  "tags": [{"id": "id-placeholder","name": "Example"}],
  "assignment": {"resource": "assignment","id": "id-placeholder","sender_email": "person@example.com","method": "virtual","expires_at": null,"message": null,"signers": [{"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true,"verification_method": null,"notification_methods": ["Email"],"step": 1,"notified": true,"completed": true,"notification_history": [{"event": "example","status": "sent","error_code": null,"error_message": null,"sent_at": null,"failed_at": null}]}],"copy_receivers": [{"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true}],"items": [{"id": "id-placeholder","page": {"id": "id-placeholder","number": 1,"height": 1,"width": 1,"download_url": "https://example.com/resource"},"signer": {"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true},"field": {"resource": "field","id": "id-placeholder","name": "Example","type": "text","regex": null,"is_pre_defined": true,"is_active": true,"is_required": true,"is_standard": true,"is_read_only": true,"is_visible": true},"display_settings": {"left": 1,"top": 1,"width": 1,"height": 1,"fontFamily": "Arial","fontSize": 1,"backgroundColor": "#D5EBFF"},"value": null,"completed": true}],"summary": {"signer_count": 1,"completed_count": 1,"signers": [{"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true}]},"signing_urls": [{"signer_id": "signer-id-placeholder","url": "https://example.com/resource"}]},
  "pages": [{"id": "id-placeholder","number": 1,"height": 1,"width": 1,"download_url": "https://example.com/resource"}],
  "created_at": "2026-01-01T00:00:00Z",
  "updated_at": "2026-01-01T00:00:00Z"
}
```

### `documents.download`

`GET /v1/documents/{documentId}/download/{artifactName}`

Parameters: `documentId` (path, required), `artifactName` (path, required).

Request body: none.

Response 200: raw binary bytes (`application/pdf`), without a JSON envelope.

### `documents.verify`

`GET /v1/documents/{documentSignatureHash}/verify`

Parameters: `documentSignatureHash` (path, required).

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
{
  "hash": "example",
  "id": null,
  "agreement_code": null,
  "status": null,
  "page_count": null,
  "signer_count": null,
  "completed_count": 1,
  "completed_at": null,
  "verified_at": "2026-01-01T00:00:00Z",
  "is_valid": true,
  "message": "example"
}
```

### `documents.listTags`

`GET /v1/accounts/{accountId}/documents/{documentId}/tags`

Parameters: `accountId` (path, required), `documentId` (path, required).

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
[
  {
    "resource": "tag",
    "id": "id-placeholder",
    "name": "Example",
    "color": null,
    "created_at": "2026-01-01T00:00:00Z",
    "updated_at": "2026-01-01T00:00:00Z"
  }
]
```

### `documents.replaceTags`

`PUT /v1/accounts/{accountId}/documents/{documentId}/tags`

Parameters: `accountId` (path, required), `documentId` (path, required).

Request body (`application/json`; optional members may be omitted):
```json
{
  "tags": ["example"]
}
```

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
[
  {
    "resource": "tag",
    "id": "id-placeholder",
    "name": "Example",
    "color": null,
    "created_at": "2026-01-01T00:00:00Z",
    "updated_at": "2026-01-01T00:00:00Z"
  }
]
```

### `documents.addTags`

`POST /v1/accounts/{accountId}/documents/{documentId}/tags`

Parameters: `accountId` (path, required), `documentId` (path, required).

Request body (`application/json`; optional members may be omitted):
```json
{
  "tags": ["example"]
}
```

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
[
  {
    "resource": "tag",
    "id": "id-placeholder",
    "name": "Example",
    "color": null,
    "created_at": "2026-01-01T00:00:00Z",
    "updated_at": "2026-01-01T00:00:00Z"
  }
]
```

### `documents.detachTag`

`DELETE /v1/accounts/{accountId}/documents/{documentId}/tags/{tagId}`

Parameters: `accountId` (path, required), `documentId` (path, required), `tagId` (path, required).

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
{
  "detached": true
}
```

### `documents.thumbnail`

`GET /v1/documents/{documentId}/thumbnail`

Parameters: `documentId` (path, required).

Request body: none.

Response 200: raw binary bytes (`image/*`), without a JSON envelope.

### `documents.downloadPage`

`GET /v1/documents/{documentId}/pages/{pageId}/download`

Parameters: `documentId` (path, required), `pageId` (path, required).

Request body: none.

Response 200: raw binary bytes (`image/*`), without a JSON envelope.

### `documents.createFromTemplate`

`POST /v1/accounts/{accountId}/templates/{templateId}/documents`

Parameters: `accountId` (path, required), `templateId` (path, required).

Request body (`application/json`; optional members may be omitted):
```json
{
  "signers": [{"role_id": "role-id-placeholder","id": "id-placeholder","verification_method": "Email","notification_methods": ["Email"],"step": 1}],
  "editor_fields": [{"field_id": "field-id-placeholder","value": "example"}],
  "name": "Example",
  "message": "example",
  "expires_at": "2026-01-01T00:00:00Z",
  "tags": ["example"]
}
```

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
{
  "resource": "document",
  "id": "id-placeholder",
  "account_id": "account-id-placeholder",
  "template_id": null,
  "name": "Example",
  "status": "example",
  "artifacts": {},
  "is_closed": true,
  "signing_url": "https://example.com/resource",
  "decline_reason": null,
  "declined_by": {"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true},
  "tags": [{"id": "id-placeholder","name": "Example"}],
  "assignment": {"resource": "assignment","id": "id-placeholder","sender_email": "person@example.com","method": "virtual","expires_at": null,"message": null,"signers": [{"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true,"verification_method": null,"notification_methods": ["Email"],"step": 1,"notified": true,"completed": true,"notification_history": [{"event": "example","status": "sent","error_code": null,"error_message": null,"sent_at": null,"failed_at": null}]}],"copy_receivers": [{"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true}],"items": [{"id": "id-placeholder","page": {"id": "id-placeholder","number": 1,"height": 1,"width": 1,"download_url": "https://example.com/resource"},"signer": {"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true},"field": {"resource": "field","id": "id-placeholder","name": "Example","type": "text","regex": null,"is_pre_defined": true,"is_active": true,"is_required": true,"is_standard": true,"is_read_only": true,"is_visible": true},"display_settings": {"left": 1,"top": 1,"width": 1,"height": 1,"fontFamily": "Arial","fontSize": 1,"backgroundColor": "#D5EBFF"},"value": null,"completed": true}],"summary": {"signer_count": 1,"completed_count": 1,"signers": [{"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true}]},"signing_urls": [{"signer_id": "signer-id-placeholder","url": "https://example.com/resource"}]},
  "pages": [{"id": "id-placeholder","number": 1,"height": 1,"width": 1,"download_url": "https://example.com/resource"}],
  "created_at": "2026-01-01T00:00:00Z",
  "updated_at": "2026-01-01T00:00:00Z"
}
```

### `documents.estimateCostFromTemplate`

`POST /v1/accounts/{accountId}/templates/{templateId}/documents/estimate-cost`

Parameters: `accountId` (path, required), `templateId` (path, required).

Request body (`application/json`; optional members may be omitted):
```json
{
  "signers": [{"role_id": "role-id-placeholder","verification_method": "Email","notification_methods": ["Email"]}]
}
```

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
{
  "documents": 1,
  "credits": 1,
  "needs_extra_document": true,
  "extra_document_cost": 1,
  "total_credits": 1,
  "breakdown": [{"code": "example","name": "Example","cost": 1,"quantity": 1,"unit_cost": 1}],
  "document_balance": 1,
  "credit_balance": 1,
  "has_sufficient_resources": true,
  "blocking_reason": "PendingPayment",
  "message": null
}
```

### `fields.list`

`GET /v1/accounts/{accountId}/fields`

Parameters: `accountId` (path, required), `include_inactive` (query, optional), `include_standard` (query, optional).

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
[
  {
    "resource": "field",
    "id": "id-placeholder",
    "name": "Example",
    "type": "text",
    "regex": null,
    "is_pre_defined": true,
    "is_active": true,
    "is_required": true,
    "is_standard": true,
    "is_read_only": true,
    "is_visible": true
  }
]
```

### `fields.create`

`POST /v1/accounts/{accountId}/fields`

Parameters: `accountId` (path, required).

Request body (`application/json`; optional members may be omitted):
```json
{
  "name": "Example",
  "type": "text",
  "regex": null,
  "is_required": true
}
```

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
{
  "resource": "field",
  "id": "id-placeholder",
  "name": "Example",
  "type": "text",
  "regex": null,
  "is_pre_defined": true,
  "is_active": true,
  "is_required": true,
  "is_standard": true,
  "is_read_only": true,
  "is_visible": true
}
```

### `fields.get`

`GET /v1/accounts/{accountId}/fields/{fieldId}`

Parameters: `accountId` (path, required), `fieldId` (path, required).

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
{
  "resource": "field",
  "id": "id-placeholder",
  "name": "Example",
  "type": "text",
  "regex": null,
  "is_pre_defined": true,
  "is_active": true,
  "is_required": true,
  "is_standard": true,
  "is_read_only": true,
  "is_visible": true
}
```

### `fields.update`

`PUT /v1/accounts/{accountId}/fields/{fieldId}`

Parameters: `accountId` (path, required), `fieldId` (path, required).

Request body (`application/json`; optional members may be omitted):
```json
{
  "name": "Example",
  "regex": null,
  "is_active": true
}
```

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
{
  "resource": "field",
  "id": "id-placeholder",
  "name": "Example",
  "type": "text",
  "regex": null,
  "is_pre_defined": true,
  "is_active": true,
  "is_required": true,
  "is_standard": true,
  "is_read_only": true,
  "is_visible": true
}
```

### `fields.delete`

`DELETE /v1/accounts/{accountId}/fields/{fieldId}`

Parameters: `accountId` (path, required), `fieldId` (path, required).

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
[]
```

### `fields.validate`

`POST /v1/accounts/{accountId}/fields/{fieldId}/validate`

Parameters: `accountId` (path, required), `fieldId` (path, required).

Request body (`application/json`; optional members may be omitted):
```json
{
  "value": null
}
```

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
{
  "type": "text",
  "success": true,
  "error_message": "example"
}
```

### `fields.validateMultiple`

`POST /v1/accounts/{accountId}/fields/validate-multiple`

Parameters: `accountId` (path, required).

Request body (`application/json`; optional members may be omitted):
```json
[
  {
    "field_id": "field-id-placeholder",
    "value": null
  }
]
```

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
[
  {
    "field_id": "field-id-placeholder",
    "type": "text",
    "success": true,
    "error_message": "example"
  }
]
```

### `fields.listTypes`

`GET /v1/field-types`

Parameters: none.

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
[
  {
    "type": "text",
    "name": "Example"
  }
]
```

### `signers.list`

`GET /v1/accounts/{accountId}/signers`

Parameters: `accountId` (path, required), `search` (query, optional), `page` (query, optional), `per-page` (query, optional).

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
[
  {
    "resource": "signer",
    "id": "id-placeholder",
    "full_name": "Example",
    "email": null,
    "whatsapp_phone_number": null,
    "has_accepted_terms": true
  }
]
```

### `signers.create`

`POST /v1/accounts/{accountId}/signers`

Parameters: `accountId` (path, required).

Request body (`application/json`; optional members may be omitted):
```json
{
  "full_name": "Example",
  "email": "person@example.com",
  "whatsapp_phone_number": "+15555550100",
  "government_id": "390.533.447-05"
}
```

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
{
  "resource": "signer",
  "id": "id-placeholder",
  "full_name": "Example",
  "email": null,
  "whatsapp_phone_number": null,
  "has_accepted_terms": true
}
```

### `signers.get`

`GET /v1/accounts/{accountId}/signers/{signerId}`

Parameters: `accountId` (path, required), `signerId` (path, required).

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
{
  "resource": "signer",
  "id": "id-placeholder",
  "full_name": "Example",
  "email": null,
  "whatsapp_phone_number": null,
  "has_accepted_terms": true
}
```

### `signers.update`

`PUT /v1/accounts/{accountId}/signers/{signerId}`

Parameters: `accountId` (path, required), `signerId` (path, required).

Request body (`application/json`; optional members may be omitted):
```json
{
  "full_name": "Example",
  "email": "person@example.com",
  "whatsapp_phone_number": "+15555550100",
  "government_id": "government-id-placeholder"
}
```

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
{
  "resource": "signer",
  "id": "id-placeholder",
  "full_name": "Example",
  "email": null,
  "whatsapp_phone_number": null,
  "has_accepted_terms": true
}
```

### `signers.delete`

`DELETE /v1/accounts/{accountId}/signers/{signerId}`

Parameters: `accountId` (path, required), `signerId` (path, required).

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
[]
```

### `signerDocuments.self`

`GET /v1/signers/self`

Parameters: none.

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
{
  "resource": "signer",
  "id": "id-placeholder",
  "full_name": "Example",
  "email": null,
  "whatsapp_phone_number": null,
  "government_id": null,
  "has_accepted_terms": true,
  "has_signature": true,
  "has_initial": true,
  "is_signature_reusable": true
}
```

### `signerDocuments.getCurrent`

`GET /v1/signers/{signerId}/document`

Parameters: `signerId` (path, required).

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
{
  "resource": "document",
  "id": "id-placeholder",
  "account_id": "account-id-placeholder",
  "template_id": null,
  "name": "Example",
  "status": "example",
  "artifacts": {},
  "is_closed": true,
  "signing_url": "https://example.com/resource",
  "decline_reason": null,
  "declined_by": {"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true},
  "tags": [{"id": "id-placeholder","name": "Example"}],
  "assignment": {"resource": "assignment","id": "id-placeholder","sender_email": "person@example.com","method": "virtual","expires_at": null,"message": null,"signers": [{"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true,"verification_method": null,"notification_methods": ["Email"],"step": 1,"notified": true,"completed": true,"notification_history": [{"event": "example","status": "sent","error_code": null,"error_message": null,"sent_at": null,"failed_at": null}]}],"copy_receivers": [{"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true}],"items": [{"id": "id-placeholder","page": {"id": "id-placeholder","number": 1,"height": 1,"width": 1,"download_url": "https://example.com/resource"},"signer": {"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true},"field": {"resource": "field","id": "id-placeholder","name": "Example","type": "text","regex": null,"is_pre_defined": true,"is_active": true,"is_required": true,"is_standard": true,"is_read_only": true,"is_visible": true},"display_settings": {"left": 1,"top": 1,"width": 1,"height": 1,"fontFamily": "Arial","fontSize": 1,"backgroundColor": "#D5EBFF"},"value": null,"completed": true}],"summary": {"signer_count": 1,"completed_count": 1,"signers": [{"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true}]},"signing_urls": [{"signer_id": "signer-id-placeholder","url": "https://example.com/resource"}]},
  "pages": [{"id": "id-placeholder","number": 1,"height": 1,"width": 1,"download_url": "https://example.com/resource"}],
  "created_at": "2026-01-01T00:00:00Z",
  "updated_at": "2026-01-01T00:00:00Z"
}
```

### `signerDocuments.getAssignment`

`GET /v1/sign`

Parameters: `has_accepted_terms` (query, optional).

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
{
  "resource": "document",
  "id": "id-placeholder",
  "account_id": "account-id-placeholder",
  "template_id": null,
  "name": "Example",
  "status": "example",
  "artifacts": {},
  "is_closed": true,
  "signing_url": "https://example.com/resource",
  "decline_reason": null,
  "declined_by": {"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true},
  "tags": [{"id": "id-placeholder","name": "Example"}],
  "assignment": {"resource": "assignment","id": "id-placeholder","sender_email": "person@example.com","method": "virtual","expires_at": null,"message": null,"signers": [{"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true,"verification_method": null,"notification_methods": ["Email"],"step": 1,"notified": true,"completed": true,"notification_history": [{"event": "example","status": "sent","error_code": null,"error_message": null,"sent_at": null,"failed_at": null}]}],"copy_receivers": [{"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true}],"items": [{"id": "id-placeholder","page": {"id": "id-placeholder","number": 1,"height": 1,"width": 1,"download_url": "https://example.com/resource"},"signer": {"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true},"field": {"resource": "field","id": "id-placeholder","name": "Example","type": "text","regex": null,"is_pre_defined": true,"is_active": true,"is_required": true,"is_standard": true,"is_read_only": true,"is_visible": true},"display_settings": {"left": 1,"top": 1,"width": 1,"height": 1,"fontFamily": "Arial","fontSize": 1,"backgroundColor": "#D5EBFF"},"value": null,"completed": true}],"summary": {"signer_count": 1,"completed_count": 1,"signers": [{"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true}]},"signing_urls": [{"signer_id": "signer-id-placeholder","url": "https://example.com/resource"}]},
  "pages": [{"id": "id-placeholder","number": 1,"height": 1,"width": 1,"download_url": "https://example.com/resource"}],
  "created_at": "2026-01-01T00:00:00Z",
  "updated_at": "2026-01-01T00:00:00Z"
}
```

### `signerDocuments.sign`

`POST /v1/documents/{documentId}/assignments/{assignmentId}`

Parameters: `documentId` (path, required), `assignmentId` (path, required).

Request body (`application/json`; optional members may be omitted):
```json
[
  {
    "itemId": "itemId-placeholder",
    "fieldId": "fieldId-placeholder",
    "pageId": "pageId-placeholder",
    "value": "example"
  }
]
```

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
{

}
```

### `signerDocuments.decline`

`PUT /v1/documents/{documentId}/assignments/{assignmentId}/reject`

Parameters: `documentId` (path, required), `assignmentId` (path, required).

Request body (`application/json`; optional members may be omitted):
```json
{
  "decline_reason": "example"
}
```

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
[]
```

### `signerDocuments.signMultiple`

`PUT /v1/signers/documents/sign-multiple`

Parameters: none.

Request body (`application/json`; optional members may be omitted):
```json
{
  "document_ids": ["example"]
}
```

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
[]
```

### `signerDocuments.declineMultiple`

`PUT /v1/signers/documents/decline-multiple`

Parameters: none.

Request body (`application/json`; optional members may be omitted):
```json
{
  "document_ids": ["example"],
  "decline_reason": "example"
}
```

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
[]
```

### `signerDocuments.verifyEmail`

`POST /v1/verify`

Parameters: none.

Request body (`application/json`; optional members may be omitted):
```json
{
  "verification-code": "example"
}
```

Response 200 body; optional members depend on document state and permissions:
```json
{
  "status": 200,
  "message": "example"
}
```

### `signerDocuments.confirmData`

`PUT /v1/documents/{documentId}/signers/confirm-data`

Parameters: `documentId` (path, required).

Request body (`application/json`; optional members may be omitted):
```json
{
  "full_name": "Example",
  "email": "person@example.com",
  "government_id": "government-id-placeholder"
}
```

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
{
  "resource": "signer",
  "id": "id-placeholder",
  "full_name": "Example",
  "email": null,
  "whatsapp_phone_number": null,
  "has_accepted_terms": true
}
```

### `signerDocuments.acceptTerms`

`PUT /v1/signers/accept-terms`

Parameters: none.

Request body: none.

Response 200 body; optional members depend on document state and permissions:
```json
{
  "status": 200,
  "message": "example"
}
```

### `signerDocuments.uploadSignature`

`POST /v1/signature`

Parameters: `type` (query, optional), `reuse` (query, optional).

Request body (`image/png`; optional members may be omitted):
```json
"<binary bytes>"
```

Response 200 body; optional members depend on document state and permissions:
```json
{
  "status": 200,
  "message": "example"
}
```

### `signerDocuments.downloadSignature`

`GET /v1/signature/{signatureType}`

Parameters: `signatureType` (path, required).

Request body: none.

Response 200: raw binary bytes (`image/*`), without a JSON envelope.

### `signerDocuments.list`

`GET /v1/signers/{signerId}/documents`

Parameters: `signerId` (path, required), `page` (query, optional), `per-page` (query, optional).

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
[
  {
    "resource": "document",
    "id": "id-placeholder",
    "account_id": "account-id-placeholder",
    "template_id": null,
    "name": "Example",
    "status": "example",
    "artifacts": {},
    "is_closed": true,
    "signing_url": "https://example.com/resource",
    "decline_reason": null,
    "declined_by": {"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true},
    "tags": [{"id": "id-placeholder","name": "Example"}],
    "assignment": {"resource": "assignment","id": "id-placeholder","sender_email": "person@example.com","method": "virtual","expires_at": null,"message": null,"signers": [{"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true,"verification_method": null,"notification_methods": ["Email"],"step": 1,"notified": true,"completed": true,"notification_history": [{"event": "example","status": "sent","error_code": null,"error_message": null,"sent_at": null,"failed_at": null}]}],"copy_receivers": [{"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true}],"items": [{"id": "id-placeholder","page": {"id": "id-placeholder","number": 1,"height": 1,"width": 1,"download_url": "https://example.com/resource"},"signer": {"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true},"field": {"resource": "field","id": "id-placeholder","name": "Example","type": "text","regex": null,"is_pre_defined": true,"is_active": true,"is_required": true,"is_standard": true,"is_read_only": true,"is_visible": true},"display_settings": {"left": 1,"top": 1,"width": 1,"height": 1,"fontFamily": "Arial","fontSize": 1,"backgroundColor": "#D5EBFF"},"value": null,"completed": true}],"summary": {"signer_count": 1,"completed_count": 1,"signers": [{"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true}]},"signing_urls": [{"signer_id": "signer-id-placeholder","url": "https://example.com/resource"}]},
    "pages": [{"id": "id-placeholder","number": 1,"height": 1,"width": 1,"download_url": "https://example.com/resource"}],
    "created_at": "2026-01-01T00:00:00Z",
    "updated_at": "2026-01-01T00:00:00Z"
  }
]
```

### `signerDocuments.search`

`GET /v1/signers/{signerId}/documents/search`

Parameters: `signerId` (path, required), `search` (query, optional).

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
[
  {
    "resource": "document",
    "id": "id-placeholder",
    "account_id": "account-id-placeholder",
    "template_id": null,
    "name": "Example",
    "status": "example",
    "artifacts": {},
    "is_closed": true,
    "signing_url": "https://example.com/resource",
    "decline_reason": null,
    "declined_by": {"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true},
    "tags": [{"id": "id-placeholder","name": "Example"}],
    "assignment": {"resource": "assignment","id": "id-placeholder","sender_email": "person@example.com","method": "virtual","expires_at": null,"message": null,"signers": [{"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true,"verification_method": null,"notification_methods": ["Email"],"step": 1,"notified": true,"completed": true,"notification_history": [{"event": "example","status": "sent","error_code": null,"error_message": null,"sent_at": null,"failed_at": null}]}],"copy_receivers": [{"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true}],"items": [{"id": "id-placeholder","page": {"id": "id-placeholder","number": 1,"height": 1,"width": 1,"download_url": "https://example.com/resource"},"signer": {"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true},"field": {"resource": "field","id": "id-placeholder","name": "Example","type": "text","regex": null,"is_pre_defined": true,"is_active": true,"is_required": true,"is_standard": true,"is_read_only": true,"is_visible": true},"display_settings": {"left": 1,"top": 1,"width": 1,"height": 1,"fontFamily": "Arial","fontSize": 1,"backgroundColor": "#D5EBFF"},"value": null,"completed": true}],"summary": {"signer_count": 1,"completed_count": 1,"signers": [{"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true}]},"signing_urls": [{"signer_id": "signer-id-placeholder","url": "https://example.com/resource"}]},
    "pages": [{"id": "id-placeholder","number": 1,"height": 1,"width": 1,"download_url": "https://example.com/resource"}],
    "created_at": "2026-01-01T00:00:00Z",
    "updated_at": "2026-01-01T00:00:00Z"
  }
]
```

### `signerDocuments.download`

`GET /v1/signers/{signerId}/documents/{documentId}/download/{artifactName}`

Parameters: `signerId` (path, required), `documentId` (path, required), `artifactName` (path, required).

Request body: none.

Response 200: raw binary bytes (`application/pdf`), without a JSON envelope.

### `documents.getPublic`

`GET /v1/public/documents/{documentId}`

Parameters: `documentId` (path, required).

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
{
  "resource": "document",
  "id": "id-placeholder",
  "account_id": "account-id-placeholder",
  "template_id": null,
  "name": "Example",
  "status": "example",
  "artifacts": {},
  "is_closed": true,
  "signing_url": "https://example.com/resource",
  "decline_reason": null,
  "declined_by": {"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true},
  "tags": [{"id": "id-placeholder","name": "Example"}],
  "assignment": {"resource": "assignment","id": "id-placeholder","sender_email": "person@example.com","method": "virtual","expires_at": null,"message": null,"signers": [{"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true,"verification_method": null,"notification_methods": ["Email"],"step": 1,"notified": true,"completed": true,"notification_history": [{"event": "example","status": "sent","error_code": null,"error_message": null,"sent_at": null,"failed_at": null}]}],"copy_receivers": [{"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true}],"items": [{"id": "id-placeholder","page": {"id": "id-placeholder","number": 1,"height": 1,"width": 1,"download_url": "https://example.com/resource"},"signer": {"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true},"field": {"resource": "field","id": "id-placeholder","name": "Example","type": "text","regex": null,"is_pre_defined": true,"is_active": true,"is_required": true,"is_standard": true,"is_read_only": true,"is_visible": true},"display_settings": {"left": 1,"top": 1,"width": 1,"height": 1,"fontFamily": "Arial","fontSize": 1,"backgroundColor": "#D5EBFF"},"value": null,"completed": true}],"summary": {"signer_count": 1,"completed_count": 1,"signers": [{"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true}]},"signing_urls": [{"signer_id": "signer-id-placeholder","url": "https://example.com/resource"}]},
  "pages": [{"id": "id-placeholder","number": 1,"height": 1,"width": 1,"download_url": "https://example.com/resource"}],
  "created_at": "2026-01-01T00:00:00Z",
  "updated_at": "2026-01-01T00:00:00Z"
}
```

### `documents.sendToken`

`PUT /v1/public/documents/{documentId}/send-token`

Parameters: `documentId` (path, required).

Request body (`application/json`):
```json
{
  "recipient": "person@example.com",
  "channel": "email"
}
```

Response 200 body; optional members depend on document state and permissions:
```json
{
  "status": 200,
  "message": "example"
}
```

### `tags.list`

`GET /v1/accounts/{accountId}/tags`

Parameters: `accountId` (path, required), `search` (query, optional).

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
[
  {
    "resource": "tag",
    "id": "id-placeholder",
    "name": "Example",
    "color": null,
    "created_at": "2026-01-01T00:00:00Z",
    "updated_at": "2026-01-01T00:00:00Z"
  }
]
```

### `tags.create`

`POST /v1/accounts/{accountId}/tags`

Parameters: `accountId` (path, required).

Request body (`application/json`; optional members may be omitted):
```json
{
  "name": "Example",
  "color": null
}
```

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
{
  "resource": "tag",
  "id": "id-placeholder",
  "name": "Example",
  "color": null,
  "created_at": "2026-01-01T00:00:00Z",
  "updated_at": "2026-01-01T00:00:00Z"
}
```

### `tags.update`

`PUT /v1/accounts/{accountId}/tags/{tagId}`

Parameters: `accountId` (path, required), `tagId` (path, required).

Request body (`application/json`; optional members may be omitted):
```json
{
  "name": "Example",
  "color": null
}
```

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
{
  "resource": "tag",
  "id": "id-placeholder",
  "name": "Example",
  "color": null,
  "created_at": "2026-01-01T00:00:00Z",
  "updated_at": "2026-01-01T00:00:00Z"
}
```

### `tags.delete`

`DELETE /v1/accounts/{accountId}/tags/{tagId}`

Parameters: `accountId` (path, required), `tagId` (path, required), `force` (query, optional).

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
{
  "deleted": true
}
```

### `templates.list`

`GET /v1/accounts/{accountId}/templates`

Parameters: `accountId` (path, required), `search` (query, optional), `page` (query, optional), `per-page` (query, optional).

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
[
  {
    "resource": "template",
    "id": "id-placeholder",
    "name": "Example",
    "document_name": null,
    "message": null,
    "status": "example",
    "pages": [{"id": "id-placeholder","number": 1,"height": 1,"width": 1,"download_url": "https://example.com/resource","fields": [{"id": "id-placeholder","field_id": "field-id-placeholder","role_id": "role-id-placeholder","label": "example","display_settings": null,"created_at": "2026-01-01T00:00:00Z","updated_at": "2026-01-01T00:00:00Z"}]}],
    "roles": [{"id": "id-placeholder","name": "Example","assignment_type": "example","created_at": "2026-01-01T00:00:00Z","updated_at": "2026-01-01T00:00:00Z"}],
    "tags": [{"id": "id-placeholder","name": "Example"}],
    "default_document_tags": [{"id": "id-placeholder","name": "Example"}],
    "created_at": "2026-01-01T00:00:00Z",
    "updated_at": "2026-01-01T00:00:00Z"
  }
]
```

### `users.getCurrent`

`GET /v1/users/self`

Parameters: none.

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
{
  "id": "id-placeholder",
  "name": "Example",
  "email": "person@example.com",
  "telephone": null,
  "government_id": null,
  "is_email_verified": true,
  "has_accepted_terms": true,
  "created_at": "2026-01-01T00:00:00Z",
  "to_be_deleted_at": null
}
```

### `users.getStats`

`GET /v1/users/self/stats`

Parameters: `granularity` (query, optional), `month` (query, optional).

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
[
  {
    "period": "example",
    "documents_uploaded": 1,
    "documents_sent": 1,
    "signature_requests": 1,
    "signature_requests_notification_email": 1,
    "signature_requests_notification_whatsapp": 1,
    "signature_requests_notification_bypass": 1,
    "signature_requests_verification_email": 1,
    "signature_requests_verification_whatsapp": 1,
    "signature_requests_verification_bypass": 1,
    "signature_requests_verification_digital_certificate": 1,
    "signature_requests_viewed": 1,
    "signature_requests_completed": 1,
    "documents_certified": 1
  }
]
```

### `users.getNotificationPreferences`

`GET /v1/users/self/notification-preferences`

Parameters: none.

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
{
  "DocumentCompleted": true,
  "SignerDeclined": true,
  "DocumentCancelled": true,
  "DocumentAboutToExpire": true,
  "DocumentExpired": true,
  "DocumentExpirationReset": true,
  "DocumentProcessingFailed": true,
  "TemplateProcessingFailed": true,
  "SignerWhatsappFailed": true
}
```

### `users.updateNotificationPreferences`

`PUT /v1/users/self/notification-preferences`

Parameters: none.

Request body (`application/json`; optional members may be omitted):
```json
{
  "DocumentCompleted": true,
  "SignerDeclined": true,
  "DocumentCancelled": true,
  "DocumentAboutToExpire": true,
  "DocumentExpired": true,
  "DocumentExpirationReset": true,
  "DocumentProcessingFailed": true,
  "TemplateProcessingFailed": true,
  "SignerWhatsappFailed": true
}
```

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
{
  "DocumentCompleted": true,
  "SignerDeclined": true,
  "DocumentCancelled": true,
  "DocumentAboutToExpire": true,
  "DocumentExpired": true,
  "DocumentExpirationReset": true,
  "DocumentProcessingFailed": true,
  "TemplateProcessingFailed": true,
  "SignerWhatsappFailed": true
}
```

### `webhooks.get`

`GET /v1/accounts/{accountId}/webhooks/subscriptions`

Parameters: `accountId` (path, required).

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
{
  "events": ["example"],
  "is_active": true,
  "url": null,
  "email": null,
  "updated_at": null
}
```

### `webhooks.register`

`PUT /v1/accounts/{accountId}/webhooks/subscriptions`

Parameters: `accountId` (path, required).

Request body (`application/json`; optional members may be omitted):
```json
{
  "events": ["example"],
  "is_active": true,
  "url": "https://example.com/resource",
  "email": "person@example.com"
}
```

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
{
  "events": ["example"],
  "is_active": true,
  "url": null,
  "email": null,
  "updated_at": null
}
```

### `webhooks.inactivate`

`PUT /v1/accounts/{accountId}/webhooks/inactivate`

Parameters: `accountId` (path, required).

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
{
  "events": ["example"],
  "is_active": true,
  "url": null,
  "email": null,
  "updated_at": null
}
```

### `webhooks.listEventTypes`

`GET /v1/webhooks/event-types`

Parameters: none.

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
[
  {
    "id": "id-placeholder",
    "description": "example"
  }
]
```

### `webhooks.listDispatches`

`GET /v1/accounts/{accountId}/webhooks`

Parameters: `accountId` (path, required), `endpoint_id` (query, optional), `event` (query, optional), `delivered` (query, optional), `from` (query, optional), `to` (query, optional), `page` (query, optional), `per-page` (query, optional).

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
[
  {
    "resource": "activity_dispatching_history",
    "id": "id-placeholder",
    "event": "example",
    "activity_id": 1,
    "endpoint_id": "id-placeholder",
    "endpoint": "https://example.com/webhooks/assinafy",
    "payload": {},
    "delivered": true,
    "http_status": 1,
    "response_body": null,
    "error": null,
    "created_at": "2026-01-01T00:00:00Z",
    "updated_at": "2026-01-01T00:00:00Z"
  }
]
```

### `webhooks.retryDispatch`

`POST /v1/accounts/{accountId}/webhooks/{historyId}/retry`

Parameters: `accountId` (path, required), `historyId` (path, required).

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
{
  "resource": "activity_dispatching_history",
  "id": "id-placeholder",
  "event": "example",
  "activity_id": 1,
  "endpoint_id": "id-placeholder",
  "endpoint": "https://example.com/webhooks/assinafy",
  "payload": {},
  "delivered": true,
  "http_status": 1,
  "response_body": null,
  "error": null,
  "created_at": "2026-01-01T00:00:00Z",
  "updated_at": "2026-01-01T00:00:00Z"
}
```

### `webhooks.listEndpoints`

`GET /v1/accounts/{accountId}/webhooks/endpoints`

Parameters: `accountId` (path, required).

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`) is an array of endpoints, oldest first:
```json
[
  {
    "id": "id-placeholder",
    "name": "ERP",
    "url": "https://example.com/webhooks/assinafy",
    "email": "ops@example.com",
    "events": ["document_ready"],
    "is_active": true,
    "signing_enabled": true,
    "created_at": "2026-01-01T00:00:00Z",
    "updated_at": "2026-01-01T00:00:00Z"
  }
]
```

### `webhooks.createEndpoint`

`POST /v1/accounts/{accountId}/webhooks/endpoints`

Parameters: `accountId` (path, required).

Request body (`application/json`; `name`, `is_active` and `signing_enabled` are optional):
```json
{
  "url": "https://example.com/webhooks/assinafy",
  "email": "ops@example.com",
  "events": ["document_ready", "signer_signed_document"],
  "name": "ERP",
  "is_active": true,
  "signing_enabled": true
}
```

Response 200 `data` payload (inside `{status,message,data}`):
```json
{
  "id": "id-placeholder",
  "name": "ERP",
  "url": "https://example.com/webhooks/assinafy",
  "email": "ops@example.com",
  "events": ["document_ready", "signer_signed_document"],
  "is_active": true,
  "signing_enabled": true,
  "created_at": "2026-01-01T00:00:00Z",
  "updated_at": "2026-01-01T00:00:00Z"
}
```

Past the plan's endpoint limit the API answers `403`; a `url` already used in the workspace answers `400`.

### `webhooks.getEndpoint`

`GET /v1/accounts/{accountId}/webhooks/endpoints/{endpointId}`

Parameters: `accountId` (path, required), `endpointId` (path, required).

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`):
```json
{
  "id": "id-placeholder",
  "name": "ERP",
  "url": "https://example.com/webhooks/assinafy",
  "email": "ops@example.com",
  "events": ["document_ready", "signer_signed_document"],
  "is_active": true,
  "signing_enabled": true,
  "created_at": "2026-01-01T00:00:00Z",
  "updated_at": "2026-01-01T00:00:00Z"
}
```

### `webhooks.updateEndpoint`

`PUT /v1/accounts/{accountId}/webhooks/endpoints/{endpointId}`

Parameters: `accountId` (path, required), `endpointId` (path, required).

Request body (`application/json`; every member optional, at least one required):
```json
{
  "url": "https://example.com/webhooks/assinafy",
  "email": "ops@example.com",
  "events": ["document_ready"],
  "name": "ERP",
  "is_active": false,
  "signing_enabled": true
}
```

Response 200 `data` payload (inside `{status,message,data}`):
```json
{
  "id": "id-placeholder",
  "name": "ERP",
  "url": "https://example.com/webhooks/assinafy",
  "email": "ops@example.com",
  "events": ["document_ready", "signer_signed_document"],
  "is_active": true,
  "signing_enabled": true,
  "created_at": "2026-01-01T00:00:00Z",
  "updated_at": "2026-01-01T00:00:00Z"
}
```

`signing_enabled: true` generates a secret when the endpoint has none and keeps it otherwise; `false` discards it.

### `webhooks.deleteEndpoint`

`DELETE /v1/accounts/{accountId}/webhooks/endpoints/{endpointId}`

Parameters: `accountId` (path, required), `endpointId` (path, required).

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`):
```json
[]
```

### `webhooks.getEndpointSecret`

`GET /v1/accounts/{accountId}/webhooks/endpoints/{endpointId}/secret` (API key; not available to OAuth applications)

Parameters: `accountId` (path, required), `endpointId` (path, required).

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`):
```json
{
  "secret": "whsec_<base64-key>"
}
```

Answers `400` when signing is disabled.

### `webhooks.rotateEndpointSecret`

`POST /v1/accounts/{accountId}/webhooks/endpoints/{endpointId}/secret/rotate` (API key; not available to OAuth applications)

Parameters: `accountId` (path, required), `endpointId` (path, required).

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`):
```json
{
  "secret": "whsec_<base64-key>"
}
```

The previous secret stops working immediately. Answers `400` when signing is disabled.

## OAuth method payloads

OAuth responses are flat objects; form-field JSON below illustrates members, not the encoding sent on the wire.

### `oauth.authorizationRequest`

Browser request: `GET https://auth.assinafy.com.br/oauth/authorize`; no request body.
Complete query fields (no verifier or client secret is sent):
```json
{
  "response_type": "code",
  "client_id": "public-client-id",
  "redirect_uri": "https://example.com/callback",
  "scope": "documents:read account:read openid offline_access",
  "state": "per-attempt-state",
  "code_challenge": "S256-challenge",
  "code_challenge_method": "S256",
  "resource": "https://api.assinafy.com.br",
  "nonce": "per-attempt-nonce"
}
```
The response is the interactive approval page, followed by the registered redirect with
`code`, `state` and `iss`, or `error`, `error_description`, `state` and `iss`.

### `oauth.parseCallback`

Local operation; no HTTP request or response. Input is the complete registered HTTPS
redirect with query fields `code`, `state`, `iss`, or `error`, `error_description`, `state`, `iss`.
Returns the single-use code; no token is exchanged. Duplicate parameters, a different
registered address, fragments and malformed URIs raise ValidationException.

### `oauth.exchangeCode`

Complete form fields for `POST /oauth/token` (shown as JSON for readability; sent as form encoding):
```json
{
  "grant_type": "authorization_code",
  "code": "<single-use-code>",
  "redirect_uri": "https://example.com/callback",
  "client_id": "public-client-id",
  "code_verifier": "<43-to-128-unreserved-characters>",
  "resource": "https://api.assinafy.com.br"
}
```
Malformed or incomplete successful token responses raise `OAuthException.INVALID_RESPONSE`.

### `oauth.refresh`

Complete form fields for `POST /oauth/token` (shown as JSON; sent as form encoding):
```json
{
  "grant_type": "refresh_token",
  "refresh_token": "<latest-refresh-token>",
  "client_id": "public-client-id"
}
```
Complete flat response body:
```json
{
  "access_token": "<access-token>",
  "token_type": "Bearer",
  "expires_in": 3600,
  "scope": "documents:read account:read openid",
  "refresh_token": "<rotated-refresh-token>",
  "id_token": "<id-token>"
}
```

### `oauth.revoke`

Complete form fields for `POST /oauth/revoke` (shown as JSON; sent as form encoding):
```json
{
  "token": "<latest-token>",
  "token_type_hint": "refresh_token",
  "client_id": "public-client-id"
}
```
Response: HTTP 200 with no body. The hint is optional and accepts only `access_token` or `refresh_token`.

### `oauth.userInfo`

Request: `GET /oauth/userinfo`, bearer authentication, no request body.
Complete flat response body (name/email claims depend on granted scopes):
```json
{
  "sub": "user-placeholder",
  "name": "Example User",
  "email": "person@example.com",
  "email_verified": true
}
```

### `oauth.protectedResourceMetadata`

Request: `GET {apiOrigin}/.well-known/oauth-protected-resource`; no credentials or body.
Complete flat response body:
```json
{
  "resource": "https://api.assinafy.com.br",
  "authorization_servers": [
    "https://auth.assinafy.com.br"
  ],
  "scopes_supported": [
    "documents:read",
    "documents:write",
    "templates:read",
    "templates:write",
    "account:read",
    "webhooks:write",
    "openid",
    "profile",
    "email"
  ],
  "bearer_methods_supported": [
    "header"
  ]
}
```

### `oauth.authorizationServerMetadata`

Request: `GET {issuer}/.well-known/oauth-authorization-server`; no credentials or body.
Complete flat response body:
```json
{
  "issuer": "https://auth.assinafy.com.br",
  "authorization_endpoint": "https://auth.assinafy.com.br/oauth/authorize",
  "token_endpoint": "https://api.assinafy.com.br/v1/oauth/token",
  "revocation_endpoint": "https://api.assinafy.com.br/v1/oauth/revoke",
  "userinfo_endpoint": "https://api.assinafy.com.br/v1/oauth/userinfo",
  "introspection_endpoint": "https://api.assinafy.com.br/v1/oauth/introspect",
  "introspection_endpoint_auth_methods_supported": [
    "client_secret_post"
  ],
  "jwks_uri": "https://auth.assinafy.com.br/.well-known/jwks.json",
  "scopes_supported": [
    "documents:read",
    "documents:write",
    "templates:read",
    "templates:write",
    "account:read",
    "webhooks:write",
    "openid",
    "profile",
    "email",
    "offline_access"
  ],
  "response_types_supported": [
    "code"
  ],
  "grant_types_supported": [
    "authorization_code",
    "refresh_token",
    "urn:ietf:params:oauth:grant-type:token-exchange"
  ],
  "code_challenge_methods_supported": [
    "S256"
  ],
  "token_endpoint_auth_methods_supported": [
    "client_secret_post",
    "none"
  ],
  "authorization_response_iss_parameter_supported": true,
  "client_id_metadata_document_supported": true
}
```
The issuer also advertises token exchange and introspection for confidential services. Public Android applications use authorization codes with PKCE and refresh tokens; this SDK does not implement confidential-service operations.


Authorization-code exchange response:

```json
{
  "access_token": "<access-token>",
  "token_type": "Bearer",
  "expires_in": 3600,
  "scope": "documents:read account:read openid",
  "refresh_token": "<rotated-refresh-token>",
  "id_token": "<id-token>"
}
```
