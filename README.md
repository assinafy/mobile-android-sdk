# SDK Android da Assinafy

*Português · [Read in English](README.en.md)*

Cliente Kotlin coroutine-first para a [API Assinafy v1](https://api.assinafy.com.br/v1/docs) —
plataforma brasileira de assinatura eletrônica. Cobre todas as operações publicadas da v1:
administração de conta, upload e recuperação de documentos, signatários, assignments de assinatura, o
fluxo de assinatura do signatário, autenticação, definições de campo, tags, templates, usuários e
webhooks.

> **Referência completa em inglês.** Este documento cobre requisitos, instalação, construção do
> cliente e onde cada credencial pertence. O guia completo do ciclo de vida está em
> **[README.en.md](README.en.md)**, e a referência por função em
> [docs/API_REFERENCE.md](docs/API_REFERENCE.md).

## Requisitos

| Requisito | Valor |
|---|---|
| Runtime | Android API 21 ou superior |
| Compilado contra | Android 17 estável / API 37.0 |
| Bytecode do consumidor | Java 17 |
| JDK de build | JDK 25 LTS, com toolchain Java 17 para compilação |
| Linguagem | Kotlin com coroutines |

A aplicação consumidora é dona do `targetSdk`. O AAR declara a permissão Android `INTERNET` e carrega
um arquivo ProGuard de consumidor, então um build de release minificado não precisa de configuração
extra: os campos de modelo serializados do SDK são anotados com `@SerializedName` e sobrevivem às
regras R8 do próprio Gson.

## Instalação

A coordenada é `com.assinafy:assinafy-android-sdk`.

Para compilar contra um checkout, publique primeiro no Maven Local:

```shell
./gradlew :sdk:publishReleasePublicationToMavenLocal \
  -Pversion=2.0.3-local-SNAPSHOT \
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
    implementation("com.assinafy:assinafy-android-sdk:2.0.3-local-SNAPSHOT")
}
```

Os releases publicados são públicos no Maven Central e não exigem credenciais de pacote:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

dependencies {
    implementation("com.assinafy:assinafy-android-sdk:2.0.3")
}
```

## Criando um cliente

`AssinafyClient.create` valida a configuração e devolve um cliente cujos recursos são expostos como
propriedades. Construa um por configuração e reutilize durante toda a vida do processo, para que o
pool de conexões OkHttp e o dispatcher sejam compartilhados.

```kotlin
fun accountClient(apiKey: String, accountId: String, sandbox: Boolean): AssinafyClient =
    AssinafyClient.create(
        apiKey = apiKey,
        accountId = accountId,
        baseUrl = if (sandbox) {
            "https://sandbox.assinafy.com.br/v1"
        } else {
            SdkConstants.DEFAULT_BASE_URL // https://api.assinafy.com.br/v1
        },
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
        baseUrl = "https://sandbox.assinafy.com.br/v1",
        webhookSecret = null,     // só verificação HMAC local; nunca enviado à Assinafy
        timeoutMs = 30_000L,      // conexão, leitura e escrita
        logger = null,            // Logger.NONE por padrão; segredos nunca vão para o log
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

A API tem três tipos de credencial, e o SDK as mantém em transportes separados, para que um segredo
de conta de vida longa nunca alcance uma URL pública de assinatura ou um redirecionamento
cross-origin.

| Credencial | Enviada como | Pertence a | Usada por |
|---|---|---|---|
| Chave de API | Header `X-Api-Key` | Um back-end confiável ou processo JVM controlado | `authentication`, `workspaces`, `documents`, `signers`, `assignments`, `fields`, `users`, `tags`, `templates`, `webhooks` |
| Token de acesso | `Authorization: Bearer` | O mesmo que a chave de API; obtido de `authentication.login` | Os mesmos recursos de conta |
| Código de acesso do signatário | Parâmetro de query `signer-access-code` | O app ou sessão de navegador do próprio signatário, pela duração de uma assinatura | `signerDocuments` |

> Uma chave de API de conta é uma **credencial de back-end de vida longa**. Não a embarque em um APK
> distribuído, não registre em log, não persista em dispositivo. Rode operações de conta em um
> back-end confiável e exponha ao app Android apenas o resultado específico da aplicação.

Um app voltado ao signatário **não precisa de credencial de conta alguma** — construa um cliente sem
credenciais e passe o código de acesso de curta duração por chamada:

```kotlin
val signerClient = AssinafyClient.create(
    AssinafyClientConfig(baseUrl = "https://sandbox.assinafy.com.br/v1"),
)
```

## Métodos de verificação do signatário

Definidos por signatário ao criar o assignment. O método de verificação e o de notificação são
**acoplados**: envie um, os dois ou nenhum — o lado que faltar é inferido. Sem nenhum dos dois, ambos
assumem `Email`.

| Método | Como funciona | Custo por signatário |
| --- | --- | --- |
| `Email` *(padrão)* | Código de uso único (OTP) por e-mail, exigido antes de assinar | Gratuito |
| `Whatsapp` | Código de uso único (OTP) por WhatsApp | Verificação gratuita; notificação 0,45 crédito, só em planos pagos |
| `DigitalCertificate` | O signatário assina com o **próprio certificado ICP-Brasil (A1/A3)**, pela extensão de navegador Web PKI, gerando uma assinatura **PAdES qualificada** | 2 créditos |

Combinações permitidas: `Email` → notifica por `Email`; `Whatsapp` → notifica por `Whatsapp`;
`DigitalCertificate` → notifica por `Email` **ou** `Whatsapp`. Apenas um método de notificação por
signatário.

O certificado digital exige o recurso na conta (planos Standard e Pro), CPF ou CNPJ em
`government_id`, e que o signatário esteja **sozinho no seu passo**. Como a assinatura por
certificado acontece por um handshake de dois passos com a extensão de navegador Web PKI
(`/v1/signers/certificate/start` + `/complete`, rotas **somente de produção**), ela não é concluída
pelo fluxo de assinatura nativo deste SDK — encaminhe o signatário à página web de assinatura.

## Trilha de atividades e artefatos

As atividades de um documento devolvem todos os eventos registrados, cada um com um snapshot do
`payload` do evento e a `origin` da requisição (`ip`, `user-agent`).

| Artefato | Conteúdo |
| --- | --- |
| `original` | O PDF enviado, como recebido |
| `certificated` | O documento assinado, com a certificação da plataforma |
| `certificate-page` | Apenas a página de certificação |
| `pades` | Assinaturas ICP-Brasil dos signatários + caixa de certificação — só existe em documentos que tiveram signatários por certificado digital |
| `bundle` | Zip com `original`, `certificated` e `certificate-page`, mais o `pades` quando houver |

A verificação pública confere um documento assinado pelo hash da assinatura, sem autenticação.

## Ambientes

| | |
| --- | --- |
| Produção | `SdkConstants.DEFAULT_BASE_URL` — `https://api.assinafy.com.br/v1` |
| Sandbox | `https://sandbox.assinafy.com.br/v1` |

## Documentação

- **[README.en.md](README.en.md)** — guia completo do ciclo de vida, em inglês
- [docs/API_REFERENCE.md](docs/API_REFERENCE.md) — referência por função
- [Documentação da API](https://api.assinafy.com.br/v1/docs)

## Licença

Distribuído sob a licença [MIT](LICENSE).
