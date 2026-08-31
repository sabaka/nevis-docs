import org.springframework.cloud.contract.spec.ContractDsl.Companion.contract

listOf(
    contract {
        name = "search_mixed_results"
        description = "GET /search?q=mixed returns 200 with a client and a document result"
        request {
            method = GET
            url = url("/search?q=mixed")
        }
        response {
            status = OK
            headers { contentType = APPLICATION_JSON }
            body = body(
                listOf(
                    mapOf(
                        "type" to "CLIENT",
                        "id" to "31a67593-e39a-4e22-83df-f3494b55a439",
                        "first_name" to "John",
                        "last_name" to "Doe",
                        "email" to "john.doe@neviswealth.com",
                        "description" to "Private wealth client",
                        "social_links" to listOf("https://linkedin.com/in/john-doe")
                    ),
                    mapOf(
                        "type" to "DOCUMENT",
                        "id" to "66206f62-cff6-4e52-ad8e-978b8d8b9094",
                        "client_id" to "31a67593-e39a-4e22-83df-f3494b55a439",
                        "title" to "Electricity statement",
                        "content" to "Utility bill for 10 Downing Street",
                        "created_at" to "2026-08-29T14:00:00Z",
                        "summary" to "An electricity utility bill for 10 Downing Street."
                    )
                )
            )
        }
    },
    contract {
        name = "search_empty_results"
        description = "GET /search?q=empty returns 200 with an empty list"
        request {
            method = GET
            url = url("/search?q=empty")
        }
        response {
            status = OK
            headers { contentType = APPLICATION_JSON }
            body = body(listOf<Any>())
        }
    },
    contract {
        name = "search_missing_query"
        description = "GET /search without a q parameter returns 400"
        request {
            method = GET
            url = url("/search")
        }
        response {
            status = BAD_REQUEST
        }
    },
    contract {
        name = "search_blank_query"
        description = "GET /search?q= with a blank q parameter returns 400"
        request {
            method = GET
            url = url("/search?q=")
        }
        response {
            status = BAD_REQUEST
        }
    },
    contract {
        name = "search_unavailable"
        description = "GET /search?q=unavailable returns 503 when the embedding model is unavailable"
        request {
            method = GET
            url = url("/search?q=unavailable")
        }
        response {
            status = SERVICE_UNAVAILABLE
        }
    }
)
