package com.assinafy.sdk.resources

import com.assinafy.sdk.AssinafyClient
import com.assinafy.sdk.AssinafyClientConfig
import com.assinafy.sdk.ContractField
import com.assinafy.sdk.ContractRelationType
import com.assinafy.sdk.ContractStatus
import com.assinafy.sdk.exceptions.ApiException
import com.assinafy.sdk.exceptions.AssinafyException
import com.assinafy.sdk.exceptions.ValidationException
import com.assinafy.sdk.helper.MockApiHttpClient
import com.assinafy.sdk.http.HttpRawResponse
import com.assinafy.sdk.models.ContractExternalReference
import com.assinafy.sdk.models.DocumentContract
import com.assinafy.sdk.models.DocumentContractAmendment
import com.assinafy.sdk.request.ContractListParams
import com.assinafy.sdk.request.RenewContractRequest
import com.assinafy.sdk.request.TerminateContractRequest
import com.assinafy.sdk.request.UpdateContractRequest
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.concurrent.CancellationException

class ContractResourceTest {
    private val contract = """{"resource":"document_contract","document_id":"doc","status":"active","value":9999999999999.99,"currency":"BRL","term":1,"updated_at":"2026-10-23T12:00:00Z","amendments":[],"document":{"id":"doc","name":"Contract.pdf","status":"certificated","has_contract":true}}"""
    private val amendment = """{"resource":"document_contract_amendment","document_id":"instrument","document_name":"Amendment.pdf","parent_document_id":"doc","relation_type":"amendment","status":"pending","document_status":"pending_signature","number":null,"currency":"BRL","apply_error":null,"terms":{"value":130000.50},"changes":{"value":{"from":120000.50,"to":130000.50}},"applied_at":null,"created_at":"2026-10-23T12:00:00Z","updated_at":"2026-10-23T12:00:00Z"}"""
    private fun success(data: String) = HttpRawResponse(200, """{"status":200,"data":$data}""", emptyMap())

    @Test
    fun `client wires contract requests only to the authenticated transport`() = runTest {
        val authenticated = MockApiHttpClient(success(contract))
        val public = MockApiHttpClient()
        val client = AssinafyClient.create(AssinafyClientConfig(accountId = "account"), authenticated, public)
        client.contracts.get("doc")
        assertThat(authenticated.lastCall().path).isEqualTo("/accounts/account/documents/doc/contract")
        assertThat(public.calls).isEmpty()
    }

    @Test
    fun `list sends every filter and preserves expansion decimal precision and pagination`() = runTest {
        val mock = MockApiHttpClient(HttpRawResponse(200, """{"status":200,"data":[$contract]}""", mapOf("X-Pagination-Total-Count" to "21", "X-Pagination-Current-Page" to "2")))
        val result = ContractResource(mock, "default").list(
            ContractListParams(page = 2, perPage = 5, statuses = listOf(ContractStatus.ACTIVE, ContractStatus.EXPIRED), counterparty = "Example", search = "Service", endDateFrom = "2026-01-01", endDateTo = "2026-12-31", endingWithinDays = 0, externalSystem = "crm", externalId = "record", sort = "-end_date", expandDocument = true),
            "other/account",
        )
        assertThat(mock.lastCall().method).isEqualTo("GET")
        assertThat(mock.lastCall().path).isEqualTo("/accounts/other%2Faccount/contracts")
        assertThat(mock.lastCall().queryParams).containsExactlyInAnyOrderEntriesOf(
            mapOf("page" to 2, "per-page" to 5, "status" to "active,expired", "counterparty" to "Example", "search" to "Service", "end_date_from" to "2026-01-01", "end_date_to" to "2026-12-31", "ending_within_days" to 0, "external_system" to "crm", "external_id" to "record", "sort" to "-end_date", "expand" to "document"),
        )
        assertThat(result.data.single().value).isEqualByComparingTo(BigDecimal("9999999999999.99"))
        assertThat(result.data.single().document!!.hasContract).isTrue()
        assertThat(result.meta!!.total).isEqualTo(21)
    }

    @Test
    fun `get distinguishes terms from pending and applied amendments`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(success(contract))
        mock.enqueue(success(amendment))
        mock.enqueue(success(amendment.replace("\"pending\"", "\"applied\"").replace("\"number\":null", "\"number\":1")))
        val resource = ContractResource(mock, "account")
        assertThat(resource.get("doc")).isInstanceOf(DocumentContract::class.java)
        val pending = resource.get("instrument") as DocumentContractAmendment
        assertThat(pending.terms!!["value"].asBigDecimal).isEqualByComparingTo(BigDecimal("130000.50"))
        assertThat(pending.changes!!["value"].asJsonObject["from"].asBigDecimal).isEqualByComparingTo(BigDecimal("120000.50"))
        assertThat((resource.get("instrument") as DocumentContractAmendment).number).isEqualTo(1)
        assertThat(mock.calls.map { it.method }).containsOnly("GET")
    }

    @Test
    fun `update sends all terms with exact money and no implementation fields`() = runTest {
        val mock = MockApiHttpClient(success(contract))
        ContractResource(mock, "account").update(
            "doc/id",
            UpdateContractRequest(status = ContractStatus.ACTIVE, counterpartyName = "Example Ltd", counterpartyTaxpayerId = "11.222.333/0001-81", value = BigDecimal("120000.50"), currency = "BRL", startDate = "2026-11-01", endDate = "2027-10-31", autoRenew = false, renewalTermMonths = 12, noticePeriodDays = 0, ownerId = "owner", metadata = mapOf("crm_stage" to "won"), externalReferences = listOf(ContractExternalReference("crm", "record")), updatedAt = "2026-10-23T12:00:00Z"),
        )
        assertThat(mock.lastCall().method).isEqualTo("PUT")
        assertThat(mock.lastCall().path).isEqualTo("/accounts/account/documents/doc%2Fid/contract")
        assertThat(JsonParser.parseString(mock.lastCall().body)).isEqualTo(JsonParser.parseString("""{"status":"active","counterparty_name":"Example Ltd","counterparty_taxpayer_id":"11.222.333/0001-81","value":120000.50,"currency":"BRL","start_date":"2026-11-01","end_date":"2027-10-31","auto_renew":false,"renewal_term_months":12,"notice_period_days":0,"owner_id":"owner","metadata":{"crm_stage":"won"},"external_references":[{"system":"crm","id":"record"}],"updated_at":"2026-10-23T12:00:00Z"}"""))
    }

    @Test
    fun `partial update distinguishes omitted null and empty references`() = runTest {
        val mock = MockApiHttpClient(success(contract))
        val resource = ContractResource(mock, "account")
        resource.update("doc", UpdateContractRequest(noticePeriodDays = 30, clearFields = setOf(ContractField.COUNTERPARTY_NAME, ContractField.VALUE, ContractField.AUTO_RENEW)))
        assertThat(JsonParser.parseString(mock.lastCall().body)).isEqualTo(JsonParser.parseString("""{"notice_period_days":30,"counterparty_name":null,"value":null,"auto_renew":null}"""))
        resource.update("doc", UpdateContractRequest(externalReferences = emptyList()))
        assertThat(mock.lastCall().body).isEqualTo("""{"external_references":[]}""")
    }

    @Test
    fun `link edit unlink and related use the document family routes`() = runTest {
        val mock = MockApiHttpClient(success(amendment))
        val resource = ContractResource(mock, "account")
        assertThat(resource.update("instrument", UpdateContractRequest(parentDocumentId = "doc", relationType = ContractRelationType.AMENDMENT, value = BigDecimal("130000")))).isInstanceOf(DocumentContractAmendment::class.java)
        assertThat(JsonParser.parseString(mock.lastCall().body)).isEqualTo(JsonParser.parseString("""{"parent_document_id":"doc","relation_type":"amendment","value":130000}"""))
        resource.update("instrument", UpdateContractRequest(clearFields = setOf(ContractField.VALUE), updatedAt = "2026-10-23T12:00:00Z"))
        assertThat(JsonParser.parseString(mock.lastCall().body).asJsonObject["value"].isJsonNull).isTrue()
        mock.enqueue(success(contract))
        resource.update("instrument", UpdateContractRequest(clearFields = setOf(ContractField.PARENT_DOCUMENT_ID)))
        assertThat(mock.lastCall().body).isEqualTo("""{"parent_document_id":null}""")
        mock.enqueue(success(contract))
        assertThat(resource.related("instrument").documentId).isEqualTo("doc")
        assertThat(mock.lastCall().path).isEqualTo("/accounts/account/documents/instrument/contract/related")
    }

    @Test
    fun `renew retains preconditions and supports explicit clearing`() = runTest {
        val mock = MockApiHttpClient(success(contract))
        val resource = ContractResource(mock, "account")
        resource.renew("doc", RenewContractRequest(term = 1, clearValue = true, updatedAt = "2026-10-23T12:00:00Z"))
        assertThat(mock.lastCall().method).isEqualTo("POST")
        assertThat(mock.lastCall().path).endsWith("/contract/renew")
        assertThat(JsonParser.parseString(mock.lastCall().body)).isEqualTo(JsonParser.parseString("""{"term":1,"value":null,"updated_at":"2026-10-23T12:00:00Z"}"""))
        resource.renew("doc", RenewContractRequest(endDate = "2028-02-29", value = BigDecimal("42.10")))
        assertThat(JsonParser.parseString(mock.lastCall().body)).isEqualTo(JsonParser.parseString("""{"end_date":"2028-02-29","value":42.10}"""))
    }

    @Test
    fun `terminate sends reason and version or an empty object`() = runTest {
        val mock = MockApiHttpClient(success(contract))
        val resource = ContractResource(mock, "account")
        resource.terminate("doc", TerminateContractRequest("😀".repeat(500), "2026-10-23T12:00:00Z"))
        assertThat(mock.lastCall().method).isEqualTo("POST")
        assertThat(mock.lastCall().path).endsWith("/contract/terminate")
        val body = JsonParser.parseString(mock.lastCall().body).asJsonObject
        assertThat(body["reason"].asString).isEqualTo("😀".repeat(500))
        assertThat(body["updated_at"].asString).isEqualTo("2026-10-23T12:00:00Z")
        resource.terminate("doc")
        assertThat(mock.lastCall().body).isEqualTo("{}")
    }

    @Test
    fun `invalid partial writes and filters fail before a request`() {
        val mock = MockApiHttpClient()
        val resource = ContractResource(mock, "account")
        val updates = listOf(
            UpdateContractRequest(), UpdateContractRequest(status = "unknown"), UpdateContractRequest(relationType = "unknown"),
            UpdateContractRequest(value = BigDecimal("-1")), UpdateContractRequest(value = BigDecimal("10000000000000")), UpdateContractRequest(value = BigDecimal("1.001")),
            UpdateContractRequest(currency = "REAL"), UpdateContractRequest(endDate = "2026-02-29"), UpdateContractRequest(startDate = "0999-01-01"),
            UpdateContractRequest(startDate = "2026-12-01", endDate = "2026-01-01"), UpdateContractRequest(renewalTermMonths = 601), UpdateContractRequest(noticePeriodDays = -1),
            UpdateContractRequest(value = BigDecimal.ONE, clearFields = setOf(ContractField.VALUE)), UpdateContractRequest(parentDocumentId = " "),
            UpdateContractRequest(metadata = mapOf("bad key" to "text")), UpdateContractRequest(metadata = mapOf("number_at" to "123")),
            UpdateContractRequest(externalReferences = listOf(ContractExternalReference("crm", "😀"))),
        )
        updates.forEach { request -> assertThatThrownBy { runBlocking { resource.update("doc", request) } }.isInstanceOf(ValidationException::class.java) }
        listOf(ContractListParams(page = 0), ContractListParams(perPage = 101), ContractListParams(endingWithinDays = 3651), ContractListParams(statuses = listOf("pending")), ContractListParams(sort = "value"), ContractListParams(endDateFrom = "2026-13-01")).forEach {
            assertThatThrownBy { runBlocking { resource.list(it) } }.isInstanceOf(ValidationException::class.java)
        }
        listOf(RenewContractRequest(), RenewContractRequest(term = 0), RenewContractRequest(term = 1, value = BigDecimal.ONE, clearValue = true)).forEach {
            assertThatThrownBy { runBlocking { resource.renew("doc", it) } }.isInstanceOf(ValidationException::class.java)
        }
        assertThatThrownBy { runBlocking { resource.get("..") } }.isInstanceOf(ValidationException::class.java)
        assertThatThrownBy { runBlocking { resource.get(" ") } }.isInstanceOf(ValidationException::class.java)
        assertThatThrownBy { runBlocking { resource.terminate("doc", TerminateContractRequest("x".repeat(501))) } }.isInstanceOf(ValidationException::class.java)
        assertThat(mock.calls).isEmpty()
    }

    @Test
    fun `conflicts validation details and missing terms remain visible without retries`() {
        listOf(400, 403, 404, 409).forEach { status ->
            val mock = MockApiHttpClient(HttpRawResponse(status, """{"status":$status,"message":"Refused","code":"ContractChanged","errors":[{"field":"value","code":"TooSmall","message":"Invalid amount"}]}""", emptyMap()))
            assertThatThrownBy { runBlocking { ContractResource(mock, "account").update("doc", UpdateContractRequest(status = ContractStatus.ACTIVE)) } }
                .isInstanceOfSatisfying(ApiException::class.java) {
                    assertThat(it.statusCode).isEqualTo(status)
                    assertThat((it.responseData as Map<*, *>)["errors"]).isInstanceOf(List::class.java)
                }
            assertThat(mock.callCount()).isEqualTo(1)
        }
    }

    @Test
    fun `malformed union records fail in the SDK hierarchy and cancellation propagates`() {
        listOf("{}", """{"resource":"unexpected","document_id":"doc","status":"active"}""", """{"resource":"document_contract_amendment","document_id":"doc","status":"pending"}""").forEach {
            assertThatThrownBy { runBlocking { ContractResource(MockApiHttpClient(success(it)), "account").get("doc") } }.isInstanceOf(AssinafyException::class.java)
        }
        val mock = MockApiHttpClient().apply { transportError = CancellationException("cancelled") }
        assertThatThrownBy { runBlocking { ContractResource(mock, "account").get("doc") } }.isInstanceOf(CancellationException::class.java)
    }
}
