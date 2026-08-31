package com.github.sabaka.nevis_docs.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.log;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.sabaka.nevis_docs.PostgresTestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Exercises {@code GET /search} end to end against real Postgres: hybrid RRF ranking, semantic
 * candidate eligibility, hydration from the source tables, and the wire shape.
 *
 * <p>Two seeding styles are used deliberately. Ranking and eligibility tests insert {@code
 * search_entry} rows directly, because {@code embedding_status} values other than READY (and null
 * embeddings) cannot be produced through the API. The hydration and stale-entry tests create rows
 * through the real POST endpoints, so the whole ingest-to-search chain is covered at least once.
 */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
@Sql(
    statements = "delete from search_entry",
    executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class SearchIntegrationTest {

  private static final int EMBEDDING_DIMENSIONS = 1024;
  private static final float EMBEDDING_VALUE = 0.125f;

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcClient jdbcClient;

  @MockitoBean private EmbeddingModel embeddingModel;

  @BeforeEach
  void stubEmbeddingModelToReturnNearEmbeddingByDefault() {
    given(embeddingModel.embed(anyString())).willReturn(nearEmbedding());
  }

  @Test
  void search_whenQueryMatchesEmailDomain_shouldReturnClient() throws Exception {
    UUID clientId =
        seedClient(
            "John",
            "Doe",
            "john.doe.search@neviswealth.com",
            "Private wealth client",
            List.of("https://linkedin.com/in/john-doe"));

    assertThat(searchIds("NevisWealth")).contains(clientId.toString());
  }

  @ParameterizedTest
  @MethodSource("searchableClientFields")
  void search_whenQueryMatchesAnyClientField_shouldReturnExactlyThatClient(
      String firstName,
      String lastName,
      String email,
      String description,
      List<String> socialLinks,
      String queryTerm)
      throws Exception {
    UUID clientId = seedClient(firstName, lastName, email, description, socialLinks);

    assertThat(searchIds(queryTerm)).containsExactly(clientId.toString());
  }

  private static Stream<Arguments> searchableClientFields() {
    String firstNameMarker = marker("firstname");
    String lastNameMarker = marker("lastname");
    String emailLocalMarker = marker("emaillocal");
    String descriptionMarker = marker("description");
    String socialLinkMarker = marker("social");
    return Stream.of(
        Arguments.of(
            firstNameMarker,
            "Doe",
            uniqueEmail(),
            "Private wealth client",
            List.of("https://linkedin.com/in/john-doe"),
            firstNameMarker),
        Arguments.of(
            "John",
            lastNameMarker,
            uniqueEmail(),
            "Private wealth client",
            List.of("https://linkedin.com/in/john-doe"),
            lastNameMarker),
        Arguments.of(
            "John",
            "Doe",
            emailLocalMarker + "@neviswealth.com",
            "Private wealth client",
            List.of("https://linkedin.com/in/john-doe"),
            emailLocalMarker),
        Arguments.of(
            "John",
            "Doe",
            uniqueEmail(),
            descriptionMarker,
            List.of("https://linkedin.com/in/john-doe"),
            descriptionMarker),
        Arguments.of(
            "John",
            "Doe",
            uniqueEmail(),
            "Private wealth client",
            List.of("https://linkedin.com/in/" + socialLinkMarker),
            socialLinkMarker));
  }

  @Test
  void search_whenQueryMatchesSemanticallyNotLexically_shouldReturnDocument() throws Exception {
    UUID clientId = seedClientRow();
    UUID documentId =
        seedDocument(
            clientId,
            "Electricity statement",
            "Utility bill for 10 Downing Street",
            "READY",
            nearEmbedding());

    assertThat(searchIds("address proof")).contains(documentId.toString());
  }

  @ParameterizedTest
  @MethodSource("nonSemanticDocumentStatuses")
  void search_whenDocumentNotEligibleForSemanticCandidates_shouldStillMatchLexically(
      String embeddingStatus) throws Exception {
    UUID clientId = seedClientRow();
    String lexicalMarker = marker("lexical");
    UUID documentId = seedDocument(clientId, lexicalMarker, "content body", embeddingStatus, null);

    assertThat(searchIds(lexicalMarker)).containsExactly(documentId.toString());
    assertThat(searchIds("totally unrelated inquiry text")).isEmpty();
  }

  private static Stream<Arguments> nonSemanticDocumentStatuses() {
    return Stream.of(
        Arguments.of("PENDING"),
        Arguments.of("PROCESSING"),
        Arguments.of("FAILED"),
        Arguments.of("NOT_REQUIRED"),
        Arguments.of("READY"));
  }

  @Test
  void search_whenResultsFusedAcrossLexicalAndSemantic_shouldRankDualMatchHighest()
      throws Exception {
    UUID clientId = seedClientRow();
    String fusionQuery = marker("fusion");

    UUID lexicalOnlyClientId =
        seedClient(
            "Jane",
            "Smith",
            uniqueEmail(),
            "Private wealth client " + fusionQuery,
            List.of("https://linkedin.com/in/jane-smith"));
    UUID semanticOnlyDocumentId =
        seedDocument(
            clientId,
            "Quarterly review",
            "Notes without any overlap with the search query",
            "READY",
            nearEmbedding());
    UUID dualMatchDocumentId =
        seedDocument(
            clientId,
            "Quarterly review",
            "Explicitly mentions " + fusionQuery + " in this document",
            "READY",
            withinCutoffEmbedding());

    List<String> ids = searchIds(fusionQuery);

    assertThat(ids)
        .containsExactlyInAnyOrder(
            lexicalOnlyClientId.toString(),
            semanticOnlyDocumentId.toString(),
            dualMatchDocumentId.toString());
    assertThat(ids.getFirst()).isEqualTo(dualMatchDocumentId.toString());
  }

  @Test
  void search_whenClientAndDocumentMatch_shouldReturnBothHydratedFromSourceTablesInScoreOrder()
      throws Exception {
    String marker = marker("hydration");
    String email = uniqueEmail();
    UUID clientId =
        createClient(
            "John",
            "Doe",
            email,
            "Private wealth client " + marker,
            List.of("https://linkedin.com/in/john-doe"));

    String title = "Electricity statement";
    String content = "Utility bill for 10 Downing Street";
    UUID documentId = createDocument(clientId, title, content);
    markDocumentReady(documentId, nearEmbedding());

    mockMvc
        .perform(get("/search").param("q", marker))
        .andDo(log())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].type").value("CLIENT"))
        .andExpect(jsonPath("$[0].id").value(clientId.toString()))
        .andExpect(jsonPath("$[0].first_name").value("John"))
        .andExpect(jsonPath("$[0].last_name").value("Doe"))
        .andExpect(jsonPath("$[0].email").value(email))
        .andExpect(jsonPath("$[0].description").value("Private wealth client " + marker))
        .andExpect(jsonPath("$[0].social_links[0]").value("https://linkedin.com/in/john-doe"))
        .andExpect(jsonPath("$[0].score").doesNotExist())
        .andExpect(jsonPath("$[1].type").value("DOCUMENT"))
        .andExpect(jsonPath("$[1].id").value(documentId.toString()))
        .andExpect(jsonPath("$[1].client_id").value(clientId.toString()))
        .andExpect(jsonPath("$[1].title").value(title))
        .andExpect(jsonPath("$[1].content").value(content))
        .andExpect(jsonPath("$[1].created_at").exists())
        .andExpect(jsonPath("$[1].score").doesNotExist());
  }

  @Test
  void search_whenSearchEntryHasNoSourceRow_shouldSkipStaleEntryAndReturnGenuineResults()
      throws Exception {
    String marker = marker("stale");
    UUID staleDocumentId = UUID.randomUUID();
    insertOrphanDocumentSearchEntry(staleDocumentId, marker + " orphan content");

    UUID clientId =
        createClient(
            "Jane",
            "Smith",
            uniqueEmail(),
            "Private wealth client " + marker,
            List.of("https://linkedin.com/in/jane-smith"));

    assertThat(searchIds(marker))
        .doesNotContain(staleDocumentId.toString())
        .contains(clientId.toString());
  }

  @Test
  void search_whenEmbeddingModelUnavailable_shouldReturn503WithEmptyBody() throws Exception {
    given(embeddingModel.embed(anyString())).willThrow(new RuntimeException("ollama unavailable"));

    mockMvc
        .perform(get("/search").param("q", "anything"))
        .andDo(log())
        .andExpect(status().isServiceUnavailable())
        .andExpect(content().string(""));
  }

  private List<String> searchIds(String query) throws Exception {
    var result =
        mockMvc
            .perform(get("/search").param("q", query))
            .andDo(log())
            .andExpect(status().isOk())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$[*].id");
  }

  private static float[] nearEmbedding() {
    float[] vector = new float[EMBEDDING_DIMENSIONS];
    Arrays.fill(vector, EMBEDDING_VALUE);
    return vector;
  }

  private static float[] withinCutoffEmbedding() {
    float[] vector = new float[EMBEDDING_DIMENSIONS];
    Arrays.fill(vector, EMBEDDING_VALUE);
    Arrays.fill(vector, 0, EMBEDDING_DIMENSIONS / 8, -EMBEDDING_VALUE);
    return vector;
  }

  private static String vectorLiteral(float[] embedding) {
    return IntStream.range(0, embedding.length)
        .mapToObj(index -> String.valueOf(embedding[index]))
        .collect(Collectors.joining(",", "[", "]"));
  }

  private static String uniqueEmail() {
    return "search." + UUID.randomUUID() + "@neviswealth.com";
  }

  private static String marker(String label) {
    return label + UUID.randomUUID().toString().replace("-", "");
  }

  private UUID createClient(
      String firstName, String lastName, String email, String description, List<String> socialLinks)
      throws Exception {
    String requestBody =
        """
        {
          "first_name": "%s",
          "last_name": "%s",
          "email": "%s",
          "description": "%s",
          "social_links": ["%s"]
        }
        """
            .formatted(firstName, lastName, email, description, String.join("\",\"", socialLinks));

    var result =
        mockMvc
            .perform(post("/clients").contentType(MediaType.APPLICATION_JSON).content(requestBody))
            .andDo(log())
            .andExpect(status().isCreated())
            .andReturn();

    return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
  }

  private UUID createDocument(UUID clientId, String title, String content) throws Exception {
    String requestBody =
        """
        {
          "title": "%s",
          "content": "%s"
        }
        """
            .formatted(title, content);

    var result =
        mockMvc
            .perform(
                post("/clients/{clientId}/documents", clientId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
            .andDo(log())
            .andExpect(status().isCreated())
            .andReturn();

    return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
  }

  private void markDocumentReady(UUID documentId, float[] embedding) {
    jdbcClient
        .sql(
            "update search_entry set embedding = cast(:embedding as vector), "
                + "embedding_status = 'READY' "
                + "where entity_type = 'DOCUMENT' and entity_id = :entityId")
        .param("embedding", vectorLiteral(embedding))
        .param("entityId", documentId)
        .update();
  }

  private void insertOrphanDocumentSearchEntry(UUID documentId, String searchableText) {
    jdbcClient
        .sql(
            """
            insert into search_entry (entity_type, entity_id, searchable_text,
                                      embedding, embedding_status, embedding_error,
                                      created_at, updated_at)
            values ('DOCUMENT', :entityId, :searchableText, null, 'NOT_REQUIRED', null, :now, :now)
            """)
        .param("entityId", documentId)
        .param("searchableText", searchableText)
        .param("now", Timestamp.from(Instant.now()))
        .update();
  }

  private UUID seedClientRow() {
    UUID clientId = UUID.randomUUID();
    insertClient(
        clientId,
        "Jane",
        "Smith",
        uniqueEmail(),
        "Private wealth client",
        List.of("https://linkedin.com/in/jane-smith"));
    return clientId;
  }

  private UUID seedClient(
      String firstName,
      String lastName,
      String email,
      String description,
      List<String> socialLinks) {
    UUID clientId = UUID.randomUUID();
    insertClient(clientId, firstName, lastName, email, description, socialLinks);
    String searchableText =
        String.join(" ", firstName, lastName, email, description, String.join(" ", socialLinks));
    insertSearchEntry(EntityType.CLIENT, clientId, searchableText, null, "NOT_REQUIRED");
    return clientId;
  }

  private UUID seedDocument(
      UUID clientId, String title, String content, String embeddingStatus, float[] embedding) {
    UUID documentId = UUID.randomUUID();
    insertDocument(documentId, clientId, title, content, Instant.now());
    insertSearchEntry(
        EntityType.DOCUMENT, documentId, title + "\n" + content, embedding, embeddingStatus);
    return documentId;
  }

  private void insertClient(
      UUID id,
      String firstName,
      String lastName,
      String email,
      String description,
      List<String> socialLinks) {
    jdbcClient
        .sql(
            """
            insert into client (id, first_name, last_name, email, description, social_links)
            values (:id, :firstName, :lastName, :email, :description, cast(:socialLinks as text[]))
            """)
        .param("id", id)
        .param("firstName", firstName)
        .param("lastName", lastName)
        .param("email", email)
        .param("description", description)
        .param("socialLinks", socialLinksLiteral(socialLinks))
        .update();
  }

  private void insertDocument(
      UUID id, UUID clientId, String title, String content, Instant createdAt) {
    jdbcClient
        .sql(
            "insert into document (id, client_id, title, content, created_at) "
                + "values (:id, :clientId, :title, :content, :createdAt)")
        .param("id", id)
        .param("clientId", clientId)
        .param("title", title)
        .param("content", content)
        .param("createdAt", Timestamp.from(createdAt))
        .update();
  }

  private void insertSearchEntry(
      EntityType entityType,
      UUID entityId,
      String searchableText,
      float[] embedding,
      String embeddingStatus) {
    String embeddingExpression = embedding == null ? "null" : "cast(:embedding as vector)";
    var query =
        jdbcClient
            .sql(
                """
                insert into search_entry (entity_type, entity_id, searchable_text,
                                          embedding, embedding_status, embedding_error,
                                          created_at, updated_at)
                values (:entityType, :entityId, :searchableText, %s, :embeddingStatus, null,
                        :now, :now)
                """
                    .formatted(embeddingExpression))
            .param("entityType", entityType.name())
            .param("entityId", entityId)
            .param("searchableText", searchableText)
            .param("embeddingStatus", embeddingStatus)
            .param("now", Timestamp.from(Instant.now()));
    if (embedding != null) {
      query = query.param("embedding", vectorLiteral(embedding));
    }
    query.update();
  }

  private static String socialLinksLiteral(List<String> socialLinks) {
    return socialLinks.stream()
        .map(link -> "\"" + link.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
        .collect(Collectors.joining(",", "{", "}"));
  }
}
