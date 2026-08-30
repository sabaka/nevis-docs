package com.github.sabaka.nevis_docs.client;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import io.restassured.module.mockmvc.RestAssuredMockMvc;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.context.WebApplicationContext;

@WebMvcTest(ClientController.class)
public abstract class ClientContractBase {

  private static final UUID CLIENT_ID = UUID.fromString("31a67593-e39a-4e22-83df-f3494b55a439");
  private static final String DUPLICATE_EMAIL = "duplicate@neviswealth.com";

  @Autowired private WebApplicationContext context;

  @MockitoBean private ClientService clientService;
  @MockitoBean private DocumentService documentService;

  @BeforeEach
  public void setUp() {
    RestAssuredMockMvc.webAppContextSetup(context);
    given(clientService.create(any(), any(), any(), any(), any()))
        .willAnswer(
            invocation -> {
              String firstName = invocation.getArgument(0);
              String lastName = invocation.getArgument(1);
              String email = invocation.getArgument(2);
              String description = invocation.getArgument(3);
              List<String> socialLinks = invocation.getArgument(4);
              return new Client(CLIENT_ID, firstName, lastName, email, description, socialLinks);
            });
    given(clientService.create(any(), any(), eq(DUPLICATE_EMAIL), any(), any()))
        .willThrow(new ClientEmailAlreadyExistsException(DUPLICATE_EMAIL));
  }
}
