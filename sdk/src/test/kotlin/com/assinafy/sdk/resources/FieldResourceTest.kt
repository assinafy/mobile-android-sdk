package com.assinafy.sdk.resources

import com.assinafy.sdk.exceptions.ValidationException
import com.assinafy.sdk.helper.MockApiHttpClient
import com.assinafy.sdk.http.HttpRawResponse
import com.assinafy.sdk.request.CreateFieldRequest
import com.assinafy.sdk.request.FieldValidationEntry
import com.assinafy.sdk.request.UpdateFieldRequest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class FieldResourceTest {

    @Test
    fun `create posts the exact body and parses the field`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(ok(FIELD_DATA))

        val result = FieldResource(mock, "account-1").create(
            CreateFieldRequest(name = "Employee CPF", type = "cpf", isRequired = true),
        )

        assertThat(mock.lastCall()).isEqualTo(
            MockApiHttpClient.Call(
                "POST",
                "/accounts/account-1/fields",
                """{"name":"Employee CPF","type":"cpf","is_required":true}""",
            ),
        )
        assertThat(result.id).isEqualTo("field-1")
        assertThat(result.isRequired).isTrue()
    }

    @Test
    fun `list gets the exact filters and parses unpaginated fields`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(ok("[$FIELD_DATA]"))

        val result = FieldResource(mock, "account-1").list(
            includeInactive = true,
            includeStandard = false,
        )

        assertThat(mock.lastCall()).isEqualTo(
            MockApiHttpClient.Call(
                "GET",
                "/accounts/account-1/fields",
                queryParams = mapOf("include_inactive" to true, "include_standard" to false),
            ),
        )
        assertThat(result.single().type).isEqualTo("cpf")
        assertThat(result.single().isVisible).isTrue()
    }

    @Test
    fun `get uses the exact field path and parses the complete response`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(ok(FIELD_DATA))

        val result = FieldResource(mock, "account-1").get("field-1")

        assertThat(mock.lastCall()).isEqualTo(
            MockApiHttpClient.Call("GET", "/accounts/account-1/fields/field-1"),
        )
        assertThat(result.name).isEqualTo("Employee CPF")
        assertThat(result.regex).isNull()
    }

    @Test
    fun `update puts exact nullable regex body and parses the updated field`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(ok(FIELD_DATA.replace("Employee CPF", "Renamed").replaceFirst("true", "false")))

        val result = FieldResource(mock, "account-1").update(
            "field-1",
            UpdateFieldRequest(name = "Renamed", clearRegex = true, isActive = false),
        )

        assertThat(mock.lastCall()).isEqualTo(
            MockApiHttpClient.Call(
                "PUT",
                "/accounts/account-1/fields/field-1",
                """{"name":"Renamed","regex":null,"is_active":false}""",
            ),
        )
        assertThat(result.name).isEqualTo("Renamed")
        assertThat(result.isActive).isFalse()
    }

    @Test
    fun `delete uses the exact field path without body or query`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(ok("[]"))

        val result = FieldResource(mock, "account-1").delete("field-1")

        assertThat(mock.lastCall()).isEqualTo(
            MockApiHttpClient.Call("DELETE", "/accounts/account-1/fields/field-1"),
        )
        assertThat(result).isEqualTo(Unit)
    }

    @Test
    fun `validate posts the required value key and parses its typed result`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(ok("""{"type":"cpf","success":true,"error_message":""}"""))

        val result = FieldResource(mock, "account-1").validate("field-1", "400.676.228-36")

        assertThat(mock.lastCall()).isEqualTo(
            MockApiHttpClient.Call(
                "POST",
                "/accounts/account-1/fields/field-1/validate",
                """{"value":"400.676.228-36"}""",
            ),
        )
        assertThat(result.type).isEqualTo("cpf")
        assertThat(result.success).isTrue()
        assertThat(result.errorMessage).isEmpty()
    }

    @Test
    fun `validateMultiple posts the array itself and parses per-field results`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(
            ok(
                """[{"field_id":"field-1","type":"cpf","success":false,"error_message":"Invalid CPF."}]""",
            ),
        )

        val result = FieldResource(mock, "account-1").validateMultiple(
            listOf(FieldValidationEntry("field-1", "11111111111")),
        )

        assertThat(mock.lastCall()).isEqualTo(
            MockApiHttpClient.Call(
                "POST",
                "/accounts/account-1/fields/validate-multiple",
                """[{"field_id":"field-1","value":"11111111111"}]""",
            ),
        )
        assertThat(result.single().fieldId).isEqualTo("field-1")
        assertThat(result.single().success).isFalse()
        assertThat(result.single().errorMessage).isEqualTo("Invalid CPF.")
    }

    @Test
    fun `listTypes gets the global path and parses type entries`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(ok("""[{"type":"cpf","name":"CPF"}]"""))

        val result = FieldResource(mock, "account-1").listTypes()

        assertThat(mock.lastCall()).isEqualTo(MockApiHttpClient.Call("GET", "/field-types"))
        assertThat(result.single().type).isEqualTo("cpf")
        assertThat(result.single().name).isEqualTo("CPF")
    }

    @Test
    fun `invalid or empty field changes are rejected before transport calls`() {
        val mock = MockApiHttpClient()
        val fields = FieldResource(mock, "account-1")

        assertThatThrownBy { runBlocking { fields.create(CreateFieldRequest("", "text")) } }
            .isInstanceOf(ValidationException::class.java)
        assertThatThrownBy { runBlocking { fields.update("field-1", UpdateFieldRequest()) } }
            .isInstanceOf(ValidationException::class.java)
        assertThatThrownBy { runBlocking { fields.validateMultiple(emptyList()) } }
            .isInstanceOf(ValidationException::class.java)
        assertThat(mock.callCount()).isZero()
    }

    private fun ok(data: String): HttpRawResponse = HttpRawResponse(
        200,
        """{"status":200,"message":"","data":$data}""",
        emptyMap(),
    )

    private companion object {
        val FIELD_DATA = """
            {
              "resource":"field","id":"field-1","name":"Employee CPF","type":"cpf","regex":null,
              "is_pre_defined":false,"is_active":true,"is_required":true,"is_standard":false,
              "is_read_only":false,"is_visible":true
            }
        """.trimIndent()
    }
}
