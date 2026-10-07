# Module Assinafy Android SDK

Coroutine-based Kotlin client for the Assinafy v1 API. Start with `AssinafyClient.create`, then use
its typed account or credentialless signer resources. Account credentials belong only in trusted
processes; never embed them in a distributed Android application.

Every public declaration has KDoc. Exact HTTP methods, routes, authentication, request payloads,
response fields, and errors are centralized in the repository's
[API reference](https://github.com/assinafy/mobile-android-sdk/blob/main/docs/API_REFERENCE.md) and
[operation index](https://github.com/assinafy/mobile-android-sdk/blob/main/docs/API_COVERAGE.md).

Webhook endpoints (1, or up to 3 on paid plans) can sign deliveries with Standard Webhooks;
`WebhookVerifier.verifySignature` checks them on a server receiver. Users with two-factor
authentication finish `login` through `verifyMfa` after an `MfaRequiredException`, and manage their
authenticator and recovery codes through `authentication`.

For delegated workspace access, register a Public OAuth app and use PKCE. The client validates the
registered callback, parses flat token responses, refreshes rotating grants and revokes connections.
Production and sandbox use separate app registrations and credentials. Kotlin 2.4.20 builds Java17
consumer bytecode; runtime support starts at Android API21.
