package com.github.sabaka.nevis_docs.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.log;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.sabaka.nevis_docs.PostgresTestcontainersConfiguration;
import com.github.sabaka.nevis_docs.search.EntityType;
import com.jayway.jsonpath.JsonPath;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
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
            .andExpect(jsonPath("$.summary").value(nullValue()))
            .andExpect(jsonPath("$.summary_status").value("PENDING"))
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
    assertThat(documentRow.summary()).isNull();
    assertThat(documentRow.summaryStatus()).isEqualTo("PENDING");
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

  @Test
  void createClient_shouldPersistClientSearchEntry() throws Exception {
    String createClientRequest =
        """
        {
          "first_name": "John",
          "last_name": "Doe",
          "email": "search.client@neviswealth.com",
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

    UUID clientId =
        UUID.fromString(
            JsonPath.read(createClientResult.getResponse().getContentAsString(), "$.id"));

    Optional<SearchEntryRow> entry = findEntry(EntityType.CLIENT, clientId);
    assertThat(entry).isPresent();
    SearchEntryRow row = entry.orElseThrow();
    assertThat(row.searchableText())
        .isEqualTo(
            "John Doe search.client@neviswealth.com Private wealth client"
                + " https://linkedin.com/in/john-doe");
    assertThat(row.lexicalIndex()).isNotBlank();
    assertThat(row.embeddingStatus()).isEqualTo("NOT_REQUIRED");
    assertThat(row.embeddingPresent()).isFalse();
  }

  @Test
  void createDocument_shouldPersistDocumentSearchEntryInPendingState() throws Exception {
    String createClientRequest =
        """
        {
          "first_name": "John",
          "last_name": "Doe",
          "email": "search.document@neviswealth.com",
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

    UUID documentId =
        UUID.fromString(
            JsonPath.read(createDocumentResult.getResponse().getContentAsString(), "$.id"));

    Optional<SearchEntryRow> entry = findEntry(EntityType.DOCUMENT, documentId);
    assertThat(entry).isPresent();
    SearchEntryRow row = entry.orElseThrow();
    assertThat(row.searchableText())
        .isEqualTo("Electricity statement\nUtility bill for 10 Downing Street");
    assertThat(row.lexicalIndex()).isNotBlank();
    assertThat(row.embeddingStatus()).isEqualTo("PENDING");
    assertThat(row.embeddingPresent()).isFalse();
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
        .sql(
            "select client_id, title, content, summary, summary_status "
                + "from document where id = :id")
        .param("id", id)
        .query(
            (rs, _) ->
                new DocumentRow(
                    rs.getString("client_id"),
                    rs.getString("title"),
                    rs.getString("content"),
                    rs.getString("summary"),
                    rs.getString("summary_status")))
        .single();
  }

  private long countDocuments() {
    return jdbcClient.sql("select count(*) from document").query(Long.class).single();
  }

  private Optional<SearchEntryRow> findEntry(EntityType entityType, UUID entityId) {
    return jdbcClient
        .sql(
            """
            select searchable_text,
                   lexical_index::text as lexical_index,
                   embedding_status,
                   embedding_error,
                   embedding is not null as embedding_present,
                   coalesce(vector_dims(embedding), 0) as embedding_dims
            from search_entry
            where entity_type = :entityType and entity_id = :entityId
            """)
        .param("entityType", entityType.name())
        .param("entityId", entityId)
        .query(
            (rs, rowNum) ->
                new SearchEntryRow(
                    rs.getString("searchable_text"),
                    rs.getString("lexical_index"),
                    rs.getString("embedding_status"),
                    rs.getString("embedding_error"),
                    rs.getBoolean("embedding_present"),
                    rs.getInt("embedding_dims")))
        .optional();
  }

  private record ClientRow(
      String firstName, String lastName, String email, String description, String[] socialLinks) {}

  private record DocumentRow(
      String clientId, String title, String content, String summary, String summaryStatus) {}

  private record SearchEntryRow(
      String searchableText,
      String lexicalIndex,
      String embeddingStatus,
      String embeddingError,
      boolean embeddingPresent,
      int embeddingDims) {}
}
