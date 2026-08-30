import org.springframework.cloud.contract.spec.ContractDsl.Companion.contract

listOf(
    contract {
        name = "create_client_success"
        description = "POST /clients creates a client and returns 201"
        request {
            method = POST
            url = url("/clients")
            headers { contentType = APPLICATION_JSON }
            body = body(
                mapOf(
                    "first_name" to "John",
                    "last_name" to "Doe",
                    "email" to "john.doe@neviswealth.com",
                    "description" to "Private wealth client",
                    "social_links" to listOf("https://linkedin.com/in/john-doe")
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
                        consumer("/clients/31a67593-e39a-4e22-83df-f3494b55a439"),
                        producer(regex("/clients/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
                    )
                )
            }
            body = body(
                mapOf(
                    "id" to value(
                        consumer("31a67593-e39a-4e22-83df-f3494b55a439"),
                        producer(regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
                    ),
                    "first_name" to fromRequest().body("$.first_name"),
                    "last_name" to fromRequest().body("$.last_name"),
                    "email" to fromRequest().body("$.email"),
                    "description" to fromRequest().body("$.description"),
                    "social_links" to listOf("https://linkedin.com/in/john-doe")
                )
            )
        }
    },
    contract {
        name = "create_client_invalid_email"
        description = "POST /clients with an invalid email returns 400"
        request {
            method = POST
            url = url("/clients")
            headers { contentType = APPLICATION_JSON }
            body = body(
                mapOf(
                    "first_name" to "John",
                    "last_name" to "Doe",
                    "email" to "not-an-email",
                    "description" to "Private wealth client",
                    "social_links" to listOf("https://linkedin.com/in/john-doe")
                )
            )
        }
        response {
            status = BAD_REQUEST
        }
    },
    contract {
        name = "create_client_duplicate_email"
        description = "POST /clients with a duplicate email returns 409"
        request {
            method = POST
            url = url("/clients")
            headers { contentType = APPLICATION_JSON }
            body = body(
                mapOf(
                    "first_name" to "John",
                    "last_name" to "Doe",
                    "email" to "duplicate@neviswealth.com",
                    "description" to "Private wealth client",
                    "social_links" to listOf("https://linkedin.com/in/john-doe")
                )
            )
        }
        response {
            status = CONFLICT
        }
    }
)
