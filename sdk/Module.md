# Module Assinafy Android SDK

Coroutine-based Kotlin client for the Assinafy v1 API. Start with `AssinafyClient.create`, then use
its typed account or credentialless signer resources. Account credentials belong only in trusted
processes; never embed them in a distributed Android application.

Every public declaration has KDoc. Exact HTTP methods, routes, authentication, request payloads,
response fields, and errors are centralized in the repository's
[API reference](https://github.com/assinafy/mobile-android-sdk/blob/main/docs/API_REFERENCE.md) and
[operation index](https://github.com/assinafy/mobile-android-sdk/blob/main/docs/API_COVERAGE.md).
