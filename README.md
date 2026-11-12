# SDK Android da Assinafy

*Português · [Read in English](README.en.md)*

Cliente Kotlin coroutine-first para a [API Assinafy v1](https://api.assinafy.com.br/v1/docs). Cobre documentos, signatários, assignments, assinatura pública, OAuth 2.1/PKCE, biometria, certificado ICP-Brasil A1/A3, templates, contratos, usuários, tags, campos e webhooks. Todos os métodos de rede são `suspend`.

## Requisitos e instalação

- Android API 21+, Java 17 para o consumidor e TLS 1.2+.
- Build do SDK: Kotlin 2.4.21, Gradle 9.5, AGP 9.3.3 e JDK 25 LTS.
- Dependências de runtime: OkHttp 5, Gson e kotlinx-coroutines-core.

```kotlin
// settings.gradle.kts: google(); mavenCentral()
dependencies { implementation("com.assinafy:assinafy-android-sdk:2.9.0") }
```

O AAR declara `INTERNET` e inclui regras R8 de consumidor. Veja [API_REFERENCE.md](docs/API_REFERENCE.md) para toda operação e payload, [API_COVERAGE.md](docs/API_COVERAGE.md) para o mapa de endpoints e [TESTING.md](docs/TESTING.md) para os testes.

## Credenciais e clientes

Chaves de API e tokens de conta são segredos de back-end: nunca os coloque no APK, logs, analytics ou armazenamento do dispositivo. Um app distribuído usa OAuth público com PKCE para agir no workspace do usuário, ou recebe do seu back-end apenas os dados necessários. O código de acesso do signatário também é uma credencial e só pertence à sessão de assinatura.

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

O cliente mantém transportes separados para operações autenticadas, login/OAuth e assinatura pública. Credenciais são removidas em redirecionamentos para outra origem; mutações não seguem redirects, não usam retry automático e têm corpo one-shot. Reutilize uma instância por configuração.

## OAuth 2.1 público com PKCE

Registre uma aplicação **Public**, uma URI HTTPS exata e os escopos necessários. Android não consegue guardar `client_secret`; o SDK recusa qualquer valor não nulo.

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
val attempt = oauthClient.oauth.authorizationRequest() // abra attempt.url em Custom Tab
val code = oauthClient.oauth.parseCallback(callbackUri, attempt) // valida URI, state e iss
val tokens = oauthClient.oauth.exchangeCode(code, attempt.pkce.codeVerifier)
val authorized = AssinafyClient.create(AssinafyClientConfig(token = tokens.accessToken, accountId = accountId))
```

O código expira em 60 segundos. Guarde `state` e o verifier só até a troca. O access token dura uma hora. Se `offline_access` foi consentido, cada `refresh(latestRefreshToken)` devolve um refresh token novo e invalida o anterior; persista o novo par atomicamente e nunca repita um refresh incerto. `revoke` encerra a conexão. Escopo insuficiente retorna `ApiException.challenge`; reconecte pedindo o escopo indicado, sem retry.

Em sandbox, leia o emissor por `protectedResourceMetadata()`; produção usa `https://auth.assinafy.com.br`. Login com MFA lança `MfaRequiredException`; conclua com `authentication.verifyMfa(...)`.

## Fluxo completo: PDF até certificado

As operações de conta abaixo rodam no back-end confiável ou com OAuth autorizado.

```kotlin
val uploaded = backend.documents.upload(pdfBytes, "contrato.pdf")
val document = backend.documents.waitUntilReady(uploaded.id)
val signer = backend.signers.create(
    CreateSignerRequest(
        fullName = "Pessoa Exemplo",
        email = "pessoa@example.com",
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

Para `collect`, use IDs de `document.pages` e `fields.list()` em `entries`; todo signatário precisa possuir ao menos um campo. Para `virtual`, confirme os dados e envie `entries = emptyList()` ao assinar. Não crie um assignment antes de estimar quando o custo ou entitlement puder bloquear a operação.

| Verificação | Notificação | Requisitos e conclusão |
|---|---|---|
| `Email` | `Email` | OTP por e-mail; padrão gratuito |
| `Whatsapp` | `Whatsapp` | OTP por WhatsApp; plano pago e custo de notificação |
| `IdDocumentFaceMatchBasic` | `Email` ou `Whatsapp` | CPF válido, entitlement, consentimento e captura hospedada |
| `DigitalCertificate` | `Email` ou `Whatsapp` | CPF/CNPJ, entitlement, assignment `virtual`, signatário sozinho no step; assinatura ICP-Brasil A1/A3 na página hospedada |

Envie um, ambos ou nenhum dos métodos; a API infere o par ausente e usa Email quando ambos faltam. Há exatamente uma notificação por signatário. O preço final vem de `estimateCost`.

O link retornado por Email é uma página de reenvio, sem acesso ao documento. Use o `accessCode` do convite entregue ao signatário, sem registrá-lo. O exemplo seguinte é para Email/Whatsapp; biometria e certificado seguem os fluxos abaixo:

```kotlin
val self = signerClient.signerDocuments.self(accessCode)
signerClient.signerDocuments.verifyEmail(accessCode, VerifySignerEmailRequest(otp))
val documentToSign = signerClient.signerDocuments.getCurrent(self.id, accessCode)
signerClient.signerDocuments.confirmData(documentToSign.id, accessCode, ConfirmSignerDataRequest(email = "pessoa@example.com"))
signerClient.signerDocuments.acceptTerms(accessCode)
val assignmentToSign = requireNotNull(signerClient.signerDocuments.getAssignment(accessCode).assignment)
signerClient.signerDocuments.sign(documentToSign.id, assignmentToSign.id, accessCode, fieldValues)
```

Para biometria, leia `getIdentityVerification(accessCode)`. Somente quando `canStart == true`, obtenha consentimento explícito, chame `startIdentityVerification(accessCode, consent = true)` e abra a URL hospedada; não a registre nem armazene. Sessões Pending/InReview/Approved/Declined/Abandoned não são substituídas. A conta revisa com `assignments.getIdentityVerification`, baixa apenas as imagens nomeadas para uso temporário e decide com `reviewIdentityVerification`; rejeição cancela o documento.

Para `DigitalCertificate`, abra o convite entregue ao signatário em navegador compatível. A Assinafy conduz o handshake A1/A3; o app não recebe a chave privada nem chama as rotas da extensão. Após todos assinarem e as revisões terminarem:

```kotlin
val finalState = backend.documents.details(document.id) // depois do webhook DOCUMENT_READY
require(finalState.status == DocumentStatus.CERTIFICATED)
val pdf = backend.documents.download(document.id, DocumentArtifact.CERTIFICATED)
val verification = backend.documents.verify(requireNotNull(finalState.verificationCode))
```

Prefira webhooks a polling para `DOCUMENT_READY`, falhas, assinatura, identidade e contratos.

## Envelopes e assinatura incorporada

Para enviar vários PDFs juntos, faça upload e aguarde `metadata_ready` em todos, estime e crie:

```kotlin
val envelopeRequest = CreateEnvelopeRequest("Example", listOf(SignerReference.ofId(signer.id)), readyDocumentIds.map { EnvelopeDocumentRequest(it, listOf(signer.id)) }, idempotencyKey = requestId)
val envelopeCost = backend.envelopes.estimateCost(envelopeRequest)
val envelope = backend.envelopes.create(envelopeRequest.copy(maxTotalCredits = BigDecimal(requireNotNull(envelopeCost.totalCredits).toString())))
backend.envelopes.activities(envelope.id, ListParams(page = 1, perPage = 20))
```

A criação não garante entrega: consulte o histórico de notificações. Use `get`, `update`, `addDocument`, `removeDocument`, `resendNotification`, `cancel`, `download` e `delete`; excluir um envelope fechado preserva os documentos. Reutilize a chave somente ao repetir explicitamente a mesma criação. Templates também aceitam `idempotencyKey` e `maxTotalCredits`.

Para incorporar, registre uma origem HTTPS com `embeddedSigning.addOrigin` no backend e crie o signatário do assignment com `embedded=true` (Email/Whatsapp). Gere `createSession(documentId, assignmentId, signerId)` a cada abertura; carregue a URL uma vez, em cinco minutos, num iframe da origem autorizada. Não a registre nem faça prefetch. O app nativo abre essa página de integração hospedada; a Assinafy troca o token internamente.

`workspaces.get(accountId, expandSigningFeatures=true)` mostra permissões do plano; `getUsage` mostra saldos. Política de alertas e preferências pessoais de push têm métodos separados, indisponíveis a OAuth de terceiros. Downloads do signatário exigem código e permitem apenas `original`; após assinar, consulte `certifiedCopy` e use `resendCertifiedCopy` para receber a cópia pelo canal original.

## Webhooks

Crie um endpoint HTTPS com `signingEnabled = true`, guarde o `secret` retornado uma única vez e use os bytes crus do corpo. OAuth pode criar/rotacionar o segredo, mas não reler um segredo existente.

```kotlin
val endpoint = backend.webhooks.createEndpoint(
    CreateWebhookEndpointRequest("https://hooks.example/assinafy", "ops@example.com", events, signingEnabled = true),
)
val verifier = WebhookVerifier(requireNotNull(endpoint.secret))
require(verifier.verifySignature(rawBody, headers["webhook-id"], headers["webhook-timestamp"], headers["webhook-signature"]))
```

`testEndpoint` envia uma entrega de teste. Para trocar segredos sem interrupção, use `rotateEndpointSecret(id, keepPrevious=true)` e conclua a troca antes de `previousSecretExpiresAt` (24 horas). A verificação aplica HMAC-SHA256, comparação constante e janela antirreplay de 300 segundos. Só processe o evento depois dela e torne o consumidor idempotente por `webhook-id`.

## Gestão de contratos

Faça upload do instrumento, guarde o ID e salve os termos. Dinheiro usa `BigDecimal`; envie o último `updatedAt` em toda mutação para receber `409` em escrita obsoleta.

```kotlin
val contract = backend.contracts.update(
    document.id,
    UpdateContractRequest(counterpartyName = "Empresa Exemplo", value = BigDecimal("1250.00"), currency = "BRL", endDate = "2027-12-31"),
) as DocumentContract
val renewed = backend.contracts.renew(document.id, RenewContractRequest(term = contract.term, updatedAt = contract.updatedAt))
backend.contracts.terminate(document.id, TerminateContractRequest("Encerrado por acordo", renewed.updatedAt))
```

Para aditivos, envie `parentDocumentId`, `relationType` e apenas o delta, depois use o fluxo normal de assinatura. `related` devolve a raiz e instrumentos; referências substituem a lista inteira; `clearFields` envia `null` explícito. Não repita mutações automaticamente.

## Recursos, respostas e erros

`client` expõe `authentication`, `oauth`, `workspaces`, `documents`, `signers`, `signerDocuments`, `assignments`, `templates`, `fields`, `tags`, `contracts`, `users` e `webhooks`. Respostas comuns são desembrulhadas de `{status,message,data}`; OAuth é RFC flat. Listas devolvem `PaginatedResult`, com metadados dos headers e query `per-page`.

- `ValidationException`: entrada rejeitada antes da rede.
- `ApiException`: HTTP/envelope não 2xx; inclui dados, challenge e rate limits.
- `OAuthException`: erro OAuth flat.
- `NetworkException`: falha de I/O. Cancelamento de coroutine é preservado.

Somente `GET`/`HEAD`/`OPTIONS` repetem `429`, no máximo duas vezes e até 30 segundos. Use escopo estruturado (`viewModelScope`, `lifecycleScope`) e não bloqueie a main thread.

## Build e testes

O caminho reproduzível é Docker:

```shell
docker compose build build
docker compose run --rm build ./gradlew :sdk:assembleRelease :sdk:test :sdk:lintDebug :sdk:ktlintCheck :sdk:dokkaGeneratePublicationHtml :sdk:publishReleasePublicationToMavenLocal :consumer-smoke:assembleRelease -Pversion=0.0.0-ci-SNAPSHOT --no-daemon
```

Testes live são opt-in, usam as variáveis `ASSINAFY_*` descritas em [TESTING.md](docs/TESTING.md) e devem apontar para sandbox com escritas desativadas por padrão. O projeto segue SemVer e mantém as mudanças em [CHANGELOG.md](CHANGELOG.md).
