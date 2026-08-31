import org.springframework.cloud.contract.spec.ContractDsl.Companion.contract

listOf(
    contract {
        name = "create_document_success"
        description = "POST /clients/{clientId}/documents creates a document and returns 201"
        request {
            method = POST
            url = url("/clients/31a67593-e39a-4e22-83df-f3494b55a439/documents")
            headers { contentType = APPLICATION_JSON }
            body = body(
                mapOf(
                    "title" to "Electricity statement",
                    "content" to "Utility bill for 10 Downing Street"
                )
            )
        }
        response {
            status = CREATED
            headers {
                contentType = APPLICATION_JSON
                header(
                    LOCATION,
                    value(
                        consumer("/clients/31a67593-e39a-4e22-83df-f3494b55a439/documents/66206f62-cff6-4e52-ad8e-978b8d8b9094"),
                        producer(regex("/clients/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}/documents/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
                    )
                )
            }
            body = body(
                mapOf<String, Any?>(
                    "id" to value(
                        consumer("66206f62-cff6-4e52-ad8e-978b8d8b9094"),
                        producer(regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
                    ),
                    "client_id" to value(
                        consumer("31a67593-e39a-4e22-83df-f3494b55a439"),
                        producer(regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
                    ),
                    "title" to fromRequest().body("$.title"),
                    "content" to fromRequest().body("$.content"),
                    "created_at" to value(
                        consumer("2026-08-29T14:00:00Z"),
                        producer(regex("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}([.][0-9]+)?Z"))
                    ),
                    "summary" to null,
                    "summary_status" to "PENDING"
                )
            )
        }
    },
    contract {
        name = "create_document_client_not_found"
        description = "POST /clients/{clientId}/documents for an unknown client returns 404"
        request {
            method = POST
            url = url("/clients/4d0f2f1a-8b3c-4d5e-9f60-1a2b3c4d5e6f/documents")
            headers { contentType = APPLICATION_JSON }
            body = body(
                mapOf(
                    "title" to "Electricity statement",
                    "content" to "Utility bill for 10 Downing Street"
                )
            )
        }
        response {
            status = NOT_FOUND
        }
    },
    contract {
        name = "create_document_invalid_content"
        description = "POST /clients/{clientId}/documents with blank content returns 400"
        request {
            method = POST
            url = url("/clients/31a67593-e39a-4e22-83df-f3494b55a439/documents")
            headers { contentType = APPLICATION_JSON }
            body = body(
                mapOf(
                    "title" to "Electricity statement",
                    "content" to ""
                )
            )
        }
        response {
            status = BAD_REQUEST
        }
    }
)
