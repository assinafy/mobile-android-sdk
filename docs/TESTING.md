# Building and testing

## Supported build environment

- Gradle Wrapper 9.5; use `./gradlew`, not a machine-wide Gradle installation.
- JDK 25 LTS to run Gradle and Android Gradle Plugin 9.3.2 with built-in Kotlin support and Kotlin compiler 2.4.20.
- Java 17 toolchain and bytecode for the published library.
- Android SDK platform 37.0 and Build Tools 36.0.0.
- Android API 21 minimum at runtime. The consuming application owns `targetSdk`.

The wrapper distribution checksum is pinned in `gradle/wrapper/gradle-wrapper.properties`. The
container base image and Android command-line tools are also checksum/digest pinned.

## Local verification

Install JDK 25 and Android SDK platform 37.0, make the Java 17 toolchain discoverable to Gradle,
then run:

```shell
./gradlew \
  :sdk:assembleRelease \
  :sdk:test \
  :sdk:lintDebug \
  :sdk:ktlintCheck \
  :sdk:dokkaGeneratePublicationHtml \
  :sdk:publishReleasePublicationToMavenLocal \
  :consumer-smoke:assembleRelease \
  --no-daemon \
  --stacktrace
```

Useful focused checks:

```shell
./gradlew :sdk:testDebugUnitTest --no-daemon
./gradlew :sdk:lintDebug :sdk:ktlintCheck --no-daemon
./gradlew :sdk:dokkaGeneratePublicationHtml --no-daemon
./gradlew :sdk:assembleRelease --no-daemon
```

AGP 9.3.2 exposes the library's configured debug unit-test variant; `:sdk:test` is the aggregate
task and runs every configured unit-test variant.

Test reports are written below `sdk/build/reports/tests/` and
`sdk/build/test-results/`. Android lint writes `sdk/build/reports/lint-results-debug.*`; the release
AAR is below `sdk/build/outputs/aar/`. The consumer smoke publishes that AAR to Maven Local, resolves
the published coordinates from a separate minified app, and writes its unsigned APK below
`consumer-smoke/build/outputs/apk/release/`.

## Reproducible Docker build

Docker is the shortest path when the host does not have the required JDK and Android SDK:

```shell
docker compose build --pull
docker compose run --rm test
docker compose run --rm build
```

The `test` service runs the SDK tests. The `build` service runs the complete module build, including
compilation, tests, Android lint, and packaging tasks selected by Gradle's `build` lifecycle.

## Opt-in live integration tests

Live tests are skipped by JUnit assumptions unless their environment is supplied. Keep every value
in a local secret manager or a temporary owner-readable environment file; never put values in Gradle files, shell history,
test source, commits, reports, or issue comments.

| Environment variable | Purpose |
|---|---|
| `ASSINAFY_API_KEY` | API credential |
| `ASSINAFY_ACCOUNT_ID` | Existing account used by the checks |
| `ASSINAFY_BASE_URL` | v1 endpoint override; defaults to `https://sandbox.assinafy.com.br/v1` |
| `ASSINAFY_TEST_EMAIL` | First test recipient for flows that require one |
| `ASSINAFY_TEST_EMAIL_2` | Second test recipient for multi-signer flows |
| `ASSINAFY_SIGNER_ACCESS_CODE` | Optional disposable signer-flow code |
| `ASSINAFY_REQUIRE_LIVE` | Fail instead of skip when base live credentials are absent; set locally |
| `ASSINAFY_LIVE_WRITES` | Explicit opt-in gate for tests that create, update, or delete real data |

After setting the needed variables outside the repository, run the read-only suite:

```shell
./gradlew \
  :sdk:testDebugUnitTest \
  --tests 'com.assinafy.sdk.live.*' \
  --rerun-tasks \
  --no-daemon \
  --stacktrace
```

The read-only smoke test exercises document statuses/search, account details/theme/logo, signer
listing, fields, templates, users, webhook history/event types, and tags. It does not send messages
or modify account state.

The discovery check sends no credential, but shares the suite's API-key/account opt-in gate so
ordinary unit runs remain offline. It reads the resource metadata and the issuer it advertises.
An optional endpoint that returns 404 is reported as a named JUnit skip. Fixture-dependent tests
also report a skip when the account has no suitable document, signer, or template.

Write tests require the additional `ASSINAFY_LIVE_WRITES` opt-in. They exercise reversible
preference, field, signer, document, assignment, notification, tag, and compatible-template flows.
Every created record uses a unique SDK test name and cleanup runs even after a failed assertion. Run
them only against an isolated live account; confirm the base URL and account before enabling the
gate.

Never use live automation to rotate/revoke API keys, change passwords, delete an existing account,
overwrite an existing webhook, or submit a real signature. Password, social-login, OTP, signer-code,
and notification success paths require purpose-created disposable state; unit contract tests cover
their exact requests when such state is unavailable.

## Browser-assisted Public OAuth lifecycle

`LiveOAuthTest` uses production auth and a Public OAuth app; it requires no API key. The requested
scopes are `documents:read`, `account:read`, `openid` and `offline_access`. It reads the selected
workspace and documents, checks the missing-scope challenge, refreshes once, reads with the rotated
access token, revokes the latest tokens in `finally`, and checks that the revoked grant is refused.
It never replays the old refresh token while the connection is active.

| Variable | Purpose |
|---|---|
| `ASSINAFY_OAUTH_CLIENT_ID` | Public app identifier; never a client secret |
| `ASSINAFY_OAUTH_REDIRECT_URI` | Registered HTTPS callback ending in `/callback` |
| `ASSINAFY_OAUTH_CALLBACK_DIR` | Directory visible to the test JVM, containing callback/URL files |

Prepare an empty directory outside the checkout with permissions `0700`. A local callback server
must write the incoming `/callback?...` request target to `callback.txt` atomically and with
permissions `0600`, without logging the URL or authorization code. For example:

```python
import os
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path

callback_dir = Path(os.environ["ASSINAFY_OAUTH_CALLBACK_DIR"])

class Callback(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path.startswith("/callback?"):
            temporary = callback_dir / "callback.tmp"
            with os.fdopen(os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600), "w") as output:
                output.write(self.path)
            temporary.replace(callback_dir / "callback.txt")
        self.send_response(200)
        self.end_headers()
        self.wfile.write(b"OAuth callback received. You may close this tab.")

    def log_message(self, *args):
        pass

HTTPServer(("127.0.0.1", 18763), Callback).serve_forever()
```

Run this listener, then `cloudflared tunnel --url http://127.0.0.1:18763`. Register the tunnel's
HTTPS `/callback` URL in a temporary Public app at `app.assinafy.com.br`, with the four scopes above.
Set the client ID and redirect URI outside the checkout, then start:

```shell
docker compose run --rm \
  -v "$ASSINAFY_OAUTH_CALLBACK_DIR:/oauth-test" \
  -e ASSINAFY_OAUTH_CLIENT_ID \
  -e ASSINAFY_OAUTH_REDIRECT_URI \
  -e ASSINAFY_OAUTH_CALLBACK_DIR=/oauth-test \
  build ./gradlew :sdk:testDebugUnitTest \
  --tests 'com.assinafy.sdk.live.LiveOAuthTest' --no-daemon
```

The SDK writes `authorization-url.txt` to the mounted directory. Open that URL in the system
browser, sign in to production auth and approve the temporary app for a test workspace. The test
waits up to ten minutes, exchanges the returned code immediately, and keeps the verifier and tokens
in memory. Stop the callback server and tunnel and remove the temporary directory after the run.
A failed token exchange may require a new browser authorization; do not reuse a code or refresh token.

Email and WhatsApp OTP success paths require controlled disposable recipients. Assinafy handles
A1/A3 certificate signing through its web signing flow; certificate completion is outside the SDK
test scope. SDK tests cover certificate-signing request serialization, channel validation and the
same webhook handling used by other signature methods. A passing offline suite does not establish
successful live OTP delivery. No existing account, credential, or subscription is deleted by this test.

## CI and the GitLab-to-GitHub mirror

GitLab CI is the canonical pipeline and GitHub Actions verifies the mirror. Both run the same Gradle
verification tasks. GitHub actions are commit-SHA pinned, checkout persistence is disabled, job
permissions are read-only, concurrency cancels superseded pull-request work, and reports are uploaded
from the actual Android/Gradle output paths.

GitHub Actions does not run the live suite, and neither does GitLab CI: it stays opt-in and runs
from a local checkout with the `ASSINAFY_*` environment variables, keeping live credentials out of
every pipeline and fork. A mirrored repository should accept dependency updates
on the canonical GitLab side so automated GitHub-only branches are not overwritten.

## Release verification

Before the first Maven Central release, verify ownership of the `com.assinafy` namespace in Central
Portal and configure these protected GitLab CI variables:

| CI variable | Value |
|---|---|
| `CENTRAL_USERNAME` | A newly generated Central Portal user token name |
| `CENTRAL_PASSWORD` | The matching Central Portal user token password |
| `MAVEN_SIGNING_KEY_FILE` | File variable containing the ASCII-armored GPG private key |
| `MAVEN_SIGNING_PASSWORD` | Password for that GPG private key |

`MAVEN_SIGNING_KEY` may be used instead of the file variable. Never commit or print any of these
values. Before saving the private key, publish and re-fetch its unexpired public key from a
[keyserver accepted by Central](https://central.sonatype.org/publish/requirements/gpg/), then verify
a test signature uses the primary signing key rather than a signing subkey.

The protected `publish-release` job runs only for a protected semantic-version tag, uploads the
signed bundle with automatic publishing, and waits for Central to report `PUBLISHED`. The following
`verify-release` job waits for public availability and resolves the artifact in the minified
consumer app. If public propagation is transiently slow, retry only `verify-release`.

Before tagging:

1. Run the full local verification command from a clean checkout.
2. Confirm no credential-like value or personal address is tracked.
3. Inspect the generated POM and AAR under `sdk/build/`.
4. Confirm `:consumer-smoke:assembleRelease` resolved the Maven-local artifact and ran R8.
5. Run read-only live checks; enable disposable writes only when the live account is confirmed.
6. Confirm the tag version is new; Maven Central versions cannot be replaced or deleted.
7. Push a protected `vMAJOR.MINOR.PATCH` tag and require both release jobs to pass.
