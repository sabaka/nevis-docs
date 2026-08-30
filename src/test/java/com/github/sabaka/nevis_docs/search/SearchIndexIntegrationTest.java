package com.github.sabaka.nevis_docs.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.sabaka.nevis_docs.PostgresTestcontainersConfiguration;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ActiveProfiles("test")
@SpringBootTest
@Import(PostgresTestcontainersConfiguration.class)
class SearchIndexIntegrationTest {

  private static final int EMBEDDING_DIMENSIONS = 1024;
  private static final float EMBEDDING_VALUE = 0.125f;

  @Autowired private JdbcClient jdbcClient;
  @Autowired private SearchIndexer searchIndexer;
  @Autowired private PlatformTransactionManager platformTransactionManager;

  @Test
  void index_whenCalledTwiceForSameEntity_shouldReplaceTextAndResetEmbeddingState() {
    UUID entityId = UUID.randomUUID();
    TransactionTemplate transactionTemplate = new TransactionTemplate(platformTransactionManager);

    transactionTemplate.executeWithoutResult(
        status -> searchIndexer.index(EntityType.DOCUMENT, entityId, () -> "first text"));

    jdbcClient
        .sql(
            "update search_entry set embedding = cast(:embedding as vector), "
                + "embedding_status = 'READY' "
                + "where entity_type = :entityType and entity_id = :entityId")
        .param("embedding", probeVectorLiteral())
        .param("entityType", EntityType.DOCUMENT.name())
        .param("entityId", entityId)
        .update();

    transactionTemplate.executeWithoutResult(
        status -> searchIndexer.index(EntityType.DOCUMENT, entityId, () -> "second text"));

    Optional<SearchEntryRow> entry = findEntry(EntityType.DOCUMENT, entityId);
    assertThat(entry).isPresent();
    SearchEntryRow row = entry.orElseThrow();
    assertThat(row.searchableText()).isEqualTo("second text");
    assertThat(row.embeddingStatus()).isEqualTo("PENDING");
    assertThat(row.embeddingPresent()).isFalse();
  }

  @Test
  void index_whenCalledOutsideTransaction_shouldThrowIllegalTransactionStateException() {
    UUID entityId = UUID.randomUUID();
    ThrowingCallable call = () -> searchIndexer.index(EntityType.DOCUMENT, entityId, () -> "text");

    assertThatThrownBy(call).isInstanceOf(IllegalTransactionStateException.class);
  }

  private static String probeVectorLiteral() {
    return IntStream.range(0, EMBEDDING_DIMENSIONS)
        .mapToObj(index -> String.valueOf(EMBEDDING_VALUE))
        .collect(Collectors.joining(",", "[", "]"));
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
