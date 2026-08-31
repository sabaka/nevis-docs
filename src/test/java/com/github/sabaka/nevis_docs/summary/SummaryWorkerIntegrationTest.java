package com.github.sabaka.nevis_docs.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.log;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.sabaka.nevis_docs.PostgresTestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlMergeMode;
import org.springframework.test.context.jdbc.SqlMergeMode.MergeMode;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
@Sql(statements = "delete from document", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@SqlMergeMode(MergeMode.MERGE)
class SummaryWorkerIntegrationTest {

  private static final String FAKE_SUMMARY = "An electricity utility bill for 10 Downing Street.";
  private static final String TITLE = "Electricity statement";
  private static final String CONTENT = "Utility bill for 10 Downing Street";

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcClient jdbcClient;
  @Autowired private SummaryWorker summaryWorker;

  @MockitoBean private ChatModel chatModel;

  @Test
  void processPendingSummaries_whenChatModelSucceeds_shouldCompleteWithSummaryAndNoError()
      throws Exception {
    UUID documentId = createDocument(createClient());
    given(chatModel.call(anyString())).willReturn(FAKE_SUMMARY);

    summaryWorker.processPendingSummaries();

    DocumentRow row = readDocument(documentId);
    assertThat(row.status()).isEqualTo("COMPLETED");
    assertThat(row.summary()).isEqualTo(FAKE_SUMMARY);
    assertThat(row.error()).isNull();
  }

  @Test
  void processPendingSummaries_whenChatModelThrows_shouldFailWithChatModelCallFailedReason()
      throws Exception {
    UUID documentId = createDocument(createClient());
    given(chatModel.call(anyString())).willThrow(new RuntimeException("boom"));

    summaryWorker.processPendingSummaries();

    DocumentRow row = readDocument(documentId);
    assertThat(row.status()).isEqualTo("FAILED");
    assertThat(row.summary()).isNull();
    assertThat(row.error()).isEqualTo("chat model call failed");
  }

  @Test
  void processPendingSummaries_whenChatModelReturnsBlank_shouldFailWithBlankSummaryReason()
      throws Exception {
    UUID documentId = createDocument(createClient());
    given(chatModel.call(anyString())).willReturn("   ");

    summaryWorker.processPendingSummaries();

    DocumentRow row = readDocument(documentId);
    assertThat(row.status()).isEqualTo("FAILED");
    assertThat(row.summary()).isNull();
    assertThat(row.error()).isEqualTo("model returned a blank summary");
  }

  @Test
  void processPendingSummaries_whenSuccessful_shouldLeaveSearchEntryUntouched() throws Exception {
    UUID documentId = createDocument(createClient());
    SearchEntryRow before = readSearchEntry(documentId);
    given(chatModel.call(anyString())).willReturn(FAKE_SUMMARY);

    summaryWorker.processPendingSummaries();

    assertThat(readDocument(documentId).status()).isEqualTo("COMPLETED");
    SearchEntryRow after = readSearchEntry(documentId);
    assertThat(after).isEqualTo(before);
  }

  private static String uniqueEmail() {
    return "summary." + UUID.randomUUID() + "@neviswealth.com";
  }

  private UUID createClient() throws Exception {
    String requestBody =
        """
        {
          "first_name": "Jane",
          "last_name": "Summary",
          "email": "%s",
          "description": "Private wealth client",
          "social_links": ["https://linkedin.com/in/jane-summary"]
        }
        """
            .formatted(uniqueEmail());

    var result =
        mockMvc
            .perform(post("/clients").contentType(MediaType.APPLICATION_JSON).content(requestBody))
            .andDo(log())
            .andExpect(status().isCreated())
            .andReturn();

    return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
  }

  private UUID createDocument(UUID clientId) throws Exception {
    String requestBody =
        """
        {
          "title": "%s",
          "content": "%s"
        }
        """
            .formatted(TITLE, CONTENT);

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

  private DocumentRow readDocument(UUID documentId) {
    return jdbcClient
        .sql("select summary_status, summary, summary_error from document where id = :id")
        .param("id", documentId)
        .query(
            (rs, rowNum) ->
                new DocumentRow(
                    rs.getString("summary_status"),
                    rs.getString("summary"),
                    rs.getString("summary_error")))
        .single();
  }

  private SearchEntryRow readSearchEntry(UUID documentId) {
    return jdbcClient
        .sql(
            """
            select searchable_text,
                   lexical_index::text as lexical_index,
                   embedding_status,
                   embedding is not null as embedding_present,
                   updated_at
            from search_entry
            where entity_type = 'DOCUMENT' and entity_id = :entityId
            """)
        .param("entityId", documentId)
        .query(
            (rs, rowNum) ->
                new SearchEntryRow(
                    rs.getString("searchable_text"),
                    rs.getString("lexical_index"),
                    rs.getString("embedding_status"),
                    rs.getBoolean("embedding_present"),
                    rs.getTimestamp("updated_at").toInstant()))
        .single();
  }

  private record DocumentRow(String status, String summary, String error) {}

  private record SearchEntryRow(
      String searchableText,
      String lexicalIndex,
      String embeddingStatus,
      boolean embeddingPresent,
      Instant updatedAt) {}
}
