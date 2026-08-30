package com.github.sabaka.nevis_docs.search.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.github.sabaka.nevis_docs.PostgresTestcontainersConfiguration;
import com.github.sabaka.nevis_docs.search.EntityType;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlMergeMode;
import org.springframework.test.context.jdbc.SqlMergeMode.MergeMode;

@ActiveProfiles("test")
@SpringBootTest
@Import(PostgresTestcontainersConfiguration.class)
@Sql(
    statements = "delete from search_entry",
    executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@SqlMergeMode(MergeMode.MERGE)
class EmbeddingWorkerIntegrationTest {

  private static final int EMBEDDING_DIMENSIONS = 1024;
  private static final float EMBEDDING_VALUE = 0.125f;
  private static final String SEARCHABLE_TEXT =
      "Electricity statement\nUtility bill for 10 Downing Street";

  @Autowired private EmbeddingWorker embeddingWorker;
  @Autowired private JdbcClient jdbcClient;

  @MockitoBean private EmbeddingModel embeddingModel;

  @Test
  void processPendingEmbeddings_whenDocumentEntryPending_shouldPersistReadyEmbedding() {
    UUID entityId = seedEntry(EntityType.DOCUMENT, "PENDING", SEARCHABLE_TEXT);
    given(embeddingModel.embed(anyString())).willReturn(fakeEmbedding());

    embeddingWorker.processPendingEmbeddings();

    verify(embeddingModel).embed(SEARCHABLE_TEXT);
    Optional<SearchEntryRow> entry = findEntry(EntityType.DOCUMENT, entityId);
    assertThat(entry).isPresent();
    SearchEntryRow row = entry.orElseThrow();
    assertThat(row.embeddingStatus()).isEqualTo("READY");
    assertThat(row.embeddingError()).isNull();
    assertThat(row.embeddingDims()).isEqualTo(EMBEDDING_DIMENSIONS);
    assertThat(cosineDistanceToProbe(EntityType.DOCUMENT, entityId)).isCloseTo(0.0d, within(1e-6d));
  }

  @Test
  void processPendingEmbeddings_whenModelFails_shouldMarkEntryFailedWithoutEmbedding() {
    UUID entityId = seedEntry(EntityType.DOCUMENT, "PENDING", SEARCHABLE_TEXT);
    given(embeddingModel.embed(anyString())).willThrow(new RuntimeException("boom"));

    embeddingWorker.processPendingEmbeddings();

    Optional<SearchEntryRow> entry = findEntry(EntityType.DOCUMENT, entityId);
    assertThat(entry).isPresent();
    SearchEntryRow row = entry.orElseThrow();
    assertThat(row.embeddingStatus()).isEqualTo("FAILED");
    assertThat(row.embeddingPresent()).isFalse();
    assertThat(row.embeddingError()).isEqualTo("RuntimeException");
  }

  @ParameterizedTest
  @MethodSource("nonClaimableEntries")
  void processPendingEmbeddings_whenEntryNotClaimable_shouldNotCallModel(
      EntityType entityType, String embeddingStatus) {
    UUID entityId = seedEntry(entityType, embeddingStatus, SEARCHABLE_TEXT);

    embeddingWorker.processPendingEmbeddings();

    verifyNoInteractions(embeddingModel);
    Optional<SearchEntryRow> entry = findEntry(entityType, entityId);
    assertThat(entry).isPresent();
    assertThat(entry.orElseThrow().embeddingStatus()).isEqualTo(embeddingStatus);
  }

  private static Stream<Arguments> nonClaimableEntries() {
    return Stream.of(
        Arguments.of(EntityType.DOCUMENT, "PROCESSING"),
        Arguments.of(EntityType.DOCUMENT, "READY"),
        Arguments.of(EntityType.DOCUMENT, "FAILED"),
        Arguments.of(EntityType.DOCUMENT, "NOT_REQUIRED"),
        Arguments.of(EntityType.CLIENT, "PENDING"));
  }

  private static float[] fakeEmbedding() {
    float[] embedding = new float[EMBEDDING_DIMENSIONS];
    Arrays.fill(embedding, EMBEDDING_VALUE);
    return embedding;
  }

  private static String probeVectorLiteral() {
    return IntStream.range(0, EMBEDDING_DIMENSIONS)
        .mapToObj(index -> String.valueOf(EMBEDDING_VALUE))
        .collect(Collectors.joining(",", "[", "]"));
  }

  private UUID seedEntry(EntityType entityType, String embeddingStatus, String searchableText) {
    UUID entityId = UUID.randomUUID();
    jdbcClient
        .sql(
            """
            insert into search_entry (entity_type, entity_id, searchable_text,
                                      embedding, embedding_status, embedding_error,
                                      created_at, updated_at)
            values (:entityType, :entityId, :searchableText, null, :embeddingStatus, null, :now, :now)
            """)
        .param("entityType", entityType.name())
        .param("entityId", entityId)
        .param("searchableText", searchableText)
        .param("embeddingStatus", embeddingStatus)
        .param("now", Timestamp.from(Instant.parse("2026-08-29T14:00:00Z")))
        .update();
    return entityId;
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

  private double cosineDistanceToProbe(EntityType entityType, UUID entityId) {
    return jdbcClient
        .sql(
            "select embedding <=> cast(:probe as vector) from search_entry "
                + "where entity_type = :entityType and entity_id = :entityId")
        .param("probe", probeVectorLiteral())
        .param("entityType", entityType.name())
        .param("entityId", entityId)
        .query(Double.class)
        .single();
  }

  private record SearchEntryRow(
      String searchableText,
      String lexicalIndex,
      String embeddingStatus,
      String embeddingError,
      boolean embeddingPresent,
      int embeddingDims) {}
}
