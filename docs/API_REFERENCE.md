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
| `ApiException` | HTTP or envelope status was not 2xx. Inspect `statusCode`, `responseData`, and `challenge` (the parsed `WWW-Authenticate: Bearer` challenge, if any); do not branch on human-readable text, and redact response data before logging because servers can echo sensitive input. A `429` also carries `retryAfter` (`Retry-After`, seconds) and `rateLimitLimit`, `rateLimitRemaining`, `rateLimitReset` (`X-Rate-Limit-*`); each is `null` when the header was absent. |
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
user information, a query, or a fragment is rejected. All clients require HTTPS except for loopback development hosts. `apiKey` and `token` are mutually exclusive. The convenience
`create(apiKey, accountId, baseUrl, webhookSecret, timeoutMs, logger)` builds the same client.

| Property | Operations |
|---|---|
| `authentication` | Login, passwords, social identity, personal API keys |
| `oauth` | OAuth 2.1 authorization-code flow with PKCE, refresh, revocation, userinfo, discovery |
| `workspaces` | Accounts, themes, logos, account statistics |
| `documents` | Documents, artifacts, public document access, template instantiation, document tags |
| `contracts` | Contract terms, filters, amendments, family reads, renewal and termination |
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
Digital-certificate signers require a `virtual` assignment. A rejected collect combination throws
`ValidationException` with `errors["code"]` equal to
`ValidationCode.DIGITAL_CERTIFICATE_REQUIRES_VIRTUAL_ASSIGNMENT`
(`digital_certificate_requires_virtual_assignment`).
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

## ContractResource

| Kotlin function | Exact request | JSON body | Return (`data`) |
|---|---|---|---|
| `list(ContractListParams, accountId)` | `GET /v1/accounts/{accountId}/contracts` | none; filters in query | `PaginatedResult<DocumentContract>` |
| `get(documentId, accountId)` | `GET /v1/accounts/{accountId}/documents/{documentId}/contract` | none | `ContractRecord` |
| `update(documentId, UpdateContractRequest, accountId)` | `PUT /v1/accounts/{accountId}/documents/{documentId}/contract` | Supplied terms/link/delta fields; `clearFields` sends explicit null | `ContractRecord` |
| `related(documentId, accountId)` | `GET /v1/accounts/{accountId}/documents/{documentId}/contract/related` | none | `DocumentContract` with amendments |
| `renew(documentId, RenewContractRequest, accountId)` | `POST /v1/accounts/{accountId}/documents/{documentId}/contract/renew` | `term`, `end_date`, `value`, `updated_at`; term or end date required | `DocumentContract` |
| `terminate(documentId, TerminateContractRequest, accountId)` | `POST /v1/accounts/{accountId}/documents/{documentId}/contract/terminate` | Optional `reason`, `updated_at` | `DocumentContract` |

OAuth reads require `documents:read`; writes require `documents:write` and the account's contract-tracking entitlement.
Contract reads and updates return `ContractRecord`, either root terms or an amendment link.

Contract lists accept page/per-page, comma-separated statuses, counterparty, document-name search,
inclusive end-date bounds, ending-within-days (0..3650), external-system/ID, sort (end_date/updated_at,
optionally descending) and document expansion. Root terms use exact BigDecimal values. Get/update
return ContractRecord; related/renew/terminate return DocumentContract. Amendment terms and changes
remain JSON objects to preserve their varying field types and exact numbers.

UpdateContractRequest omits nulls and uses clearFields for explicit null. A pending amendment's null
removes a key from its delta; clearing PARENT_DOCUMENT_ID unlinks it. External references replace the
list. Counterparty-name emoji limits follow the server's stored representation. RenewContractRequest
requires the current term or a later endDate; clearValue sends value:null. TerminateContractRequest
accepts a reason up to 500 Unicode characters. Each write accepts the last-read updatedAt; a changed
record returns 409 with code ContractChanged. ApiException.responseData preserves all field/code/message
validation entries. Membership, CPF/CNPJ, plan and existing-state rules are enforced by the API.

## DocumentResource

| Kotlin function | Exact request | Query/body | Return (`data`) |
|---|---|---|---|
| `upload(fileData, fileName, metadata, accountId)` | `POST /v1/accounts/{accountId}/documents` | Multipart `file` (`application/pdf`). Only when `metadata` is non-null, opt in to live compatibility `name` and JSON-string `metadata` parts | `DocumentUploadResponse` |
| `list(ListParams, accountId)` | `GET /v1/accounts/{accountId}/documents` | Optional `status`, `method`, `search`, comma-separated tag IDs in `tags`, `sort`, `page`, `per-page` | `PaginatedResult<DocumentListItem>`; alias of full `DocumentDetails` |
| `search(query, status, page, perPage, accountId)` | `GET /v1/accounts/{accountId}/documents/search` | Optional `search`, `status`, `page`, `per-page` | `PaginatedResult<DocumentListItem>`; alias of full `DocumentDetails` |
| `search(ListParams, accountId)` | Same search route | `search`, `status`, `sort`, `page`, `per-page` | `PaginatedResult<DocumentListItem>` |
| `details(documentId)` | `GET /v1/documents/{documentId}` | none | `DocumentDetails` |
| `get(documentId)` | Same as `details` | none | `DocumentDetails` |
| `waitUntilReady(documentId, maxWaitMs, pollIntervalMs)` | Repeats `GET /v1/documents/{documentId}` | Local timing arguments only | First `DocumentDetails` whose status is in `DocumentStatus.READY` |
| `download(documentId, artifactName)` | `GET /v1/documents/{documentId}/download/{artifactName}` | none | Raw PDF/ZIP `ByteArray` |
| `thumbnail(documentId)` | `GET /v1/documents/{documentId}/thumbnail` | none | Raw image `ByteArray` |
| `downloadPage(documentId, pageId)` | `GET /v1/documents/{documentId}/pages/{pageId}/download` | none | Raw page image `ByteArray` |
| `activities(documentId)` | `GET /v1/documents/{documentId}/activities` | none | `List<DocumentActivity>` |
| `activities(documentId, ListParams)` | Same activity route | `page`, `per-page`, `sort=id` or `-id` | `PaginatedResult<DocumentActivity>` |
| `delete(documentId)` | `DELETE /v1/documents/{documentId}` | none; allowed only when the status catalog reports `deletable=true` | `Unit` |
| `rename(documentId, name)` | `PATCH /v1/documents/{documentId}` | `{"name":string}`; maximum 255 characters; only before assignment in `uploaded`/`metadata_ready` | `DocumentDetails` |
| `createFromTemplate(templateId, signers, options, accountId)` | `POST /v1/accounts/{accountId}/templates/{templateId}/documents` | `CreateDocumentFromTemplateRequest`; function `signers` replaces `options.signers` | `DocumentDetails` |
| `estimateCostFromTemplate(templateId, signers, accountId)` | `POST /v1/accounts/{accountId}/templates/{templateId}/documents/estimate-cost` | `{"signers":[{"role_id":string,"verification_method":string?,"notification_methods":[string]?}]}` | `CostEstimate` |
| `verify(hash)` | `GET /v1/documents/{documentSignatureHash}/verify` (public) | none | `DocumentVerification` |
| `getPublic(documentId)` | `GET /v1/public/documents/{documentId}` (public) | none | `PublicDocumentInfo` |
| `sendAccessToken(documentId, email)` | `PUT /v1/public/documents/{documentId}/send-token` (public) | `{"channel":"email","recipient":string}`; both required; `recipient` is the signer's email | `AccessTokenDelivery` (`document`, `channel`, `recipient`) |
| `sendToken(documentId, email?, channel?)` | Same request; deprecated | `email` required; `channel` must be `null` or `email` | `Unit` |
| `isFullySigned(documentId)` | Calls `details` | local derivation | `true` when every signer is complete or status is `certificated`; does not guarantee the certificated artifact is ready during `certificating` |
| `getSigningProgress(documentId)` | Calls `details` | local derivation; uses signer `completed` flags when `summary` is absent | `SigningProgress` |
| `getStatuses()` | `GET /v1/documents/statuses` | none | `List<DocumentStatusInfo>` |
| `confirmSignerData(documentId, accessCode, request)` | `PUT /v1/documents/{documentId}/signers/confirm-data?signer-access-code=...` | `email`, `whatsapp_phone_number`, `has_accepted_terms`; name/government-ID extensions remain opt-in | `Signer` |
| `confirmSignerData(documentId, accessCode, data)` | Same endpoint; compatibility overload | Caller-supplied non-empty JSON object | `Signer` |
| `listTags(documentId, accountId)` | `GET /v1/accounts/{accountId}/documents/{documentId}/tags` | none | `List<Tag>` |
| `replaceTags(documentId, tagNames, accountId)` | `PUT /v1/accounts/{accountId}/documents/{documentId}/tags` | `{"tags":["tag_id",...]}`; parameter name is retained for source compatibility | `List<Tag>` |
| `addTags(documentId, tagNames, accountId)` | `POST /v1/accounts/{accountId}/documents/{documentId}/tags` | `{"tags":["tag_id",...]}`; parameter name is retained for source compatibility | `List<Tag>` |
| `detachTag(documentId, tagId, accountId)` | `DELETE /v1/accounts/{accountId}/documents/{documentId}/tags/{tagId}` | none | `Unit` |

Template methods require only their documented POST permission. The API checks actual Signer-role
fields and steps, while Editor fields are rendered before signing and CopyReceiver roles do not sign.

Uploads must be non-empty PDF content, have a `.pdf` name, begin with `%PDF-`, and be no larger than
25 MiB; the service accepts at most 2,000 pages. Artifact names are `original`, `certificated`,
`certificate-page`, `pades`, and `bundle`. The `pades` artifact exists only when the document has
digital-certificate signers and contains their ICP-Brasil signatures plus the platform certification
box. `bundle` is a ZIP containing the original,
certificated, and certificate-page artifacts, plus PAdES when available. Readiness stops at
`metadata_ready`, `pending_signature`, `pending_identity_review`, `certificating`, or `certificated`, and fails immediately for
the terminal `failed`, `rejected_by_signer`, `rejected_by_user`, and `expired` states.

`verify(hash)` always returns HTTP `200`. For an unknown hash or an unsigned document,
`is_valid=false`, the other nullable certification details are null, and `message` explains why.

`sendAccessToken` sends a one-time access token to a signer who has not signed yet, on a document
pending signature. Only the `email` channel exists; the deprecated `sendToken` rejects any other
channel before sending.

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

The `search(ListParams, accountId)` overload also sends `sort`. The activity overload
`activities(documentId, ListParams)` returns `PaginatedResult<DocumentActivity>` and accepts page,
per-page and sort (`id` or `-id`); the original activity method returns the first page.

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
| `listDocumentsAsWorkspace(signerId, ListParams)` | `GET /v1/signers/{signerId}/documents` with the workspace API key or bearer token | Query uses only `page`, `per-page` | `PaginatedResult<DocumentDetails>` |
| `searchDocumentsAsWorkspace(signerId, search)` | `GET /v1/signers/{signerId}/documents/search` with the workspace API key or bearer token | `search` (omitted when blank) | `PaginatedResult<DocumentDetails>` |

The deprecated `cpf` field is sent as `government_id` on create and update; the deprecated `metadata`
create field is sent only when explicitly set and is not part of the current create-signer schema.
Every method that takes a signer access code rejects a blank code before any request and sends only
that code, never the API key or bearer token. Workspace access to a signer's documents goes through
the two `AsWorkspace` methods above.

The service locks signer updates while verification is in progress. Changing an unverified email or
WhatsApp number rotates the signer access and OTP codes; resend the notification before continuing.

To clear a signer email, WhatsApp number or government ID, pass an empty string; null omits it.

## SignerDocumentResource

All methods use a client transport that contains no account API key or bearer token. Except for the
public artifact download, `accessCode` becomes the query key `signer-access-code` and never appears
in a JSON body.

| Kotlin function | Exact request | Body | Return (`data`) |
|---|---|---|---|
| `self(accessCode)` | `GET /v1/signers/self?signer-access-code=...` | none | `SignerSelf` |
| `getIdentityVerification(accessCode)` | `GET /v1/signers/identity-verification?signer-access-code=...` | none | `SignerIdentityVerification` |
| `startIdentityVerification(accessCode, consent)` | `POST /v1/signers/identity-verification?signer-access-code=...` | `{"consent":true}` after explicit signer consent | `IdentityVerificationSession` |
| `getCurrent(signerId, accessCode)` | `GET /v1/signers/{signerId}/document?signer-access-code=...` | none | `DocumentDetails` |
| `getAssignment(accessCode, hasAcceptedTerms)` | `GET /v1/sign?signer-access-code=...&has_accepted_terms=...` | none | `DocumentDetails` |
| `sign(documentId, assignmentId, accessCode, entries)` | `POST /v1/documents/{documentId}/assignments/{assignmentId}?signer-access-code=...` | Collect: array of `SignAssignmentItemRequest`; confirmed virtual: exactly `[]` | API result `Map<String,Any>` |
| `decline(documentId, assignmentId, accessCode, declineReason)` | `PUT /v1/documents/{documentId}/assignments/{assignmentId}/reject?signer-access-code=...` | `{"decline_reason":string}` | `Unit` |
| `signMultiple(documentIds, accessCode)` | `PUT /v1/signers/documents/sign-multiple?signer-access-code=...` | `{"document_ids":[string,...]}` | `Unit` |
| `declineMultiple(documentIds, declineReason, accessCode)` | `PUT /v1/signers/documents/decline-multiple?signer-access-code=...` | `{"document_ids":[string,...],"decline_reason":string}` | `Unit` |
| `verifyEmail(accessCode, VerifySignerEmailRequest)` | `POST /v1/verify?signer-access-code=...` | `{"verification-code":string}` | `Unit` |
| `confirmData(documentId, accessCode, ConfirmSignerDataRequest)` | `PUT /v1/documents/{documentId}/signers/confirm-data?signer-access-code=...` | `email`, `whatsapp_phone_number`, `has_accepted_terms`; legacy name/ID extensions remain opt-in | `Signer` |
| `acceptTerms(accessCode)` | `PUT /v1/signers/accept-terms?signer-access-code=...` | none | `Unit` |
| `uploadSignature(accessCode, imageData, type, reuse)` | `POST /v1/signature?signer-access-code=...&type=...&reuse=...` | Raw PNG bytes, `Content-Type: image/png` | `Unit` |
| `downloadSignature(accessCode, type)` | `GET /v1/signature/{type}?signer-access-code=...` | none | Raw PNG `ByteArray` |
| `list(signerId, accessCode, ListParams)` | `GET /v1/signers/{signerId}/documents?signer-access-code=...` | Query uses only `page`, `per-page` | `PaginatedResult<DocumentDetails>` |
| `search(signerId, accessCode, search)` | `GET /v1/signers/{signerId}/documents/search?signer-access-code=...&search=...` | none | `PaginatedResult<DocumentDetails>` |
| `download(signerId, documentId, artifactName, accessCode)` | `GET /v1/signers/{signerId}/documents/{documentId}/download/{artifactName}?signer-access-code=...` | Only `original`; the old three-argument overload is deprecated | PDF `ByteArray` |

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
| `list(ListParams, accountId)` | `GET /v1/assignments` | Optional `page`, `per-page`, `sort`; sends the explicit or configured `accountId` when present (required for API keys, inferred for OAuth) | `PaginatedResult<Assignment>` |
| `create(documentId, CreateAssignmentRequest)` | `POST /v1/documents/{documentId}/assignments` | Full assignment request below | `Assignment` |
| `estimateCost(documentId, CreateAssignmentRequest)` | `POST /v1/documents/{documentId}/assignments/estimate-cost` | Projected `method`, `signers`, and `entries`; signer IDs/steps omitted | `CostEstimate` |
| `resetExpiration(documentId, assignmentId, expiresAt)` | `PUT /v1/documents/{documentId}/assignments/{assignmentId}/reset-expiration` | `{"expires_at":string}`, or `{"expires_at":null}` for `null` to remove the expiration; a blank string is rejected | `Assignment` |
| `decline(documentId, assignmentId, accessCode, reason)` | Compatibility alias for signer reject | `{"decline_reason":string}` and signer query code | `Unit` |
| `listWhatsappNotifications(documentId, assignmentId)` | `GET /v1/documents/{documentId}/assignments/{assignmentId}/whatsapp-notifications` | none | `List<WhatsappNotification>` |
| `resendNotification(documentId, assignmentId, signerId, channel?)` | `PUT /v1/documents/{documentId}/assignments/{assignmentId}/signers/{signerId}/resend` | none; optional deployed-service `{"channel":"email"\|"whatsapp"}` | `ResendEmailResponse` |
| `estimateResendCost(documentId, assignmentId, signerId)` | `POST /v1/documents/{documentId}/assignments/{assignmentId}/signers/{signerId}/estimate-resend-cost` | none | `CostEstimate` |
| `getIdentityVerification(documentId, assignmentId, signerId)` | `GET /v1/documents/{documentId}/assignments/{assignmentId}/signers/{signerId}/identity-verification` | none | `SignerIdentityReview` |
| `downloadIdentityVerificationImage(documentId, assignmentId, signerId, name)` | `GET /v1/documents/{documentId}/assignments/{assignmentId}/signers/{signerId}/identity-verification/images/{name}` | none; temporary evidence only | Raw image/PDF `ByteArray` |
| `reviewIdentityVerification(documentId, assignmentId, signerId, decision, note)` | `POST /v1/documents/{documentId}/assignments/{assignmentId}/signers/{signerId}/identity-verification/review` | `{"decision":"Approved"\|"Rejected","note":string?}` | `SignerIdentityReview` |

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
| `IdDocumentFaceMatchBasic` | `["Email"]` or `["Whatsapp"]` |

The SDK applies this table, the signing-step rules, and the collect field-placement rules locally, so
an invalid pairing raises `ValidationException` instead of costing a round trip. WhatsApp delivery
requires a paid subscription and costs 0.45 credits per signer. A digital-certificate signer must be
alone in its signing step, requires account entitlement and a CPF/CNPJ in the signer's
`government_id` (set through `signers.update` after signer creation), costs 0.5 credits in addition
to its notification, requires `method = "virtual"`, and completes through certificate-specific
signing. `assignments.create` and `assignments.estimateCost` reject `collect` with
`ValidationCode.DIGITAL_CERTIFICATE_REQUIRES_VIRTUAL_ASSIGNMENT` before sending a request.

`method` is `virtual` or `collect`. Create requires an ID for every signer. Steps, if present, form a
contiguous positive sequence; signers on a shared step act in parallel. A digital-certificate signer
must be alone in its step. A virtual assignment may be created while the document is `uploaded`,
`metadata_processing`, or `metadata_ready`; the service promotes it to `pending_signature` after
metadata processing. A collect assignment requires `metadata_ready` because its fields target
specific pages.
Every signer in a collect assignment must own at least one placed field; placement page, signer and
field identifiers are trimmed before the request is sent.

Step 1 signers are notified when the assignment is created. A later step is notified only after every
signer in all preceding steps has completed. Each assignment consumes one document from the plan
allowance; when that allowance is exhausted, an extra document costs 1 credit. Email notifications
cost 0 credits, WhatsApp notifications cost 0.45 credits each, and each digital-certificate signer
costs 0.5 credits in addition to notification costs. The estimate uses breakdown code
`SignatureDigitalCertificate` for that signer cost and may report `PendingPayment`,
`InsufficientDocuments`, or `InsufficientCredits` in `blocking_reason`.

In non-production environments, `listWhatsappNotifications` returns simulated messages without
real delivery. Its button URLs contain signer access or verification codes; treat those URLs as
credentials and do not log, persist, or publish them.

Biometric identity-document/face verification requires the account feature and a valid signer CPF.
The hosted flow needs explicit consent; `InReview` permits signing but holds certification until
the account approves. `Declined`/`Abandoned` requires a new signing process. The server prices it
per signer; use the cost estimate.

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
| `updateNotificationPreferences(request)` | `PUT /v1/users/self/notification-preferences` | Non-empty subset of the thirteen PascalCase preference keys | Complete `NotificationPreferences` |

All thirteen document-notification preferences are always returned and default to `true`. They govern
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
Signer role; CopyReceiver roles are optional and Editor roles use editor fields only. An entry
uses an existing `id` or inline `email`, `name` and `whatsapp_phone_number`. Creation returns
`uploaded` with `assignment: null`; completion produces `pending_signature` / `assignment_created`,
or `failed` / `document_processing_failed` without a charge.

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
| `listEndpoints(accountId)` / `listEndpoints(accountId, url)` | `GET /v1/accounts/{accountId}/webhooks/endpoints` | Optional exact `url` query | `List<WebhookEndpoint>`, oldest first |
| `createEndpoint(CreateWebhookEndpointRequest, accountId)` | `POST /v1/accounts/{accountId}/webhooks/endpoints` | `{"url":string,"email":string,"events":[string],"name":string?,"is_active":boolean?,"signing_enabled":boolean?}` | `WebhookEndpoint`; 403 past the plan limit |
| `getEndpoint(endpointId, accountId)` | `GET /v1/accounts/{accountId}/webhooks/endpoints/{endpointId}` | none | `WebhookEndpoint?`; null on 404 |
| `updateEndpoint(endpointId, UpdateWebhookEndpointRequest, accountId)` | `PUT /v1/accounts/{accountId}/webhooks/endpoints/{endpointId}` | Only supplied members of `{"url","email","events","name","is_active","signing_enabled"}`; at least one | `WebhookEndpoint` |
| `deleteEndpointByUrl(url, accountId)` | `DELETE /v1/accounts/{accountId}/webhooks/endpoints` | Required exact `url` query | `Unit`; 404 if absent |
| `deleteEndpoint(endpointId, accountId)` | `DELETE /v1/accounts/{accountId}/webhooks/endpoints/{endpointId}` | none | `Unit` |
| `getEndpointSecret(endpointId, accountId)` | `GET /v1/accounts/{accountId}/webhooks/endpoints/{endpointId}/secret` (API key only) | none | `WebhookEndpointSecret`; 400 when signing is disabled |
| `rotateEndpointSecret(endpointId, accountId)` | `POST /v1/accounts/{accountId}/webhooks/endpoints/{endpointId}/secret/rotate` (API key or OAuth `webhooks:write`) | none | `WebhookEndpointSecret`; the old secret stops working at once |

An account has 1 endpoint, or up to 3 on paid plans, and every active endpoint subscribed to an event
receives it independently. `register`, `get` and `inactivate` act on the account's oldest endpoint;
the `*Endpoint*` methods address each endpoint by ID. A `url` already used by another endpoint of the
workspace answers `400`.

If `events` is `null`, the SDK subscribes to `RegisterWebhookRequest.DEFAULT_EVENTS`. An explicit
empty list is sent unchanged. Retrieve the server's current event catalog with `listEventTypes()` before
offering a selection to users.

Assinafy delivers each event as an `application/json` HTTP `POST`; any `2xx` response succeeds. A
retryable failure receives up to six attempts, about ten minutes apart. Connection errors, timeouts,
408, 429 and 5xx are retryable; other statuses are final. Ten consecutive failures hold the endpoint;
probes run after 3, 15 and 60 minutes, then every six hours. Recovery sends missed events oldest first.
`retryDispatch`, changing the URL or setting is_active:true can lift the hold. Dispatch history stores only the first
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

OAuth can rotate endpoint secrets with `webhooks:write`; only existing-secret reads require an API key.
Enabling signing on update returns a new `secret` once, just as creation does.

## Request types

`?` means nullable/omittable. Wire names are shown exactly.

| Kotlin type | Complete fields |
|---|---|
| `AssinafyClientConfig` | `apiKey:String?`, `token:String?`, `accountId:String?`, `baseUrl:String`, `webhookSecret:String?`, `timeoutMs:Long`, `logger:Logger?` |
| `UpdateContractRequest` | `status:String?`, `counterparty_name→counterpartyName:String?`, `counterparty_taxpayer_id→counterpartyTaxpayerId:String?`, `value:BigDecimal?`, `currency:String?`, `start_date→startDate:String?`, `end_date→endDate:String?`, `auto_renew→autoRenew:Boolean?`, `renewal_term_months→renewalTermMonths:Int?`, `notice_period_days→noticePeriodDays:Int?`, `owner_id→ownerId:String?`, `metadata:Map<String, String>?`, `external_references→externalReferences:List<ContractExternalReference>?`, `parent_document_id→parentDocumentId:String?`, `relation_type→relationType:String?`, `updated_at→updatedAt:String?`, `clearFields:Set<ContractField>` |
| `RenewContractRequest` | `term:Int?`, `end_date→endDate:String?`, `value:BigDecimal?`, `clearValue:Boolean`, `updated_at→updatedAt:String?` |
| `TerminateContractRequest` | `reason:String?`, `updated_at→updatedAt:String?` |
| `ContractListParams` | `page:Int?`, `perPage:Int?`, `statuses:List<String>?`, `counterparty:String?`, `search:String?`, `endDateFrom:String?`, `endDateTo:String?`, `endingWithinDays:Int?`, `externalSystem:String?`, `externalId:String?`, `sort:String?`, `expandDocument:Boolean` |
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
| `ConfirmSignerDataRequest` | `full_name:String?`, `email:String?`, `government_id:String?`; `whatsapp_phone_number:String?`, `has_accepted_terms:Boolean?` confirm contacts and explicit consent |
| `SignerReference` | `id:String?` (required for creation, omitted for pricing), `verification_method:String?`, `notification_methods:List<String>?` (exactly one: Email or WhatsApp), `step:Int?` |
| `CreateAssignmentRequest` | `method:String`, `signers:List<SignerReference>`, `message:String?`, `expires_at:String?`, `copy_receivers:List<String>?` (signer IDs), `entries:List<AssignmentEntry>?` |
| `AssignmentEntry` | `page_id:String`, `fields:List<AssignmentFieldPlacement>` |
| `AssignmentFieldPlacement` | `signer_id:String`, `field_id:String`, `display_settings:DisplaySettings?` |
| `DisplaySettings` | `left:Float`, `top:Float`, `width:Float`, `height:Float`, `fontSize:Float`, `fontFamily:String?`, `backgroundColor:String?` |
| `TemplateSigner` | `role_id:String`, `id:String?` or `email:String?` for creation, `name:String?`, `whatsapp_phone_number:String?`, `verification_method:String?`, `notification_methods:List<String>?` (exactly one when supplied), `step:Int?` |
| `CreateDocumentFromTemplateRequest` | `signers:List<TemplateSigner>`, `name:String?`, `message:String?`, `expires_at:String?`, `editor_fields:List<TemplateEditorField>?`, `tags:List<String>?` (tag names; missing names are created and merged with template defaults) |
| `TemplateEditorField` | `field_id:String`, `value:String` |
| `CreateFieldRequest` | `name:String`, `type:String`, `regex:String?`, `is_required:Boolean?` |
| `UpdateFieldRequest` | local `name:String?`, `regex:String?`, `clearRegex:Boolean`, `isActive:Boolean?`; serialized as `name`, `regex`, `is_active` |
| `FieldValidationEntry` | `field_id:String`, `value:Any?` |
| `SignAssignmentItemRequest` | `itemId:String`, `fieldId:String`, `pageId:String`, `value:String` |
| `VerifySignerEmailRequest` | `verification-code:String` |
| `DocumentStatsQuery` | query `granularity:DocumentStatsGranularity?` (`monthly` or `daily`), `month:String?` (`YYYY-MM`, required for daily) |
| `UpdateNotificationPreferencesRequest` | Nullable booleans: `DocumentCompleted`, `SignerDeclined`, `DocumentCancelled`, `DocumentAboutToExpire`, `DocumentExpired`, `DocumentExpirationReset`, `DocumentProcessingFailed`, `TemplateProcessingFailed`, `SignerWhatsappFailed`; optional `Boolean?`: `ContractRenewalNotice`, `ContractExpiring`, `ContractExpired`, `ContractRenewed` |
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
| `SigningUrl` | `signer_id→signerId:String`, `url:String?`, `embedded:Boolean?` |
| `Signer` | `id:String`, `full_name→fullName:String?`, `email:String?`, `whatsapp_phone_number→whatsappPhoneNumber:String?`, legacy `cpf:String?`, deployed response `government_id→governmentId:String?`, `has_accepted_terms→hasAcceptedTerms:Boolean?`, legacy `metadata:Map<String,Any>?`; assignment expansions `completed:Boolean?`, `verification_method→verificationMethod:String?`, `notification_methods→notificationMethods:List<String>?`, `step:Int?`, `notified:Boolean?`, `notification_history→notificationHistory:List<NotificationHistoryEntry>?`; signer-self expansions `has_signature→hasSignature:Boolean?`, `has_initial→hasInitial:Boolean?`, `is_signature_reusable→isSignatureReusable:Boolean?`; `resource:String?`, `identity_verification_status→identityVerificationStatus:String?` |
| `SignerSelf` | `resource:String?`, `id:String`, `full_name→fullName:String?`, `email:String?`, `whatsapp_phone_number→whatsappPhoneNumber:String?`, `government_id→governmentId:String?`, `has_accepted_terms→hasAcceptedTerms:Boolean?`, `has_signature→hasSignature:Boolean?`, `has_initial→hasInitial:Boolean?`, `is_signature_reusable→isSignatureReusable:Boolean?`, `verification_method→verificationMethod:String?` |
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
| `ContractExternalReference` | `system:String`, `id:String` |
| `DocumentContract` | `document_id→documentId:String`, `status:String`, `resource:String?`, `counterparty_name→counterpartyName:String?`, `counterparty_taxpayer_id→counterpartyTaxpayerId:String?`, `value:BigDecimal?`, `currency:String?`, `start_date→startDate:String?`, `end_date→endDate:String?`, `auto_renew→autoRenew:Boolean?`, `renewal_term_months→renewalTermMonths:Int?`, `notice_period_days→noticePeriodDays:Int?`, `term:Int?`, `owner_id→ownerId:String?`, `metadata:Map<String, String>?`, `external_references→externalReferences:List<ContractExternalReference>?`, `document:DocumentDetails?`, `amendments:List<DocumentContractAmendment>?`, `created_at→createdAt:String?`, `updated_at→updatedAt:String?` |
| `DocumentContractAmendment` | `document_id→documentId:String`, `status:String`, `parent_document_id→parentDocumentId:String`, `relation_type→relationType:String`, `resource:String?`, `document_name→documentName:String?`, `number:Int?`, `document_status→documentStatus:String?`, `apply_error→applyError:String?`, `currency:String?`, `terms:JsonObject?`, `changes:JsonObject?`, `applied_at→appliedAt:String?`, `created_at→createdAt:String?`, `updated_at→updatedAt:String?` |
| `WorkspaceLimits` | `webhook_endpoints→webhookEndpoints:Int?` |
| `ContractRecord` | Sealed interface: `documentId:String`, `status:String`, `updatedAt:String?`; implemented by DocumentContract or DocumentContractAmendment |
| `CostEstimate` | `documents:Int?`, `credits:Double?`, `needs_extra_document→needsExtraDocument:Boolean?`, `extra_document_cost→extraDocumentCost:Double?`, `total_credits→totalCredits:Double?`, `breakdown:List<CostEstimateBreakdownItem>`, `document_balance→documentBalance:Double?`, `credit_balance→creditBalance:Double?`, `has_sufficient_resources→hasSufficientResources:Boolean?`, `blocking_reason→blockingReason:String?`, `message:String?`; resend-cost fields `total→legacyTotal:Double?`, `has_sufficient_credits→legacyHasSufficientCredits:Boolean?` |
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
| `NotificationPreferences` | Required `Boolean` fields: `DocumentCompleted`, `SignerDeclined`, `DocumentCancelled`, `DocumentAboutToExpire`, `DocumentExpired`, `DocumentExpirationReset`, `DocumentProcessingFailed`, `TemplateProcessingFailed`, `SignerWhatsappFailed`; optional `Boolean?`: `ContractRenewalNotice`, `ContractExpiring`, `ContractExpired`, `ContractRenewed` |
| `WebhookSubscription` | `url:String?`, `email:String?`, `events:List<String>`, `is_active→isActive:Boolean`, `updated_at→updatedAt:String?` |
| `WebhookEventTypeInfo` | `id:String`, `description:String?` |
| `WebhookEndpoint` | `id:String`, `name:String?`, `url:String`, `email:String?`, `events:List<String>`, `is_active→isActive:Boolean`, `signing_enabled→signingEnabled:Boolean`, `created_at→createdAt:String?`, `updated_at→updatedAt:String?`, `secret:String?` (one-time creation secret; redacted in `toString()`) |
| `WebhookEndpointSecret` | `secret:String` (`whsec_` + base64 key); `toString()` redacts it |
| `WebhookDispatch` | `resource:String?`, `id:String`, `event:String`, `activity_id→activityId:Long?`, `endpoint_id→endpointId:String?` (null once the endpoint is deleted), `endpoint:String?`, `payload:Map<String,Any>?`, `delivered:Boolean`, `http_status→httpStatus:Int?`, `response_body→responseBody:String?`, `error:String?`, `created_at→createdAt:String?`, `updated_at→updatedAt:String?` |
| `WebhookPayload` | `id:Long?`, `event:String?`, compatibility `type:String?`, `message:String?`, `payload:Map<String,Any>?`, `subject:Map<String,Any>?`, `object→obj:Map<String,Any>?`, `origin:Map<String,Any>?`, `created_at→createdAt:Long?`, `account_id→accountId:String?` |
| `PaginatedResult<T>` | Local `data:List<T>`, `meta:PaginationMeta?` |
| `PaginationMeta` | Local `currentPage:Int?`, `lastPage:Int?`, `perPage:Int?`, `total:Int?` populated from headers |

Statistics default to monthly granularity and 12 zero-filled periods; daily queries return every day
in the requested `YYYY-MM`. Rows are newest-first. Notification counters can exceed
`signature_requests` because a signer notified through multiple channels counts once in each
channel. The verification counters partition `signature_requests` and therefore sum to that total.

### Identity verification models

| Kotlin type | Complete wire fields and Kotlin types |
|---|---|
| `IdentityReviewReason` | `code:String`, `description:String` |
| `IdentityReviewExpected` | `name:String?`, `cpf:String?` |
| `IdentityReviewDocument` | `type:String?`, `name:String?`, `cpf:String?` |
| `IdentityReviewMatches` | `cpf:Boolean?`, `name:Double?`, `birth_date→birthDate:Boolean?` |
| `IdentityReviewCheck` | `name:String`, `result:String`, `score:Double?`, `reasons:List<String>` |
| `IdentityReviewer` | `id:String`, `name:String` |
| `SignerIdentityReview` | `id:String`, `status:String`, `provider:String`, `verification_method→verificationMethod:String`, `reason_codes→reasonCodes:List<IdentityReviewReason>`, `evidence_available→evidenceAvailable:Boolean`, `expected:IdentityReviewExpected?`, `document:IdentityReviewDocument?`, `matches:IdentityReviewMatches?`, `checks:List<IdentityReviewCheck>`, `images:List<String>`, `ruleset_version→rulesetVersion:String?`, `evidence_sha256→evidenceSha256:String?`, `reviewed_by→reviewedBy:IdentityReviewer?`, `reviewed_at→reviewedAt:String?`, `review_note→reviewNote:String?`, `completed_at→completedAt:String?` |
| `SignerIdentityVerification` | `can_start→canStart:Boolean`, `status:String?`, `url:String?`, `expires_at→expiresAt:String?`, `completed_at→completedAt:String?` |
| `IdentityVerificationSession` | `id:String`, `url:String`, `expires_at→expiresAt:String?` |

Hosted capture URLs are credentials; both capture response types redact them in `toString()`.

Additional response fields: DocumentDetails exposes workspace-detail-only `hasContract`; both it and
PublicDocumentInfo expose `verificationCode` and `verificationUrl`; Assignment exposes `documentId` and `createdAt`; Workspace exposes
`limits.webhookEndpoints`; WebhookSubscription and WebhookEndpoint expose `isHeld` and epoch-second
`heldSince`; WebhookEndpoint also exposes `resource`. WebhookDispatch exposes `webhookId`, `attempt`,
`kind` and `willRetry`. `WhatsappNotification.sentAt` remains epoch seconds and accepts both current
ISO-8601 wire dates and older numeric timestamps.

## Constants

- `SdkConstants`: `VERSION`/`USER_AGENT`, production `DEFAULT_BASE_URL`, 30-second
  `DEFAULT_TIMEOUT_MS`, 25 MiB `MAX_UPLOAD_BYTES`, 255-character
  `MAX_DOCUMENT_NAME_LENGTH`, 2-second `DEFAULT_POLL_INTERVAL_MS`, and 120-second
  `DEFAULT_MAX_WAIT_MS`.
- `DocumentArtifact`: `original`, `certificated`, `certificate-page`, `pades`, `bundle`.
- `SignatureType`: `signature`, `initial`.
- `AssignmentMethod`: `virtual`, `collect`.
- `ValidationCode.DIGITAL_CERTIFICATE_REQUIRES_VIRTUAL_ASSIGNMENT`:
  `digital_certificate_requires_virtual_assignment` in `ValidationException.errors["code"]`.
- `VerificationMethod`: `Email`, `Whatsapp`, `DigitalCertificate` (ICP-Brasil A1/A3), `IdDocumentFaceMatchBasic`, plus `ALL`.
- `NotificationMethod`: `Email`, `Whatsapp`, plus `ALL`. Exactly one per signer, paired with the
  verification method.
- `OAuthScope`: `documents:read`, `documents:write`, `templates:read`, `templates:write`,
  `account:read`, `webhooks:write`, `openid`, `profile`, `email`, `offline_access`.
- `OAuthConfig.DEFAULT_AUTHORIZATION_SERVER`: `https://auth.assinafy.com.br`.
- `OAuthChallenge.INSUFFICIENT_SCOPE`: `insufficient_scope`.
- `IdentityVerificationStatus`: `Pending`, `InReview`, `Approved`, `Declined`, `Expired`, `Abandoned`.
- `IdentityReviewDecision`: `Approved`, `Rejected`.
- `IdentityReviewImage`: `document_front`, `document_back`, `document_pdf`, `face`, plus `ALL`.
- `DocumentStatus.PENDING_IDENTITY_REVIEW`: `pending_identity_review`.
- `DocumentStatus.METADATA_READY`: `metadata_ready`, ready for a new assignment.
- `DocumentStatus.CERTIFICATED`: `certificated`.
- `DocumentStatus.READY`: `metadata_ready`, `pending_signature`, `pending_identity_review`, `certificating`, `certificated`.
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
  `template_processed`, `template_processing_failed`, `signer_identity_verified`,
  `signer_identity_verification_failed`, `signer_identity_review_required`,
  `signer_identity_review_approved`, and `signer_identity_review_rejected`. Use `webhooks.listEventTypes()` to accept
  future server events without an SDK update.

Contract constants: `ContractStatus` (draft, active, expired, terminated), `ContractRelationType`
(amendment, renewal, termination), `ContractAmendmentStatus` (pending, applied), and `ContractField`
for explicit null updates. `WebhookEvent.DOCUMENT_CONTRACT_*` supplies all eight contract event IDs.
`TemplateRoleType` supplies Editor/Signer/CopyReceiver; `WebhookAttemptKind` supplies scheduled/probe/manual.

## Envelopes, embedding, and delivery

`client.envelopes` lists, estimates, creates, reads, updates, cancels and deletes envelopes; downloads
ZIPs, pages activities, reminds a signer and adds/removes documents. Upload every PDF and wait for
`metadata_ready` first. Use 1..20 distinct signers and documents, with every signer assigned to at
least one document. A certificate signer signs virtual documents alone on each. Envelope signers
have no sequential steps. `maxTotalCredits` caps spending; `idempotencyKey` is a caller-chosen
`Idempotency-Key` header on creation. Reuse it only for an explicit retry of the same request.
The SDK never automatically replays a mutation. Creation success does not guarantee delivery;
read assignment notification history. Deleting a closed envelope preserves its documents.

`client.embeddedSigning` manages exact HTTPS origins (HTTP only for localhost/127.0.0.1) and mints
single-use sessions for signers created with `embedded=true`. Register an origin on the trusted
backend, create the assignment, then mint a session each time the signer opens the page. Load it
within five minutes in an iframe on an allowed origin; never prefetch or log it. The hosted page
exchanges the token internally. Email/WhatsApp verification still applies. Native clients use a
hosted integration page on an allowed origin. Origin management is unavailable to third-party OAuth.

`SigningUrl.url` is nullable. For Email it opens an invitation-resend page; it contains no signer
access code. WhatsApp-only and embedded signers have no such URL. The delivered invitation supplies
the signing access. `signerDocuments.download` requires that code and permits only `original`.
Use `resendCertifiedCopy` after completion; the API sends only to the original signing contact.
Email allows one resend every two minutes, ten per day; WhatsApp allows one per signature request.
Completion links last seven days, resent links thirty minutes. Download-token methods work without
account credentials, and expired-link HTML is rejected as a download. Closed signing reads expose
`currentSigner`, `isSignerAccessClosed`, and `certifiedCopy`; identity/contact fields may be absent.
`confirmData` sends contact data and explicit terms acceptance, then reads the signer after the
API's empty-array success to preserve the existing return type.

`workspaces.get(accountId, expandSigningFeatures=true)` exposes current method entitlements;
`getUsage` returns plan/balances as exact decimals, with null unlimited quantities.
`getNotificationPolicy` / `updateNotificationPolicy` manage workspace alert policy, and
`users.getPushPreferences` / `updatePushPreferences` manage personal push preferences separately
from email. Maps accept `NotificationEvent` keys; updates merge only supplied values. Third-party
OAuth cannot access these settings. Identity-review email is always delivered even when its policy
flag is false. Owners alone may update workspace policy.

`webhooks.testEndpoint` sends a test delivery and returns its dispatch, including `durationMs`.
`rotateEndpointSecret(endpointId, keepPrevious=true)` returns the new secret and
`previousSecretExpiresAt`; both signatures are emitted for 24 hours. Rotating again replaces the
previous overlap. Save the new secret immediately and switch receivers before that deadline.
`DocumentVerification.certificatedAt` records certification; `verifiedAt` records the check time.

## Complete method payloads

Optional fields depend on state, permissions and expansion. Examples show the full documented
shape; supply your own identifiers and contact values. Binary operations return bytes.

### `assignments.list`

Lists assignments with `GET /assignments` and no request body.

Reads `page`, `per-page` and `sort` from [params]. Sends [accountId] or the configured account
as `accountId`; OAuth can infer its workspace when neither is set. API-key calls need one.
Response `data` is an array of complete [Assignment] values and `X-Pagination-*` headers are
exposed through [PaginatedResult.meta].

Wire operation: `GET /v1/assignments`.

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
    "signers": [{"resource": "signer", "id": "id-placeholder", "full_name": "Example", "email": null, "whatsapp_phone_number": null, "government_id": null, "has_accepted_terms": true, "verification_method": null, "identity_verification_status": null, "notification_methods": ["Email"], "step": 1, "notified": true, "completed": true, "notification_history": [{"event": "signature_request", "status": "sent", "error_code": null, "error_message": null, "sent_at": null, "failed_at": null}]}],
    "copy_receivers": [{"resource": "signer", "id": "id-placeholder", "full_name": "Example", "email": null, "whatsapp_phone_number": null, "government_id": null, "has_accepted_terms": true}],
    "items": [{"id": "id-placeholder", "page": {"id": "id-placeholder", "number": 1, "height": 1, "width": 1, "download_url": "https://example.com/resource"}, "signer": {"resource": "signer", "id": "id-placeholder", "full_name": "Example", "email": null, "whatsapp_phone_number": null, "government_id": null, "has_accepted_terms": true}, "field": {"resource": "field", "id": "id-placeholder", "name": "Example", "type": "text", "regex": null, "is_pre_defined": true, "is_active": true, "is_required": true, "is_standard": true, "is_read_only": true, "is_visible": true}, "display_settings": {"left": 1, "top": 1, "width": 1, "height": 1, "fontFamily": "Arial", "fontSize": 1, "backgroundColor": "#D5EBFF"}, "value": null, "completed": true}],
    "summary": {"signer_count": 1, "completed_count": 1, "signers": [{"resource": "signer", "id": "id-placeholder", "full_name": "Example", "email": null, "whatsapp_phone_number": null, "government_id": null, "has_accepted_terms": true}]},
    "signing_urls": [{"signer_id": "signer-id-placeholder", "url": "https://example.com/resource"}],
    "document_id": "document-placeholder",
    "created_at": "2026-01-01T00:00:00Z"
  }
]
```

@param params Optional one-based page and records-per-page values.
@param accountId Workspace query override; otherwise the configured default is sent when available.
@return Assignment page and optional pagination metadata.
@throws ValidationException if an explicitly supplied [accountId] is blank.

### `assignments.create`

Creates an assignment (`POST /documents/{documentId}/assignments`). The response includes the
per-signer `signing_urls` and a `summary`.

Wire operation: `POST /v1/documents/{documentId}/assignments`

Request body (`application/json`; optional fields may be omitted):
```json
{
  "method": "virtual",
  "signers": [
    {
      "id": "id-placeholder",
      "verification_method": "Email",
      "notification_methods": [
        "Email"
      ],
      "embedded": false,
      "step": 1
    }
  ],
  "entries": [
    {
      "page_id": "example",
      "fields": [
        {
          "signer_id": "example",
          "field_id": "example",
          "display_settings": {
            "left": 69,
            "top": 282,
            "width": 421,
            "height": 45.86,
            "fontFamily": "Arial",
            "fontSize": 22,
            "backgroundColor": "#D5EBFF"
          }
        }
      ]
    }
  ],
  "message": "example",
  "expires_at": "2026-11-11T12:00:00Z",
  "copy_receivers": [
    "example"
  ],
  "max_total_credits": 1.5
}
```

Response `data` inside `{status,message,data}`:
```json
{
  "resource": "assignment",
  "id": "id-placeholder",
  "document_id": "document-id-placeholder",
  "created_at": "2026-06-03T03:54:16Z",
  "sender_email": "person@example.com",
  "method": "virtual",
  "expires_at": null,
  "message": "example",
  "signers": [
    {
      "resource": "signer",
      "id": "id-placeholder",
      "full_name": "Example Person",
      "email": "person@example.com",
      "whatsapp_phone_number": "+5548999990000",
      "government_id": "government-id-placeholder",
      "has_accepted_terms": false,
      "verification_method": "Email",
      "notification_methods": [
        "example"
      ],
      "step": 1,
      "notified": true,
      "identity_verification_status": "InReview",
      "completed": true,
      "notification_history": [
        {
          "event": "signature_request",
          "status": "failed",
          "error_code": "MailboxFull",
          "error_message": "A caixa de entrada do destinatário está cheia. Peça ao signatário para liberar espaço ou use outro e-mail.",
          "sent_at": "2026-07-07T12:00:00Z",
          "failed_at": "2026-07-07T12:05:00Z"
        }
      ]
    }
  ],
  "copy_receivers": [
    {}
  ],
  "items": [
    {
      "id": "example",
      "page": {
        "id": "id-placeholder",
        "number": 1,
        "height": 2100,
        "width": 1275,
        "download_url": "https://example.com/resource"
      },
      "signer": {},
      "field": {},
      "display_settings": null,
      "value": null,
      "completed": true
    }
  ],
  "summary": {
    "signer_count": 1,
    "completed_count": 1,
    "signers": [
      {}
    ]
  },
  "signing_urls": [
    {
      "signer_id": "example",
      "url": "https://example.com/resource",
      "embedded": false
    }
  ]
}
```
@param documentId Stable document identifier placed in the path.
@param request Assignment method, signers, optional field placements, and delivery settings.
@return Created assignment, including signer state, summary, items, and generated signing URLs.
@throws ValidationException for blank IDs, missing required signers/entries, a collect signer
without a field, unsupported methods or channels, invalid signing steps or field placement, or a digital-certificate
signer on a non-virtual assignment; that combination exposes
[com.assinafy.sdk.ValidationCode.DIGITAL_CERTIFICATE_REQUIRES_VIRTUAL_ASSIGNMENT].

### `assignments.estimateCost`

Estimates assignment cost with `POST /documents/{documentId}/assignments/estimate-cost`.

Request body carries only what affects pricing — signer IDs, steps, messages, expiration and
copy receivers are deliberately omitted:
`blocking_reason` is one of `PendingPayment`, `InsufficientDocuments`, or
`InsufficientCredits`. A digital-certificate signer adds a `SignatureDigitalCertificate`
breakdown line worth 0.5 credits on top of its notification.

Wire operation: `POST /v1/documents/{documentId}/assignments/estimate-cost`.

Request body (`application/json`; optional members may be omitted):
```json
{
  "method": "collect",
  "signers": [{"verification_method": "Whatsapp", "notification_methods": ["Whatsapp"]}],
  "entries": [{"page_id": "page-placeholder", "fields": [{"signer_id": "signer-placeholder", "field_id": "field-placeholder", "display_settings": {"left": 72, "top": 640, "width": 180, "height": 40, "fontSize": 12}}]}]
}
```

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
{
  "documents": 1,
  "credits": 0.45,
  "needs_extra_document": true,
  "extra_document_cost": 1,
  "total_credits": 1.45,
  "breakdown": [{"code": "NotificationWhatsapp", "name": "WhatsApp notification", "cost": 0.45, "quantity": 1, "unit_cost": 0.45}],
  "document_balance": 0,
  "credit_balance": 10,
  "has_sufficient_resources": true,
  "blocking_reason": null,
  "message": null
}
```

@param documentId Stable document identifier placed in the path.
@param request Proposed method, signer channels, and optional collect placements.
@return Credit/document estimate and resource-sufficiency result.
@throws ValidationException for blank IDs, missing required signers/entries, unsupported methods
or channels, invalid collect requirements, or a digital-certificate signer on a non-virtual
assignment; that combination exposes
[com.assinafy.sdk.ValidationCode.DIGITAL_CERTIFICATE_REQUIRES_VIRTUAL_ASSIGNMENT].

### `assignments.resetExpiration`

Sets a new expiration for an assignment, or removes it with `null` (the assignment then no
longer expires). Not allowed once the document is closed.

Wire request: `PUT /documents/{documentId}/assignments/{assignmentId}/reset-expiration` with
`{"expires_at":"..."}`, or `{"expires_at":null}` to clear; `expires_at` is required and always
sent. The response `data` is the complete updated [Assignment].

Wire operation: `PUT /v1/documents/{documentId}/assignments/{assignmentId}/reset-expiration`.

Request body (`application/json`; `expires_at` required, nullable):
```json
{
  "expires_at": "2026-12-31T23:59:59Z"
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
  "signers": [{"resource": "signer", "id": "id-placeholder", "full_name": "Example", "email": null, "whatsapp_phone_number": null, "government_id": null, "has_accepted_terms": true, "verification_method": null, "identity_verification_status": null, "notification_methods": ["Email"], "step": 1, "notified": true, "completed": true, "notification_history": [{"event": "signature_request", "status": "sent", "error_code": null, "error_message": null, "sent_at": null, "failed_at": null}]}],
  "copy_receivers": [{"resource": "signer", "id": "id-placeholder", "full_name": "Example", "email": null, "whatsapp_phone_number": null, "government_id": null, "has_accepted_terms": true}],
  "items": [{"id": "id-placeholder", "page": {"id": "id-placeholder", "number": 1, "height": 1, "width": 1, "download_url": "https://example.com/resource"}, "signer": {"resource": "signer", "id": "id-placeholder", "full_name": "Example", "email": null, "whatsapp_phone_number": null, "government_id": null, "has_accepted_terms": true}, "field": {"resource": "field", "id": "id-placeholder", "name": "Example", "type": "text", "regex": null, "is_pre_defined": true, "is_active": true, "is_required": true, "is_standard": true, "is_read_only": true, "is_visible": true}, "display_settings": {"left": 1, "top": 1, "width": 1, "height": 1, "fontFamily": "Arial", "fontSize": 1, "backgroundColor": "#D5EBFF"}, "value": null, "completed": true}],
  "summary": {"signer_count": 1, "completed_count": 1, "signers": [{"resource": "signer", "id": "id-placeholder", "full_name": "Example", "email": null, "whatsapp_phone_number": null, "government_id": null, "has_accepted_terms": true}]},
  "signing_urls": [{"signer_id": "signer-id-placeholder", "url": "https://example.com/resource"}],
  "document_id": "document-placeholder",
  "created_at": "2026-01-01T00:00:00Z"
}
```

@param documentId Stable document identifier.
@param assignmentId Stable assignment identifier.
@param expiresAt Replacement ISO-8601 expiration at least one hour ahead, or `null` to remove
the expiration (sent as `{"expires_at":null}`; the key is always present).
@return Complete updated assignment.
@throws ValidationException if either identifier is blank, or [expiresAt] is blank.

### `assignments.decline`

Declines an assignment through the public signer route.

Sends `PUT /documents/{documentId}/assignments/{assignmentId}/reject`, the access code only as
`signer-access-code` in the query, and `{"decline_reason":"..."}` as the complete JSON body.
A successful empty-data envelope is returned as [Unit].

Wire operation: `PUT /v1/documents/{documentId}/assignments/{assignmentId}/reject`.

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

@param documentId Stable document identifier.
@param assignmentId Stable assignment identifier.
@param signerAccessCode One-time signer code sent only in the query.
@param reason Required free-text decline reason.
@throws ValidationException if any identifier or [reason] is blank.

### `assignments.listWhatsappNotifications`

Lists WhatsApp messages dispatched for an assignment.

Wire request: `GET /documents/{documentId}/assignments/{assignmentId}/whatsapp-notifications`
with no query or body. Response `data` is an array containing render text, destination,
signer, send time, and buttons for each [WhatsappNotification].

Wire operation: `GET /v1/documents/{documentId}/assignments/{assignmentId}/whatsapp-notifications`.

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
[
  {
    "sent_at": "2026-10-28T12:00:00Z",
    "header": "example",
    "body": "example",
    "buttons": [{"text": "example"}],
    "phone_number": "+15555550100",
    "signer_id": "signer-id-placeholder"
  }
]
```

@param documentId Stable document identifier.
@param assignmentId Stable assignment identifier.
@return Rendered WhatsApp notification history.
@throws ValidationException if either identifier is blank.

### `assignments.resendNotification`

Resends one signer's signature notification.

Sends a `PUT` to
`/documents/{documentId}/assignments/{assignmentId}/signers/{signerId}/resend`. The current
OpenAPI request has no body; [channel] opts into the deployed compatibility body
`{"channel":"email"}` or `{"channel":"whatsapp"}`. Response `data` is
[ResendEmailResponse]: delivery acceptance plus document and signer IDs.

Wire operation: `PUT /v1/documents/{documentId}/assignments/{assignmentId}/signers/{signerId}/resend`.

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
{
  "is_sent": true,
  "document_id": "document-id-placeholder",
  "signer_id": "signer-id-placeholder"
}
```

@param documentId Stable document identifier.
@param assignmentId Stable assignment identifier.
@param signerId Stable signer identifier.
@param channel Optional deployed-service notification channel; omit for the OpenAPI request.
@return Resend acceptance and related IDs.
@throws ValidationException if any identifier is blank.

### `assignments.estimateResendCost`

Estimates the cost of resending one signer notification.

Sends an empty-body `POST` to
`/documents/{documentId}/assignments/{assignmentId}/signers/{signerId}/estimate-resend-cost`.
Response `data` is the complete [CostEstimate] payload.

Wire operation: `POST /v1/documents/{documentId}/assignments/{assignmentId}/signers/{signerId}/estimate-resend-cost`.

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
{
  "total": 0.45,
  "breakdown": [{"code": "NotificationWhatsappResend", "name": "WhatsApp notification resend", "cost": 0.45}],
  "credit_balance": 10,
  "has_sufficient_credits": true
}
```

@param documentId Stable document identifier.
@param assignmentId Stable assignment identifier.
@param signerId Stable signer identifier.
@return Credit cost, balances, sufficiency, and any itemized breakdown.
@throws ValidationException if any identifier is blank.

### `assignments.getIdentityVerification`

Reads an account-facing identity review without fetching evidence images.

InReview permits signing to continue, but certification waits for the account decision.
Evidence is a summary; images are fetched separately and are available only while the review is open.

Wire operation: `GET /v1/documents/{documentId}/assignments/{assignmentId}/signers/{signerId}/identity-verification`.

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`):
```json
{
  "id": "verification-placeholder",
  "status": "InReview",
  "provider": "AssinafyVerify",
  "verification_method": "IdDocumentFaceMatchBasic",
  "reason_codes": [
    {
      "code": "NAME_MISMATCH",
      "description": "Registered and captured names differ"
    }
  ],
  "evidence_available": true,
  "expected": {
    "name": "Example Signer",
    "cpf": "00000000000"
  },
  "document": {
    "type": "CNH",
    "name": "Example Signer",
    "cpf": "00000000000"
  },
  "matches": {
    "cpf": true,
    "name": 0.52,
    "birth_date": null
  },
  "checks": [
    {
      "name": "name",
      "result": "review",
      "score": 0.52,
      "reasons": [
        "NAME_MISMATCH"
      ]
    }
  ],
  "images": [
    "document_front",
    "document_back",
    "face"
  ],
  "ruleset_version": "example",
  "evidence_sha256": "digest-placeholder",
  "reviewed_by": {
    "id": "reviewer-placeholder",
    "name": "Example Reviewer"
  },
  "reviewed_at": null,
  "review_note": null,
  "completed_at": null
}
```

@param documentId Document identifier.
@param assignmentId Assignment identifier.
@param signerId Signer identifier within the assignment.

### `assignments.downloadIdentityVerificationImage`

Downloads a reviewer evidence image or digital-document PDF.

The API sends Cache-Control: no-store. Do not cache the sensitive evidence.
Images return 404 after a review decision; the SDK propagates [com.assinafy.sdk.exceptions.ApiException].

Wire operation: `GET /v1/documents/{documentId}/assignments/{assignmentId}/signers/{signerId}/identity-verification/images/{name}`.

Request body: none.

Response 200 payload: raw `image/jpeg`, `image/png`, or `application/pdf` bytes; no JSON envelope.

@param documentId Document identifier.
@param assignmentId Assignment identifier.
@param signerId Signer identifier within the assignment.
@param name One of [IdentityReviewImage], as listed in the open review.

### `assignments.reviewIdentityVerification`

Approves or rejects an open account identity review.

Approval permits certification after every signature and review completes. Rejection cancels
the document and sends the cancellation notice to signers and copy receivers.
The document must still be pending_signature or pending_identity_review.

Wire operation: `POST /v1/documents/{documentId}/assignments/{assignmentId}/signers/{signerId}/identity-verification/review`.

Request body: (`application/json`):
```json
{
  "decision": "Approved",
  "note": "Identity checked by the account reviewer"
}
```

Response 200 `data` payload (inside `{status,message,data}`):
```json
{
  "id": "verification-placeholder",
  "status": "Approved",
  "provider": "AssinafyVerify",
  "verification_method": "IdDocumentFaceMatchBasic",
  "reason_codes": [
    {
      "code": "NAME_MISMATCH",
      "description": "Registered and captured names differ"
    }
  ],
  "evidence_available": true,
  "expected": {
    "name": "Example Signer",
    "cpf": "00000000000"
  },
  "document": {
    "type": "CNH",
    "name": "Example Signer",
    "cpf": "00000000000"
  },
  "matches": {
    "cpf": true,
    "name": 0.52,
    "birth_date": null
  },
  "checks": [
    {
      "name": "name",
      "result": "review",
      "score": 0.52,
      "reasons": [
        "NAME_MISMATCH"
      ]
    }
  ],
  "images": [],
  "ruleset_version": "example",
  "evidence_sha256": "digest-placeholder",
  "reviewed_by": {
    "id": "reviewer-placeholder",
    "name": "Example Reviewer"
  },
  "reviewed_at": "2026-10-16T12:00:00Z",
  "review_note": "Identity checked by the account reviewer",
  "completed_at": "2026-10-16T12:00:00Z"
}
```

@param documentId Document identifier.
@param assignmentId Assignment identifier.
@param signerId Signer identifier within the assignment.
@param decision [IdentityReviewDecision.APPROVED] or [IdentityReviewDecision.REJECTED].
@param note Optional internal note, at most 2000 Unicode characters; never shown to the signer.

### `authentication.login`

Authenticates with `POST /login`.

Request body: `{"email":"user@example.com","password":"secret"}`.

Wire operation: `POST /v1/login`.

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
  "user": {"id": "id-placeholder", "name": "Example", "email": "person@example.com", "telephone": null, "government_id": null, "is_email_verified": true, "has_accepted_terms": true, "created_at": "2026-01-01T00:00:00Z", "to_be_deleted_at": null},
  "accounts": [{"id": "id-placeholder", "name": "Example", "roles": ["owner"], "is_delete_allowed": true, "created_at": "2026-01-01T00:00:00Z"}]
}
```

@param request Required email and password payload.
@return JWT, authenticated user, and accessible accounts.
When the user has two-factor authentication enabled, `data` carries `{"mfa_token": "..."}`
instead and the SDK throws [MfaRequiredException]; finish with [verifyMfa].

@throws ValidationException when the email is invalid or the password is blank.
@throws MfaRequiredException when a second factor is required.

### `authentication.requestPasswordReset`

Sends a reset message with `PUT /authentication/request-password-reset`.

Request body: `{"email":"user@example.com"}`.
Response `data`: `{"email":"user@example.com"}`.

Wire operation: `PUT /v1/authentication/request-password-reset`.

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

@param request Email address that receives the reset message.
@return The email address accepted by the API.
@throws ValidationException when the email is invalid.

### `authentication.resetPassword`

Completes a reset with `PUT /authentication/reset-password`.

Request body:
`{"email":"user@example.com","new_password":"new-secret","token":"emailed-token"}`.
The current schema permits omitting `token`. Response `data`: `{"email":"user@example.com"}`.

Wire operation: `PUT /v1/authentication/reset-password`.

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

@param request Email, new password, and optional emailed reset token.
@return The email address whose password was reset.
@throws ValidationException when the email is invalid, the new password is blank, or a supplied token is blank.

### `authentication.changePassword`

Changes the authenticated user's password with `PUT /authentication/change-password`.

Request body:
`{"email":"user@example.com","password":"old-secret","new_password":"new-secret"}`.
Response `data`: `{"email":"user@example.com"}`.

Wire operation: `PUT /v1/authentication/change-password`.

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

@param request Email, current password, and replacement password.
@return The email address whose password was changed.
@throws ValidationException when the email is invalid or either password is blank.

### `authentication.socialLogin`

Exchanges a provider token with `POST /authentication/social-login`.

Request body:
`{"provider":"google","token":"provider-token","has_accepted_terms":true}`.
Response `data` has the same complete `access_token`, `user`, and `accounts` shape documented by [login].

Wire operation: `POST /v1/authentication/social-login`.

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
  "user": {"id": "id-placeholder", "name": "Example", "email": "person@example.com", "telephone": null, "government_id": null, "is_email_verified": true, "has_accepted_terms": true, "created_at": "2026-01-01T00:00:00Z", "to_be_deleted_at": null},
  "accounts": [{"id": "id-placeholder", "name": "Example", "roles": ["owner"], "is_delete_allowed": true, "created_at": "2026-01-01T00:00:00Z"}]
}
```

@param request Google provider token and terms acceptance.
@return JWT, authenticated user, and accessible accounts.
@throws ValidationException when the provider token is blank.
@throws MfaRequiredException when a second factor is required.

### `authentication.linkSocialLogin`

Links a provider identity with `POST /auth/link-social-login`.

Request body: `{"provider":"google","token":"provider-token"}`.
Response body is the standard success envelope with no `data` payload.

Wire operation: `POST /v1/auth/link-social-login`.

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

@param request Google provider and provider-issued token to link.
@throws ValidationException when the provider token is blank.

### `authentication.getApiKey`

Retrieves the masked personal key with `GET /users/api-keys`.

Request body: none. Response `data`: `{"api_key":"********suffix"}` or `{"api_key":null}`.
A legacy top-level `data: null` response is normalized to `null`.

Wire operation: `GET /v1/users/api-keys`.

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
{
  "api_key": null
}
```

@return Masked key payload, or `null` when the API returns no key payload.

### `authentication.createApiKey`

Creates or rotates the personal key with `POST /users/api-keys`.

Request body: `{"password":"secret"}`. Response `data`: `{"api_key":"full-key-shown-once"}`.
Generating a new key invalidates the previous key.

Wire operation: `POST /v1/users/api-keys`.

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

@param request Current password used to authorize key generation.
@return Newly generated full API key; [ApiKeyResponse.apiKey] may be `null` only if the server returns it so.
@throws ValidationException when the password is blank.

### `authentication.deleteApiKey`

Revokes the personal key with `DELETE /users/api-keys`.

Request body: none. Response `data` is an empty JSON array.
Wire operation: `DELETE /v1/users/api-keys`.

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
[]
```

### `authentication.verifyMfa`

Completes a two-factor login by exchanging the challenge from [MfaRequiredException] for a
session. The challenge is single-use and expires five minutes after login; an expired, used
or over-tried challenge answers `401`.

Wire operation: `POST /v1/authentication/mfa/verify` (no credential).

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
  "user": {"id": "id-placeholder", "name": "Example", "email": "person@example.com", "telephone": null, "government_id": null, "is_email_verified": true, "has_accepted_terms": true, "created_at": "2026-01-01T00:00:00Z", "to_be_deleted_at": null},
  "accounts": [{"id": "id-placeholder", "name": "Example", "roles": ["owner"], "is_delete_allowed": true, "created_at": "2026-01-01T00:00:00Z"}]
}
```

@param request Challenge token and authenticator or recovery code.
@return JWT, authenticated user, and accessible accounts.
@throws ValidationException when either value is blank.

### `authentication.listMfaMethods`

Lists the authenticated user's two-factor methods and remaining recovery codes.

Wire operation: `GET /v1/users/self/mfa`.

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

@return Enrolled methods and the recovery-code balance.

### `authentication.startTotpEnrollment`

Starts authenticator-app enrollment. The returned secret is shown only once; two-factor
authentication stays off until [confirmTotpEnrollment] succeeds.

Wire operation: `POST /v1/users/self/mfa/totp`.

Request body (`application/json`; `label` is optional):
```json
{
  "label": "Phone"
}
```

Response 200 `data` payload (inside `{status,message,data}`):
```json
{
  "id": "id-placeholder",
  "secret": "<redacted-value>",
  "provisioning_uri": "otpauth://totp/Assinafy:person%40example.com?secret=<redacted-value>&issuer=Assinafy"
}
```

@param label Optional name that tells devices apart.
@return Pending method id, shared secret and provisioning URI for a QR code.

### `authentication.confirmTotpEnrollment`

Activates a pending authenticator method with one live code from the new device. From then on
every login needs a second factor. Replacing an already confirmed method also needs
re-authentication through `password` or `reauth_code`; first-time enrollment needs neither.

Wire operation: `PUT /v1/users/self/mfa/totp/confirm`.

Request body (`application/json`; `password` and `reauth_code` are optional):
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

@param request Pending method id, new-device code and optional re-authentication.
@return Recovery codes to show the user once.
@throws ValidationException when the id or code is blank.

### `authentication.regenerateRecoveryCodes`

Issues ten fresh recovery codes and invalidates the previous set.

Wire operation: `POST /v1/users/self/mfa/recovery-codes`.

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

@param request Current password, authenticator code or existing recovery code (consumed).
@return The new recovery codes.
@throws ValidationException when neither proof is supplied.

### `authentication.removeMfaMethod`

Removes an enrolled method. Removing the last one also discards the recovery codes.

Wire operation: `DELETE /v1/users/self/mfa/{methodId}`.

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

@param methodId Method identifier from [listMfaMethods].
@param request Current password, authenticator code or existing recovery code (consumed).
@return Whether two-factor authentication remains enabled.
@throws ValidationException when the id is blank or neither proof is supplied.

### `contracts.list`

Lists contracts, most recently updated first by default.

Filters include status (comma-separated), counterparty, search, end_date_from/to, ending_within_days,
external_system/id, sort, expand=document, page and per-page. X-Pagination-* headers supply metadata.
For reconciliation, page by updated_at until records predate the last sync.

Wire operation: `GET /v1/accounts/{accountId}/contracts`.

Request body: none.

Response 200 `data`, with optional document expansion (inside `{status,message,data}`):
```json
[
  {
    "resource": "document_contract",
    "document_id": "document-placeholder",
    "status": "active",
    "counterparty_name": "Acme Ltda",
    "counterparty_taxpayer_id": "11222333000181",
    "value": 120000.5,
    "currency": "BRL",
    "start_date": "2026-11-01",
    "end_date": "2027-10-31",
    "auto_renew": true,
    "renewal_term_months": 12,
    "notice_period_days": 30,
    "term": 1,
    "owner_id": "owner-placeholder",
    "metadata": {
      "crm_stage": "won"
    },
    "external_references": [
      {
        "system": "hubspot",
        "id": "98765"
      }
    ],
    "created_at": "2026-10-23T12:00:00Z",
    "updated_at": "2026-10-23T12:00:00Z",
    "document": {
      "id": "document-placeholder",
      "account_id": "document-placeholder",
      "template_id": null,
      "name": "Contrato de prestação de serviços.pdf",
      "status": "certificated",
      "artifacts": {
        "original": "https://example.com/resource"
      },
      "is_closed": true,
      "signing_url": "https://example.com/resource",
      "verification_code": "document-placeholder",
      "verification_url": "https://example.com/resource",
      "decline_reason": null,
      "declined_by": null,
      "tags": [],
      "created_at": "2026-10-20T14:00:00Z",
      "updated_at": "2026-10-22T09:00:00Z"
    }
  }
]
```

@param params Validated filters and optional document expansion.
@param accountId Workspace override; defaults to the configured workspace.
@return Contract page and pagination headers.

### `contracts.get`

Reads a document's own contract terms or its amendment link.

The concrete ContractRecord type distinguishes terms from an instrument. A missing contract returns
HTTP 404; reads remain available when the workspace plan lapses.

Wire operation: `GET /v1/accounts/{accountId}/documents/{documentId}/contract`.

Request body: none.

Response 200 `data` for a root contract (inside `{status,message,data}`):
```json
{
  "resource": "document_contract",
  "document_id": "document-placeholder",
  "status": "active",
  "counterparty_name": "Acme Ltda",
  "counterparty_taxpayer_id": "11222333000181",
  "value": 130000,
  "currency": "BRL",
  "start_date": "2026-11-01",
  "end_date": "2028-06-30",
  "auto_renew": true,
  "renewal_term_months": 12,
  "notice_period_days": 30,
  "term": 2,
  "owner_id": "owner-placeholder",
  "metadata": {
    "crm_stage": "won"
  },
  "external_references": [
    {
      "system": "hubspot",
      "id": "98765"
    }
  ],
  "created_at": "2026-10-23T12:00:00Z",
  "updated_at": "2027-05-12T18:20:00Z",
  "amendments": [
    {
      "document_id": "document-placeholder",
      "document_name": "Aditivo 1 - Contrato de prestação de serviços.pdf",
      "parent_document_id": "document-placeholder",
      "relation_type": "amendment",
      "number": 1,
      "status": "applied",
      "document_status": "certificated",
      "apply_error": null,
      "currency": "BRL",
      "terms": {
        "value": 130000,
        "end_date": "2028-06-30"
      },
      "changes": {
        "value": {
          "from": 120000.5,
          "to": 130000
        },
        "end_date": {
          "from": "2027-10-31",
          "to": "2028-06-30"
        },
        "term": {
          "from": 1,
          "to": 2
        }
      },
      "applied_at": "2027-05-12T18:20:00Z",
      "created_at": "2027-05-10T15:00:00Z",
      "updated_at": "2027-05-12T18:20:00Z"
    }
  ]
}
```

Alternative response 200 `data` for an instrument (inside `{status,message,data}`):
```json
{
  "resource": "document_contract_amendment",
  "document_id": "document-placeholder",
  "document_name": "Aditivo 1 - Contrato de prestação de serviços.pdf",
  "parent_document_id": "document-placeholder",
  "relation_type": "amendment",
  "number": null,
  "status": "pending",
  "document_status": "pending_signature",
  "apply_error": null,
  "currency": "BRL",
  "terms": {
    "value": 130000,
    "end_date": "2028-06-30"
  },
  "changes": {
    "value": {
      "from": 120000.5,
      "to": 130000
    },
    "end_date": {
      "from": "2027-10-31",
      "to": "2028-06-30"
    },
    "term": {
      "from": 1,
      "to": 2
    }
  },
  "applied_at": null,
  "created_at": "2027-05-10T15:00:00Z",
  "updated_at": "2027-05-10T15:00:00Z"
}
```

@param documentId Contract or instrument document identifier.
@param accountId Workspace override.
@return DocumentContract or DocumentContractAmendment.

### `contracts.update`

Creates terms or changes only the supplied attributes.

Use clearFields for explicit nulls and externalReferences for whole-list replacement. A pending
instrument null removes that term from its delta. Send parent_document_id and relation_type to link;
clear parent_document_id to unlink a pending instrument. Applied instruments cannot change.
An amendment may change the value, currency, dates, auto-renew, renewal/notice periods, counterparty
and metadata; a renewal only end_date/value; a termination only end_date. The API applies signed
instruments in certification order. Send the last-read updated_at to refuse stale writes with 409.
Link request example: {"parent_document_id":"parent-placeholder","relation_type":"amendment","value":130000}.

Wire operation: `PUT /v1/accounts/{accountId}/documents/{documentId}/contract`.

Request body (`application/json`; omit unchanged fields):
```json
{
  "status": "active",
  "counterparty_name": "Acme Ltda",
  "counterparty_taxpayer_id": "11.222.333/0001-81",
  "value": 120000.5,
  "currency": "BRL",
  "start_date": "2026-11-01",
  "end_date": "2027-10-31",
  "auto_renew": true,
  "renewal_term_months": 12,
  "notice_period_days": 30,
  "owner_id": "owner-placeholder",
  "metadata": {
    "crm_stage": "won"
  },
  "external_references": [
    {
      "system": "hubspot",
      "id": "98765"
    }
  ],
  "updated_at": "2026-10-23T12:00:00Z"
}
```

Response 200 `data` for contract terms (inside `{status,message,data}`):
```json
{
  "resource": "document_contract",
  "document_id": "document-placeholder",
  "status": "active",
  "counterparty_name": "Acme Ltda",
  "counterparty_taxpayer_id": "11222333000181",
  "value": 120000.5,
  "currency": "BRL",
  "start_date": "2026-11-01",
  "end_date": "2027-10-31",
  "auto_renew": true,
  "renewal_term_months": 12,
  "notice_period_days": 30,
  "term": 1,
  "owner_id": "owner-placeholder",
  "metadata": {
    "crm_stage": "won"
  },
  "external_references": [
    {
      "system": "hubspot",
      "id": "98765"
    }
  ],
  "created_at": "2026-10-23T12:00:00Z",
  "updated_at": "2026-10-23T12:00:00Z"
}
```

Alternative response 200 `data` for an instrument (inside `{status,message,data}`):
```json
{
  "resource": "document_contract_amendment",
  "document_id": "document-placeholder",
  "document_name": "Aditivo 1 - Contrato de prestação de serviços.pdf",
  "parent_document_id": "document-placeholder",
  "relation_type": "amendment",
  "number": null,
  "status": "pending",
  "document_status": "pending_signature",
  "apply_error": null,
  "currency": "BRL",
  "terms": {
    "value": 130000,
    "end_date": "2028-06-30"
  },
  "changes": {
    "value": {
      "from": 120000.5,
      "to": 130000
    },
    "end_date": {
      "from": "2027-10-31",
      "to": "2028-06-30"
    },
    "term": {
      "from": 1,
      "to": 2
    }
  },
  "applied_at": null,
  "created_at": "2027-05-10T15:00:00Z",
  "updated_at": "2027-05-10T15:00:00Z"
}
```

@param documentId Existing document identifier.
@param request Partial terms, explicit clears or instrument link/delta.
@param accountId Workspace override.
@return Saved contract or instrument; no signing operation is performed.
@throws ValidationException for an empty update, conflicting clears or invalid field values.

### `contracts.related`

Reads the root contract and every linked instrument from any document in its family.

Applied instruments are ordered by number, followed by pending instruments. A missing family returns 404.

Wire operation: `GET /v1/accounts/{accountId}/documents/{documentId}/contract/related`.

Request body: none.

Response 200 `data` (inside `{status,message,data}`):
```json
{
  "resource": "document_contract",
  "document_id": "document-placeholder",
  "status": "active",
  "counterparty_name": "Acme Ltda",
  "counterparty_taxpayer_id": "11222333000181",
  "value": 130000,
  "currency": "BRL",
  "start_date": "2026-11-01",
  "end_date": "2028-06-30",
  "auto_renew": true,
  "renewal_term_months": 12,
  "notice_period_days": 30,
  "term": 2,
  "owner_id": "owner-placeholder",
  "metadata": {
    "crm_stage": "won"
  },
  "external_references": [
    {
      "system": "hubspot",
      "id": "98765"
    }
  ],
  "created_at": "2026-10-23T12:00:00Z",
  "updated_at": "2027-05-12T18:20:00Z",
  "amendments": [
    {
      "document_id": "document-placeholder",
      "document_name": "Aditivo 1 - Contrato de prestação de serviços.pdf",
      "parent_document_id": "document-placeholder",
      "relation_type": "amendment",
      "number": 1,
      "status": "applied",
      "document_status": "certificated",
      "apply_error": null,
      "currency": "BRL",
      "terms": {
        "value": 130000,
        "end_date": "2028-06-30"
      },
      "changes": {
        "value": {
          "from": 120000.5,
          "to": 130000
        },
        "end_date": {
          "from": "2027-10-31",
          "to": "2028-06-30"
        },
        "term": {
          "from": 1,
          "to": 2
        }
      },
      "applied_at": "2027-05-12T18:20:00Z",
      "created_at": "2027-05-10T15:00:00Z",
      "updated_at": "2027-05-12T18:20:00Z"
    }
  ]
}
```

@param documentId Root or instrument identifier.
@param accountId Workspace override.
@return Root contract with amendments.

### `contracts.renew`

Starts the next term and reopens an expired contract.

Supply the current term or an explicit end date later than the current one. With no end date the
server advances by renewal_term_months (12 when unset), until the date is in the future. Repeated
preconditions are refused, so a renewal cannot advance twice. Terminated contracts cannot renew.
Omit value to retain it; clearValue sends null. A stale updated_at returns 409.

Wire operation: `POST /v1/accounts/{accountId}/documents/{documentId}/contract/renew`.

Request body (`application/json`; omit unchanged fields):
```json
{
  "term": 1,
  "end_date": "2029-06-30",
  "value": 130000,
  "updated_at": "2026-10-23T12:00:00Z"
}
```

Response 200 `data` (inside `{status,message,data}`):
```json
{
  "resource": "document_contract",
  "document_id": "document-placeholder",
  "status": "active",
  "counterparty_name": "Acme Ltda",
  "counterparty_taxpayer_id": "11222333000181",
  "value": 130000,
  "currency": "BRL",
  "start_date": "2026-11-01",
  "end_date": "2029-06-30",
  "auto_renew": true,
  "renewal_term_months": 12,
  "notice_period_days": 30,
  "term": 2,
  "owner_id": "owner-placeholder",
  "metadata": {
    "crm_stage": "won"
  },
  "external_references": [
    {
      "system": "hubspot",
      "id": "98765"
    }
  ],
  "created_at": "2026-10-23T12:00:00Z",
  "updated_at": "2027-10-15T09:30:00Z"
}
```

@param documentId Root contract document identifier.
@param request Renewal preconditions and optional value.
@param accountId Workspace override.
@return Contract after renewal.

### `contracts.terminate`

Closes a contract and stops its lifecycle alerts.

The reason is recorded in document_contract_terminated. The server strips HTML. A contract already
terminated is refused; stale updated_at returns 409. The signed document itself is preserved.

Wire operation: `POST /v1/accounts/{accountId}/documents/{documentId}/contract/terminate`.

Request body (`application/json`; omit unchanged fields):
```json
{
  "reason": "Supplier replaced",
  "updated_at": "2026-10-23T12:00:00Z"
}
```

Response 200 `data` (inside `{status,message,data}`):
```json
{
  "resource": "document_contract",
  "document_id": "document-placeholder",
  "status": "terminated",
  "counterparty_name": "Acme Ltda",
  "counterparty_taxpayer_id": "11222333000181",
  "value": 120000.5,
  "currency": "BRL",
  "start_date": "2026-11-01",
  "end_date": "2027-10-31",
  "auto_renew": true,
  "renewal_term_months": 12,
  "notice_period_days": 30,
  "term": 1,
  "owner_id": "owner-placeholder",
  "metadata": {
    "crm_stage": "won"
  },
  "external_references": [
    {
      "system": "hubspot",
      "id": "98765"
    }
  ],
  "created_at": "2026-10-23T12:00:00Z",
  "updated_at": "2027-03-02T14:00:00Z"
}
```

@param documentId Root contract document identifier.
@param request Optional reason and version precondition.
@param accountId Workspace override.
@return Terminated contract.

### `documents.upload`

Uploads a PDF (`POST /accounts/{accountId}/documents`, multipart). Validated locally: must be a
non-empty `.pdf` ≤ 25 MB. The service accepts at most 2,000 PDF pages.

Request body: `multipart/form-data` with one `file` part. When [metadata] is supplied the
request also carries `name` and a JSON-string `metadata` part, a deployed-service extension
beyond the OpenAPI `file`-only body. Response `data` is the document payload documented on this class, in
`uploaded` or `metadata_processing` status with `assignment` and `pages` not yet populated.

The current OpenAPI request contains only the `file` part. Supplying [metadata] opts into the
legacy extension that also sends `name` and `metadata` form fields.

Wire operation: `POST /v1/accounts/{accountId}/documents`.

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
  "status": "uploaded",
  "artifacts": {},
  "is_closed": false,
  "signing_url": "https://example.com/resource",
  "decline_reason": null,
  "declined_by": {"resource": "signer", "id": "id-placeholder", "full_name": "Example", "email": null, "whatsapp_phone_number": null, "government_id": null, "has_accepted_terms": true},
  "tags": [{"id": "id-placeholder", "name": "Example"}],
  "pages": [{"id": "id-placeholder", "number": 1, "height": 1, "width": 1, "download_url": "https://example.com/resource"}],
  "created_at": "2026-01-01T00:00:00Z",
  "updated_at": "2026-01-01T00:00:00Z",
  "verification_code": null,
  "verification_url": null
}
```

@param fileData Complete PDF bytes, beginning with the `%PDF-` signature.
@param fileName Non-blank `.pdf` multipart file name.
@param metadata Optional legacy metadata serialized as a JSON string part.
@param accountId Account override; otherwise the client's default account is used.
@return Uploaded document identity and initial processing state.
@throws com.assinafy.sdk.exceptions.ValidationException on a non-PDF, empty, or oversized file.

### `documents.list`

Lists documents (`GET /accounts/{accountId}/documents`).

Request body: none. Query: `status`, `method`, `search`, `tags`, `sort`, `page`, `per-page`.
Response `data` is an array of the document payload documented on this class, and `X-Pagination-*` headers are
exposed through [PaginatedResult.meta].

Wire operation: `GET /v1/accounts/{accountId}/documents`.

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
    "status": "certificated",
    "artifacts": {},
    "is_closed": true,
    "signing_url": "https://example.com/resource",
    "decline_reason": null,
    "declined_by": {"resource": "signer", "id": "id-placeholder", "full_name": "Example", "email": null, "whatsapp_phone_number": null, "government_id": null, "has_accepted_terms": true},
    "tags": [{"id": "id-placeholder", "name": "Example"}],
    "assignment": {"resource": "assignment", "id": "id-placeholder", "sender_email": "person@example.com", "method": "virtual", "expires_at": null, "message": null, "signers": [{"resource": "signer", "id": "id-placeholder", "full_name": "Example", "email": null, "whatsapp_phone_number": null, "government_id": null, "has_accepted_terms": true, "verification_method": null, "identity_verification_status": null, "notification_methods": ["Email"], "step": 1, "notified": true, "completed": true, "notification_history": [{"event": "signature_request", "status": "sent", "error_code": null, "error_message": null, "sent_at": null, "failed_at": null}]}], "copy_receivers": [{"resource": "signer", "id": "id-placeholder", "full_name": "Example", "email": null, "whatsapp_phone_number": null, "government_id": null, "has_accepted_terms": true}], "items": [{"id": "id-placeholder", "page": {"id": "id-placeholder", "number": 1, "height": 1, "width": 1, "download_url": "https://example.com/resource"}, "signer": {"resource": "signer", "id": "id-placeholder", "full_name": "Example", "email": null, "whatsapp_phone_number": null, "government_id": null, "has_accepted_terms": true}, "field": {"resource": "field", "id": "id-placeholder", "name": "Example", "type": "text", "regex": null, "is_pre_defined": true, "is_active": true, "is_required": true, "is_standard": true, "is_read_only": true, "is_visible": true}, "display_settings": {"left": 1, "top": 1, "width": 1, "height": 1, "fontFamily": "Arial", "fontSize": 1, "backgroundColor": "#D5EBFF"}, "value": null, "completed": true}], "summary": {"signer_count": 1, "completed_count": 1, "signers": [{"resource": "signer", "id": "id-placeholder", "full_name": "Example", "email": null, "whatsapp_phone_number": null, "government_id": null, "has_accepted_terms": true}]}, "signing_urls": [{"signer_id": "signer-id-placeholder", "url": "https://example.com/resource"}], "document_id": "document-placeholder", "created_at": "2026-01-01T00:00:00Z"},
    "pages": [{"id": "id-placeholder", "number": 1, "height": 1, "width": 1, "download_url": "https://example.com/resource"}],
    "created_at": "2026-01-01T00:00:00Z",
    "updated_at": "2026-01-01T00:00:00Z",
    "verification_code": null,
    "verification_url": null
  }
]
```

@param params Status, method, search, tag-ID, sort, and pagination filters.
@param accountId Account override; otherwise the client's default account is used.
@return Matching document summaries and optional pagination-header metadata.

### `documents.search`

Lightweight document search (`GET /accounts/{accountId}/documents/search`). Returns the same
[DocumentListItem] shape as [list] but only supports `search`/`status`/`page`/`per-page`.

Request body: none. Response `data` is an array of the document payload documented on this class.

Wire operation: `GET /v1/accounts/{accountId}/documents/search`.

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
    "status": "certificated",
    "artifacts": {},
    "is_closed": true,
    "signing_url": "https://example.com/resource",
    "decline_reason": null,
    "declined_by": {"resource": "signer", "id": "id-placeholder", "full_name": "Example", "email": null, "whatsapp_phone_number": null, "government_id": null, "has_accepted_terms": true},
    "tags": [{"id": "id-placeholder", "name": "Example"}],
    "created_at": "2026-01-01T00:00:00Z",
    "updated_at": "2026-01-01T00:00:00Z",
    "verification_code": null,
    "verification_url": null
  }
]
```

@param query Optional partial document-name search.
@param status Optional exact status filter.
@param page Optional one-based results page.
@param perPage Optional records-per-page limit.
@param accountId Account override; otherwise the client's default account is used.
@return Matching document summaries and optional pagination-header metadata.

### `documents.search`

Lightweight document search (`GET /accounts/{accountId}/documents/search`). Returns the same
[DocumentListItem] shape as [list] but only supports `search`/`status`/`page`/`per-page`.

Request body: none. Response `data` is an array of the document payload documented on this class.

Wire operation: `GET /v1/accounts/{accountId}/documents/search`.

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
    "status": "certificated",
    "artifacts": {},
    "is_closed": true,
    "signing_url": "https://example.com/resource",
    "decline_reason": null,
    "declined_by": {"resource": "signer", "id": "id-placeholder", "full_name": "Example", "email": null, "whatsapp_phone_number": null, "government_id": null, "has_accepted_terms": true},
    "tags": [{"id": "id-placeholder", "name": "Example"}],
    "created_at": "2026-01-01T00:00:00Z",
    "updated_at": "2026-01-01T00:00:00Z",
    "verification_code": null,
    "verification_url": null
  }
]
```

@param params Search, status, pagination and sort (name or updated_at, optionally prefixed with minus).
@param accountId Workspace override.
@return Matching documents with pagination metadata.

### `documents.details`

Fetches full document details, including assignment and pages (`GET /documents/{documentId}`).

Wire operation: `GET /v1/documents/{documentId}`

Request body: none.

Response `data` inside `{status,message,data}`:
```json
{
  "resource": "document",
  "id": "id-placeholder",
  "account_id": "account-id-placeholder",
  "template_id": null,
  "name": "document.pdf",
  "status": "metadata_ready",
  "artifacts": {
    "original": "https://example.com/resource"
  },
  "is_closed": false,
  "signing_url": "https://example.com/resource",
  "verification_code": "verification-code-placeholder",
  "verification_url": "https://example.com/resource",
  "decline_reason": null,
  "declined_by": {
    "resource": "signer",
    "id": "id-placeholder",
    "full_name": "Example Person",
    "email": "person@example.com",
    "whatsapp_phone_number": "+5548999990000",
    "government_id": "government-id-placeholder",
    "has_accepted_terms": false
  },
  "tags": [
    {
      "id": "example",
      "name": "example",
      "color": "ff8800"
    }
  ],
  "assignment": {
    "resource": "assignment",
    "id": "id-placeholder",
    "document_id": "document-id-placeholder",
    "created_at": "2026-06-03T03:54:16Z",
    "sender_email": "person@example.com",
    "method": "virtual",
    "expires_at": null,
    "message": "example",
    "signers": [
      {
        "resource": "signer",
        "id": "id-placeholder",
        "full_name": "Example Person",
        "email": "person@example.com",
        "whatsapp_phone_number": "+5548999990000",
        "government_id": "government-id-placeholder",
        "has_accepted_terms": false,
        "verification_method": "Email",
        "notification_methods": [
          "example"
        ],
        "step": 1,
        "notified": true,
        "identity_verification_status": "InReview",
        "completed": true,
        "notification_history": [
          {
            "event": "signature_request",
            "status": "failed",
            "error_code": "MailboxFull",
            "error_message": "A caixa de entrada do destinatário está cheia. Peça ao signatário para liberar espaço ou use outro e-mail.",
            "sent_at": "2026-07-07T12:00:00Z",
            "failed_at": "2026-07-07T12:05:00Z"
          }
        ]
      }
    ],
    "copy_receivers": [
      {}
    ],
    "items": [
      {
        "id": "example",
        "page": {
          "id": "id-placeholder",
          "number": 1,
          "height": 2100,
          "width": 1275,
          "download_url": "https://example.com/resource"
        },
        "signer": {},
        "field": {},
        "display_settings": null,
        "value": null,
        "completed": true
      }
    ],
    "summary": {
      "signer_count": 1,
      "completed_count": 1,
      "signers": [
        {}
      ]
    },
    "signing_urls": [
      {
        "signer_id": "example",
        "url": "https://example.com/resource",
        "embedded": false
      }
    ]
  },
  "pages": [
    {
      "id": "id-placeholder",
      "number": 1,
      "height": 2100,
      "width": 1275,
      "download_url": "https://example.com/resource"
    }
  ],
  "envelope_id": null,
  "envelope": {
    "id": "id-placeholder",
    "name": "Contrato de prestação de serviços"
  },
  "has_contract": true,
  "created_at": "2026-06-03T03:54:16Z",
  "updated_at": "2026-06-03T03:54:16Z"
}
```
@param documentId Stable document identifier.
@return Complete document state.
@throws ValidationException if [documentId] is blank.

### `documents.details`

Get document. Optional expand=envelope_id,envelope; omitted by default.

Wire operation: `GET /v1/documents/{documentId}`.

Request body: none.

Response `data` inside `{status,message,data}`:
```json
{
  "resource": "document",
  "id": "id-placeholder",
  "account_id": "account-id-placeholder",
  "template_id": null,
  "name": "document.pdf",
  "status": "metadata_ready",
  "artifacts": {
    "original": "https://example.com/resource"
  },
  "is_closed": false,
  "signing_url": "https://example.com/resource",
  "verification_code": "verification-code-placeholder",
  "verification_url": "https://example.com/resource",
  "decline_reason": null,
  "declined_by": {
    "resource": "signer",
    "id": "id-placeholder",
    "full_name": "Example Person",
    "email": "person@example.com",
    "whatsapp_phone_number": "+5548999990000",
    "government_id": "government-id-placeholder",
    "has_accepted_terms": false
  },
  "tags": [
    {
      "id": "example",
      "name": "example",
      "color": "ff8800"
    }
  ],
  "assignment": {
    "resource": "assignment",
    "id": "id-placeholder",
    "document_id": "document-id-placeholder",
    "created_at": "2026-06-03T03:54:16Z",
    "sender_email": "person@example.com",
    "method": "virtual",
    "expires_at": null,
    "message": "example",
    "signers": [
      {
        "resource": "signer",
        "id": "id-placeholder",
        "full_name": "Example Person",
        "email": "person@example.com",
        "whatsapp_phone_number": "+5548999990000",
        "government_id": "government-id-placeholder",
        "has_accepted_terms": false,
        "verification_method": "Email",
        "notification_methods": [
          "example"
        ],
        "step": 1,
        "notified": true,
        "identity_verification_status": "InReview",
        "completed": true,
        "notification_history": [
          {
            "event": "signature_request",
            "status": "failed",
            "error_code": "MailboxFull",
            "error_message": "A caixa de entrada do destinatário está cheia. Peça ao signatário para liberar espaço ou use outro e-mail.",
            "sent_at": "2026-07-07T12:00:00Z",
            "failed_at": "2026-07-07T12:05:00Z"
          }
        ]
      }
    ],
    "copy_receivers": [
      {}
    ],
    "items": [
      {
        "id": "example",
        "page": {
          "id": "id-placeholder",
          "number": 1,
          "height": 2100,
          "width": 1275,
          "download_url": "https://example.com/resource"
        },
        "signer": {},
        "field": {},
        "display_settings": null,
        "value": null,
        "completed": true
      }
    ],
    "summary": {
      "signer_count": 1,
      "completed_count": 1,
      "signers": [
        {}
      ]
    },
    "signing_urls": [
      {
        "signer_id": "example",
        "url": "https://example.com/resource",
        "embedded": false
      }
    ]
  },
  "pages": [
    {
      "id": "id-placeholder",
      "number": 1,
      "height": 2100,
      "width": 1275,
      "download_url": "https://example.com/resource"
    }
  ],
  "envelope_id": null,
  "envelope": {
    "id": "id-placeholder",
    "name": "Contrato de prestação de serviços"
  },
  "has_contract": true,
  "created_at": "2026-06-03T03:54:16Z",
  "updated_at": "2026-06-03T03:54:16Z"
}
```

### `documents.get`

Alias for [details]; same request and response.

Wire operation: `GET /v1/documents/{documentId}`.

Request body: none.

Response `data` inside `{status,message,data}`:
```json
{
  "resource": "document",
  "id": "id-placeholder",
  "account_id": "account-id-placeholder",
  "template_id": null,
  "name": "document.pdf",
  "status": "metadata_ready",
  "artifacts": {
    "original": "https://example.com/resource"
  },
  "is_closed": false,
  "signing_url": "https://example.com/resource",
  "verification_code": "verification-code-placeholder",
  "verification_url": "https://example.com/resource",
  "decline_reason": null,
  "declined_by": {
    "resource": "signer",
    "id": "id-placeholder",
    "full_name": "Example Person",
    "email": "person@example.com",
    "whatsapp_phone_number": "+5548999990000",
    "government_id": "government-id-placeholder",
    "has_accepted_terms": false
  },
  "tags": [
    {
      "id": "example",
      "name": "example",
      "color": "ff8800"
    }
  ],
  "assignment": {
    "resource": "assignment",
    "id": "id-placeholder",
    "document_id": "document-id-placeholder",
    "created_at": "2026-06-03T03:54:16Z",
    "sender_email": "person@example.com",
    "method": "virtual",
    "expires_at": null,
    "message": "example",
    "signers": [
      {
        "resource": "signer",
        "id": "id-placeholder",
        "full_name": "Example Person",
        "email": "person@example.com",
        "whatsapp_phone_number": "+5548999990000",
        "government_id": "government-id-placeholder",
        "has_accepted_terms": false,
        "verification_method": "Email",
        "notification_methods": [
          "example"
        ],
        "step": 1,
        "notified": true,
        "identity_verification_status": "InReview",
        "completed": true,
        "notification_history": [
          {
            "event": "signature_request",
            "status": "failed",
            "error_code": "MailboxFull",
            "error_message": "A caixa de entrada do destinatário está cheia. Peça ao signatário para liberar espaço ou use outro e-mail.",
            "sent_at": "2026-07-07T12:00:00Z",
            "failed_at": "2026-07-07T12:05:00Z"
          }
        ]
      }
    ],
    "copy_receivers": [
      {}
    ],
    "items": [
      {
        "id": "example",
        "page": {
          "id": "id-placeholder",
          "number": 1,
          "height": 2100,
          "width": 1275,
          "download_url": "https://example.com/resource"
        },
        "signer": {},
        "field": {},
        "display_settings": null,
        "value": null,
        "completed": true
      }
    ],
    "summary": {
      "signer_count": 1,
      "completed_count": 1,
      "signers": [
        {}
      ]
    },
    "signing_urls": [
      {
        "signer_id": "example",
        "url": "https://example.com/resource",
        "embedded": false
      }
    ]
  },
  "pages": [
    {
      "id": "id-placeholder",
      "number": 1,
      "height": 2100,
      "width": 1275,
      "download_url": "https://example.com/resource"
    }
  ],
  "envelope_id": null,
  "envelope": {
    "id": "id-placeholder",
    "name": "Contrato de prestação de serviços"
  },
  "has_contract": true,
  "created_at": "2026-06-03T03:54:16Z",
  "updated_at": "2026-06-03T03:54:16Z"
}
```
@param documentId Stable document identifier.
@return Complete document state.

### `documents.waitUntilReady`

Polls [details] until the document reaches a ready status (`metadata_ready`/`pending_signature`/
`certificated`). Throws if it reaches a terminal failure status or [maxWaitMs] elapses.

Sends no request of its own: repeats `GET /documents/{documentId}` every [pollIntervalMs] and
returns the document payload documented on this class from the first ready response. Its budget is independent of
the client's per-request timeout because readiness spans several round trips plus server-side
PDF processing.

Wire operation: `GET /v1/documents/{documentId}`.

Request body: none.

Response `data` inside `{status,message,data}`:
```json
{
  "resource": "document",
  "id": "id-placeholder",
  "account_id": "account-id-placeholder",
  "template_id": null,
  "name": "document.pdf",
  "status": "metadata_ready",
  "artifacts": {
    "original": "https://example.com/resource"
  },
  "is_closed": false,
  "signing_url": "https://example.com/resource",
  "verification_code": "verification-code-placeholder",
  "verification_url": "https://example.com/resource",
  "decline_reason": null,
  "declined_by": {
    "resource": "signer",
    "id": "id-placeholder",
    "full_name": "Example Person",
    "email": "person@example.com",
    "whatsapp_phone_number": "+5548999990000",
    "government_id": "government-id-placeholder",
    "has_accepted_terms": false
  },
  "tags": [
    {
      "id": "example",
      "name": "example",
      "color": "ff8800"
    }
  ],
  "assignment": {
    "resource": "assignment",
    "id": "id-placeholder",
    "document_id": "document-id-placeholder",
    "created_at": "2026-06-03T03:54:16Z",
    "sender_email": "person@example.com",
    "method": "virtual",
    "expires_at": null,
    "message": "example",
    "signers": [
      {
        "resource": "signer",
        "id": "id-placeholder",
        "full_name": "Example Person",
        "email": "person@example.com",
        "whatsapp_phone_number": "+5548999990000",
        "government_id": "government-id-placeholder",
        "has_accepted_terms": false,
        "verification_method": "Email",
        "notification_methods": [
          "example"
        ],
        "step": 1,
        "notified": true,
        "identity_verification_status": "InReview",
        "completed": true,
        "notification_history": [
          {
            "event": "signature_request",
            "status": "failed",
            "error_code": "MailboxFull",
            "error_message": "A caixa de entrada do destinatário está cheia. Peça ao signatário para liberar espaço ou use outro e-mail.",
            "sent_at": "2026-07-07T12:00:00Z",
            "failed_at": "2026-07-07T12:05:00Z"
          }
        ]
      }
    ],
    "copy_receivers": [
      {}
    ],
    "items": [
      {
        "id": "example",
        "page": {
          "id": "id-placeholder",
          "number": 1,
          "height": 2100,
          "width": 1275,
          "download_url": "https://example.com/resource"
        },
        "signer": {},
        "field": {},
        "display_settings": null,
        "value": null,
        "completed": true
      }
    ],
    "summary": {
      "signer_count": 1,
      "completed_count": 1,
      "signers": [
        {}
      ]
    },
    "signing_urls": [
      {
        "signer_id": "example",
        "url": "https://example.com/resource",
        "embedded": false
      }
    ]
  },
  "pages": [
    {
      "id": "id-placeholder",
      "number": 1,
      "height": 2100,
      "width": 1275,
      "download_url": "https://example.com/resource"
    }
  ],
  "envelope_id": null,
  "envelope": {
    "id": "id-placeholder",
    "name": "Contrato de prestação de serviços"
  },
  "has_contract": true,
  "created_at": "2026-06-03T03:54:16Z",
  "updated_at": "2026-06-03T03:54:16Z"
}
```
@param documentId Stable document identifier.
@param maxWaitMs Positive total polling budget in milliseconds.
@param pollIntervalMs Positive delay between API requests in milliseconds.
@return First document response whose status is in [DocumentStatus.READY].
@throws ValidationException for invalid timing values, a terminal failure status, or timeout.

### `documents.download`

Downloads a document artifact as raw bytes (`GET /documents/{documentId}/download/{artifactName}`).
Defaults to the `certificated` artifact, which is only available once the document is completed;
use [DocumentArtifact.ORIGINAL] for the uploaded file.

Request body: none. The response is the raw artifact, not the JSON envelope, and its bytes
are returned unchanged. An artifact that does not exist yet answers `404`.

Wire operation: `GET /v1/documents/{documentId}/download/{artifactName}`.

Request body: none.

Response 200: raw binary bytes (`application/pdf`), without a JSON envelope.

@param documentId Stable document identifier.
@param artifactName Artifact wire value from [DocumentArtifact].
@return Unmodified PDF or ZIP response bytes.
@throws ValidationException if either identifier is blank.

### `documents.thumbnail`

Downloads the document thumbnail (`GET /documents/{documentId}/thumbnail`).

Request body: none. The response is the raw image, not the JSON envelope; its bytes are
returned unchanged.

Wire operation: `GET /v1/documents/{documentId}/thumbnail`.

Request body: none.

Response 200: raw binary bytes (`image` media types), without a JSON envelope.

@param documentId Stable document identifier.
@return Unmodified thumbnail image bytes.

### `documents.downloadPage`

Downloads a page image (`GET /documents/{documentId}/pages/{pageId}/download`).

Request body: none. The response is the raw 150-DPI page image, not the JSON envelope, which
is the coordinate space `display_settings` positions collect fields in.

Wire operation: `GET /v1/documents/{documentId}/pages/{pageId}/download`.

Request body: none.

Response 200: raw binary bytes (`image` media types), without a JSON envelope.

@param documentId Stable document identifier.
@param pageId Page identifier from [DocumentDetails.pages].
@return Unmodified page image bytes.

### `documents.activities`

Returns the document's activity log (`GET /documents/{documentId}/activities`).

Request body: none. Response `data` is one entry per recorded event, each carrying a snapshot
of the event `payload` and the requesting `origin`:

Wire operation: `GET /v1/documents/{documentId}/activities`.

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
[
  {
    "id": 1,
    "event": "assignment_created",
    "message": "example",
    "payload": {},
    "origin": {"ip": "203.0.113.7", "user-agent": "example"},
    "created_at": "2026-01-01T00:00:00Z"
  }
]
```

@param documentId Stable document identifier.
@return First activity page; use the ListParams overload to read subsequent pages.

### `documents.activities`

Returns the document's activity log (`GET /documents/{documentId}/activities`).

Request body: none. Response `data` is one entry per recorded event, each carrying a snapshot
of the event `payload` and the requesting `origin`:

Wire operation: `GET /v1/documents/{documentId}/activities`.

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
[
  {
    "id": 1,
    "event": "assignment_created",
    "message": "example",
    "payload": {},
    "origin": {"ip": "203.0.113.7", "user-agent": "example"},
    "created_at": "2026-01-01T00:00:00Z"
  }
]
```

@param documentId Stable document identifier.
@param params Pagination and sort: id for oldest first, -id (default) for newest first.
@return Activity page with X-Pagination-* metadata.

### `documents.delete`

Wire operation: `DELETE /v1/documents/{documentId}`

Request body: none.

Response `data` inside `{status,message,data}`:
```json
[]
```
@param documentId Stable document identifier.

### `documents.rename`

Renames a document (`PATCH /documents/{documentId}`, body `{"name": ...}`). The service
allows rename before assignment while status is `uploaded` or `metadata_ready`, and
normalizes diacritics and unsupported characters. The name is required and limited to 255
characters. Returns the updated document.

Wire operation: `PATCH /v1/documents/{documentId}`

Request body (`application/json`; optional fields may be omitted):
```json
{
  "name": "Service agreement.pdf"
}
```

Response `data` inside `{status,message,data}`:
```json
{
  "resource": "document",
  "id": "id-placeholder",
  "account_id": "account-id-placeholder",
  "template_id": null,
  "name": "document.pdf",
  "status": "metadata_ready",
  "artifacts": {
    "original": "https://example.com/resource"
  },
  "is_closed": false,
  "signing_url": "https://example.com/resource",
  "verification_code": "verification-code-placeholder",
  "verification_url": "https://example.com/resource",
  "decline_reason": null,
  "declined_by": {
    "resource": "signer",
    "id": "id-placeholder",
    "full_name": "Example Person",
    "email": "person@example.com",
    "whatsapp_phone_number": "+5548999990000",
    "government_id": "government-id-placeholder",
    "has_accepted_terms": false
  },
  "tags": [
    {
      "id": "example",
      "name": "example",
      "color": "ff8800"
    }
  ],
  "assignment": {
    "resource": "assignment",
    "id": "id-placeholder",
    "document_id": "document-id-placeholder",
    "created_at": "2026-06-03T03:54:16Z",
    "sender_email": "person@example.com",
    "method": "virtual",
    "expires_at": null,
    "message": "example",
    "signers": [
      {
        "resource": "signer",
        "id": "id-placeholder",
        "full_name": "Example Person",
        "email": "person@example.com",
        "whatsapp_phone_number": "+5548999990000",
        "government_id": "government-id-placeholder",
        "has_accepted_terms": false,
        "verification_method": "Email",
        "notification_methods": [
          "example"
        ],
        "step": 1,
        "notified": true,
        "identity_verification_status": "InReview",
        "completed": true,
        "notification_history": [
          {
            "event": "signature_request",
            "status": "failed",
            "error_code": "MailboxFull",
            "error_message": "A caixa de entrada do destinatário está cheia. Peça ao signatário para liberar espaço ou use outro e-mail.",
            "sent_at": "2026-07-07T12:00:00Z",
            "failed_at": "2026-07-07T12:05:00Z"
          }
        ]
      }
    ],
    "copy_receivers": [
      {}
    ],
    "items": [
      {
        "id": "example",
        "page": {
          "id": "id-placeholder",
          "number": 1,
          "height": 2100,
          "width": 1275,
          "download_url": "https://example.com/resource"
        },
        "signer": {},
        "field": {},
        "display_settings": null,
        "value": null,
        "completed": true
      }
    ],
    "summary": {
      "signer_count": 1,
      "completed_count": 1,
      "signers": [
        {}
      ]
    },
    "signing_urls": [
      {
        "signer_id": "example",
        "url": "https://example.com/resource",
        "embedded": false
      }
    ]
  },
  "pages": [
    {
      "id": "id-placeholder",
      "number": 1,
      "height": 2100,
      "width": 1275,
      "download_url": "https://example.com/resource"
    }
  ],
  "envelope_id": null,
  "envelope": {
    "id": "id-placeholder",
    "name": "Contrato de prestação de serviços"
  },
  "has_contract": true,
  "created_at": "2026-06-03T03:54:16Z",
  "updated_at": "2026-06-03T03:54:16Z"
}
```
@param documentId Stable document identifier.
@param name Non-blank replacement name of at most [SdkConstants.MAX_DOCUMENT_NAME_LENGTH] characters.
@return Complete updated document.
@throws ValidationException if an identifier/name is blank or the name is too long.

### `documents.createFromTemplate`

Creates a document from a template
(`POST /accounts/{accountId}/templates/{templateId}/documents`).

Wire operation: `POST /v1/accounts/{accountId}/templates/{templateId}/documents`

Request body (`application/json`; optional fields may be omitted):
```json
{
  "signers": [
    {
      "role_id": "role-id-placeholder",
      "id": "id-placeholder",
      "email": "person@example.com",
      "name": "Maria Silva",
      "whatsapp_phone_number": "+5548999999999",
      "verification_method": "Email",
      "notification_methods": [
        "example"
      ],
      "embedded": false,
      "step": 1
    }
  ],
  "editor_fields": [
    {
      "field_id": "field-id-placeholder",
      "value": "Field value"
    }
  ],
  "name": "sample-contract-one-page.pdf",
  "message": "Message to the signers",
  "expires_at": "2024-07-30T23:59:00Z",
  "tags": [
    "example"
  ],
  "max_total_credits": 1.5
}
```

Response `data` inside `{status,message,data}`:
```json
{
  "resource": "document",
  "id": "id-placeholder",
  "account_id": "account-id-placeholder",
  "template_id": null,
  "name": "document.pdf",
  "status": "metadata_ready",
  "artifacts": {
    "original": "https://example.com/resource"
  },
  "is_closed": false,
  "signing_url": "https://example.com/resource",
  "verification_code": "verification-code-placeholder",
  "verification_url": "https://example.com/resource",
  "decline_reason": null,
  "declined_by": {
    "resource": "signer",
    "id": "id-placeholder",
    "full_name": "Example Person",
    "email": "person@example.com",
    "whatsapp_phone_number": "+5548999990000",
    "government_id": "government-id-placeholder",
    "has_accepted_terms": false
  },
  "tags": [
    {
      "id": "example",
      "name": "example",
      "color": "ff8800"
    }
  ],
  "assignment": {
    "resource": "assignment",
    "id": "id-placeholder",
    "document_id": "document-id-placeholder",
    "created_at": "2026-06-03T03:54:16Z",
    "sender_email": "person@example.com",
    "method": "virtual",
    "expires_at": null,
    "message": "example",
    "signers": [
      {
        "resource": "signer",
        "id": "id-placeholder",
        "full_name": "Example Person",
        "email": "person@example.com",
        "whatsapp_phone_number": "+5548999990000",
        "government_id": "government-id-placeholder",
        "has_accepted_terms": false,
        "verification_method": "Email",
        "notification_methods": [
          "example"
        ],
        "step": 1,
        "notified": true,
        "identity_verification_status": "InReview",
        "completed": true,
        "notification_history": [
          {
            "event": "signature_request",
            "status": "failed",
            "error_code": "MailboxFull",
            "error_message": "A caixa de entrada do destinatário está cheia. Peça ao signatário para liberar espaço ou use outro e-mail.",
            "sent_at": "2026-07-07T12:00:00Z",
            "failed_at": "2026-07-07T12:05:00Z"
          }
        ]
      }
    ],
    "copy_receivers": [
      {}
    ],
    "items": [
      {
        "id": "example",
        "page": {
          "id": "id-placeholder",
          "number": 1,
          "height": 2100,
          "width": 1275,
          "download_url": "https://example.com/resource"
        },
        "signer": {},
        "field": {},
        "display_settings": null,
        "value": null,
        "completed": true
      }
    ],
    "summary": {
      "signer_count": 1,
      "completed_count": 1,
      "signers": [
        {}
      ]
    },
    "signing_urls": [
      {
        "signer_id": "example",
        "url": "https://example.com/resource",
        "embedded": false
      }
    ]
  },
  "pages": [
    {
      "id": "id-placeholder",
      "number": 1,
      "height": 2100,
      "width": 1275,
      "download_url": "https://example.com/resource"
    }
  ],
  "envelope_id": null,
  "envelope": {
    "id": "id-placeholder",
    "name": "Contrato de prestação de serviços"
  },
  "has_contract": true,
  "created_at": "2026-06-03T03:54:16Z",
  "updated_at": "2026-06-03T03:54:16Z"
}
```
@param templateId Existing template identifier.
@param signers Non-empty role-mapped signers; this list replaces [options]' signer list.
Optional maxTotalCredits caps spending. idempotencyKey sends Idempotency-Key for an explicit retry of the same creation.

@param options Optional generated name, message, expiration, editor fields, tags, credit ceiling and idempotency key.
@param accountId Account override; otherwise the client's default account is used.
@return Uploaded document; assignment is null until asynchronous generation completes.
@throws ValidationException for blank identifiers, missing signer identity or invalid channels.
The API validates Signer-role steps and certificate restrictions; CopyReceiver roles ignore
signing settings and Editor-role fields are filled before generation. Template reads are not
required by this call, so documents:write is sufficient without templates:read.

### `documents.estimateCostFromTemplate`

Estimates the credit cost of creating a document from a template
(`POST /accounts/{accountId}/templates/{templateId}/documents/estimate-cost`).

Request body carries only what affects pricing — each role mapping's verification and
notification channels — so signer IDs and steps are omitted. Response `data`:
The API checks template roles and placed Signer-role fields; certificate signing requires
a virtual assignment. No template-read permission is required by this operation.

Wire operation: `POST /v1/accounts/{accountId}/templates/{templateId}/documents/estimate-cost`.

Request body (`application/json`; optional members may be omitted):
```json
{
  "signers": [{"role_id": "role-id-placeholder", "verification_method": "Whatsapp", "notification_methods": ["Whatsapp"]}]
}
```

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
{
  "documents": 1,
  "credits": 0.45,
  "needs_extra_document": true,
  "extra_document_cost": 1,
  "total_credits": 1.45,
  "breakdown": [{"code": "NotificationWhatsapp", "name": "WhatsApp notification", "cost": 0.45, "quantity": 1, "unit_cost": 0.45}],
  "document_balance": 0,
  "credit_balance": 10,
  "has_sufficient_resources": true,
  "blocking_reason": null,
  "message": null
}
```

@param templateId Existing template identifier.
@param signers Non-empty role mappings; signer IDs and signing steps are not sent for pricing.
@param accountId Account override; otherwise the client's default account is used.
@return Typed credit, balance, resource-sufficiency, and pricing-breakdown response.
@throws ValidationException for a blank account/template/role ID, an empty list or invalid channels.
The API determines which roles sign and whether their placed fields require a collect assignment.

### `documents.verify`

Verifies a signed document by signature hash (`GET /documents/{hash}/verify`, public/no-auth).

Request body: none. This call always answers HTTP `200`; an unknown hash or unsigned document
sets `is_valid` to false, leaves the details null, and explains why in `message`.

Wire operation: `GET /v1/documents/{documentSignatureHash}/verify`.

Request body: none.

Response `data` inside `{status,message,data}`:
```json
{
  "hash": "FE32EDDADE7CBDDCBB934E7402047450B0E59C02",
  "id": "id-placeholder",
  "agreement_code": "550E8400-E29B-41D4-A716-446655440000",
  "status": "certificated",
  "page_count": "1",
  "signer_count": "1",
  "completed_count": 1,
  "completed_at": "2023-01-27T19:27:44Z",
  "certificated_at": "2023-01-27T19:27:45Z",
  "verified_at": "2023-01-27T19:27:46Z",
  "is_valid": true,
  "message": ""
}
```
@param hash Verification hash printed in the signed document.
@return Server validation result and matching document details when available.

### `documents.getPublic`

Fetches non-sensitive public document information without account credentials
(`GET /public/documents/{documentId}`).

Request body: none, and no credential is sent — this uses the client's credential-free
transport. Response `data` carries only signer-safe fields:

Wire operation: `GET /v1/public/documents/{documentId}`.

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
{
  "resource": "document",
  "id": "id-placeholder",
  "account_id": "account-id-placeholder",
  "template_id": null,
  "name": "Example",
  "status": "certificated",
  "artifacts": {},
  "is_closed": true,
  "signing_url": "https://example.com/resource",
  "decline_reason": null,
  "declined_by": {"resource": "signer", "id": "id-placeholder", "full_name": "Example", "email": null, "whatsapp_phone_number": null, "government_id": null, "has_accepted_terms": true},
  "tags": [{"id": "id-placeholder", "name": "Example"}],
  "assignment": {"resource": "assignment", "id": "id-placeholder", "sender_email": "person@example.com", "method": "virtual", "expires_at": null, "message": null, "signers": [{"resource": "signer", "id": "id-placeholder", "full_name": "Example", "email": null, "whatsapp_phone_number": null, "government_id": null, "has_accepted_terms": true, "verification_method": null, "identity_verification_status": null, "notification_methods": ["Email"], "step": 1, "notified": true, "completed": true, "notification_history": [{"event": "signature_request", "status": "sent", "error_code": null, "error_message": null, "sent_at": null, "failed_at": null}]}], "copy_receivers": [{"resource": "signer", "id": "id-placeholder", "full_name": "Example", "email": null, "whatsapp_phone_number": null, "government_id": null, "has_accepted_terms": true}], "items": [{"id": "id-placeholder", "page": {"id": "id-placeholder", "number": 1, "height": 1, "width": 1, "download_url": "https://example.com/resource"}, "signer": {"resource": "signer", "id": "id-placeholder", "full_name": "Example", "email": null, "whatsapp_phone_number": null, "government_id": null, "has_accepted_terms": true}, "field": {"resource": "field", "id": "id-placeholder", "name": "Example", "type": "text", "regex": null, "is_pre_defined": true, "is_active": true, "is_required": true, "is_standard": true, "is_read_only": true, "is_visible": true}, "display_settings": {"left": 1, "top": 1, "width": 1, "height": 1, "fontFamily": "Arial", "fontSize": 1, "backgroundColor": "#D5EBFF"}, "value": null, "completed": true}], "summary": {"signer_count": 1, "completed_count": 1, "signers": [{"resource": "signer", "id": "id-placeholder", "full_name": "Example", "email": null, "whatsapp_phone_number": null, "government_id": null, "has_accepted_terms": true}]}, "signing_urls": [{"signer_id": "signer-id-placeholder", "url": "https://example.com/resource"}], "document_id": "document-placeholder", "created_at": "2026-01-01T00:00:00Z"},
  "pages": [{"id": "id-placeholder", "number": 1, "height": 1, "width": 1, "download_url": "https://example.com/resource"}],
  "created_at": "2026-01-01T00:00:00Z",
  "updated_at": "2026-01-01T00:00:00Z",
  "verification_code": null,
  "verification_url": null
}
```

@param documentId Stable public document identifier.
@return Public document identity, status, and signer-safe fields exposed by the API.

### `documents.sendAccessToken`

Sends a one-time access token to a signer of a public document so they can view it.

The document must be pending signature and [email] must belong to a signer of the document who
has not signed yet. Only the `email` channel exists. No credential is sent: this uses the
client's credential-free transport.

Wire operation: `PUT /v1/public/documents/{documentId}/send-token`.

Request body (`application/json`; both members required):
```json
{
  "channel": "email",
  "recipient": "signer@example.com"
}
```

Response 200 `data` payload (inside `{status,message,data}`):
```json
{
  "document": {"resource": "document","id": "doc-id","name": "Agreement.pdf","page_count": 3,"created_by": "Sender Name"},
  "channel": "email",
  "recipient": "signer@example.com"
}
```

@param documentId Stable public document identifier.
@param email The signer's email address, sent as `recipient`.
@return Where the token was sent.
@throws ValidationException for a blank document ID or a malformed email, before any request.

### `documents.sendToken`

Sends a one-time access token to a signer of a public document; same request as
[sendAccessToken], discarding its result.

Wire operation: `PUT /v1/public/documents/{documentId}/send-token`.

Request body (`application/json`; both members required):
```json
{
  "channel": "email",
  "recipient": "signer@example.com"
}
```

Response 200 `data` payload: the [AccessTokenDelivery] shape documented on [sendAccessToken].

@param documentId Stable public document identifier.
@param email The signer's email address. Required.
@param channel `email` or `null`; the API supports no other channel.
@throws ValidationException for a blank document ID or recipient, a malformed email, or a
channel other than `email`.

### `documents.isFullySigned`

Sends no request of its own: derived from [details], so it costs one
`GET /documents/{documentId}`.

Checks whether every assignment signer has completed, including a certificated document.
A `true` result can precede certification artifact readiness while status is `certificating`.

Wire operation: `GET /v1/documents/{documentId}`.

Request body: none.

Response `data` inside `{status,message,data}`:
```json
{
  "resource": "document",
  "id": "id-placeholder",
  "account_id": "account-id-placeholder",
  "template_id": null,
  "name": "document.pdf",
  "status": "metadata_ready",
  "artifacts": {
    "original": "https://example.com/resource"
  },
  "is_closed": false,
  "signing_url": "https://example.com/resource",
  "verification_code": "verification-code-placeholder",
  "verification_url": "https://example.com/resource",
  "decline_reason": null,
  "declined_by": {
    "resource": "signer",
    "id": "id-placeholder",
    "full_name": "Example Person",
    "email": "person@example.com",
    "whatsapp_phone_number": "+5548999990000",
    "government_id": "government-id-placeholder",
    "has_accepted_terms": false
  },
  "tags": [
    {
      "id": "example",
      "name": "example",
      "color": "ff8800"
    }
  ],
  "assignment": {
    "resource": "assignment",
    "id": "id-placeholder",
    "document_id": "document-id-placeholder",
    "created_at": "2026-06-03T03:54:16Z",
    "sender_email": "person@example.com",
    "method": "virtual",
    "expires_at": null,
    "message": "example",
    "signers": [
      {
        "resource": "signer",
        "id": "id-placeholder",
        "full_name": "Example Person",
        "email": "person@example.com",
        "whatsapp_phone_number": "+5548999990000",
        "government_id": "government-id-placeholder",
        "has_accepted_terms": false,
        "verification_method": "Email",
        "notification_methods": [
          "example"
        ],
        "step": 1,
        "notified": true,
        "identity_verification_status": "InReview",
        "completed": true,
        "notification_history": [
          {
            "event": "signature_request",
            "status": "failed",
            "error_code": "MailboxFull",
            "error_message": "A caixa de entrada do destinatário está cheia. Peça ao signatário para liberar espaço ou use outro e-mail.",
            "sent_at": "2026-07-07T12:00:00Z",
            "failed_at": "2026-07-07T12:05:00Z"
          }
        ]
      }
    ],
    "copy_receivers": [
      {}
    ],
    "items": [
      {
        "id": "example",
        "page": {
          "id": "id-placeholder",
          "number": 1,
          "height": 2100,
          "width": 1275,
          "download_url": "https://example.com/resource"
        },
        "signer": {},
        "field": {},
        "display_settings": null,
        "value": null,
        "completed": true
      }
    ],
    "summary": {
      "signer_count": 1,
      "completed_count": 1,
      "signers": [
        {}
      ]
    },
    "signing_urls": [
      {
        "signer_id": "example",
        "url": "https://example.com/resource",
        "embedded": false
      }
    ]
  },
  "pages": [
    {
      "id": "id-placeholder",
      "number": 1,
      "height": 2100,
      "width": 1275,
      "download_url": "https://example.com/resource"
    }
  ],
  "envelope_id": null,
  "envelope": {
    "id": "id-placeholder",
    "name": "Contrato de prestação de serviços"
  },
  "has_contract": true,
  "created_at": "2026-06-03T03:54:16Z",
  "updated_at": "2026-06-03T03:54:16Z"
}
```
@param documentId Stable document identifier.
@return `true` for `certificated` status or a non-empty fully completed assignment summary.

### `documents.getSigningProgress`

Derives signing counts and percentage from the current assignment summary.

Sends no request of its own: derived from [details], so it costs one
`GET /documents/{documentId}` and reads its `assignment.summary`.

Wire operation: `GET /v1/documents/{documentId}`.

Request body: none.

Response `data` inside `{status,message,data}`:
```json
{
  "resource": "document",
  "id": "id-placeholder",
  "account_id": "account-id-placeholder",
  "template_id": null,
  "name": "document.pdf",
  "status": "metadata_ready",
  "artifacts": {
    "original": "https://example.com/resource"
  },
  "is_closed": false,
  "signing_url": "https://example.com/resource",
  "verification_code": "verification-code-placeholder",
  "verification_url": "https://example.com/resource",
  "decline_reason": null,
  "declined_by": {
    "resource": "signer",
    "id": "id-placeholder",
    "full_name": "Example Person",
    "email": "person@example.com",
    "whatsapp_phone_number": "+5548999990000",
    "government_id": "government-id-placeholder",
    "has_accepted_terms": false
  },
  "tags": [
    {
      "id": "example",
      "name": "example",
      "color": "ff8800"
    }
  ],
  "assignment": {
    "resource": "assignment",
    "id": "id-placeholder",
    "document_id": "document-id-placeholder",
    "created_at": "2026-06-03T03:54:16Z",
    "sender_email": "person@example.com",
    "method": "virtual",
    "expires_at": null,
    "message": "example",
    "signers": [
      {
        "resource": "signer",
        "id": "id-placeholder",
        "full_name": "Example Person",
        "email": "person@example.com",
        "whatsapp_phone_number": "+5548999990000",
        "government_id": "government-id-placeholder",
        "has_accepted_terms": false,
        "verification_method": "Email",
        "notification_methods": [
          "example"
        ],
        "step": 1,
        "notified": true,
        "identity_verification_status": "InReview",
        "completed": true,
        "notification_history": [
          {
            "event": "signature_request",
            "status": "failed",
            "error_code": "MailboxFull",
            "error_message": "A caixa de entrada do destinatário está cheia. Peça ao signatário para liberar espaço ou use outro e-mail.",
            "sent_at": "2026-07-07T12:00:00Z",
            "failed_at": "2026-07-07T12:05:00Z"
          }
        ]
      }
    ],
    "copy_receivers": [
      {}
    ],
    "items": [
      {
        "id": "example",
        "page": {
          "id": "id-placeholder",
          "number": 1,
          "height": 2100,
          "width": 1275,
          "download_url": "https://example.com/resource"
        },
        "signer": {},
        "field": {},
        "display_settings": null,
        "value": null,
        "completed": true
      }
    ],
    "summary": {
      "signer_count": 1,
      "completed_count": 1,
      "signers": [
        {}
      ]
    },
    "signing_urls": [
      {
        "signer_id": "example",
        "url": "https://example.com/resource",
        "embedded": false
      }
    ]
  },
  "pages": [
    {
      "id": "id-placeholder",
      "number": 1,
      "height": 2100,
      "width": 1275,
      "download_url": "https://example.com/resource"
    }
  ],
  "envelope_id": null,
  "envelope": {
    "id": "id-placeholder",
    "name": "Contrato de prestação de serviços"
  },
  "has_contract": true,
  "created_at": "2026-06-03T03:54:16Z",
  "updated_at": "2026-06-03T03:54:16Z"
}
```
@param documentId Stable document identifier.
@return Signed, total, pending, and percentage values; all zero when no assignment exists.

### `documents.getStatuses`

Lists the document status catalog (`GET /documents/statuses`).

Request body/query: none. Response `data`:
`[{"code":"metadata_ready","deletable":true}]`.

Wire operation: `GET /v1/documents/statuses`.

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
[
  {
    "code": "metadata_ready",
    "deletable": true
  }
]
```

@return Status identifiers and optional deletable flags.

### `documents.confirmSignerData`

Sends a legacy signer-data confirmation using the signer's access code. The supplied
non-empty map is encoded unchanged; prefer the typed signer-facing resource for the current
`full_name`, `email`, and `government_id` schema.

Request: `PUT /documents/{documentId}/signers/confirm-data?signer-access-code={code}` with the
supplied map as the JSON body. Response `data` is the updated signer record.

Wire operation: `PUT /v1/documents/{documentId}/signers/confirm-data`.

Request body (`application/json`; optional fields may be omitted):
```json
{
  "email": "person@example.com",
  "whatsapp_phone_number": "+5548999990000",
  "has_accepted_terms": true
}
```

Response `data` inside `{status,message,data}`:
```json
[]
```
@param documentId Stable document identifier.
@param signerAccessCode One-time signer code sent only in the query string.
@param data Non-empty legacy identity map sent unchanged as JSON.
@return Updated signer.
@throws ValidationException if an identifier is blank or [data] is empty.

### `documents.confirmSignerData`

Typed compatibility overload of [confirmSignerData]; unset official fields are omitted and
phone and explicit terms acceptance are sent when supplied.

Wire operation: `PUT /v1/documents/{documentId}/signers/confirm-data`.

Request body (`application/json`; optional fields may be omitted):
```json
{
  "email": "person@example.com",
  "whatsapp_phone_number": "+5548999990000",
  "has_accepted_terms": true
}
```

Response `data` inside `{status,message,data}`:
```json
[]
```
@param documentId Stable document identifier.
@param signerAccessCode One-time signer code sent only in the query string.
@param request Supported signer identity changes.
@return Updated signer.

### `documents.listTags`

Lists tags currently attached to a document
(`GET /accounts/{accountId}/documents/{documentId}/tags`).

Request body: none. Response `data`:

Wire operation: `GET /v1/accounts/{accountId}/documents/{documentId}/tags`.

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

@param documentId Stable document identifier.
@param accountId Account override; otherwise the client's default account is used.
@return Attached tags.

### `documents.replaceTags`

Replaces the document's tag set with [tagNames], whose values are tag IDs in the current
OpenAPI. The parameter name is retained because older deployments require names on the same
route. Values are sent unchanged; an empty list detaches all tags.

Request: `PUT /accounts/{accountId}/documents/{documentId}/tags` with body
`{"tags":["fa8c09f3e709a8a1c82d69b1454"]}`. Response `data` is the resulting tag array, in
the shape documented by [listTags].

Wire operation: `PUT /v1/accounts/{accountId}/documents/{documentId}/tags`.

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

@param documentId Stable document identifier.
@param tagNames Complete tag-ID set; despite the legacy parameter name these are IDs, not names.
@param accountId Account override; otherwise the client's default account is used.
@return Resulting complete tag set.

### `documents.addTags`

Attaches [tagNames] (tag IDs in the current OpenAPI) without removing existing tags. The
parameter name is retained because older deployments require names on the same route. Values
are sent unchanged; the API returns the resulting tag set.

Request: `POST /accounts/{accountId}/documents/{documentId}/tags` with body
`{"tags":["fa8c09f3e709a8a1c82d69b1454"]}`. Response `data` is the resulting tag array, in
the shape documented by [listTags].

Wire operation: `POST /v1/accounts/{accountId}/documents/{documentId}/tags`.

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

@param documentId Stable document identifier.
@param tagNames Tag IDs to add; despite the legacy parameter name these are IDs, not names.
@param accountId Account override; otherwise the client's default account is used.
@return Resulting complete tag set.

### `documents.detachTag`

Detaches a single tag from a document without deleting the tag
(`DELETE /accounts/{accountId}/documents/{documentId}/tags/{tagId}`).

Request body: none. Response carries no `data` payload. The tag itself survives; use
[com.assinafy.sdk.resources.TagResource.delete] to remove it from the workspace.

Wire operation: `DELETE /v1/accounts/{accountId}/documents/{documentId}/tags/{tagId}`.

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
{
  "detached": true
}
```

@param documentId Stable document identifier.
@param tagId Stable tag identifier.
@param accountId Account override; otherwise the client's default account is used.

### `embeddedSigning.listOrigins`

List embed origins. Requires the embedded-signing plan feature; origin management is unavailable to OAuth apps.

Wire operation: `GET /v1/accounts/{accountId}/embed-origins`.

Request body: none.

Response `data` inside `{status,message,data}`:
```json
[
  {
    "id": "id-placeholder",
    "origin": "https://app.example.com",
    "created_at": "2026-11-04T12:05:00Z"
  }
]
```

### `embeddedSigning.addOrigin`

Add embed origin. Exact HTTPS origin, or HTTP localhost/127.0.0.1; at most ten origins. Unavailable to OAuth apps.

Wire operation: `POST /v1/accounts/{accountId}/embed-origins`.

Request body (`application/json`; optional fields may be omitted):
```json
{
  "origin": "https://app.example.com"
}
```

Response `data` inside `{status,message,data}`:
```json
{
  "id": "id-placeholder",
  "origin": "https://app.example.com",
  "created_at": "2026-11-04T12:05:00Z"
}
```

### `embeddedSigning.updateOrigin`

Update embed origin. Exact HTTPS origin, or HTTP localhost/127.0.0.1; at most ten origins. Unavailable to OAuth apps.

Wire operation: `PUT /v1/accounts/{accountId}/embed-origins/{originId}`.

Request body (`application/json`; optional fields may be omitted):
```json
{
  "origin": "https://app.example.com"
}
```

Response `data` inside `{status,message,data}`:
```json
{
  "id": "id-placeholder",
  "origin": "https://app.example.com",
  "created_at": "2026-11-04T12:05:00Z"
}
```

### `embeddedSigning.deleteOrigin`

Remove embed origin. Removing the last origin prevents new embed sessions.

Wire operation: `DELETE /v1/accounts/{accountId}/embed-origins/{originId}`.

Request body: none.

Response `data` inside `{status,message,data}`:
```json
[]
```

### `embeddedSigning.createSession`

Create an embed session. Requires an embedded signer. Load once within five minutes; do not prefetch or log the URL. Each reopening needs a new session.

Wire operation: `POST /v1/documents/{documentId}/assignments/{assignmentId}/signers/{signerId}/embed-sessions`.

Request body: none.

Response `data` inside `{status,message,data}`:
```json
{
  "url": "https://example.com/resource",
  "expires_at": "2026-11-04T12:05:00Z"
}
```

### `envelopes.list`

List envelopes. Query: page, per-page, search and status=open|closed. Newest first.

Wire operation: `GET /v1/accounts/{accountId}/envelopes`.

Optional query/header parameters: `page` (query), `per-page` (query), `search` (query), `status` (query).

Request body: none.

Response `data` inside `{status,message,data}`:
```json
[
  {
    "resource": "envelope",
    "id": "id-placeholder",
    "name": "Contrato de prestação de serviços",
    "message": "Segue o contrato e seus anexos.",
    "documents": [
      {
        "id": "id-placeholder",
        "position": 1,
        "name": "contrato.pdf",
        "status": "pending_signature",
        "artifacts": {
          "original": "https://example.com/resource",
          "thumbnail": "https://example.com/resource"
        },
        "signers": [
          {
            "id": "id-placeholder",
            "full_name": "Example Person",
            "email": "person@example.com",
            "whatsapp_phone_number": "+5548999990001",
            "completed": false,
            "signed_at": "2026-11-06T14:30:00Z"
          }
        ]
      }
    ],
    "counts": {
      "certificated": 1,
      "pending_signature": 2
    },
    "signing_urls": [
      {
        "signer_id": "example",
        "url": "https://example.com/resource",
        "embedded": false
      }
    ],
    "created_at": "2026-11-04T12:00:00Z"
  }
]
```

### `envelopes.create`

Create envelope. Upload every PDF and wait for metadata first. A successful creation does not guarantee invitation delivery. The optional Idempotency-Key header prevents duplicate creation on explicit retries.

Wire operation: `POST /v1/accounts/{accountId}/envelopes`.

Optional query/header parameters: `Idempotency-Key` (header).

Request body (`application/json`; optional fields may be omitted):
```json
{
  "name": "Contrato de prestação de serviços",
  "message": "Segue o contrato e seus anexos.",
  "max_total_credits": 1.5,
  "signers": [
    {
      "id": "id-placeholder",
      "verification_method": "Email",
      "notification_methods": [
        "Email"
      ],
      "embedded": true
    }
  ],
  "documents": [
    {
      "id": "id-placeholder",
      "method": "virtual",
      "entries": [
        {
          "page_id": "example",
          "fields": [
            {
              "signer_id": "example",
              "field_id": "example",
              "display_settings": {
                "left": 69,
                "top": 282,
                "width": 421,
                "height": 45.86,
                "fontFamily": "Arial",
                "fontSize": 22,
                "backgroundColor": "#D5EBFF"
              }
            }
          ]
        }
      ],
      "signer_ids": [
        "example"
      ],
      "expires_at": "example",
      "copy_receiver_ids": [
        "example"
      ]
    }
  ]
}
```

Response `data` inside `{status,message,data}`:
```json
{
  "resource": "envelope",
  "id": "id-placeholder",
  "name": "Contrato de prestação de serviços",
  "message": "Segue o contrato e seus anexos.",
  "documents": [
    {
      "id": "id-placeholder",
      "position": 1,
      "name": "contrato.pdf",
      "status": "pending_signature",
      "artifacts": {
        "original": "https://example.com/resource",
        "thumbnail": "https://example.com/resource"
      },
      "signers": [
        {
          "id": "id-placeholder",
          "full_name": "Example Person",
          "email": "person@example.com",
          "whatsapp_phone_number": "+5548999990001",
          "completed": false,
          "signed_at": "2026-11-06T14:30:00Z"
        }
      ]
    }
  ],
  "counts": {
    "certificated": 1,
    "pending_signature": 2
  },
  "signing_urls": [
    {
      "signer_id": "example",
      "url": "https://example.com/resource",
      "embedded": false
    }
  ],
  "created_at": "2026-11-04T12:00:00Z"
}
```

### `envelopes.estimateCost`

Estimate envelope cost. Creates and charges nothing. One document allowance is charged per envelope; certificate verification is charged per document.

Wire operation: `POST /v1/accounts/{accountId}/envelopes/estimate-cost`.

Request body (`application/json`; optional fields may be omitted):
```json
{
  "name": "Contrato de prestação de serviços",
  "message": "Segue o contrato e seus anexos.",
  "max_total_credits": 1.5,
  "signers": [
    {
      "id": "id-placeholder",
      "verification_method": "Email",
      "notification_methods": [
        "Email"
      ],
      "embedded": true
    }
  ],
  "documents": [
    {
      "id": "id-placeholder",
      "method": "virtual",
      "entries": [
        {
          "page_id": "example",
          "fields": [
            {
              "signer_id": "example",
              "field_id": "example",
              "display_settings": {
                "left": 69,
                "top": 282,
                "width": 421,
                "height": 45.86,
                "fontFamily": "Arial",
                "fontSize": 22,
                "backgroundColor": "#D5EBFF"
              }
            }
          ]
        }
      ],
      "signer_ids": [
        "example"
      ],
      "expires_at": "example",
      "copy_receiver_ids": [
        "example"
      ]
    }
  ]
}
```

Response `data` inside `{status,message,data}`:
```json
{
  "documents": 1,
  "credits": 0.9,
  "needs_extra_document": true,
  "extra_document_cost": 1,
  "total_credits": 1.9,
  "breakdown": [
    {
      "code": "NotificationWhatsapp",
      "name": "Whatsapp Notification",
      "cost": 0.9,
      "quantity": 2,
      "unit_cost": 0.45
    }
  ],
  "document_balance": 0,
  "credit_balance": 10,
  "has_sufficient_resources": true,
  "blocking_reason": null,
  "message": "example"
}
```

### `envelopes.get`

Get envelope.

Wire operation: `GET /v1/accounts/{accountId}/envelopes/{envelopeId}`.

Request body: none.

Response `data` inside `{status,message,data}`:
```json
{
  "resource": "envelope",
  "id": "id-placeholder",
  "name": "Contrato de prestação de serviços",
  "message": "Segue o contrato e seus anexos.",
  "documents": [
    {
      "id": "id-placeholder",
      "position": 1,
      "name": "contrato.pdf",
      "status": "pending_signature",
      "artifacts": {
        "original": "https://example.com/resource",
        "thumbnail": "https://example.com/resource"
      },
      "signers": [
        {
          "id": "id-placeholder",
          "full_name": "Example Person",
          "email": "person@example.com",
          "whatsapp_phone_number": "+5548999990001",
          "completed": false,
          "signed_at": "2026-11-06T14:30:00Z"
        }
      ]
    }
  ],
  "counts": {
    "certificated": 1,
    "pending_signature": 2
  },
  "signing_urls": [
    {
      "signer_id": "example",
      "url": "https://example.com/resource",
      "embedded": false
    }
  ],
  "created_at": "2026-11-04T12:00:00Z"
}
```

### `envelopes.update`

Update envelope. Omitted fields remain unchanged; clearMessage sends explicit null. Reordering requires every current document exactly once.

Wire operation: `PATCH /v1/accounts/{accountId}/envelopes/{envelopeId}`.

Request body (`application/json`; optional fields may be omitted):
```json
{
  "name": "Contrato de prestação de serviços",
  "message": "Segue o contrato e seus anexos.",
  "document_ids": [
    "example"
  ]
}
```

Response `data` inside `{status,message,data}`:
```json
{
  "resource": "envelope",
  "id": "id-placeholder",
  "name": "Contrato de prestação de serviços",
  "message": "Segue o contrato e seus anexos.",
  "documents": [
    {
      "id": "id-placeholder",
      "position": 1,
      "name": "contrato.pdf",
      "status": "pending_signature",
      "artifacts": {
        "original": "https://example.com/resource",
        "thumbnail": "https://example.com/resource"
      },
      "signers": [
        {
          "id": "id-placeholder",
          "full_name": "Example Person",
          "email": "person@example.com",
          "whatsapp_phone_number": "+5548999990001",
          "completed": false,
          "signed_at": "2026-11-06T14:30:00Z"
        }
      ]
    }
  ],
  "counts": {
    "certificated": 1,
    "pending_signature": 2
  },
  "signing_urls": [
    {
      "signer_id": "example",
      "url": "https://example.com/resource",
      "embedded": false
    }
  ],
  "created_at": "2026-11-04T12:00:00Z"
}
```

### `envelopes.delete`

Delete envelope. Every document must be closed first. Documents, certificates and downloads are preserved.

Wire operation: `DELETE /v1/accounts/{accountId}/envelopes/{envelopeId}`.

Request body: none.

Response `data` inside `{status,message,data}`:
```json
[]
```

### `envelopes.activities`

List envelope activities. Query: page, per-page and sort=id|-id. Includes documents removed from the envelope, except deleted documents.

Wire operation: `GET /v1/accounts/{accountId}/envelopes/{envelopeId}/activities`.

Optional query/header parameters: `sort` (query), `page` (query), `per-page` (query).

Request body: none.

Response `data` inside `{status,message,data}`:
```json
[
  {
    "id": 4,
    "event": "assignment_created",
    "message": "Assignment created by John Smith.",
    "payload": [],
    "origin": {
      "ip": "172.19.0.1",
      "user-agent": "example"
    },
    "created_at": "2022-07-19T19:28:13Z",
    "document_id": "document-id-placeholder",
    "document_name": "Contrato.pdf"
  }
]
```

### `envelopes.download`

Download envelope artifacts. ZIP of available document artifacts, in envelope order; 404 when no document has the artifact.

Wire operation: `GET /v1/accounts/{accountId}/envelopes/{envelopeId}/download/{artifactName}`.

Request body: none.

Response: binary bytes or an empty successful response, as the operation specifies.

### `envelopes.cancel`

Cancel envelope. Cancels eligible open documents and notifies their participants. Closed or certification-queued documents remain unchanged.

Wire operation: `PUT /v1/accounts/{accountId}/envelopes/{envelopeId}/cancel`.

Request body: none.

Response `data` inside `{status,message,data}`:
```json
[
  {
    "id": "id-placeholder",
    "status": "rejected_by_user",
    "cancelled": true
  }
]
```

### `envelopes.resendNotification`

Remind envelope signer. Sends one reminder for the whole envelope. Paid notification channels are charged again.

Wire operation: `PUT /v1/accounts/{accountId}/envelopes/{envelopeId}/signers/{signerId}/resend`.

Request body: none.

Response `data` inside `{status,message,data}`:
```json
{
  "is_sent": true,
  "envelope_id": "example",
  "signer_id": "example"
}
```

### `envelopes.addDocument`

Add document to envelope. Only existing envelope signers may be selected; the server validates their channels and certificate isolation. Sends invitations for the added document.

Wire operation: `POST /v1/accounts/{accountId}/envelopes/{envelopeId}/documents`.

Request body (`application/json`; optional fields may be omitted):
```json
{
  "id": "id-placeholder",
  "method": "virtual",
  "entries": [
    {
      "page_id": "example",
      "fields": [
        {
          "signer_id": "example",
          "field_id": "example",
          "display_settings": {
            "left": 69,
            "top": 282,
            "width": 421,
            "height": 45.86,
            "fontFamily": "Arial",
            "fontSize": 22,
            "backgroundColor": "#D5EBFF"
          }
        }
      ]
    }
  ],
  "signer_ids": [
    "example"
  ],
  "expires_at": "example",
  "copy_receiver_ids": [
    "example"
  ],
  "max_total_credits": 1.5
}
```

Response `data` inside `{status,message,data}`:
```json
{
  "resource": "envelope",
  "id": "id-placeholder",
  "name": "Contrato de prestação de serviços",
  "message": "Segue o contrato e seus anexos.",
  "documents": [
    {
      "id": "id-placeholder",
      "position": 1,
      "name": "contrato.pdf",
      "status": "pending_signature",
      "artifacts": {
        "original": "https://example.com/resource",
        "thumbnail": "https://example.com/resource"
      },
      "signers": [
        {
          "id": "id-placeholder",
          "full_name": "Example Person",
          "email": "person@example.com",
          "whatsapp_phone_number": "+5548999990001",
          "completed": false,
          "signed_at": "2026-11-06T14:30:00Z"
        }
      ]
    }
  ],
  "counts": {
    "certificated": 1,
    "pending_signature": 2
  },
  "signing_urls": [
    {
      "signer_id": "example",
      "url": "https://example.com/resource",
      "embedded": false
    }
  ],
  "created_at": "2026-11-04T12:00:00Z"
}
```

### `envelopes.removeDocument`

Take document out of envelope. Cancels and removes an open document; the other documents continue. Participants are notified.

Wire operation: `DELETE /v1/accounts/{accountId}/envelopes/{envelopeId}/documents/{documentId}`.

Request body: none.

Response `data` inside `{status,message,data}`:
```json
{
  "resource": "envelope",
  "id": "id-placeholder",
  "name": "Contrato de prestação de serviços",
  "message": "Segue o contrato e seus anexos.",
  "documents": [
    {
      "id": "id-placeholder",
      "position": 1,
      "name": "contrato.pdf",
      "status": "pending_signature",
      "artifacts": {
        "original": "https://example.com/resource",
        "thumbnail": "https://example.com/resource"
      },
      "signers": [
        {
          "id": "id-placeholder",
          "full_name": "Example Person",
          "email": "person@example.com",
          "whatsapp_phone_number": "+5548999990001",
          "completed": false,
          "signed_at": "2026-11-06T14:30:00Z"
        }
      ]
    }
  ],
  "counts": {
    "certificated": 1,
    "pending_signature": 2
  },
  "signing_urls": [
    {
      "signer_id": "example",
      "url": "https://example.com/resource",
      "embedded": false
    }
  ],
  "created_at": "2026-11-04T12:00:00Z"
}
```

### `fields.create`

Creates a definition with `POST /accounts/{accountId}/fields`.

Request body:
`{"name":"Employee CPF","type":"cpf","regex":null,"is_required":true}`.

Wire operation: `POST /v1/accounts/{accountId}/fields`.

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

@param request Required name/type and optional regex/required state.
@param accountId Account override; otherwise the resource default is used.
@return Created field definition.
@throws ValidationException when the account, name, or type is blank.

### `fields.list`

Lists definitions with `GET /accounts/{accountId}/fields`.

Query shape: optional `include_inactive` and `include_standard` booleans; request body: none.
Response `data` is an unpaginated array of complete [FieldDefinition] objects:

Wire operation: `GET /v1/accounts/{accountId}/fields`.

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

@param includeInactive Whether inactive definitions should be included.
@param includeStandard Whether built-in signature, initial, and signature-date definitions should be included.
@param accountId Account override; otherwise the resource default is used.
@return Field definitions matching the query.

### `fields.get`

Retrieves one definition with `GET /accounts/{accountId}/fields/{fieldId}`.

Request body/query: none. Response `data` contains `resource`, `id`, `name`, `type`, nullable `regex`,
`is_pre_defined`, `is_active`, `is_required`, `is_standard`, `is_read_only`, and `is_visible`.

Wire operation: `GET /v1/accounts/{accountId}/fields/{fieldId}`.

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

@param fieldId Field definition identifier.
@param accountId Account override; otherwise the resource default is used.
@return Complete field definition.
@throws ValidationException when the account or field identifier is blank.

### `fields.update`

Updates a definition with `PUT /accounts/{accountId}/fields/{fieldId}`.

Request body is a non-empty subset of `{"name":"New name","regex":null,"is_active":false}`.
Response `data` is the complete updated [FieldDefinition] shape documented by [create].

Wire operation: `PUT /v1/accounts/{accountId}/fields/{fieldId}`.

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

@param fieldId Field definition identifier.
@param request Name, regex, explicit regex removal, and/or active-state changes.
@param accountId Account override; otherwise the resource default is used.
@return Updated field definition.
@throws ValidationException for a blank field ID/name, conflicting regex changes, or an empty update.

### `fields.delete`

Deletes a definition with `DELETE /accounts/{accountId}/fields/{fieldId}`.

Request body/query: none. Response is the standard success envelope without a typed `data` payload.
Definitions already used by assignments may need to be deactivated with [update] instead.

Wire operation: `DELETE /v1/accounts/{accountId}/fields/{fieldId}`.

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
[]
```

@param fieldId Field definition identifier.
@param accountId Account override; otherwise the resource default is used.
@throws ValidationException when the account or field identifier is blank.

### `fields.validate`

Validates one value with `POST /accounts/{accountId}/fields/{fieldId}/validate`.

Request body: `{"value":"400.676.228-36"}`; request query: none.
Response `data`: `{"type":"cpf","success":true,"error_message":""}`.

Wire operation: `POST /v1/accounts/{accountId}/fields/{fieldId}/validate`.

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

@param fieldId Field definition whose type/regex validates the value.
@param value JSON-compatible input value; the `value` key is retained when this is `null`.
@param accountId Account override; otherwise the resource default is used.
@return Type, success flag, and error message from the validator.
@throws ValidationException when the account or field identifier is blank.

### `fields.validateMultiple`

Validates values in one request with `POST /accounts/{accountId}/fields/validate-multiple`.

Request body: `[{"field_id":"field-1","value":"123"}]`; request query: none.
`[{"field_id":"field-1","type":"cpf","success":false,"error_message":"Invalid CPF."}]`.

Wire operation: `POST /v1/accounts/{accountId}/fields/validate-multiple`.

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

@param entries Non-empty field identifier/value array sent directly as the JSON body.
@param accountId Account override; otherwise the resource default is used.
@return One validation result for each request entry.
@throws ValidationException when the account is blank, the array is empty, or an entry has a blank field ID.

### `fields.listTypes`

Lists supported types with `GET /field-types`.

Request body/query: none. Response `data`: `[{"type":"cpf","name":"CPF"}]`.

Wire operation: `GET /v1/field-types`.

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

@return Machine-readable type codes and human-readable names.

### `signerDocuments.self`

Fetches the signer identified by an access code.

Wire request: `GET /signers/self?signer-access-code={code}`, with no body or credential
header. The typed [SignerSelf] response represents:
Signature-state flags remain nullable because older responses can omit them.

Wire operation: `GET /v1/signers/self`.

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
  "verification_method": "Email",
  "has_signature": true,
  "has_initial": true,
  "is_signature_reusable": true
}
```

@param signerAccessCode One-time code from the signer's signing link.
@return The signer's identity and stored-signature state.

### `signerDocuments.getCurrent`

Fetches the current document for a known signer.

Wire request: `GET /signers/{signerId}/document?signer-access-code={code}`, with both path
and query values URL-encoded. The [DocumentDetails] response has the full document shape:

Wire operation: `GET /v1/signers/{signerId}/document`.

Request body: none.

Response `data` inside `{status,message,data}`:
```json
{
  "resource": "document",
  "id": "id-placeholder",
  "account_id": "account-id-placeholder",
  "template_id": null,
  "name": "document.pdf",
  "status": "metadata_ready",
  "artifacts": {
    "original": "https://example.com/resource"
  },
  "is_closed": false,
  "signing_url": "https://example.com/resource",
  "verification_code": "verification-code-placeholder",
  "verification_url": "https://example.com/resource",
  "decline_reason": null,
  "declined_by": {
    "resource": "signer",
    "id": "id-placeholder",
    "full_name": "Example Person",
    "email": "person@example.com",
    "whatsapp_phone_number": "+5548999990000",
    "government_id": "government-id-placeholder",
    "has_accepted_terms": false
  },
  "tags": [
    {
      "id": "example",
      "name": "example",
      "color": "ff8800"
    }
  ],
  "assignment": {
    "resource": "assignment",
    "id": "id-placeholder",
    "document_id": "document-id-placeholder",
    "created_at": "2026-06-03T03:54:16Z",
    "sender_email": "person@example.com",
    "method": "virtual",
    "expires_at": null,
    "message": "example",
    "signers": [
      {
        "resource": "signer",
        "id": "id-placeholder",
        "full_name": "Example Person",
        "email": "person@example.com",
        "whatsapp_phone_number": "+5548999990000",
        "government_id": "government-id-placeholder",
        "has_accepted_terms": false,
        "verification_method": "Email",
        "notification_methods": [
          "example"
        ],
        "step": 1,
        "notified": true,
        "identity_verification_status": "InReview",
        "completed": true,
        "notification_history": [
          {
            "event": "signature_request",
            "status": "failed",
            "error_code": "MailboxFull",
            "error_message": "A caixa de entrada do destinatário está cheia. Peça ao signatário para liberar espaço ou use outro e-mail.",
            "sent_at": "2026-07-07T12:00:00Z",
            "failed_at": "2026-07-07T12:05:00Z"
          }
        ]
      }
    ],
    "copy_receivers": [
      {}
    ],
    "items": [
      {
        "id": "example",
        "page": {
          "id": "id-placeholder",
          "number": 1,
          "height": 2100,
          "width": 1275,
          "download_url": "https://example.com/resource"
        },
        "signer": {},
        "field": {},
        "display_settings": null,
        "value": null,
        "completed": true
      }
    ],
    "summary": {
      "signer_count": 1,
      "completed_count": 1,
      "signers": [
        {}
      ]
    },
    "signing_urls": [
      {
        "signer_id": "example",
        "url": "https://example.com/resource",
        "embedded": false
      }
    ]
  },
  "pages": [
    {
      "id": "id-placeholder",
      "number": 1,
      "height": 2100,
      "width": 1275,
      "download_url": "https://example.com/resource"
    }
  ],
  "envelope_id": null,
  "envelope": {
    "id": "id-placeholder",
    "name": "Contrato de prestação de serviços"
  },
  "has_contract": true,
  "created_at": "2026-06-03T03:54:16Z",
  "updated_at": "2026-06-03T03:54:16Z",
  "current_signer": {
    "resource": "signer",
    "id": "id-placeholder",
    "full_name": "Example Person",
    "email": "person@example.com",
    "whatsapp_phone_number": "+5548999990000",
    "government_id": "government-id-placeholder",
    "has_accepted_terms": false,
    "verification_method": "Email",
    "notification_methods": [
      "example"
    ],
    "completed": false
  },
  "is_signer_access_closed": false,
  "certified_copy": {
    "available": false,
    "notification_method": "Email",
    "resends_left": null
  }
}
```
@param signerId Signer ID placed in the URL path.
@param signerAccessCode One-time code sent only as `signer-access-code` in the query.
@return The current document, including any assignment and signer-visible items.

### `signerDocuments.getCurrent`

Get signer's document. Set expandEnvelope to request envelope identity.

Wire operation: `GET /v1/signers/{signerId}/document`.

Request body: none.

Response `data` inside `{status,message,data}`:
```json
{
  "resource": "document",
  "id": "id-placeholder",
  "account_id": "account-id-placeholder",
  "template_id": null,
  "name": "document.pdf",
  "status": "metadata_ready",
  "artifacts": {
    "original": "https://example.com/resource"
  },
  "is_closed": false,
  "signing_url": "https://example.com/resource",
  "verification_code": "verification-code-placeholder",
  "verification_url": "https://example.com/resource",
  "decline_reason": null,
  "declined_by": {
    "resource": "signer",
    "id": "id-placeholder",
    "full_name": "Example Person",
    "email": "person@example.com",
    "whatsapp_phone_number": "+5548999990000",
    "government_id": "government-id-placeholder",
    "has_accepted_terms": false
  },
  "tags": [
    {
      "id": "example",
      "name": "example",
      "color": "ff8800"
    }
  ],
  "assignment": {
    "resource": "assignment",
    "id": "id-placeholder",
    "document_id": "document-id-placeholder",
    "created_at": "2026-06-03T03:54:16Z",
    "sender_email": "person@example.com",
    "method": "virtual",
    "expires_at": null,
    "message": "example",
    "signers": [
      {
        "resource": "signer",
        "id": "id-placeholder",
        "full_name": "Example Person",
        "email": "person@example.com",
        "whatsapp_phone_number": "+5548999990000",
        "government_id": "government-id-placeholder",
        "has_accepted_terms": false,
        "verification_method": "Email",
        "notification_methods": [
          "example"
        ],
        "step": 1,
        "notified": true,
        "identity_verification_status": "InReview",
        "completed": true,
        "notification_history": [
          {
            "event": "signature_request",
            "status": "failed",
            "error_code": "MailboxFull",
            "error_message": "A caixa de entrada do destinatário está cheia. Peça ao signatário para liberar espaço ou use outro e-mail.",
            "sent_at": "2026-07-07T12:00:00Z",
            "failed_at": "2026-07-07T12:05:00Z"
          }
        ]
      }
    ],
    "copy_receivers": [
      {}
    ],
    "items": [
      {
        "id": "example",
        "page": {
          "id": "id-placeholder",
          "number": 1,
          "height": 2100,
          "width": 1275,
          "download_url": "https://example.com/resource"
        },
        "signer": {},
        "field": {},
        "display_settings": null,
        "value": null,
        "completed": true
      }
    ],
    "summary": {
      "signer_count": 1,
      "completed_count": 1,
      "signers": [
        {}
      ]
    },
    "signing_urls": [
      {
        "signer_id": "example",
        "url": "https://example.com/resource",
        "embedded": false
      }
    ]
  },
  "pages": [
    {
      "id": "id-placeholder",
      "number": 1,
      "height": 2100,
      "width": 1275,
      "download_url": "https://example.com/resource"
    }
  ],
  "envelope_id": null,
  "envelope": {
    "id": "id-placeholder",
    "name": "Contrato de prestação de serviços"
  },
  "has_contract": true,
  "created_at": "2026-06-03T03:54:16Z",
  "updated_at": "2026-06-03T03:54:16Z",
  "current_signer": {
    "resource": "signer",
    "id": "id-placeholder",
    "full_name": "Example Person",
    "email": "person@example.com",
    "whatsapp_phone_number": "+5548999990000",
    "government_id": "government-id-placeholder",
    "has_accepted_terms": false,
    "verification_method": "Email",
    "notification_methods": [
      "example"
    ],
    "completed": false
  },
  "is_signer_access_closed": false,
  "certified_copy": {
    "available": false,
    "notification_method": "Email",
    "resends_left": null
  }
}
```

### `signerDocuments.getAssignment`

Opens the assignment as the signer sees it and records the document as viewed.

Wire request: `GET /sign?signer-access-code={code}&has_accepted_terms={boolean}`. The terms
query is omitted when [hasAcceptedTerms] is null and preserves an explicit false. A `409`
means document preparation is still in progress. The [DocumentDetails] response contains the
same complete document shape as [getCurrent], with signer-facing assignment content such as:
A `DigitalCertificate` signer must call [confirmData] and [acceptTerms] before this request;
accepting terms only through this request's query is too late for that verification method.

Wire operation: `GET /v1/sign`.

Request body: none.

Response `data` inside `{status,message,data}`:
```json
{
  "resource": "document",
  "id": "id-placeholder",
  "account_id": "account-id-placeholder",
  "template_id": null,
  "name": "document.pdf",
  "status": "metadata_ready",
  "artifacts": {
    "original": "https://example.com/resource"
  },
  "is_closed": false,
  "signing_url": "https://example.com/resource",
  "verification_code": "verification-code-placeholder",
  "verification_url": "https://example.com/resource",
  "decline_reason": null,
  "declined_by": {
    "resource": "signer",
    "id": "id-placeholder",
    "full_name": "Example Person",
    "email": "person@example.com",
    "whatsapp_phone_number": "+5548999990000",
    "government_id": "government-id-placeholder",
    "has_accepted_terms": false
  },
  "tags": [
    {
      "id": "example",
      "name": "example",
      "color": "ff8800"
    }
  ],
  "assignment": {
    "resource": "assignment",
    "id": "id-placeholder",
    "document_id": "document-id-placeholder",
    "created_at": "2026-06-03T03:54:16Z",
    "sender_email": "person@example.com",
    "method": "virtual",
    "expires_at": null,
    "message": "example",
    "signers": [
      {
        "resource": "signer",
        "id": "id-placeholder",
        "full_name": "Example Person",
        "email": "person@example.com",
        "whatsapp_phone_number": "+5548999990000",
        "government_id": "government-id-placeholder",
        "has_accepted_terms": false,
        "verification_method": "Email",
        "notification_methods": [
          "example"
        ],
        "step": 1,
        "notified": true,
        "identity_verification_status": "InReview",
        "completed": true,
        "notification_history": [
          {
            "event": "signature_request",
            "status": "failed",
            "error_code": "MailboxFull",
            "error_message": "A caixa de entrada do destinatário está cheia. Peça ao signatário para liberar espaço ou use outro e-mail.",
            "sent_at": "2026-07-07T12:00:00Z",
            "failed_at": "2026-07-07T12:05:00Z"
          }
        ]
      }
    ],
    "copy_receivers": [
      {}
    ],
    "items": [
      {
        "id": "example",
        "page": {
          "id": "id-placeholder",
          "number": 1,
          "height": 2100,
          "width": 1275,
          "download_url": "https://example.com/resource"
        },
        "signer": {},
        "field": {},
        "display_settings": null,
        "value": null,
        "completed": true
      }
    ],
    "summary": {
      "signer_count": 1,
      "completed_count": 1,
      "signers": [
        {}
      ]
    },
    "signing_urls": [
      {
        "signer_id": "example",
        "url": "https://example.com/resource",
        "embedded": false
      }
    ]
  },
  "pages": [
    {
      "id": "id-placeholder",
      "number": 1,
      "height": 2100,
      "width": 1275,
      "download_url": "https://example.com/resource"
    }
  ],
  "envelope_id": null,
  "envelope": {
    "id": "id-placeholder",
    "name": "Contrato de prestação de serviços"
  },
  "has_contract": true,
  "created_at": "2026-06-03T03:54:16Z",
  "updated_at": "2026-06-03T03:54:16Z",
  "current_signer": {
    "resource": "signer",
    "id": "id-placeholder",
    "full_name": "Example Person",
    "email": "person@example.com",
    "whatsapp_phone_number": "+5548999990000",
    "government_id": "government-id-placeholder",
    "has_accepted_terms": false,
    "verification_method": "Email",
    "notification_methods": [
      "example"
    ],
    "completed": false
  },
  "is_signer_access_closed": false,
  "certified_copy": {
    "available": false,
    "notification_method": "Email",
    "resends_left": null
  }
}
```
@param signerAccessCode One-time signer code sent as a query parameter.
@param hasAcceptedTerms Optional terms-acceptance value sent as `has_accepted_terms`.
@return The document, pages, assignment, signers, and signer-visible assignment items.

### `signerDocuments.getAssignment`

View document to sign. Set expandEnvelope to request envelope identity; closed codes return a reduced projection with certified_copy availability.

Wire operation: `GET /v1/sign`.

Request body: none.

Response `data` inside `{status,message,data}`:
```json
{
  "resource": "document",
  "id": "id-placeholder",
  "account_id": "account-id-placeholder",
  "template_id": null,
  "name": "document.pdf",
  "status": "metadata_ready",
  "artifacts": {
    "original": "https://example.com/resource"
  },
  "is_closed": false,
  "signing_url": "https://example.com/resource",
  "verification_code": "verification-code-placeholder",
  "verification_url": "https://example.com/resource",
  "decline_reason": null,
  "declined_by": {
    "resource": "signer",
    "id": "id-placeholder",
    "full_name": "Example Person",
    "email": "person@example.com",
    "whatsapp_phone_number": "+5548999990000",
    "government_id": "government-id-placeholder",
    "has_accepted_terms": false
  },
  "tags": [
    {
      "id": "example",
      "name": "example",
      "color": "ff8800"
    }
  ],
  "assignment": {
    "resource": "assignment",
    "id": "id-placeholder",
    "document_id": "document-id-placeholder",
    "created_at": "2026-06-03T03:54:16Z",
    "sender_email": "person@example.com",
    "method": "virtual",
    "expires_at": null,
    "message": "example",
    "signers": [
      {
        "resource": "signer",
        "id": "id-placeholder",
        "full_name": "Example Person",
        "email": "person@example.com",
        "whatsapp_phone_number": "+5548999990000",
        "government_id": "government-id-placeholder",
        "has_accepted_terms": false,
        "verification_method": "Email",
        "notification_methods": [
          "example"
        ],
        "step": 1,
        "notified": true,
        "identity_verification_status": "InReview",
        "completed": true,
        "notification_history": [
          {
            "event": "signature_request",
            "status": "failed",
            "error_code": "MailboxFull",
            "error_message": "A caixa de entrada do destinatário está cheia. Peça ao signatário para liberar espaço ou use outro e-mail.",
            "sent_at": "2026-07-07T12:00:00Z",
            "failed_at": "2026-07-07T12:05:00Z"
          }
        ]
      }
    ],
    "copy_receivers": [
      {}
    ],
    "items": [
      {
        "id": "example",
        "page": {
          "id": "id-placeholder",
          "number": 1,
          "height": 2100,
          "width": 1275,
          "download_url": "https://example.com/resource"
        },
        "signer": {},
        "field": {},
        "display_settings": null,
        "value": null,
        "completed": true
      }
    ],
    "summary": {
      "signer_count": 1,
      "completed_count": 1,
      "signers": [
        {}
      ]
    },
    "signing_urls": [
      {
        "signer_id": "example",
        "url": "https://example.com/resource",
        "embedded": false
      }
    ]
  },
  "pages": [
    {
      "id": "id-placeholder",
      "number": 1,
      "height": 2100,
      "width": 1275,
      "download_url": "https://example.com/resource"
    }
  ],
  "envelope_id": null,
  "envelope": {
    "id": "id-placeholder",
    "name": "Contrato de prestação de serviços"
  },
  "has_contract": true,
  "created_at": "2026-06-03T03:54:16Z",
  "updated_at": "2026-06-03T03:54:16Z",
  "current_signer": {
    "resource": "signer",
    "id": "id-placeholder",
    "full_name": "Example Person",
    "email": "person@example.com",
    "whatsapp_phone_number": "+5548999990000",
    "government_id": "government-id-placeholder",
    "has_accepted_terms": false,
    "verification_method": "Email",
    "notification_methods": [
      "example"
    ],
    "completed": false
  },
  "is_signer_access_closed": false,
  "certified_copy": {
    "available": false,
    "notification_method": "Email",
    "resends_left": null
  }
}
```

### `signerDocuments.sign`

Submits signer field values and signs one assignment.

Wire request:
`POST /documents/{documentId}/assignments/{assignmentId}?signer-access-code={code}` with a
JSON-array body. Virtual assignments must first call [confirmData], then send an empty array.
Digital-certificate signers cannot use this operation; the service references separate
certificate routes that are not defined by the current v1 OpenAPI. Every collect
[SignAssignmentItemRequest] is emitted in the exact API shape:
The typed result preserves the operation-defined response object, for example
`{"signed": true}`, as a `Map<String, Any>` without inventing undocumented keys.

Wire operation: `POST /v1/documents/{documentId}/assignments/{assignmentId}`.

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

@param documentId Document ID placed in the URL path.
@param assignmentId Assignment ID placed in the URL path.
@param signerAccessCode One-time signer code sent only in the query.
@param entries Assignment-item values, or an empty list for a confirmed virtual assignment.
@return The API's signing-result object.

### `signerDocuments.decline`

Declines one assignment as the signer.

Wire request:
`PUT /documents/{documentId}/assignments/{assignmentId}/reject?signer-access-code={code}`
with the complete JSON body `{"decline_reason":"Reason shown to the sender"}`. The API's
successful envelope is `{"status":200,"data":[]}` and is intentionally returned as [Unit].

Wire operation: `PUT /v1/documents/{documentId}/assignments/{assignmentId}/reject`.

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

@param documentId Document ID placed in the URL path.
@param assignmentId Assignment ID placed in the URL path.
@param signerAccessCode One-time signer code sent only in the query.
@param declineReason Required free-text reason sent as `decline_reason`.

### `signerDocuments.signMultiple`

Signs several virtual-method documents for the same signer.

Wire request: `PUT /signers/documents/sign-multiple?signer-access-code={code}` with the
complete body `{"document_ids":["doc_1","doc_2"]}`. The documented success payload is an
empty data array, `{"status":200,"data":[]}`, so this function returns [Unit].

Wire operation: `PUT /v1/signers/documents/sign-multiple`.

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

@param documentIds Non-empty document-ID list serialized as `document_ids`.
@param signerAccessCode One-time signer code sent only in the query.

### `signerDocuments.declineMultiple`

Declines several documents with one shared reason.

Wire request: `PUT /signers/documents/decline-multiple?signer-access-code={code}` with the
complete body
`{"document_ids":["doc_1","doc_2"],"decline_reason":"Unfavorable terms"}`. The documented
`{"status":200,"data":[]}` success response is returned as [Unit].

Wire operation: `PUT /v1/signers/documents/decline-multiple`.

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

@param documentIds Non-empty document-ID list serialized as `document_ids`.
@param declineReason Required reason serialized as `decline_reason`.
@param signerAccessCode One-time signer code sent only in the query.

### `signerDocuments.verifyEmail`

Verifies the one-time password delivered to a signer.

Wire request: `POST /verify?signer-access-code={code}`. [request] is the entire JSON body,
`{"verification-code":"123456"}`; the body never contains `signer-access-code`. The API's
bare success envelope is validated and returned as [Unit].

Wire operation: `POST /v1/verify`.

Request body (`application/json`; optional members may be omitted):
```json
{
  "verification-code": "123456"
}
```

Response 200 body; optional members depend on document state and permissions:
```json
{
  "status": 200,
  "message": "example"
}
```

@param signerAccessCode Signing-link access code sent only in the query.
@param request OTP body whose exact wire key is `verification-code`.

### `signerDocuments.confirmData`

Confirm signer data. Confirms contact data and explicit terms acceptance using signer-access-code. The current API returns an empty array; this method then reads GET /signers/self to preserve its Signer return value. Legacy deployments returning a signer object need no follow-up. fullName and governmentId are compatibility extensions; use the workspace signer update for persisted identity changes.

Wire operation: `PUT /v1/documents/{documentId}/signers/confirm-data`.

Request body (`application/json`; optional fields may be omitted):
```json
{
  "email": "person@example.com",
  "whatsapp_phone_number": "+5548999990000",
  "has_accepted_terms": true
}
```

Response `data` inside `{status,message,data}`:
```json
[]
```

### `signerDocuments.acceptTerms`

Records the signer's terms acceptance.

Wire request: `PUT /signers/accept-terms?signer-access-code={code}` with no JSON body. The
bare success envelope, typically `{"status":200,"message":"Terms accepted"}`, is validated
and returned as [Unit]. Putting the access code in a body would leave this request
unauthenticated, so this function always sends it in the query.

Wire operation: `PUT /v1/signers/accept-terms`.

Request body: none.

Response 200 body; optional members depend on document state and permissions:
```json
{
  "status": 200,
  "message": "example"
}
```

@param signerAccessCode One-time signer code sent only as `signer-access-code`.

### `signerDocuments.uploadSignature`

Uploads the signer's signature or initials as raw PNG bytes.

Wire request: `POST /signature?signer-access-code={code}&type={type}&reuse={boolean}` with
`Content-Type: image/png` and [imageData] as the complete raw body (not JSON or multipart).
`type` defaults to `signature`; `reuse` is omitted when null. The bare success envelope is
validated and returned as [Unit].

Wire operation: `POST /v1/signature`.

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

@param signerAccessCode One-time signer code sent only in the query.
@param imageData Non-empty PNG file bytes sent without multipart framing.
@param type Image kind, normally [SignatureType.SIGNATURE] or [SignatureType.INITIAL].
@param reuse Optional instruction to update the signer's reusable-signature preference.

### `signerDocuments.downloadSignature`

Downloads the signer's stored signature or initials.

Wire request: `GET /signature/{type}?signer-access-code={code}` with no body. The path type
and access code are URL-encoded. The response is returned unchanged as PNG [ByteArray]
content; a missing stored image surfaces as the API's `404` exception.

Wire operation: `GET /v1/signature/{signatureType}`.

Request body: none.

Response 200: raw binary bytes (`image` media types), without a JSON envelope.

@param signerAccessCode One-time signer code sent only in the query.
@param type Stored image kind, normally [SignatureType.SIGNATURE] or [SignatureType.INITIAL].
@return Raw signature/initial image bytes.

### `signerDocuments.list`

Lists documents belonging to a signer.

Wire request: `GET /signers/{signerId}/documents?signer-access-code={code}&page={page}`
`&per-page={perPage}`. Only the OpenAPI-documented pagination fields from [params] are sent.
The response body is a `data` array of full [DocumentDetails] values, for example
`{"data":[{"id":"doc_123","account_id":"account_123","name":"Agreement.pdf",
"status":"pending_signature","artifacts":{},"tags":[],"pages":[],
"is_closed":false}]}`. `X-Pagination-*` headers become [PaginatedResult.meta], such as
`{currentPage=1, perPage=25, total=1, lastPage=1}`.

Wire operation: `GET /v1/signers/{signerId}/documents`.

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
    "status": "certificated",
    "artifacts": {},
    "is_closed": true,
    "signing_url": "https://example.com/resource",
    "decline_reason": null,
    "declined_by": {"resource": "signer", "id": "id-placeholder", "full_name": "Example", "email": null, "whatsapp_phone_number": null, "government_id": null, "has_accepted_terms": true},
    "tags": [{"id": "id-placeholder", "name": "Example"}],
    "assignment": {"resource": "assignment", "id": "id-placeholder", "sender_email": "person@example.com", "method": "virtual", "expires_at": null, "message": null, "signers": [{"resource": "signer", "id": "id-placeholder", "full_name": "Example", "email": null, "whatsapp_phone_number": null, "government_id": null, "has_accepted_terms": true, "verification_method": null, "identity_verification_status": null, "notification_methods": ["Email"], "step": 1, "notified": true, "completed": true, "notification_history": [{"event": "signature_request", "status": "sent", "error_code": null, "error_message": null, "sent_at": null, "failed_at": null}]}], "copy_receivers": [{"resource": "signer", "id": "id-placeholder", "full_name": "Example", "email": null, "whatsapp_phone_number": null, "government_id": null, "has_accepted_terms": true}], "items": [{"id": "id-placeholder", "page": {"id": "id-placeholder", "number": 1, "height": 1, "width": 1, "download_url": "https://example.com/resource"}, "signer": {"resource": "signer", "id": "id-placeholder", "full_name": "Example", "email": null, "whatsapp_phone_number": null, "government_id": null, "has_accepted_terms": true}, "field": {"resource": "field", "id": "id-placeholder", "name": "Example", "type": "text", "regex": null, "is_pre_defined": true, "is_active": true, "is_required": true, "is_standard": true, "is_read_only": true, "is_visible": true}, "display_settings": {"left": 1, "top": 1, "width": 1, "height": 1, "fontFamily": "Arial", "fontSize": 1, "backgroundColor": "#D5EBFF"}, "value": null, "completed": true}], "summary": {"signer_count": 1, "completed_count": 1, "signers": [{"resource": "signer", "id": "id-placeholder", "full_name": "Example", "email": null, "whatsapp_phone_number": null, "government_id": null, "has_accepted_terms": true}]}, "signing_urls": [{"signer_id": "signer-id-placeholder", "url": "https://example.com/resource"}], "document_id": "document-placeholder", "created_at": "2026-01-01T00:00:00Z"},
    "pages": [{"id": "id-placeholder", "number": 1, "height": 1, "width": 1, "download_url": "https://example.com/resource"}],
    "created_at": "2026-01-01T00:00:00Z",
    "updated_at": "2026-01-01T00:00:00Z",
    "verification_code": null,
    "verification_url": null
  }
]
```

@param signerId Signer ID placed in the URL path.
@param signerAccessCode One-time signer code sent only in the query.
@param params Pagination; `page` is 1-based and `perPage` is between 1 and 100.
@return Full signer documents plus optional pagination metadata.

### `signerDocuments.search`

Searches documents belonging to a signer by name.

Wire request:
`GET /signers/{signerId}/documents/search?signer-access-code={code}&search={term}`; the
`search` query is omitted when null or blank. The typed [PaginatedResult] contains the same
complete [DocumentDetails] elements as [list], for example
`{"data":[{"id":"doc_123","account_id":"account_123","name":"Agreement.pdf",
"status":"pending_signature","artifacts":{},"tags":[],"pages":[],
"is_closed":false}]}`, plus pagination metadata when the server returns pagination headers.

Wire operation: `GET /v1/signers/{signerId}/documents/search`.

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
    "status": "certificated",
    "artifacts": {},
    "is_closed": true,
    "signing_url": "https://example.com/resource",
    "decline_reason": null,
    "declined_by": {"resource": "signer", "id": "id-placeholder", "full_name": "Example", "email": null, "whatsapp_phone_number": null, "government_id": null, "has_accepted_terms": true},
    "tags": [{"id": "id-placeholder", "name": "Example"}],
    "assignment": {"resource": "assignment", "id": "id-placeholder", "sender_email": "person@example.com", "method": "virtual", "expires_at": null, "message": null, "signers": [{"resource": "signer", "id": "id-placeholder", "full_name": "Example", "email": null, "whatsapp_phone_number": null, "government_id": null, "has_accepted_terms": true, "verification_method": null, "identity_verification_status": null, "notification_methods": ["Email"], "step": 1, "notified": true, "completed": true, "notification_history": [{"event": "signature_request", "status": "sent", "error_code": null, "error_message": null, "sent_at": null, "failed_at": null}]}], "copy_receivers": [{"resource": "signer", "id": "id-placeholder", "full_name": "Example", "email": null, "whatsapp_phone_number": null, "government_id": null, "has_accepted_terms": true}], "items": [{"id": "id-placeholder", "page": {"id": "id-placeholder", "number": 1, "height": 1, "width": 1, "download_url": "https://example.com/resource"}, "signer": {"resource": "signer", "id": "id-placeholder", "full_name": "Example", "email": null, "whatsapp_phone_number": null, "government_id": null, "has_accepted_terms": true}, "field": {"resource": "field", "id": "id-placeholder", "name": "Example", "type": "text", "regex": null, "is_pre_defined": true, "is_active": true, "is_required": true, "is_standard": true, "is_read_only": true, "is_visible": true}, "display_settings": {"left": 1, "top": 1, "width": 1, "height": 1, "fontFamily": "Arial", "fontSize": 1, "backgroundColor": "#D5EBFF"}, "value": null, "completed": true}], "summary": {"signer_count": 1, "completed_count": 1, "signers": [{"resource": "signer", "id": "id-placeholder", "full_name": "Example", "email": null, "whatsapp_phone_number": null, "government_id": null, "has_accepted_terms": true}]}, "signing_urls": [{"signer_id": "signer-id-placeholder", "url": "https://example.com/resource"}], "document_id": "document-placeholder", "created_at": "2026-01-01T00:00:00Z"},
    "pages": [{"id": "id-placeholder", "number": 1, "height": 1, "width": 1, "download_url": "https://example.com/resource"}],
    "created_at": "2026-01-01T00:00:00Z",
    "updated_at": "2026-01-01T00:00:00Z",
    "verification_code": null,
    "verification_url": null
  }
]
```

@param signerId Signer ID placed in the URL path.
@param signerAccessCode One-time signer code sent only in the query.
@param search Optional free-text document-name query.
@return Matching full signer documents and any response pagination metadata.

### `signerDocuments.download`

Downloads an artifact from the documented public signer-link route.

Wire request:
`GET /signers/{signerId}/documents/{documentId}/download/{artifactName}`. This operation has
an empty OpenAPI security list: it deliberately sends no signer access code, API key, bearer
token, query parameters, or body. [artifactName] accepts `original`, `certificated`,
`certificate-page`, `pades`, or `bundle`. The response is raw PDF bytes, except `bundle`,
which is a ZIP archive.

Wire operation: `GET /v1/signers/{signerId}/documents/{documentId}/download/{artifactName}`.

Request body: none.

Response 200: raw binary bytes (`application/pdf`), without a JSON envelope.

@param signerId Signer ID placed in the public URL path.
@param documentId Document ID placed in the public URL path.
@param artifactName Artifact kind; defaults to [DocumentArtifact.ORIGINAL].
@return Unmodified PDF or ZIP response bytes.

### `signerDocuments.getIdentityVerification`

Reads the hosted identity-document verification state.

can_start is true only before a provider session exists or after it expires. InReview
permits viewing and signing while the account reviews identity; certification waits.
Declined or Abandoned requires a new document and assignment, not another session.

Wire operation: `GET /v1/signers/identity-verification`.

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`):
```json
{
  "can_start": false,
  "status": "Pending",
  "url": "https://example.com/verification#t=redacted",
  "expires_at": "2026-10-16T13:00:00Z",
  "completed_at": null
}
```

@param signerAccessCode Signer access code, sent only on the credential-free transport as a query parameter.

### `signerDocuments.startIdentityVerification`

Records explicit biometric-processing consent and opens a hosted capture session.

Open the returned URL in the browser. A live session cannot be replaced; only an Expired
session can restart. Resending a notification never replaces it, and restarting does not
charge again. Never log the hosted URL or obtain consent on the signer's behalf.

Wire operation: `POST /v1/signers/identity-verification`.

Request body: (`application/json`):
```json
{
  "consent": true
}
```

Response 200 `data` payload (inside `{status,message,data}`):
```json
{
  "id": "verification-placeholder",
  "url": "https://example.com/verification#t=redacted",
  "expires_at": "2026-10-16T13:00:00Z"
}
```

@param signerAccessCode Signer access code, sent only on the credential-free transport as a query parameter.
@param consent The signer explicitly agreed to biometric processing; false is rejected before a request.

### `signerDocuments.download`

Download the document to sign. Requires signer-access-code. Only original is available; certified copies arrive through Email or WhatsApp.

Wire operation: `GET /v1/signers/{signerId}/documents/{documentId}/download/{artifactName}`.

Request body: none.

Response: raw binary bytes without a JSON envelope.

### `signerDocuments.getTheme`

Workspace brand for the signing page. Uses signer-access-code on the credential-free transport, including before verification and after deletion.

Wire operation: `GET /v1/sign/theme`.

Request body: none.

Response `data` inside `{status,message,data}`:
```json
{
  "account_name": "Account Name",
  "primary_color": "aabbcc",
  "secondary_color": "ffffff",
  "logo": "https://example.com/resource"
}
```

### `signerDocuments.getLogo`

Workspace logo for the signing page. Uses signer-access-code; returns null when no logo exists (404).

Wire operation: `GET /v1/sign/logo`.

Request body: none.

Response: binary bytes or an empty successful response, as the operation specifies.

### `signerDocuments.resendCertifiedCopy`

Send the certified copy again. Uses signer-access-code. Sends only to the recorded contact and channel. Email: two-minute cooldown and ten per day; WhatsApp: one resend per request.

Wire operation: `POST /v1/sign/certified-copy`.

Request body (`application/json`; optional fields may be omitted):
```json
{
  "document_id": "document-id-placeholder"
}
```

Response `data` inside `{status,message,data}`:
```json
{
  "available": true,
  "notification_method": "Email",
  "resends_left": 1,
  "sent_to": "person@example.com"
}
```

### `signerDocuments.downloadCertifiedCopy`

Download the certified copy from a sent link. The token is the credential; no account credentials are sent. Optional /pades selects the certificate-signed artifact. Expired links fail instead of returning an HTML page as PDF bytes.

Wire operation: `GET /v1/signers/downloads/{token}`.

Request body: none.

Response: binary bytes or an empty successful response, as the operation specifies.

### `signerDocuments.resendCertifiedCopyByToken`

Send the certified copy again from an expired link. The expired link token is the only credential. Sends to the original contact under the same limits as resendCertifiedCopy.

Wire operation: `POST /v1/signers/downloads/{token}/resend`.

Request body: none.

Response `data` inside `{status,message,data}`:
```json
{
  "available": true,
  "notification_method": "Email",
  "resends_left": 1,
  "sent_to": "person@example.com"
}
```

### `signerDocuments.search`

Search signer's documents. Query: search and optional envelope_id; requires signer-access-code.

Wire operation: `GET /v1/signers/{signerId}/documents/search`.

Request body: none.

Response `data` inside `{status,message,data}`:
```json
[
  {
    "resource": "document",
    "id": "id-placeholder",
    "account_id": "id-placeholder",
    "template_id": null,
    "name": "document.pdf",
    "status": "metadata_ready",
    "artifacts": {
      "original": "https://api.assinafy.com.br/v1/documents/doc1/download/original"
    },
    "is_closed": false,
    "signing_url": "https://example.com/resource",
    "verification_code": "id-placeholder",
    "verification_url": "https://example.com/resource",
    "decline_reason": null,
    "declined_by": null,
    "tags": [
      {
        "id": "example",
        "name": "example",
        "color": "ff8800"
      }
    ],
    "assignment": null,
    "pages": [
      {
        "id": "id-placeholder",
        "number": 1,
        "height": 2100,
        "width": 1275,
        "download_url": "https://example.com/resource"
      }
    ],
    "envelope_id": null,
    "envelope": {
      "id": "id-placeholder",
      "name": "Contrato de prestação de serviços"
    },
    "has_contract": true,
    "created_at": "2026-06-03T03:54:16Z",
    "updated_at": "2026-06-03T03:54:16Z"
  }
]
```

### `signers.create`

Creates a signer (`POST /accounts/{accountId}/signers`). Idempotent by email: if a signer with
the same email already exists in the workspace it is returned instead of creating a duplicate,
and the API's duplicate-email error (HTTP 400) is recovered by re-fetching the existing signer.

Request body: `{"full_name":"John Dove","email":"john@example.com","whatsapp_phone_number":"+5548999990000"}`;
null fields are omitted. Response `data`:

Wire operation: `POST /v1/accounts/{accountId}/signers`.

Request body (`application/json`; optional members may be omitted):
```json
{
  "full_name": "Example",
  "email": "person@example.com",
  "whatsapp_phone_number": "+15555550100"
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
  "government_id": null,
  "has_accepted_terms": true
}
```

@param request signer details; [CreateSignerRequest.email] is validated and normalized.
@param accountId overrides the client's default account.
@return the created or pre-existing [Signer].
@throws com.assinafy.sdk.exceptions.ValidationException on an invalid email or missing account.

### `signers.get`

Fetches a signer by ID (`GET /accounts/{accountId}/signers/{signerId}`).

Request body: none. Response `data`:

Wire operation: `GET /v1/accounts/{accountId}/signers/{signerId}`.

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
  "has_accepted_terms": true
}
```

@param signerId Stable signer identifier.
@param accountId Account override; otherwise the client's default account is used.
@return Complete account signer record.
@throws ValidationException if an account or signer ID is blank.

### `signers.list`

Lists signers (`GET /accounts/{accountId}/signers`).

Request body: none. Query: `search`, `page`, `per-page`. Response `data` is an array of the
signer payload documented by [get], and `X-Pagination-*` headers are exposed through
[PaginatedResult.meta].

Wire operation: `GET /v1/accounts/{accountId}/signers`.

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
    "government_id": null,
    "has_accepted_terms": true
  }
]
```

@param params Search and pagination values; unsupported common filters are ignored.
@param accountId Account override; otherwise the client's default account is used.
@return Matching signer records and optional pagination-header metadata.

### `signers.update`

Updates a signer (`PUT /accounts/{accountId}/signers/{signerId}`). The service rejects
changes while verification is in progress. Changing an unverified email or WhatsApp number
rotates its access/verification codes, so resend the notification before continuing.

Request body carries only the supplied fields, e.g.
`{"full_name":"John Dove","email":"john@example.com","whatsapp_phone_number":"+5548999990000","government_id":"39053344705"}`.
Response `data` is the complete updated signer documented by [get]. `government_id` holds the
CPF or CNPJ that [com.assinafy.sdk.VerificationMethod.DIGITAL_CERTIFICATE] requires.

Wire operation: `PUT /v1/accounts/{accountId}/signers/{signerId}`.

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
  "government_id": null,
  "has_accepted_terms": true
}
```

@param signerId Stable signer identifier.
@param request Mutable identity/contact fields; null omits a field, an empty contact string clears it.
Verified in-flight contact channels cannot change; unverified links/OTPs rotate and need a resend.
@param accountId Account override; otherwise the client's default account is used.
@return Complete updated signer record.
@throws ValidationException if an ID is blank or a supplied email is invalid.

### `signers.delete`

Deletes a signer (`DELETE /accounts/{accountId}/signers/{signerId}`).

Request body: none. Response `data` is an empty JSON array.

Wire operation: `DELETE /v1/accounts/{accountId}/signers/{signerId}`.

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
[]
```

@param signerId Stable signer identifier.
@param accountId Account override; otherwise the client's default account is used.

### `signers.findByEmail`

Finds a signer by exact (case-insensitive) email, paging through the `search` results.
Returns `null` if none matches.

Issues repeated `GET /accounts/{accountId}/signers?search={email}` requests with no request
body, and returns the first record whose `email` matches exactly, in the shape documented by
[get]. The API's `search` is a partial match, so this narrowing is done client-side.

Wire operation: `GET /v1/accounts/{accountId}/signers`.

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
    "government_id": null,
    "has_accepted_terms": true
  }
]
```

@param email Valid email to match after trimming.
@param accountId Account override; otherwise the client's default account is used.
@return Exact signer match, or `null` when no page contains one.
@throws ValidationException if the email or account is invalid.

### `signers.listDocumentsAsWorkspace`

Lists the documents a workspace signer is party to, authenticated with the workspace API key
or bearer token. The signer must belong to the credential's workspace. For the signer's own
view with an access code, use [SignerDocumentResource.list].

Wire operation: `GET /v1/signers/{signerId}/documents` with query `page`, `per-page`.

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`), with `X-Pagination-*` headers
exposed through [PaginatedResult.meta]:
```json
[
  {
    "resource": "document",
    "id": "doc-id",
    "account_id": "account-id",
    "template_id": null,
    "name": "Agreement.pdf",
    "status": "pending_signature",
    "artifacts": {"original": "https://api.assinafy.com.br/v1/documents/doc-id/download/original"},
    "is_closed": false,
    "signing_url": "https://api.assinafy.com.br/v1/sign/doc-id",
    "decline_reason": null,
    "declined_by": null,
    "tags": [],
    "assignment": null,
    "pages": [],
    "created_at": "2026-01-01T00:00:00Z",
    "updated_at": "2026-01-01T00:00:00Z",
    "verification_code": null,
    "verification_url": null
  }
]
```

@param signerId Signer ID placed in the URL path.
@param params Pagination; only `page` and `perPage` are sent.
@return The signer's documents and optional pagination metadata.
@throws ValidationException if [signerId] is blank.

### `signers.searchDocumentsAsWorkspace`

Searches a workspace signer's documents by name, authenticated with the workspace API key or
bearer token. For the signer's own view with an access code, use [SignerDocumentResource.search].

Wire operation: `GET /v1/signers/{signerId}/documents/search` with query `search` (omitted
when null or blank).

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`):
```json
[
  {
    "resource": "document",
    "id": "doc-id",
    "account_id": "account-id",
    "template_id": null,
    "name": "Agreement.pdf",
    "status": "pending_signature",
    "artifacts": {"original": "https://api.assinafy.com.br/v1/documents/doc-id/download/original"},
    "is_closed": false,
    "signing_url": "https://api.assinafy.com.br/v1/sign/doc-id",
    "decline_reason": null,
    "declined_by": null,
    "tags": [],
    "assignment": null,
    "pages": [],
    "created_at": "2026-01-01T00:00:00Z",
    "updated_at": "2026-01-01T00:00:00Z",
    "verification_code": null,
    "verification_url": null
  }
]
```

@param signerId Signer ID placed in the URL path.
@param search Optional document-name term.
@return Matching documents.
@throws ValidationException if [signerId] is blank.

### `signers.getSelf`

Fetches the current signer through a public signing-link access code.

Request: `GET /signers/self?signer-access-code={code}` with no body and no account
credential. Response `data` is the signer payload documented by [get], with the signer's
verification state. See [SignerDocumentResource.self] for the maintained version.

Wire operation: `GET /v1/signers/self`.

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
  "verification_method": "Email",
  "has_signature": true,
  "has_initial": true,
  "is_signature_reusable": true
}
```

@param signerAccessCode One-time signer code sent only in the query string.
@return Current signer identity and signature state returned by the API.

### `signers.acceptTerms`

Records signer terms acceptance (`PUT /signers/accept-terms`).

Request: the access code only as the `signer-access-code` query parameter, with an empty
body. Response `data` is returned as a map. See [SignerDocumentResource.acceptTerms] for the
maintained version.

Wire operation: `PUT /v1/signers/accept-terms`.

Request body: none.

Response 200 body; optional members depend on document state and permissions:
```json
{
  "status": 200,
  "message": "example"
}
```

@param signerAccessCode One-time signer code sent only in the query string.
@return API success data normalized to a map.

### `signers.verifyEmail`

Verifies the signer's email or WhatsApp one-time password (`POST /verify`).

Request: the access code as the `signer-access-code` query parameter and
`{"verification-code":"123456"}` as the body — the wire key is hyphenated, and the same key
carries a code delivered over WhatsApp. Response `data` is returned as a map. See
[SignerDocumentResource.verifyEmail] for the maintained version.

Wire operation: `POST /v1/verify`.

Request body (`application/json`; optional members may be omitted):
```json
{
  "verification-code": "123456"
}
```

Response 200 body; optional members depend on document state and permissions:
```json
{
  "status": 200,
  "message": "example"
}
```

@param signerAccessCode One-time signer code sent only in the query string.
@param verificationCode Verification value sent using the exact `verification-code` JSON key.
@return API success data normalized to a map.

### `signers.uploadSignature`

Uploads the signer's signature or initial image (`POST /signature`).

Request: the access code, `type`, and `reuse` as query parameters, and the **raw image bytes**
as the body with an `image/png` or `image/jpeg` content type — not multipart form data.
Response `data` is returned as a map. See [SignerDocumentResource.uploadSignature] for the
maintained version, which accepts PNG only.

Wire operation: `POST /v1/signature`.

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

@param type `"signature"` or `"initial"`.
@param imageData Raw PNG or legacy JPEG bytes.
@param contentType MIME type of [imageData]. The current API documents `image/png`; JPEG is
retained for compatibility with older deployments.
@param reuse Optional instruction to update whether the image can be reused.
@throws ValidationException if the access code/type is blank, bytes are empty, or MIME type is unsupported.

### `signers.downloadSignature`

Downloads a signer's stored signature or initials as raw image bytes
(`GET /signature/{signatureType}`).

Request: the access code as the `signer-access-code` query parameter, no body. The response
is the raw image rather than the JSON envelope. See [SignerDocumentResource.downloadSignature]
for the maintained version.

Wire operation: `GET /v1/signature/{signatureType}`.

Request body: none.

Response 200: raw binary bytes (`image` media types), without a JSON envelope.

@param signerAccessCode One-time signer code sent only in the query string.
@param type Stored image kind, normally [com.assinafy.sdk.SignatureType.SIGNATURE] or
[com.assinafy.sdk.SignatureType.INITIAL].
@return Raw PNG or legacy JPEG response bytes.
@throws ValidationException if the signer access code or image type is blank.

### `tags.list`

Lists workspace tags with `GET /accounts/{accountId}/tags`, ordered alphabetically.

Request body: none. Optional query: `search`. Response `data`:

Wire operation: `GET /v1/accounts/{accountId}/tags`.

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

@param search Optional case-insensitive partial name filter.
@param accountId Account override; otherwise the client's default account is used.
@return Matching tags.

### `tags.create`

Creates a workspace tag with `POST /accounts/{accountId}/tags`.

Request body: `{"name":"Contracts","color":"ff8800"}`; `color` is omitted when not supplied.
Response `data` is the complete created tag:

Wire operation: `POST /v1/accounts/{accountId}/tags`.

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

@param name Non-blank name, collapsed to single spaces and limited to 64 Unicode characters.
@param color Optional six-digit hexadecimal value; a leading `#` is accepted and removed before sending.
@param accountId Account override; otherwise the client's default account is used.
@return Created tag.
@throws ValidationException for an invalid account, name, or color.

### `tags.update`

Updates a tag's [name] and/or [color] with `PUT /accounts/{accountId}/tags/{tagId}`. Omit a
parameter to leave it unchanged. Pass [clearColor] = `true` to remove the color.

Request body carries only the supplied fields, e.g. `{"name":"Signed Contracts","color":"00aa55"}`;
clearing sends an explicit `{"color":null}`. Response `data` is the complete updated tag:

Wire operation: `PUT /v1/accounts/{accountId}/tags/{tagId}`.

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

@param tagId Stable tag identifier.
@param name Optional replacement name.
@param color Optional replacement six-digit hexadecimal color.
@param clearColor Whether to send an explicit JSON `null` and remove the current color.
@param accountId Account override; otherwise the client's default account is used.
@return Complete updated tag.
@throws ValidationException for an empty update, conflicting color options, or invalid input.

### `tags.delete`

Deletes a tag with `DELETE /accounts/{accountId}/tags/{tagId}`. By default fails with a 409
if the tag is still attached to anything; pass [force] = `true` to detach it from all
documents and templates first.

Request body: none; [force] is sent as the `?force=true` query. Response `data`:
`{"deleted":true}`.

Wire operation: `DELETE /v1/accounts/{accountId}/tags/{tagId}`.

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
{
  "deleted": true
}
```

@param tagId Stable tag identifier.
@param force Whether the server should detach all uses before deletion.
@param accountId Account override; otherwise the client's default account is used.

### `templates.list`

Lists templates with `GET /accounts/{accountId}/templates`.

Request body: none. Optional query: `search`, `page`, `per-page`. Response `data` is an array
of templates; a list entry omits the page/role detail a single template carries:
`X-Pagination-*` headers are exposed through [PaginatedResult.meta].

Wire operation: `GET /v1/accounts/{accountId}/templates`.

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
    "status": "ready",
    "pages": [{"id": "id-placeholder", "number": 1, "height": 1, "width": 1, "download_url": "https://example.com/resource", "fields": [{"id": "id-placeholder", "field_id": "field-id-placeholder", "role_id": "role-id-placeholder", "label": "example", "display_settings": null, "created_at": "2026-01-01T00:00:00Z", "updated_at": "2026-01-01T00:00:00Z"}]}],
    "roles": [{"id": "id-placeholder", "name": "Example", "assignment_type": "Signer", "created_at": "2026-01-01T00:00:00Z", "updated_at": "2026-01-01T00:00:00Z"}],
    "tags": [{"id": "id-placeholder", "name": "Example"}],
    "created_at": "2026-01-01T00:00:00Z",
    "updated_at": "2026-01-01T00:00:00Z"
  }
]
```

@param params Search and pagination values; unsupported common filters are ignored.
@param accountId Account override; otherwise the client's default account is used.
@return Matching templates and optional pagination-header metadata.

### `users.getCurrent`

Retrieves the authenticated profile with `GET /users/self`.

Request body/query: none. Response `data`:
The older `{ "user": {...}, "accounts": [...] }` data variant is normalized to the same model.

Wire operation: `GET /v1/users/self`.

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

@return Complete authenticated-user profile.

### `users.getStats`

Retrieves cross-account KPIs with `GET /users/self/stats`.

Query shape: optional `granularity=monthly|daily` and `month=YYYY-MM`; request body: none.

Wire operation: `GET /v1/users/self/stats`.

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
[
  {
    "period": "2026-10",
    "documents_uploaded": 1,
    "documents_sent": 1,
    "signature_requests": 1,
    "signature_requests_notification_email": 1,
    "signature_requests_notification_whatsapp": 0,
    "signature_requests_notification_bypass": 0,
    "signature_requests_verification_email": 1,
    "signature_requests_verification_whatsapp": 0,
    "signature_requests_verification_bypass": 0,
    "signature_requests_verification_digital_certificate": 0,
    "signature_requests_viewed": 1,
    "signature_requests_completed": 1,
    "documents_certified": 1
  }
]
```

@param query Monthly/daily granularity and optional target month.
@return Zero-filled KPI periods, most recent first.
@throws ValidationException when daily granularity lacks a month or month is not `YYYY-MM`.

### `users.getNotificationPreferences`

Retrieves settings with `GET /users/self/notification-preferences`.

Request body/query: none. Response `data` always contains all nine booleans:

Wire operation: `GET /v1/users/self/notification-preferences`.

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
  "SignerWhatsappFailed": true,
  "ContractRenewalNotice": true,
  "ContractExpiring": true,
  "ContractExpired": true,
  "ContractRenewed": true
}
```

@return Complete owner-facing email notification preferences.

### `users.updateNotificationPreferences`

Merges settings with `PUT /users/self/notification-preferences`.

Request body is a non-empty subset such as `{"DocumentCompleted":false,"SignerDeclined":true}`.
Omitted keys retain their current value. Response `data` is the complete nine-boolean map documented by
[getNotificationPreferences].

Wire operation: `PUT /v1/users/self/notification-preferences`.

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
  "SignerWhatsappFailed": true,
  "ContractRenewalNotice": true,
  "ContractExpiring": true,
  "ContractExpired": true,
  "ContractRenewed": true
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
  "SignerWhatsappFailed": true,
  "ContractRenewalNotice": true,
  "ContractExpiring": true,
  "ContractExpired": true,
  "ContractRenewed": true
}
```

@param request One or more owner-facing email preferences to merge.
@return Complete preferences after the merge.
@throws ValidationException when no preference is supplied.

### `users.getPushPreferences`

Get my push notification preferences. Personal settings; third-party OAuth applications receive 403.

Wire operation: `GET /v1/users/self/push-preferences`.

Request body: none.

Response `data` inside `{status,message,data}`:
```json
{
  "SignerViewed": true,
  "SignerSigned": true,
  "DocumentCompleted": true,
  "SignerDeclined": true,
  "DocumentAboutToExpire": true,
  "DocumentExpired": true,
  "SignerIdentityReviewRequired": true,
  "SignerWhatsappFailed": true
}
```

### `users.updatePushPreferences`

Update my push notification preferences. Merges only supplied switches; separate from email preferences. Third-party OAuth applications receive 403.

Wire operation: `PUT /v1/users/self/push-preferences`.

Request body (`application/json`; optional fields may be omitted):
```json
{
  "SignerViewed": true,
  "SignerSigned": true,
  "DocumentCompleted": true,
  "SignerDeclined": true,
  "DocumentAboutToExpire": true,
  "DocumentExpired": true,
  "SignerIdentityReviewRequired": true,
  "SignerWhatsappFailed": true
}
```

Response `data` inside `{status,message,data}`:
```json
{
  "SignerViewed": true,
  "SignerSigned": true,
  "DocumentCompleted": true,
  "SignerDeclined": true,
  "DocumentAboutToExpire": true,
  "DocumentExpired": true,
  "SignerIdentityReviewRequired": true,
  "SignerWhatsappFailed": true
}
```

### `webhooks.register`

Updates the account's oldest webhook endpoint, creating it when the account has none
(`PUT /accounts/{accountId}/webhooks/subscriptions`). Use [updateEndpoint] to target one
endpoint of several. When [RegisterWebhookRequest.events] is
null, [RegisterWebhookRequest.DEFAULT_EVENTS] is used; an explicit empty list is preserved.

Request body:
Response `data` is the complete stored subscription:

Wire operation: `PUT /v1/accounts/{accountId}/webhooks/subscriptions`.

Request body (`application/json`; optional members may be omitted):
```json
{
  "events": ["document_ready"],
  "is_active": true,
  "url": "https://example.com/resource",
  "email": "person@example.com"
}
```

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
{
  "events": ["document_ready"],
  "is_active": true,
  "url": null,
  "email": null,
  "updated_at": null,
  "is_held": false,
  "held_since": null
}
```

@param request Destination URL, delivery contact, event IDs, and active state.
@param accountId Account override; otherwise the client's default account is used.
@return Complete subscription stored for the account.
@throws com.assinafy.sdk.exceptions.ValidationException on an invalid URL or email.

### `webhooks.get`

Fetches the account's oldest webhook endpoint
(`GET /accounts/{accountId}/webhooks/subscriptions`). Use [listEndpoints] to see all of them.

Request body: none. Response `data`:

Wire operation: `GET /v1/accounts/{accountId}/webhooks/subscriptions`.

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
{
  "events": ["document_ready"],
  "is_active": true,
  "url": null,
  "email": null,
  "updated_at": null,
  "is_held": false,
  "held_since": null
}
```

@param accountId Account override; otherwise the client's default account is used.
@return Subscription, or `null` when the API returns HTTP 404.

### `webhooks.inactivate`

Deactivates the account's oldest endpoint without deleting it
(`PUT /accounts/{accountId}/webhooks/inactivate`); other endpoints are unaffected. To remove an
endpoint and free its slot, use [deleteEndpoint].

Request body: none. Response `data` is the complete subscription with `is_active` now false,
in the shape documented by [get].

Wire operation: `PUT /v1/accounts/{accountId}/webhooks/inactivate`.

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
{
  "events": ["document_ready"],
  "is_active": true,
  "url": null,
  "email": null,
  "updated_at": null,
  "is_held": false,
  "held_since": null
}
```

@param accountId Account override; otherwise the client's default account is used.
@return Complete inactive subscription.

### `webhooks.listEventTypes`

Lists webhook event types (`GET /webhooks/event-types`).

Request body/query: none. Response `data`:
Reading this catalog lets an integration accept new server events without an SDK update;
[com.assinafy.sdk.WebhookEvent] holds the identifiers known at release time.

Wire operation: `GET /v1/webhooks/event-types`.

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
[
  {
    "id": "document_ready",
    "description": "Document ready"
  }
]
```

@return Wire event IDs and human-readable descriptions.

### `webhooks.listDispatches`

Lists past webhook dispatches using legacy common-list pagination values.

Only [ListParams.page] and [ListParams.perPage] are sent because the webhook endpoint does not
accept the document-specific filters in [ListParams].

Request body: none. Response `data` is an array of dispatch records in the shape documented
by [retryDispatch].

Wire operation: `GET /v1/accounts/{accountId}/webhooks`.

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
[
  {
    "resource": "activity_dispatching_history",
    "id": "id-placeholder",
    "event": "document_ready",
    "activity_id": 1,
    "endpoint_id": "id-placeholder",
    "endpoint": "https://example.com/webhooks/assinafy",
    "payload": {},
    "delivered": true,
    "http_status": 1,
    "response_body": null,
    "error": null,
    "created_at": "2026-01-01T00:00:00Z",
    "updated_at": "2026-01-01T00:00:00Z",
    "webhook_id": "delivery-placeholder",
    "attempt": 1,
    "kind": "scheduled",
    "will_retry": false
  }
]
```

@param params Pagination values; all other [ListParams] fields are ignored.
@param accountId Account override; otherwise the client's default account is used.
@return Dispatch attempts and optional pagination-header metadata.

### `webhooks.listDispatches`

Lists dispatch history with every current API filter
(`GET /accounts/{accountId}/webhooks`).

Request body: none. Query: `endpoint_id`, `event`, `delivered`, `from`, `to`, `page`, `per-page`. Response
`data` is an array of dispatch records in the shape documented by [retryDispatch], and
`X-Pagination-*` headers are exposed through [PaginatedResult.meta].

Wire operation: `GET /v1/accounts/{accountId}/webhooks`.

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
[
  {
    "resource": "activity_dispatching_history",
    "id": "id-placeholder",
    "event": "document_ready",
    "activity_id": 1,
    "endpoint_id": "id-placeholder",
    "endpoint": "https://example.com/webhooks/assinafy",
    "payload": {},
    "delivered": true,
    "http_status": 1,
    "response_body": null,
    "error": null,
    "created_at": "2026-01-01T00:00:00Z",
    "updated_at": "2026-01-01T00:00:00Z",
    "webhook_id": "delivery-placeholder",
    "attempt": 1,
    "kind": "scheduled",
    "will_retry": false
  }
]
```

@param params Endpoint, event, delivery-state, timestamp-range, and pagination filters.
@param accountId Account override; otherwise the client's default account is used.
@return Matching dispatch attempts and optional pagination-header metadata.
@throws ValidationException for an invalid timestamp range or pagination value.

### `webhooks.retryDispatch`

Retries a failed webhook dispatch
(`POST /accounts/{accountId}/webhooks/{historyId}/retry`).

Request body: none. Response `data` is the updated delivery record:

Wire operation: `POST /v1/accounts/{accountId}/webhooks/{historyId}/retry`.

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
{
  "resource": "activity_dispatching_history",
  "id": "id-placeholder",
  "event": "document_ready",
  "activity_id": 1,
  "endpoint_id": "id-placeholder",
  "endpoint": "https://example.com/webhooks/assinafy",
  "payload": {},
  "delivered": true,
  "http_status": 1,
  "response_body": null,
  "error": null,
  "created_at": "2026-01-01T00:00:00Z",
  "updated_at": "2026-01-01T00:00:00Z",
  "webhook_id": "delivery-placeholder",
  "attempt": 1,
  "kind": "scheduled",
  "will_retry": false
}
```

@param dispatchId Stable delivery-attempt identifier.
@param accountId Account override; otherwise the client's default account is used.
@return Updated dispatch record for the retry.

### `webhooks.listEndpoints`

Lists the account's webhook endpoints, oldest first.

Wire operation: `GET /v1/accounts/{accountId}/webhooks/endpoints`.

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`) is an array of endpoints:
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
    "updated_at": "2026-01-01T00:00:00Z",
    "is_held": false,
    "held_since": null,
    "resource": "webhook_endpoint"
  }
]
```

@param accountId Account override; otherwise the client's default account is used.
@return Every endpoint of the account, oldest first.

### `webhooks.listEndpoints`

Lists the account's webhook endpoints, oldest first.

Wire operation: `GET /v1/accounts/{accountId}/webhooks/endpoints`.

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`) is an array of endpoints:
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
    "updated_at": "2026-01-01T00:00:00Z",
    "is_held": false,
    "held_since": null,
    "resource": "webhook_endpoint"
  }
]
```

@param url Optional exact registered endpoint URL; omitted to return all endpoints.
@param accountId Account override; otherwise the client's default account is used.
@return Every endpoint of the account, oldest first.

### `webhooks.createEndpoint`

Registers a new URL to receive the account's webhook events. An account can have 1 endpoint,
or up to 3 on paid plans; one past the limit answers `403`, and a `url` another endpoint of
the workspace already uses answers `400`. With `signing_enabled: true` a secret is generated;
store [WebhookEndpoint.secret] from this response; OAuth applications cannot read it later.

Wire operation: `POST /v1/accounts/{accountId}/webhooks/endpoints`.

Request body (`application/json`; `name`, `is_active` and `signing_enabled` are optional):
```json
{
  "url": "https://example.com/webhooks/assinafy",
  "email": "ops@example.com",
  "events": [
    "document_ready",
    "signer_signed_document"
  ],
  "name": "ERP",
  "is_active": true,
  "signing_enabled": true
}
```

Response 200 `data` payload (inside `{status,message,data}`) is the created endpoint:
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
  "updated_at": "2026-01-01T00:00:00Z",
  "secret": "whsec_redacted",
  "is_held": false,
  "held_since": null,
  "resource": "webhook_endpoint"
}
```

The response may include a one-time `secret` when signing is first enabled; store it on the server.

@param request URL, contact email, events and optional label, state and signing flag.
@param accountId Account override; otherwise the client's default account is used.
@return The created endpoint.
@throws ValidationException on an invalid URL or email, or an empty event list.
@throws com.assinafy.sdk.exceptions.ApiException with status 403 when the plan's endpoint limit is reached.

### `webhooks.getEndpoint`

Fetches one webhook endpoint.

Wire operation: `GET /v1/accounts/{accountId}/webhooks/endpoints/{endpointId}`.

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
  "updated_at": "2026-01-01T00:00:00Z",
  "is_held": false,
  "held_since": null,
  "resource": "webhook_endpoint"
}
```

@param endpointId Endpoint identifier.
@param accountId Account override; otherwise the client's default account is used.
@return The endpoint, or `null` when the API returns HTTP 404.

### `webhooks.updateEndpoint`

Changes a webhook endpoint; only the non-null fields of [request] are sent and updated. A
`url` another endpoint of the workspace already uses answers `400`. `signing_enabled: true`
generates a secret when the endpoint has none and keeps it otherwise; `false` discards it.

Wire operation: `PUT /v1/accounts/{accountId}/webhooks/endpoints/{endpointId}`.

Request body (`application/json`; every member is optional, at least one is required):
```json
{
  "url": "https://example.com/webhooks/assinafy",
  "email": "ops@example.com",
  "events": [
    "document_ready"
  ],
  "name": "ERP",
  "is_active": false,
  "signing_enabled": true
}
```

Response 200 `data` payload (inside `{status,message,data}`) is the updated endpoint; `secret` appears only when this request turns signing on:
```json
{
  "id": "id-placeholder",
  "name": "ERP",
  "url": "https://example.com/webhooks/assinafy",
  "email": "ops@example.com",
  "events": ["document_ready"],
  "is_active": false,
  "signing_enabled": true,
  "created_at": "2026-01-01T00:00:00Z",
  "updated_at": "2026-01-01T00:00:00Z",
  "is_held": false,
  "held_since": null,
  "resource": "webhook_endpoint",
  "secret": "whsec_redacted"
}
```

Enabling signing returns a one-time secret when the endpoint has none; store it immediately.

@param endpointId Endpoint identifier.
@param request Fields to change.
@param accountId Account override; otherwise the client's default account is used.
@return The updated endpoint.
@throws ValidationException when no field is supplied, or on an invalid URL, email or empty event list.

### `webhooks.deleteEndpoint`

Stops delivering events to an endpoint and frees its slot.

Wire operation: `DELETE /v1/accounts/{accountId}/webhooks/endpoints/{endpointId}`.

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`):
```json
[]
```

@param endpointId Endpoint identifier.
@param accountId Account override; otherwise the client's default account is used.

### `webhooks.getEndpointSecret`

Returns the secret that signs deliveries to this endpoint; pass it to
[com.assinafy.sdk.support.WebhookVerifier]. Answers `400` when signing is disabled. Not
available to OAuth applications, so call it with an API key.

Wire operation: `GET /v1/accounts/{accountId}/webhooks/endpoints/{endpointId}/secret`.

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`):
```json
{
  "secret": "whsec_<base64-key>"
}
```

@param endpointId Endpoint identifier.
@param accountId Account override; otherwise the client's default account is used.
@return The current signing secret.

### `webhooks.rotateEndpointSecret`

Replaces the endpoint's signing secret and returns the new one. The old secret stops working
immediately, so update the receiver right away. Answers `400` when signing is disabled.
OAuth applications need `webhooks:write`; an API key can also rotate it.

Wire operation: `POST /v1/accounts/{accountId}/webhooks/endpoints/{endpointId}/secret/rotate`.

Request body (`application/json`; optional fields may be omitted):
```json
{
  "keep_previous": false
}
```

Response `data` inside `{status,message,data}`:
```json
{
  "secret": "whsec_MfKQ9r8GKYqrTwjUPD8ILPZIo2LaLaSw",
  "previous_secret_expires_at": "2026-10-30T15:00:00Z"
}
```
@param endpointId Endpoint identifier.
@param accountId Account override; otherwise the client's default account is used.
@return The new signing secret.

### `webhooks.rotateEndpointSecret`

Rotate webhook endpoint signing secret. keep_previous=true lets the previous secret sign for 24 hours. Persist the returned secret securely.

Wire operation: `POST /v1/accounts/{accountId}/webhooks/endpoints/{endpointId}/secret/rotate`.

Request body (`application/json`; optional fields may be omitted):
```json
{
  "keep_previous": false
}
```

Response `data` inside `{status,message,data}`:
```json
{
  "secret": "whsec_MfKQ9r8GKYqrTwjUPD8ILPZIo2LaLaSw",
  "previous_secret_expires_at": "2026-10-30T15:00:00Z"
}
```

### `webhooks.deleteEndpointByUrl`

Deletes an endpoint by its exact registered URL, freeing its slot and stopping deliveries.

Wire operation: `DELETE /v1/accounts/{accountId}/webhooks/endpoints?url={url}`.

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`):
```json
[]
```

@param url Exact URL used at registration; sent as a URL-encoded query parameter.
@param accountId Explicit account or the client's configured default.
@throws com.assinafy.sdk.exceptions.ApiException if no endpoint uses the URL (404).

### `webhooks.testEndpoint`

Send a test delivery. Sends a webhook_test event even to inactive or paused endpoints; does not change pause state. Maximum ten tests per user per ten minutes.

Wire operation: `POST /v1/accounts/{accountId}/webhooks/endpoints/{endpointId}/test`.

Request body: none.

Response `data` inside `{status,message,data}`:
```json
{
  "resource": "activity_dispatching_history",
  "id": "id-placeholder",
  "event": "document_ready",
  "activity_id": 456,
  "endpoint_id": "endpoint-id-placeholder",
  "endpoint": "https://example.com/resource",
  "payload": {},
  "delivered": true,
  "webhook_id": "webhook-id-placeholder",
  "attempt": 2,
  "kind": "scheduled",
  "will_retry": true,
  "duration_ms": 184,
  "http_status": 200,
  "response_body": "OK",
  "error": null,
  "created_at": "2024-01-15T10:30:00Z",
  "updated_at": "2024-01-15T10:30:00Z"
}
```

### `workspaces.create`

Creates a workspace (`POST /accounts`).

Request body: `{"name":"Acme Inc.","notification_sender_type":"Account"}`; null fields are
omitted. Response `data`:

Wire operation: `POST /v1/accounts`.

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
  "roles": ["owner"],
  "is_delete_allowed": true,
  "created_at": "2026-01-01T00:00:00Z"
}
```

@param request Name, sender identity, and optional legacy branding colors.
@return Complete created account.
@throws ValidationException for a blank name, unsupported sender type, or invalid color.

### `workspaces.list`

Lists workspaces accessible to the credential (`GET /accounts`).

Request body/query: none. Response `data` is an array of the account payload documented by
[get]. With an OAuth token it holds exactly the one workspace the user approved, so
`list().data.first().id` is how an OAuth integration finds its workspace id.

Wire operation: `GET /v1/accounts`.

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
    "roles": ["owner"],
    "is_delete_allowed": true,
    "created_at": "2026-01-01T00:00:00Z"
  }
]
```

@return Accounts and optional pagination-header metadata.

### `workspaces.get`

Fetches a workspace by ID (`GET /accounts/{accountId}`).

Request body: none. Response `data`:

Wire operation: `GET /v1/accounts/{accountId}`.

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
  "roles": ["owner"],
  "is_delete_allowed": true,
  "created_at": "2026-01-01T00:00:00Z",
  "limits": {"webhook_endpoints": 3}
}
```

@param accountId Stable account identifier.
@return Complete account record.

### `workspaces.get`

Get account. Set expandSigningFeatures to send expand=signing_features. Feature flags describe current entitlements; estimates still check balances and request rules.

Wire operation: `GET /v1/accounts/{accountId}`.

Request body: none.

Response `data` inside `{status,message,data}`:
```json
{
  "resource": "account",
  "id": "id-placeholder",
  "name": "Acme Inc.",
  "primary_color": "aabbcc",
  "secondary_color": "112233",
  "notification_sender_type": "User",
  "roles": [
    "example"
  ],
  "is_delete_allowed": true,
  "limits": {
    "webhook_endpoints": 1
  },
  "signing_features": {
    "DigitalCertificate": true,
    "VerificationIdDocumentFaceMatchBasic": false,
    "WhatsappNotification": true,
    "VerificationBypass": false,
    "Embed": false
  },
  "created_at": "2026-06-03T03:54:16Z"
}
```

### `workspaces.update`

Updates a workspace (`PUT /accounts/{accountId}`).

Request body carries only the supplied fields, e.g.
`{"name":"Acme Inc.","notification_sender_type":"Account"}`. Response `data` is the complete
updated account documented by [get].

Wire operation: `PUT /v1/accounts/{accountId}`.

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
  "roles": ["owner"],
  "is_delete_allowed": true,
  "created_at": "2026-01-01T00:00:00Z"
}
```

@param accountId Stable account identifier.
@param request Non-empty set of account fields to replace.
@return Complete updated account.
@throws ValidationException for blank IDs/names, empty updates, unsupported sender types, or invalid colors.

### `workspaces.delete`

Deletes a workspace (`DELETE /accounts/{accountId}`).

Request body: `{"force":false}`, sent only when [force] is supplied. Response `data` is an
empty JSON array.

Wire operation: `DELETE /v1/accounts/{accountId}`.

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

@param accountId Stable account identifier.
@param force Optional server-side force flag; omit it to use normal deletion safeguards.

### `workspaces.getTheme`

Fetches the account's branding theme (`GET /accounts/{accountId}/theme`).

Request body: none. Response `data`:

Wire operation: `GET /v1/accounts/{accountId}/theme`.

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

@param accountId Stable account identifier.
@return Account name, colors, and optional logo URL.

### `workspaces.getLogo`

Downloads the account logo image as raw bytes (`GET /accounts/{accountId}/logo`).
Returns `null` when the account has no logo set (the API responds `404`).

Request body: none. The response is the raw image, not the JSON envelope, and its bytes are
returned unchanged.

Wire operation: `GET /v1/accounts/{accountId}/logo`.

Request body: none.

Response 200: raw binary bytes (`image` media types), without a JSON envelope.

@param accountId Stable account identifier.
@return Unmodified image bytes, or `null` for HTTP 404.

### `workspaces.uploadLogo`

Uploads or replaces the account logo as a single multipart `file` part
(`POST /accounts/{accountId}/logo`).

Request body: `multipart/form-data` with one `file` part carrying the image bytes and
[contentType]. Response is the success envelope with no `data` payload:
`{"status":200,"message":""}`.

Wire operation: `POST /v1/accounts/{accountId}/logo`.

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

@param accountId Stable account identifier.
@param fileData Non-empty image bytes.
@param fileName Non-blank multipart file name.
@param contentType Image MIME type, defaulting to `image/png`.
@throws ValidationException for blank identifiers/names, empty bytes, or a non-image MIME type.

### `workspaces.deleteLogo`

Deletes the account's current logo (`DELETE /accounts/{accountId}/logo`).

Request body: none. Response is the success envelope with no `data` payload:
`{"status":200,"message":""}`.

Wire operation: `DELETE /v1/accounts/{accountId}/logo`.

Request body: none.

Response 200 body; optional members depend on document state and permissions:
```json
{
  "status": 200,
  "message": "example"
}
```

@param accountId Stable account identifier.

### `workspaces.getStats`

Returns account document and signature KPIs.

Wire operation: `GET /v1/accounts/{accountId}/stats`.

Request body: none.

Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
```json
[
  {
    "period": "2026-10",
    "documents_uploaded": 1,
    "documents_sent": 1,
    "signature_requests": 1,
    "signature_requests_notification_email": 1,
    "signature_requests_notification_whatsapp": 0,
    "signature_requests_notification_bypass": 0,
    "signature_requests_verification_email": 1,
    "signature_requests_verification_whatsapp": 0,
    "signature_requests_verification_bypass": 0,
    "signature_requests_verification_digital_certificate": 0,
    "signature_requests_viewed": 1,
    "signature_requests_completed": 1,
    "documents_certified": 1
  }
]
```

@param accountId Stable account identifier.
@param granularity `monthly`, `daily`, or `null` for the API default.
Request body: none. Query: optional `granularity=monthly|daily` and `month=YYYY-MM`.

@param month Required `YYYY-MM` target when [granularity] is `daily`.
@return Zero-filled KPI periods, newest-first. Monthly queries default to 12 rows;
notification counters can exceed total requests because a signer notified through multiple
channels counts once per channel, while verification counters partition the request total.
@throws ValidationException for a blank account, unsupported granularity, or invalid/missing month.

### `workspaces.getUsage`

Get account usage. Uses account:read; balances can be unlimited (null).

Wire operation: `GET /v1/accounts/{accountId}/usage`.

Request body: none.

Response `data` inside `{status,message,data}`:
```json
{
  "plan": "Pro",
  "documents_renewal_date": "2026-12-01",
  "balances": {
    "documents": {
      "balance": 100,
      "limit": 100,
      "spent": 0
    },
    "credits": {
      "balance": 100,
      "limit": 100,
      "spent": 0
    }
  }
}
```

### `workspaces.getNotificationPolicy`

Get the workspace notification policy.

Wire operation: `GET /v1/accounts/{accountId}/notification-policy`.

Request body: none.

Response `data` inside `{status,message,data}`:
```json
{
  "editable": true,
  "owners_receive_all": false,
  "events": {
    "DocumentCompleted": true,
    "SignerDeclined": true,
    "DocumentCancelled": true,
    "DocumentAboutToExpire": true,
    "DocumentExpired": true,
    "DocumentExpirationReset": true,
    "DocumentProcessingFailed": true,
    "TemplateProcessingFailed": true,
    "SignerWhatsappFailed": true,
    "ContractRenewalNotice": true,
    "ContractExpiring": true,
    "ContractExpired": true,
    "ContractRenewed": true,
    "SignerViewed": false,
    "SignerSigned": true,
    "SignerIdentityReviewRequired": true
  }
}
```

### `workspaces.updateNotificationPolicy`

Update the workspace notification policy. Owners only. Omitted keys remain unchanged; identity-review email cannot be disabled.

Wire operation: `PUT /v1/accounts/{accountId}/notification-policy`.

Request body (`application/json`; optional fields may be omitted):
```json
{
  "events": {
    "DocumentCompleted": true,
    "SignerDeclined": true,
    "DocumentCancelled": true,
    "DocumentAboutToExpire": true,
    "DocumentExpired": true,
    "DocumentExpirationReset": true,
    "DocumentProcessingFailed": true,
    "TemplateProcessingFailed": true,
    "SignerWhatsappFailed": true,
    "ContractRenewalNotice": true,
    "ContractExpiring": true,
    "ContractExpired": true,
    "ContractRenewed": true,
    "SignerViewed": false,
    "SignerSigned": true,
    "SignerIdentityReviewRequired": true
  },
  "owners_receive_all": true
}
```

Response `data` inside `{status,message,data}`:
```json
{
  "editable": true,
  "owners_receive_all": false,
  "events": {
    "DocumentCompleted": true,
    "SignerDeclined": true,
    "DocumentCancelled": true,
    "DocumentAboutToExpire": true,
    "DocumentExpired": true,
    "DocumentExpirationReset": true,
    "DocumentProcessingFailed": true,
    "TemplateProcessingFailed": true,
    "SignerWhatsappFailed": true,
    "ContractRenewalNotice": true,
    "ContractExpiring": true,
    "ContractExpired": true,
    "ContractRenewed": true,
    "SignerViewed": false,
    "SignerSigned": true,
    "SignerIdentityReviewRequired": true
  }
}
```

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

## Identity verification method payloads
