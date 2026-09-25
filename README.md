# SDK Android da Assinafy

*Português · [Read in English](README.en.md)*

Cliente Kotlin coroutine-first para a [API Assinafy v1](https://api.assinafy.com.br/v1/docs) —
plataforma brasileira de assinatura eletrônica. Cobre todas as operações publicadas da v1:
administração de conta, upload e recuperação de documentos, signatários, assignments de assinatura,
o fluxo de assinatura do signatário, autenticação por senha e por OAuth 2.1, definições de campo,
tags, templates, usuários e webhooks.

Todo método de rede é uma `suspend function`. As dependências são OkHttp, Gson e
kotlinx-coroutines — nada além disso.

## Sumário

| | |
|---|---|
| [Requisitos](#requisitos) · [Instalação](#instalação) | Preparar o projeto |
| [Criando um cliente](#criando-um-cliente) | Construir e reutilizar o cliente |
| [Onde cada credencial pertence](#onde-cada-credencial-pertence) | Escolher a credencial certa |
| [OAuth 2.1](#oauth-21) | Agir no workspace de outra pessoa |
| [O ciclo de vida do documento](#o-ciclo-de-vida-do-documento) | Os sete passos, do PDF ao certificado |
| [Métodos de verificação e notificação](#métodos-de-verificação-e-notificação) | E-mail, WhatsApp e certificado A1/A3 |
| [O atalho de uma chamada](#o-atalho-de-uma-chamada) | Upload, signatários e assignment juntos |
| [Todo o resto no cliente](#todo-o-resto-no-cliente) | Templates, tags, webhooks, campos |
| [Respostas, paginação e erros](#respostas-paginação-e-erros) | Envelope, páginas, exceções |
| [Coroutines e ciclo de vida](#coroutines-e-ciclo-de-vida) | Cancelamento e timeouts |
| [Ambientes](#ambientes) · [Build e testes](#build-e-testes) · [Versionamento](#versionamento) | Operação |

## Requisitos

| Requisito | Valor |
|---|---|
| Runtime | Android API 21 ou superior |
| Compilado contra | Android 17 estável / API 37.0 |
| Bytecode do consumidor | Java 17 |
| JDK de build | JDK 25 LTS, com toolchain Java 17 para compilação |
| Linguagem | Kotlin com coroutines |
| TLS | 1.2 ou superior; o cliente padrão recusa TLS 1.0 e 1.1 |

A aplicação consumidora é dona do `targetSdk`. O AAR declara a permissão Android `INTERNET` e carrega
um arquivo ProGuard de consumidor, então um build de release minificado não precisa de configuração
extra: os campos de modelo serializados do SDK são anotados com `@SerializedName` e sobrevivem às
regras R8 do próprio Gson.

## Instalação

A coordenada é `com.assinafy:assinafy-android-sdk`. Os releases publicados são públicos no Maven
Central e não exigem credenciais de pacote:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

// app/build.gradle.kts
dependencies {
    implementation("com.assinafy:assinafy-android-sdk:2.5.0")
}
```

Para compilar contra um checkout local, publique primeiro no Maven Local e acrescente `mavenLocal()`
aos repositórios:

```shell
./gradlew :sdk:publishReleasePublicationToMavenLocal \
  -Pversion=2.5.0-local-SNAPSHOT \
  --no-daemon
```

## Criando um cliente

`AssinafyClient.create` valida a configuração e devolve um cliente cujos recursos são expostos como
propriedades. Construa um por configuração e reutilize durante toda a vida do processo, para que o
pool de conexões OkHttp e o dispatcher sejam compartilhados.

```kotlin
fun accountClient(apiKey: String, accountId: String): AssinafyClient =
    AssinafyClient.create(
        apiKey = apiKey,
        accountId = accountId,
        baseUrl = SdkConstants.DEFAULT_BASE_URL, // https://api.assinafy.com.br/v1
    )
```

`AssinafyClientConfig` expõe a superfície completa quando você precisa de mais do que as quatro
configurações comuns:

```kotlin
val client = AssinafyClient.create(
    AssinafyClientConfig(
        apiKey = apiKey,          // enviada como X-Api-Key
        token = null,             // ou um token bearer; os dois são mutuamente exclusivos
        accountId = accountId,    // conta padrão para chamadas com escopo de conta
        baseUrl = "https://api.assinafy.com.br/v1",
        webhookSecret = null,     // só verificação HMAC local; nunca enviado à Assinafy
        timeoutMs = 30_000L,      // conexão, leitura e escrita
        logger = null,            // Logger.NONE por padrão; segredos nunca vão para o log
        oauth = null,             // aplicação OAuth registrada; veja OAuth 2.1
    ),
)
```

`baseUrl` é o prefixo completo da API ao qual as rotas do SDK são anexadas. URLs hospedadas pela
Assinafy incluem `/v1`; um proxy reverso pode usar outro prefixo de caminho. Barra ao final é aceita.
Informação de usuário, query e fragmento são rejeitados, e fornecer uma credencial exige HTTPS, a
menos que o host seja `localhost`, `127.0.0.1` ou `::1`.

Todo método com escopo de conta aceita um `accountId` opcional que sobrescreve o padrão do cliente,
então um cliente pode servir vários workspaces. `client.workspaces` é a exceção: seus métodos sempre
nomeiam a conta explicitamente, porque `list()` abrange toda conta alcançável e `delete()` é
irreversível.

## Onde cada credencial pertence

A API tem quatro tipos de credencial, e o SDK as mantém em transportes separados, para que um segredo
de conta de vida longa nunca alcance uma URL pública de assinatura ou um redirecionamento
cross-origin.

| Credencial | Enviada como | Pertence a | Usada por |
|---|---|---|---|
| Chave de API | Header `X-Api-Key` | Um back-end confiável ou processo JVM controlado | Todos os recursos de conta |
| Token de acesso | `Authorization: Bearer` | O mesmo que a chave de API; obtido de `authentication.login` | Os mesmos recursos de conta |
| Token OAuth 2.1 | `Authorization: Bearer` | Um app que age no workspace de **outra pessoa**, com a permissão dela | Os recursos permitidos pelos escopos aprovados |
| Código de acesso do signatário | Parâmetro de query `signer-access-code` | O app ou sessão de navegador do próprio signatário, pela duração de uma assinatura | `signerDocuments` |

> Uma chave de API de conta é uma **credencial de back-end de vida longa** com acesso total ao
> workspace. Não a embarque em um APK distribuído, não registre em log, não persista em dispositivo.
> Rode operações de conta em um back-end confiável e exponha ao app Android apenas o resultado
> específico da aplicação — ou use OAuth 2.1, que foi feito exatamente para esse caso.

Um app voltado ao signatário **não precisa de credencial de conta alguma** — construa um cliente sem
credenciais e passe o código de acesso de curta duração por chamada:

```kotlin
val signerClient = AssinafyClient.create(
    AssinafyClientConfig(baseUrl = "https://api.assinafy.com.br/v1"),
)
```

O último segmento de uma URL de assinatura (`/sign/{accessCode}`) **é** esse código: trate-o como
credencial, não registre em log, não persista, não mande para analytics.

## OAuth 2.1

Use OAuth quando sua aplicação age **no workspace de outra pessoa, com a permissão dela**, sem nunca
tocar na senha ou na chave de API dessa pessoa. Se você automatiza o seu próprio workspace, continue
com a chave de API — nada desta seção se aplica.

| | Chave de API | OAuth |
|---|---|---|
| Age sobre | O **seu** workspace | O workspace de **outra pessoa**, com permissão |
| Pode fazer | Tudo que a sua conta pode | Só o que a pessoa aprovou |
| A pessoa pode desligar | Não | Sim, a qualquer momento |

Um app Android é um **cliente público**: ele roda no dispositivo da pessoa e não consegue guardar
segredo. Registre-o como `Public`, deixe `clientSecret` nulo e autentique só com PKCE. Uma
`client_secret` embarcada em um APK é extraível por qualquer um que abra o pacote, então o SDK
nunca a envia: um `clientSecret` preenchido é recusado com `ValidationException`. Um app registrado
como `Confidential` precisa passar para uma aplicação nova — veja
[Migrar de uma aplicação Confidential](#migrar-de-uma-aplicação-confidential).

Registre a aplicação no app da Assinafy em **Configurações → Aplicações OAuth → Nova aplicação**.
Você recebe um `client_id` e cadastra uma ou mais URIs de redirecionamento — elas são comparadas
caractere a caractere, precisam ser `https://` e sem `#`, e `…/callback` é diferente de
`…/callback/`. Cadastre uma por ambiente.

### Configuração

```kotlin
val client = AssinafyClient.create(
    AssinafyClientConfig(
        baseUrl = SdkConstants.DEFAULT_BASE_URL,
        oauth = OAuthConfig(
            clientId = "seu-client-id",
            redirectUri = "https://seuapp.example/oauth/callback",
            scopes = listOf(
                OAuthScope.DOCUMENTS_READ,
                OAuthScope.DOCUMENTS_WRITE,
                OAuthScope.OFFLINE_ACCESS,
            ),
        ),
    ),
)
```

| Escopo | Permite |
|---|---|
| `OAuthScope.DOCUMENTS_READ` | Ler documentos, páginas, tags, signatários, assignments e atividades |
| `OAuthScope.DOCUMENTS_WRITE` | Criar documentos e enviá-los para assinatura (consome créditos de notificação) |
| `OAuthScope.TEMPLATES_READ` / `TEMPLATES_WRITE` | Ler / manter templates, papéis, campos e tags |
| `OAuthScope.ACCOUNT_READ` | Ler perfil, tema e logo do workspace |
| `OAuthScope.WEBHOOKS_WRITE` | Configurar e desativar a assinatura de webhooks do workspace |
| `OAuthScope.OPENID` · `PROFILE` · `EMAIL` | Identificar a pessoa e receber nome / e-mail |
| `OAuthScope.OFFLINE_ACCESS` | Receber um refresh token e continuar funcionando sem a pessoa presente |

Peça o mínimo: a pessoa aprova tudo o que foi pedido ou nada. Cobrança, assinatura de plano,
gestão de membros, credenciais e administração **nunca** estão disponíveis a um token OAuth,
qualquer que seja o escopo.

### 1. Preparar a tentativa e abrir o navegador

Cada tentativa de conexão precisa de um par PKCE novo e de um `state` novo. `authorizationRequest()`
gera os dois e monta a URL; nada é enviado pela rede.

```kotlin
val attempt = client.oauth.authorizationRequest()

// Guarde na sessão: o passo 2 e o passo 3 precisam dos dois valores.
session.save(state = attempt.state, verifier = attempt.pkce.codeVerifier)

CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(attempt.url))
```

Use uma navegação de página inteira — Custom Tab ou o navegador do sistema. Nunca um WebView
embutido, nunca uma chamada AJAX.

Se o `client_id` ou a `redirect_uri` estiverem errados, a pessoa **não** volta para o seu app: o
servidor de autorização mostra o erro na própria página dele, porque redirecionar para um endereço
não verificado seria inseguro. Usuários presos em uma página de erro da Assinafy quase sempre
significam que um desses dois valores está errado.

### 2. Validar o retorno

`parseCallback` confere o `state` e o `iss` antes de qualquer outra coisa e devolve o código. Uma
divergência em qualquer um dos dois significa que a resposta não é sua, e o código é descartado.

```kotlin
val code = try {
    client.oauth.parseCallback(callbackUri.toString(), session.state)
} catch (error: OAuthException) {
    if (error.isAccessDenied) return showDeclinedByUser() else throw error
}
```

### 3. Trocar o código por tokens

O código é de uso único e expira **60 segundos** depois da aprovação.

```kotlin
val tokens = client.oauth.exchangeCode(code, session.verifier)

store.save(
    accessToken = tokens.accessToken,   // vale 1 hora
    refreshToken = tokens.refreshToken, // só existe se offline_access foi aprovado
    scopes = tokens.scopes,             // leia; não assuma que veio tudo que foi pedido
)
```

### 4. Descobrir o workspace e chamar a API

Um token pertence a **um único workspace** — aquele que a pessoa escolheu. Com um token OAuth, a
lista de workspaces devolve exatamente ele.

```kotlin
val workspaceClient = AssinafyClient.create(
    AssinafyClientConfig(token = tokens.accessToken, baseUrl = SdkConstants.DEFAULT_BASE_URL),
)
val workspaceId = workspaceClient.workspaces.list().data.first().id
store.save(workspaceId)

val documents = workspaceClient.documents.list(accountId = workspaceId)
```

> **Uma conexão, um workspace.** Chamar qualquer outro workspace devolve `403`, mesmo um do qual a
> mesma pessoa participa. Se o seu cliente usa vários workspaces, conecte cada um separadamente e
> guarde os tokens por workspace.

### 5. Renovar

O access token dura 1 hora. Com `offline_access`, renove sem a pessoa:

```kotlin
val sent = store.refreshToken
val renewed = try {
    // NonCancellable: sair da tela no meio do refresh não pode descartar um token já girado.
    withContext(NonCancellable) {
        client.oauth.refresh(sent).also {
            store.save(accessToken = it.accessToken, refreshToken = it.refreshToken) // os dois, antes de usar
        }
    }
} catch (error: AssinafyException) {
    val cause = error.cause
    return when {
        // Falhou antes de o pedido sair do aparelho: `sent` continua válido, tente de novo mais tarde.
        error is NetworkException &&
            (cause is UnknownHostException || cause is ConnectException || cause is SSLHandshakeException) ->
            retryLater()
        // Outro refresh gravou um token mais novo nesse meio-tempo: siga com os tokens gravados.
        store.refreshToken != sent -> useStoredTokens()
        // Qualquer outra falha pode ter chegado ao servidor e girado `sent`: nunca o envie de novo.
        else -> askUserToConnectAgain()
    }
}

// O cliente do passo 4 continua enviando o token expirado: faça as próximas chamadas com o novo.
val workspaceClient = AssinafyClient.create(
    AssinafyClientConfig(token = renewed.accessToken, baseUrl = SdkConstants.DEFAULT_BASE_URL),
)
val documents = workspaceClient.documents.list(accountId = workspaceId)
```

> **Todo refresh gira o token.** Cada renovação devolve um refresh token novo e aposenta o anterior.
> Um refresh token reutilizado não pode ser distinguido de um roubado sendo replicado, então ele
> encerra a conexão inteira e a pessoa precisa conectar de novo. Grave os dois tokens novos antes de
> qualquer outra coisa e renove um de cada vez por conexão. O SDK envia cada pedido de token uma única
> vez e nunca o repete sozinho, e recusa com `OAuthException` `invalid_response` uma resposta de
> sucesso sem refresh token novo.
>
> **Nunca envie o mesmo refresh token duas vezes.** Timeout, conexão derrubada, status de erro ou
> `invalid_response` não dizem se o servidor girou o token. Reler o armazenamento não torna a nova
> tentativa segura: se a resposta se perdeu, ele ainda guarda o token aposentado. Siga só se um token
> diferente e mais novo foi gravado; senão, peça à pessoa que conecte de novo. Só uma falha que
> comprovadamente aconteceu antes de qualquer envio — `UnknownHostException`, `ConnectException` ou
> `SSLHandshakeException` como causa da `NetworkException` — pode ser repetida com o mesmo token.
>
> Um refresh token vale **30 dias**, e cada renovação devolve um novo com mais 30 dias. A conexão só
> expira se o app ficar 30 dias sem renovar; depois disso, a pessoa precisa conectar de novo.

### 6. Desconectar

Revogue em vez de apenas apagar o token local. O endpoint sempre responde `200`.

```kotlin
client.oauth.revoke(store.refreshToken, tokenTypeHint = "refresh_token")
store.clear()
```

### Identificar a pessoa (OpenID Connect)

Com o escopo `openid`, a troca devolve um `id_token` (JWT RS256) e o endpoint de claims fica
disponível. Valide o `id_token` com qualquer biblioteca OIDC contra
`https://auth.assinafy.com.br/.well-known/jwks.json`, conferindo `iss`, `aud` (seu `client_id`),
`exp` e o `nonce` que você enviou.

```kotlin
val info = workspaceClient.oauth.userInfo() // exige openid; name exige profile, email exige email
println("${info.sub} · ${info.name} · ${info.email}")
```

### Descoberta

As URLs são estáveis, mas podem ser lidas dos documentos de metadados — é assim que se aponta o
fluxo para um ambiente que não seja produção.

```kotlin
val resource = client.oauth.protectedResourceMetadata()   // RFC 9728, servido por esta API
val issuer = requireNotNull(resource.authorizationServer)
val server = client.oauth.authorizationServerMetadata(issuer) // RFC 8414, servido pelo emissor
```

O indicador `resource` e a URL de metadados são derivados do `baseUrl` do cliente, então apontar o
cliente já direciona o fluxo. Só o `authorizationServerUrl` precisa ser
informado fora de produção — leia-o de `resource.authorizationServer` em vez de adivinhar.

### Erros de OAuth

Diferente do resto da API, os endpoints OAuth respondem com objetos planos (`{error,
error_description}`), não com o envelope. O SDK os converte em `OAuthException`, que carrega o código
padrão.

| Situação | Tratamento |
|---|---|
| `OAuthException.isAccessDenied` | A pessoa recusou na tela de aprovação |
| `OAuthException.isInvalidGrant` | Código expirado/reutilizado, `code_verifier` ou `redirect_uri` divergente, refresh token já usado ou permissões alteradas — reconecte |
| `OAuthException.INVALID_CLIENT` | `client_id` errado ou aplicação desativada |
| `ApiException` com status `401` | Token expirado ou revogado — renove; se falhar, peça nova conexão |
| `ApiException` com status `403` e `challenge?.isInsufficientScope == true` | Falta escopo — reconecte pedindo `challenge.scope` |
| `ApiException` com status `403`, sem esse `challenge` | Outro workspace, o papel da pessoa, ou área que OAuth nunca alcança |

Uma chamada sem o escopo necessário responde `403` com
`WWW-Authenticate: Bearer error="insufficient_scope", scope="documents:write"`. Isso é um pedido
para reconectar com aquele escopo, **não** para repetir a chamada. `ApiException.challenge` traz esse
header já interpretado; `OAuthChallenge.parse` lê um valor cru.

### Migrar de uma aplicação Confidential

Versões anteriores enviavam o `clientSecret` quando ele estava configurado. Agora o SDK o recusa:
toda chamada de `client.oauth` que usa a aplicação registrada lança `ValidationException` antes de
enviar qualquer coisa, então a recusa nunca gasta um refresh token gravado. O tipo de uma aplicação
não pode ser alterado depois de criada, então um app registrado como `Confidential` passa para uma
aplicação nova:

1. No app da Assinafy, crie uma em **Configurações → Aplicações OAuth → Nova aplicação**, do tipo
   **Public**, com as mesmas URIs de redirecionamento e as permissões que você usa.
2. Coloque o `client_id` dela no `OAuthConfig` e remova o `clientSecret`.
3. Descarte os tokens emitidos para a aplicação antiga e peça a cada pessoa que conecte de novo. Esses
   tokens pertencem ao `client_id` antigo: a aplicação nova não consegue renová-los, e revogá-los por
   ela não tem efeito. Os access tokens já emitidos continuam valendo até expirar, em no máximo uma
   hora.
4. O segredo antigo foi embarcado nos seus APKs, então trate-o como exposto. Quando nenhuma versão
   que você ainda suporta usar a aplicação antiga, desative-a ou exclua-a na Assinafy; isso desconecta
   de uma vez tudo o que foi conectado por ela.

### Antes de ir para produção

- Um par PKCE e um `state` novos a cada tentativa de conexão
- `state` e `iss` conferidos na URI de redirecionamento — `parseCallback` faz os dois
- `client_secret` só no servidor, nunca em app, código de navegador ou repositório
- Os dois tokens novos gravados antes de serem usados, um refresh por vez por conexão, e nenhum
  refresh token enviado duas vezes
- `401` tratado: renovar e, se falhar, pedir nova conexão
- O id do workspace guardado por conexão, e o `scope` devolvido realmente lido
- Toda URI de redirecionamento de produção cadastrada, `https://` e exata
- Apenas os escopos necessários
- Tokens revogados quando a pessoa desconecta

## O ciclo de vida do documento

Uma solicitação de assinatura passa por sete estágios. As seções abaixo seguem um documento do
começo ao fim; [O atalho de uma chamada](#o-atalho-de-uma-chamada) colapsa os estágios 1, 2 e 4 em
uma chamada só quando os padrões servem.

```
upload ──▶ metadata_ready ──▶ pending_signature ──▶ certificating ──▶ certificated
   │              │                    │                                    │
 passo 1      passos 3-4            passo 5                            passo 7
 upload      estimar + pedir     signatário assina                 baixar + verificar
```

### Passo 1 — Enviar um PDF

O documento precisa ser um PDF de verdade (o SDK confere a assinatura `%PDF-`), de no máximo 25 MB e
2.000 páginas. Leia assets do Android com `use` para que o stream seja sempre fechado.

```kotlin
val pdfBytes = context.assets.open("contrato.pdf").use { it.readBytes() }
val uploaded = client.documents.upload(pdfBytes, "contrato.pdf")
```

O upload retorna imediatamente, com o documento em status `uploaded`, enquanto o serviço extrai os
metadados das páginas. Um assignment `collect` precisa desses metadados, então espere por eles:

```kotlin
val ready = client.documents.waitUntilReady(uploaded.id)   // metadata_ready
```

`waitUntilReady` consulta `GET /v1/documents/{id}` a cada dois segundos por até dois minutos, retorna
assim que o status entra em `DocumentStatus.READY` e lança `ValidationException` se o documento
chegar a um estado terminal de falha ou o tempo acabar. Um assignment `virtual` pode ser criado antes
do fim do processamento, então para ele essa espera é opcional.

O nome enviado pode ser corrigido enquanto o documento ainda está em `uploaded` ou `metadata_ready` e
não tem signatários. O serviço normaliza acentos e caracteres não suportados:

```kotlin
val renamed = client.documents.rename(uploaded.id, "Contrato de serviço 2026.pdf")
```

### Passo 2 — Resolver os signatários

Signatários pertencem à conta, não ao documento, então a mesma pessoa é reaproveitada entre
documentos. `signers.create` é idempotente por e-mail: devolve o registro existente em vez de criar
duplicata, e se recupera de uma criação concorrente que perca a corrida.

```kotlin
val signer = client.signers.create(
    CreateSignerRequest(
        fullName = "Ada Lovelace",
        email = "ada@example.com",
        whatsappPhoneNumber = null,
    ),
)
```

Alguns métodos de verificação têm pré-requisitos. Verificação por WhatsApp exige
`whatsappPhoneNumber` e assinatura paga; verificação por certificado digital exige o recurso na conta
e um CPF ou CNPJ em `government_id`, definido depois da criação:

```kotlin
client.signers.update(signer.id, UpdateSignerRequest(governmentId = "12345678909"))
```

O serviço recusa alterar um e-mail ou número de WhatsApp já verificado enquanto o signatário tiver um
documento em andamento naquele canal, respondendo `400` e nomeando o documento.

### Passo 3 — Estimar o custo

Assignments consomem documentos do plano e créditos. Precifique antes de enviar — não são necessários
IDs de signatário, apenas os canais:

```kotlin
val estimate = client.assignments.estimateCost(
    ready.id,
    CreateAssignmentRequest(
        method = AssignmentMethod.VIRTUAL,
        signers = listOf(SignerReference(notificationMethods = listOf(NotificationMethod.WHATSAPP))),
    ),
)

if (!(estimate.hasSufficientResources ?: true)) {
    error("Bloqueado: ${estimate.blockingReason} — ${estimate.message}")
}
```

`CostEstimate` informa `totalCredits`, um `breakdown` item a item, os saldos atuais
`documentBalance` e `creditBalance`, e um `blockingReason` entre `PendingPayment`,
`InsufficientDocuments` e `InsufficientCredits`.

### Passo 4 — Solicitar assinaturas

O assignment **é** a solicitação de assinatura. `virtual` coleta uma assinatura sem campos de
entrada; `collect` posiciona campos em páginas específicas.

```kotlin
val assignment = client.assignments.create(
    ready.id,
    CreateAssignmentRequest(
        method = AssignmentMethod.VIRTUAL,
        signers = listOf(SignerReference.ofId(signer.id)),
        message = "Por favor, revise e assine",
        expiresAt = "2026-12-31T23:59:59Z",
        copyReceivers = listOf(observerSignerId),
    ),
)
```

`step` define a ordem de assinatura. Signatários com o mesmo passo assinam em paralelo, e um passo
posterior só é notificado depois que todos do passo anterior terminarem. Se algum signatário tem
`step`, todos precisam ter, e os valores precisam ser contíguos a partir de 1.

```kotlin
signers = listOf(
    SignerReference(id = primeiroId, step = 1),
    SignerReference(id = segundoId, step = 2),
)
```

Um assignment `collect` acrescenta posicionamento de campos — é por isso que ele exige
`metadata_ready`: cada entrada aponta para uma página real.

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

A resposta traz `signingUrls`, uma por signatário. Entregue cada URL pelo canal certo e trate-a como
credencial.

Se uma notificação precisar sair de novo, `assignments.resendNotification` reenvia para um
signatário, e `assignments.estimateResendCost` precifica isso antes.
`assignments.resetExpiration` move o prazo; passar `null` remove o prazo onde o ambiente permite.

### Passo 5 — Assinar, como signatário

Esta é a única parte que pertence a um app de usuário final, e ela usa o cliente sem credenciais de
[Onde cada credencial pertence](#onde-cada-credencial-pertence). Passe o código de acesso, o código
de verificação de uso único e a imagem da assinatura explicitamente; não os leia de armazenamento de
vida longa.

```kotlin
suspend fun assinarDocumentoVirtual(
    client: AssinafyClient,
    signerAccessCode: String,
    verificationCode: String,
    signaturePng: ByteArray,
): Map<String, Any> {
    val signer = client.signerDocuments.self(signerAccessCode)
    val document = client.signerDocuments.getAssignment(signerAccessCode)
    val assignment = requireNotNull(document.assignment) { "Documento sem assignment" }

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

    // Um assignment virtual não tem campos collect, então o corpo do contrato é exatamente [].
    return client.signerDocuments.sign(
        documentId = document.id,
        assignmentId = assignment.id,
        signerAccessCode = signerAccessCode,
        entries = emptyList(),
    )
}
```

Observações sobre esse fluxo:

- `getAssignment` marca o documento como visualizado e responde `409` enquanto a preparação ainda
  roda; tente de novo com backoff.
- `verifyEmail` também trata o código entregue por WhatsApp; a chave de rede é `verification-code`
  nos dois casos.
- `confirmData` é obrigatório antes de `sign` em um assignment virtual — o serviço responde `400`
  caso contrário.
- Um assignment `collect` passa valores `SignAssignmentItemRequest` em vez de uma lista vazia.
- Para recusar, chame `signerDocuments.decline(documentId, assignmentId, accessCode, reason)`.
  `signMultiple` e `declineMultiple` agem sobre vários documentos de uma vez.

Um signatário que não recebeu o link pode pedir um novo token de uso único para um documento público:

```kotlin
client.documents.sendToken(documentId, "ada@example.com")            // canal padrão é e-mail
client.documents.sendToken(documentId, "+5511999998888", "whatsapp") // destinatário é o telefone
```

### Passo 6 — Acompanhar a conclusão

Em um back-end, prefira webhooks a polling. Registre a inscrição uma vez por conta:

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

`WebhookEvent` lista todos os identificadores de evento, e `webhooks.listEventTypes()` devolve o
catálogo ao vivo. `webhooks.listDispatches(WebhookDispatchParams(...))` é o histórico de entregas —
filtre por evento, estado ou intervalo de tempo — e `webhooks.retryDispatch(id)` repete uma entrega
que falhou. Não existe exclusão: `webhooks.inactivate()` interrompe as entregas, e `register`
sobrescreve.

Webhooks pertencem a um back-end, não a um dispositivo. `WebhookVerifier` é um utilitário opcional de
HMAC-SHA256 para gateways configurados para assinar entregas; `webhookSecret` só é usado localmente.

```kotlin
if (client.webhookVerifier.verify(rawBodyBytes, signatureHeader)) {
    val event = client.webhookVerifier.extractEvent(rawBodyBytes)
    when (client.webhookVerifier.getEventType(event)) {
        WebhookEvent.DOCUMENT_READY -> tratarPronto(client.webhookVerifier.getEventData(event))
    }
}
```

Onde não houver webhook, consulte o lado da conta:

```kotlin
val progress = client.documents.getSigningProgress(documentId) // assinados, total, pendentes, %
val complete = client.documents.isFullySigned(documentId)
val activity = client.documents.activities(documentId)         // log completo de atividades
```

`isFullySigned` pode devolver `true` durante `certificating`, antes de o artefato imutável existir.

### Passo 7 — Baixar e verificar

Espere `certificated` antes de buscar o PDF final:

```kotlin
suspend fun baixarPdfConcluido(client: AssinafyClient, documentId: String): ByteArray {
    val document = client.documents.details(documentId)
    check(document.status == DocumentStatus.CERTIFICATED) { "Documento não está certificado" }
    return client.documents.download(documentId, DocumentArtifact.CERTIFICATED)
}
```

| `DocumentArtifact` | Conteúdo |
|---|---|
| `ORIGINAL` | O PDF como foi enviado |
| `CERTIFICATED` | O PDF assinado, com a página de certificação |
| `CERTIFICATE_PAGE` | Apenas a página de certificação |
| `PADES` | Assinaturas PAdES; só existe com signatários por certificado digital |
| `BUNDLE` | Um ZIP com os artefatos acima |

`documents.thumbnail(id)` e `documents.downloadPage(id, pageId)` devolvem imagens de página. Todos os
endpoints binários devolvem os bytes da resposta sem alteração.

As atividades de um documento devolvem todos os eventos registrados, cada um com um snapshot do
`payload` do evento e a `origin` da requisição (`ip`, `user-agent`).

Qualquer pessoa com o hash impresso em um documento assinado pode verificá-lo sem credenciais:

```kotlin
val result = client.documents.verify(signatureHash)
if (result.isValid) println("Assinado em ${result.completedAt}")
```

Essa chamada sempre responde HTTP `200`. Um hash desconhecido ou documento não assinado define
`isValid = false`, deixa os demais detalhes nulos e explica o motivo em `message`.

## Métodos de verificação e notificação

Definidos por signatário no `SignerReference` ao criar o assignment. O método de verificação (como a
pessoa prova identidade) e o de notificação (como ela é avisada) são **acoplados**: envie um, os dois
ou nenhum — o lado que faltar é inferido. Sem nenhum dos dois, ambos assumem `Email`.

| Verificação | Como funciona | Notificação permitida | Custo por signatário |
| --- | --- | --- | --- |
| `VerificationMethod.EMAIL` *(padrão)* | Código de uso único (OTP) por e-mail, exigido antes de assinar | `NotificationMethod.EMAIL` | Gratuito |
| `VerificationMethod.WHATSAPP` | Código de uso único (OTP) por WhatsApp | `NotificationMethod.WHATSAPP` | 0,45 crédito, só em planos pagos |
| `VerificationMethod.DIGITAL_CERTIFICATE` | O signatário assina com o **próprio certificado ICP-Brasil (A1 ou A3)**, pela extensão de navegador Web PKI, gerando uma assinatura **PAdES qualificada** | `EMAIL` **ou** `WHATSAPP` | 2 créditos + a notificação |

Apenas um método de notificação por signatário. O SDK valida o par localmente, então uma combinação
inválida falha com `ValidationException` antes de a requisição sair.

```kotlin
// Verificação e notificação pelo mesmo canal.
SignerReference.over(signer.id, NotificationMethod.WHATSAPP)

// Certificado digital A1/A3, convite por e-mail, sozinho no passo 2.
SignerReference.withDigitalCertificate(signer.id, notifyBy = NotificationMethod.EMAIL, step = 2)
```

Nenhum método de verificação tem preço próprio: o que se cobra é a **notificação** com a qual ele
anda junto. Como escolher verificação por WhatsApp escolhe também a notificação por WhatsApp, esse
signatário custa 0,45 crédito contra 0 de um verificado por e-mail. O certificado digital é a
exceção: a assinatura em si custa 2 créditos por signatário, além da notificação, cobrados na criação
do assignment e visíveis no `breakdown` da estimativa sob o código `SignatureDigitalCertificate`.

O certificado digital exige o recurso na conta (planos Standard e Pro), CPF ou CNPJ em
`government_id`, e que o signatário esteja **sozinho no seu passo**. Um CPF exige o certificado
daquela pessoa (e-CPF, ou e-CNPJ que a nomeie como representante legal); um CNPJ exige um e-CNPJ
daquela empresa, de qualquer representante. Como a assinatura por certificado acontece por um
handshake com a extensão de navegador Web PKI, ela não é concluída pelo fluxo de assinatura nativo
deste SDK — encaminhe o signatário à página web de assinatura.

## O atalho de uma chamada

Quando os padrões servem — assignment virtual, verificação por e-mail, signatários identificados por
e-mail — `uploadAndRequestSignatures` executa os estágios 1, 2 e 4 em uma chamada. Ele valida cada
signatário, envia o PDF, espera os metadados ficarem prontos, reaproveita ou cria cada signatário
pelo e-mail exato, e cria o assignment.

```kotlin
suspend fun solicitarAssinatura(
    client: AssinafyClient,
    context: Context,
    signerName: String,
    signerEmail: String,
): UploadAndRequestSignaturesResult {
    val pdfBytes = context.assets.open("contrato.pdf").use { it.readBytes() }

    return client.uploadAndRequestSignatures(
        UploadAndRequestSignaturesRequest(
            fileData = pdfBytes,
            fileName = "contrato.pdf",
            signers = listOf(
                UploadAndRequestSignaturesRequest.SignerEntry(
                    name = signerName,
                    email = signerEmail,
                ),
            ),
            message = "Por favor, revise e assine",
        ),
    )
}
```

O resultado traz o `DocumentDetails`, o `Assignment` criado com suas `signingUrls` por signatário, e
os IDs de signatário resolvidos. Uma falha depois do upload deixa o documento na conta; apague-o
explicitamente se o seu fluxo exigir rollback. Use os recursos individuais para campos collect,
assinatura sequencial, WhatsApp ou verificação por certificado digital.

## Todo o resto no cliente

| Recurso | O que faz |
|---|---|
| `client.authentication` | Login, redefinição e troca de senha, login social e vínculo, chaves de API pessoais |
| `client.oauth` | Fluxo OAuth 2.1 com PKCE, refresh, revogação, userinfo e descoberta |
| `client.workspaces` | CRUD de conta, tema, upload/download/remoção de logo, KPIs por conta |
| `client.documents` | Upload, listagem, busca, detalhes, renomear, apagar, artefatos, atividades, criação a partir de template, acesso público, tags do documento |
| `client.signers` | CRUD de signatários da conta e `findByEmail` |
| `client.signerDocuments` | O fluxo completo do signatário, sem credenciais |
| `client.assignments` | Criar, estimar, redefinir prazo, reenviar, histórico de notificações WhatsApp |
| `client.fields` | Definições de campo, catálogo de tipos, validação individual e em lote no servidor |
| `client.users` | Perfil autenticado, KPIs entre contas, preferências de notificação |
| `client.tags` | CRUD de tags do workspace, com exclusão forçada |
| `client.templates` | Leitura de templates; instancie por `documents.createFromTemplate` |
| `client.webhooks` | Inscrição, histórico de entregas, retry |
| `client.webhookVerifier` | Verificação HMAC local e parsing do payload |

Templates produzem um documento e seu assignment em uma chamada. Mapeie um signatário para cada papel
do template; os signatários já precisam existir:

```kotlin
val template = client.templates.list().data.first { it.status == "ready" }
val role = requireNotNull(template.roles).first()

val document = client.documents.createFromTemplate(
    templateId = template.id,
    signers = listOf(TemplateSigner(roleId = role.id, id = signer.id)),
    options = CreateDocumentFromTemplateRequest(
        signers = emptyList(), // substituído pelo argumento signers
        name = "Contrato para Ada.pdf",
        message = "Por favor, revise e assine",
        editorFields = listOf(TemplateEditorField(fieldId = fieldId, value = "Valor de exemplo")),
    ),
)
```

Tags são rótulos do workspace, únicos por workspace e sem distinção de maiúsculas. Crie na conta e
depois vincule por ID:

```kotlin
val tag = client.tags.create(name = "Contratos", color = "1f6feb")
client.documents.addTags(documentId, listOf(tag.id))
client.documents.replaceTags(documentId, listOf(tag.id))  // substituição total
client.documents.detachTag(documentId, tag.id)
```

## Respostas, paginação e erros

Toda resposta JSON usa um envelope:

```json
{"status": 200, "message": "OK", "data": {}}
```

O SDK valida o status HTTP e o `status` do envelope, desembrulha `data` no tipo documentado, e
devolve `Unit` onde a operação não tem payload. Endpoints binários devolvem os bytes sem alteração.
Os endpoints OAuth são a exceção: eles respondem com objetos planos, e o SDK os trata separadamente.

Endpoints de listagem devolvem `PaginatedResult<T>`, cujo `meta` vem dos headers
`X-Pagination-Current-Page`, `X-Pagination-Page-Count`, `X-Pagination-Per-Page` e
`X-Pagination-Total-Count`. Páginas começam em 1, e `perPage` precisa estar entre 1 e 100:

```kotlin
var page = 1
do {
    val result = client.documents.list(ListParams(page = page, perPage = 100))
    result.data.forEach(::processar)
    page++
} while (result.meta?.lastPage?.let { page <= it } == true)
```

HTTP 429 é repetido no máximo duas vezes, só em leituras seguras, respeitando `Retry-After` e
`X-Rate-Limit-Reset` até um teto de 30 segundos. Mutações nunca são repetidas.

Cinco tipos de exceção cobrem toda falha. Capture-os na fronteira da aplicação:

```kotlin
try {
    client.documents.details(documentId)
} catch (error: ValidationException) {
    // Verificação local falhou; nenhuma requisição foi enviada. error.errors nomeia o campo.
    mostrarErroDeEntrada(error.message)
} catch (error: OAuthException) {
    // Erro plano de OAuth. Ramifique por error.error, nunca pelo texto da mensagem.
    tratarOAuth(error.error)
} catch (error: ApiException) {
    // Status HTTP ou de envelope fora de 2xx. Ramifique por statusCode, nunca pelo texto.
    reportarStatus(error.statusCode) // Nunca registre responseData sem redigir.
} catch (error: NetworkException) {
    // Falha de DNS, TLS, conexão, timeout ou leitura da resposta.
    mostrarErroDeRedeRecuperavel()
} catch (error: AssinafyException) {
    // Tipo base; também cobre falhas de decodificação da resposta.
    reportarInesperado(error)
}
```

`ApiException.responseData` preserva o envelope de erro completo, que pode ecoar valores enviados
pelo chamador. Redija antes de registrar em log ou encaminhar.

## Coroutines e ciclo de vida

Todo método de rede é uma `suspend function` e pode ser chamado de qualquer dispatcher; as chamadas
OkHttp subjacentes são assíncronas, então não bloqueiam a thread chamadora. Cancelar a coroutine
cancela a requisição em andamento — trate cancelamento como cancelamento, nunca como sinal para
repetir.

```kotlin
viewModelScope.launch {
    val details = runCatching { client.documents.details(documentId) }
    // Sair deste escopo cancela a requisição.
}
```

Timeouts são por requisição e configurados no cliente. `waitUntilReady` abrange várias requisições e
tem orçamento próprio e independente.

## Ambientes

| | |
| --- | --- |
| Produção | `SdkConstants.DEFAULT_BASE_URL` — `https://api.assinafy.com.br/v1` |

Produção é o único ambiente suportado; informe outro `baseUrl` para apontar para outra implantação.

## Build e testes

O repositório compila com o Gradle wrapper. Quando a máquina não tem o JDK e o Android SDK, o Docker
é o caminho mais curto:

```shell
docker compose build --pull
docker compose run --rm test    # testes unitários do SDK
docker compose run --rm build   # build completo: compilar, testar, lint, empacotar
```

Com a toolchain instalada localmente:

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

Os testes ao vivo são ignorados sem as variáveis de ambiente correspondentes, e os
que escrevem exigem uma segunda autorização explícita. [Build e testes](docs/TESTING.md) documenta o
ambiente completo, o limite seguro dos testes ao vivo e o checklist de release.

## Versionamento

Os releases seguem versionamento semântico e são marcados como `vMAJOR.MINOR.PATCH`. O
[changelog](CHANGELOG.md) registra cada release.

Recompile todo consumidor 1.x contra a 2.x. `DocumentListItem`, `DocumentUploadResponse`,
`WorkspaceListItem` e `TemplateListItem` são type aliases dos modelos completos, então as classes JVM
distintas da 1.x não existem mais. Campos que uma projeção de lista, busca ou upload omite são
anuláveis e precisam ser checados antes do uso.

Membros marcados com `@Deprecated` são aliases compatíveis mantidos para integrações antigas. Código
novo deve usar a substituição nomeada em cada mensagem de depreciação.

## Documentação

- [README.en.md](README.en.md) — este mesmo guia, em inglês
- [docs/API_REFERENCE.md](docs/API_REFERENCE.md) — referência por função
- [docs/API_COVERAGE.md](docs/API_COVERAGE.md) — cobertura endpoint a endpoint
- [docs/TESTING.md](docs/TESTING.md) — camadas de teste e release
- [Documentação da API](https://api.assinafy.com.br/v1/docs)

## Licença

Distribuído sob a licença [MIT](LICENSE).
