package com.github.sabaka.nevis_docs.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.log;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.sabaka.nevis_docs.PostgresTestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
class ClientDocumentIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcClient jdbcClient;

  @Test
  void createsClientAndDocumentAndPersistsBothRowsWithForeignKeyLink() throws Exception {
    String createClientRequest =
        """
        {
          "first_name": "John",
          "last_name": "Doe",
          "email": "john.doe@neviswealth.com",
          "description": "Private wealth client",
          "social_links": ["https://linkedin.com/in/john-doe"]
        }
        """;

    var createClientResult =
        mockMvc
            .perform(
                post("/clients")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createClientRequest))
            .andDo(log())
            .andExpect(status().isCreated())
            .andReturn();

    String clientId = JsonPath.read(createClientResult.getResponse().getContentAsString(), "$.id");

    String createDocumentRequest =
        """
        {
          "title": "Electricity statement",
          "content": "Utility bill for 10 Downing Street"
        }
        """;

    var createDocumentResult =
        mockMvc
            .perform(
                post("/clients/{clientId}/documents", clientId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createDocumentRequest))
            .andDo(log())
            .andExpect(status().isCreated())
            .andReturn();

    String documentId =
        JsonPath.read(createDocumentResult.getResponse().getContentAsString(), "$.id");

    ClientRow clientRow = readClient(UUID.fromString(clientId));
    assertThat(clientRow.firstName()).isEqualTo("John");
    assertThat(clientRow.lastName()).isEqualTo("Doe");
    assertThat(clientRow.email()).isEqualTo("john.doe@neviswealth.com");
    assertThat(clientRow.description()).isEqualTo("Private wealth client");
    assertThat(clientRow.socialLinks()).containsExactly("https://linkedin.com/in/john-doe");

    DocumentRow documentRow = readDocument(UUID.fromString(documentId));
    assertThat(documentRow.clientId()).isEqualToIgnoringCase(clientId);
    assertThat(documentRow.title()).isEqualTo("Electricity statement");
    assertThat(documentRow.content()).isEqualTo("Utility bill for 10 Downing Street");
  }

  @Test
  void createDocument_whenClientDoesNotExist_shouldReturn404AndNotPersistDocument()
      throws Exception {
    UUID unknownClientId = UUID.randomUUID();
    long documentCountBefore = countDocuments();

    String createDocumentRequest =
        """
        {
          "title": "Electricity statement",
          "content": "Utility bill for 10 Downing Street"
        }
        """;

    mockMvc
        .perform(
            post("/clients/{clientId}/documents", unknownClientId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createDocumentRequest))
        .andDo(log())
        .andExpect(status().isNotFound());

    assertThat(countDocuments()).isEqualTo(documentCountBefore);
  }

  @Test
  void createDocument_whenClientIdMalformed_shouldReturn400() throws Exception {
    String createDocumentRequest =
        """
        {
          "title": "Electricity statement",
          "content": "Utility bill for 10 Downing Street"
        }
        """;

    mockMvc
        .perform(
            post("/clients/{clientId}/documents", "not-a-uuid")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createDocumentRequest))
        .andDo(log())
        .andExpect(status().isBadRequest());
  }

  private ClientRow readClient(UUID id) {
    return jdbcClient
        .sql(
            "select first_name, last_name, email, description, social_links "
                + "from client where id = :id")
        .param("id", id)
        .query(
            (rs, _) ->
                new ClientRow(
                    rs.getString("first_name"),
                    rs.getString("last_name"),
                    rs.getString("email"),
                    rs.getString("description"),
                    (String[]) rs.getArray("social_links").getArray()))
        .single();
  }

  private DocumentRow readDocument(UUID id) {
    return jdbcClient
        .sql("select client_id, title, content from document where id = :id")
        .param("id", id)
        .query(
            (rs, _) ->
                new DocumentRow(
                    rs.getString("client_id"), rs.getString("title"), rs.getString("content")))
        .single();
  }

  private long countDocuments() {
    return jdbcClient.sql("select count(*) from document").query(Long.class).single();
  }

  private record ClientRow(
      String firstName, String lastName, String email, String description, String[] socialLinks) {}

  private record DocumentRow(String clientId, String title, String content) {}
}
