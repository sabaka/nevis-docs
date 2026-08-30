package com.github.sabaka.nevis_docs.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willAnswer;

import com.github.sabaka.nevis_docs.PostgresTestcontainersConfiguration;
import com.github.sabaka.nevis_docs.search.EntityType;
import com.github.sabaka.nevis_docs.search.SearchIndexer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;

@ActiveProfiles("test")
@SpringBootTest
@Import(PostgresTestcontainersConfiguration.class)
class SearchIndexTransactionalityIntegrationTest {

  private static final UUID SEEDED_CLIENT_ID =
      UUID.fromString("2f1e6c9a-4b3d-4a8e-9c7f-6a1b2c3d4e5f");

  @Autowired private ClientService clientService;
  @Autowired private DocumentService documentService;
  @Autowired private JdbcClient jdbcClient;

  @MockitoBean private SearchIndexer searchIndexer;

  private final AtomicReference<UUID> indexedEntityId = new AtomicReference<>();

  @BeforeEach
  void stubSearchIndexerToFailAfterIndexing() {
    willAnswer(
            invocation -> {
              indexedEntityId.set(invocation.getArgument(1));
              throw new IllegalStateException("indexing failed");
            })
        .given(searchIndexer)
        .index(any(), any(), any());
  }

  @Test
  void createClient_whenIndexingFails_shouldRollBackClientAndSearchEntry() {
    ThrowingCallable call =
        () ->
            clientService.create(
                "John",
                "Doe",
                "rollback.client@neviswealth.com",
                "Private wealth client",
                List.of("https://linkedin.com/in/john-doe"));

    assertThatThrownBy(call).isInstanceOf(IllegalStateException.class);

    assertThat(countClientsByEmail("rollback.client@neviswealth.com")).isZero();
    assertThat(findEntry(EntityType.CLIENT, indexedEntityId.get())).isEmpty();
  }

  @Test
  @Sql(
      statements =
          "insert into client (id, first_name, last_name, email) "
              + "values ('2f1e6c9a-4b3d-4a8e-9c7f-6a1b2c3d4e5f', 'Jane', 'Smith', "
              + "'rollback.document@neviswealth.com')")
  void createDocument_whenIndexingFails_shouldRollBackDocumentAndSearchEntry() {
    ThrowingCallable call =
        () ->
            documentService.create(
                SEEDED_CLIENT_ID, "Electricity statement", "Utility bill for 10 Downing Street");

    assertThatThrownBy(call).isInstanceOf(IllegalStateException.class);

    assertThat(countDocumentsByClientId(SEEDED_CLIENT_ID)).isZero();
    assertThat(findEntry(EntityType.DOCUMENT, indexedEntityId.get())).isEmpty();
  }

  private long countClientsByEmail(String email) {
    return jdbcClient
        .sql("select count(*) from client where lower(email) = lower(:email)")
        .param("email", email)
        .query(Long.class)
        .single();
  }

  private long countDocumentsByClientId(UUID clientId) {
    return jdbcClient
        .sql("select count(*) from document where client_id = :clientId")
        .param("clientId", clientId)
        .query(Long.class)
        .single();
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

  private record SearchEntryRow(
      String searchableText,
      String lexicalIndex,
      String embeddingStatus,
      String embeddingError,
      boolean embeddingPresent,
      int embeddingDims) {}
}
