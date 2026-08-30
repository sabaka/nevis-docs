package com.github.sabaka.nevis_docs.client;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import io.restassured.module.mockmvc.RestAssuredMockMvc;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.context.WebApplicationContext;

@WebMvcTest(ClientController.class)
public abstract class DocumentContractBase {

  private static final UUID CLIENT_ID = UUID.fromString("31a67593-e39a-4e22-83df-f3494b55a439");
  private static final UUID DOCUMENT_ID = UUID.fromString("66206f62-cff6-4e52-ad8e-978b8d8b9094");
  private static final UUID UNKNOWN_CLIENT_ID =
      UUID.fromString("4d0f2f1a-8b3c-4d5e-9f60-1a2b3c4d5e6f");
  private static final Instant CREATED_AT = Instant.parse("2026-08-29T14:00:00Z");

  @Autowired private WebApplicationContext context;

  @MockitoBean private ClientService clientService;
  @MockitoBean private DocumentService documentService;

  @BeforeEach
  public void setUp() {
    RestAssuredMockMvc.webAppContextSetup(context);
    given(documentService.create(eq(CLIENT_ID), any(), any()))
        .willAnswer(
            invocation -> {
              String title = invocation.getArgument(1);
              String content = invocation.getArgument(2);
              return new Document(DOCUMENT_ID, CLIENT_ID, title, content, CREATED_AT);
            });
    given(documentService.create(eq(UNKNOWN_CLIENT_ID), any(), any()))
        .willThrow(new ClientNotFoundException(UNKNOWN_CLIENT_ID));
  }
}
