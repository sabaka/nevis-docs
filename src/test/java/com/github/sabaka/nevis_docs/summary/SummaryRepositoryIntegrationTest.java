package com.github.sabaka.nevis_docs.summary;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.sabaka.nevis_docs.PostgresTestcontainersConfiguration;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlMergeMode;
import org.springframework.test.context.jdbc.SqlMergeMode.MergeMode;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import({PostgresTestcontainersConfiguration.class, SummaryRepository.class})
@Sql(
    statements =
        "insert into client (id, first_name, last_name, email) "
            + "values ('9a15b1c2-fb69-4c9d-8f3e-000000000001', 'Jane', 'Repo', "
            + "'summary.repository@neviswealth.com')",
    executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@Sql(statements = "delete from document", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@SqlMergeMode(MergeMode.MERGE)
class SummaryRepositoryIntegrationTest {

  private static final UUID CLIENT_ID = UUID.fromString("9a15b1c2-fb69-4c9d-8f3e-000000000001");
  private static final UUID EARLIEST_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
  private static final UUID MIDDLE_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
  private static final UUID LATEST_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
  private static final UUID DOCUMENT_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");
  private static final Instant NOW = Instant.parse("2026-08-29T14:00:00Z");
  private static final String TITLE = "Electricity statement";
  private static final String CONTENT = "Utility bill for 10 Downing Street";

  @Autowired private SummaryRepository summaryRepository;
  @Autowired private JdbcClient jdbcClient;

  @Test
  @Sql(
      statements = {
        "insert into document (id, client_id, title, content, created_at, summary_status) "
            + "values ('11111111-1111-4111-8111-111111111111', "
            + "'9a15b1c2-fb69-4c9d-8f3e-000000000001', 'Electricity statement', "
            + "'Utility bill for 10 Downing Street', '2026-08-29T14:00:00Z', 'PENDING')",
        "insert into document (id, client_id, title, content, created_at, summary_status) "
            + "values ('22222222-2222-4222-8222-222222222222', "
            + "'9a15b1c2-fb69-4c9d-8f3e-000000000001', 'Electricity statement', "
            + "'Utility bill for 10 Downing Street', '2026-08-29T14:00:01Z', 'PENDING')",
        "insert into document (id, client_id, title, content, created_at, summary_status) "
            + "values ('33333333-3333-4333-8333-333333333333', "
            + "'9a15b1c2-fb69-4c9d-8f3e-000000000001', 'Electricity statement', "
            + "'Utility bill for 10 Downing Street', '2026-08-29T14:00:02Z', 'PENDING')"
      })
  void claimPendingDocuments_shouldClaimEarliestBatchByCreatedAtThenId() {
    List<SummaryCandidate> claimed =
        summaryRepository.claimPendingDocuments(2, NOW.plusSeconds(60));

    assertThat(claimed)
        .extracting(SummaryCandidate::documentId)
        .containsExactlyInAnyOrder(EARLIEST_ID, MIDDLE_ID);
    assertThat(claimed)
        .allSatisfy(
            candidate -> {
              assertThat(candidate.title()).isEqualTo(TITLE);
              assertThat(candidate.content()).isEqualTo(CONTENT);
            });
    assertThat(statusOf(LATEST_ID)).isEqualTo("PENDING");
  }

  @Test
  @Sql(
      statements =
          "insert into document (id, client_id, title, content, created_at, summary_status) "
              + "values ('44444444-4444-4444-8444-444444444444', "
              + "'9a15b1c2-fb69-4c9d-8f3e-000000000001', 'Electricity statement', "
              + "'Utility bill for 10 Downing Street', '2026-08-29T14:00:00Z', 'PENDING')")
  void claimPendingDocuments_shouldTransitionClaimedRowsToProcessing() {
    List<SummaryCandidate> claimed =
        summaryRepository.claimPendingDocuments(5, NOW.plusSeconds(60));

    assertThat(claimed).extracting(SummaryCandidate::documentId).containsExactly(DOCUMENT_ID);
    assertThat(statusOf(DOCUMENT_ID)).isEqualTo("PROCESSING");
  }

  @Test
  @Sql(
      statements =
          "insert into document (id, client_id, title, content, created_at, summary_status) "
              + "values ('44444444-4444-4444-8444-444444444444', "
              + "'9a15b1c2-fb69-4c9d-8f3e-000000000001', 'Electricity statement', "
              + "'Utility bill for 10 Downing Street', '2026-08-29T14:00:00Z', 'PENDING')")
  void claimPendingDocuments_whenCalledAgain_shouldNotReclaimAlreadyProcessingRows() {
    summaryRepository.claimPendingDocuments(5, NOW.plusSeconds(60));

    List<SummaryCandidate> secondClaim =
        summaryRepository.claimPendingDocuments(5, NOW.plusSeconds(120));

    assertThat(secondClaim).isEmpty();
  }

  @Test
  @Sql(
      statements =
          "insert into document (id, client_id, title, content, created_at, summary_status, "
              + "summary_error) "
              + "values ('44444444-4444-4444-8444-444444444444', "
              + "'9a15b1c2-fb69-4c9d-8f3e-000000000001', 'Electricity statement', "
              + "'Utility bill for 10 Downing Street', '2026-08-29T14:00:00Z', 'PROCESSING', "
              + "'chat model call failed')")
  void markCompleted_whenRowIsProcessing_shouldPersistSummaryAndClearStaleError() {
    int updatedRows =
        summaryRepository.markCompleted(
            DOCUMENT_ID, "An electricity utility bill for 10 Downing Street.", NOW.plusSeconds(60));

    assertThat(updatedRows).isEqualTo(1);
    DocumentRow row = readDocument(DOCUMENT_ID);
    assertThat(row.status()).isEqualTo("COMPLETED");
    assertThat(row.summary()).isEqualTo("An electricity utility bill for 10 Downing Street.");
    assertThat(row.error()).isNull();
  }

  @Test
  @Sql(
      statements =
          "insert into document (id, client_id, title, content, created_at, summary_status) "
              + "values ('44444444-4444-4444-8444-444444444444', "
              + "'9a15b1c2-fb69-4c9d-8f3e-000000000001', 'Electricity statement', "
              + "'Utility bill for 10 Downing Street', '2026-08-29T14:00:00Z', 'PROCESSING')")
  void markFailed_whenRowIsProcessing_shouldNullSummaryStoreReasonAndNotBeReclaimed() {
    int updatedRows =
        summaryRepository.markFailed(DOCUMENT_ID, "chat model call failed", NOW.plusSeconds(60));

    assertThat(updatedRows).isEqualTo(1);
    DocumentRow row = readDocument(DOCUMENT_ID);
    assertThat(row.status()).isEqualTo("FAILED");
    assertThat(row.summary()).isNull();
    assertThat(row.error()).isEqualTo("chat model call failed");

    List<SummaryCandidate> reclaimed =
        summaryRepository.claimPendingDocuments(5, NOW.plusSeconds(120));
    assertThat(reclaimed).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(
      value = DocumentSummaryStatus.class,
      names = {"PENDING", "COMPLETED", "FAILED"})
  void terminalUpdates_whenRowNotProcessing_shouldReturnZeroAndLeaveStatusUnchanged(
      DocumentSummaryStatus status) {
    String summary = status == DocumentSummaryStatus.COMPLETED ? CONTENT : null;
    insertDocument(DOCUMENT_ID, NOW, status.name(), summary);

    int completedRows =
        summaryRepository.markCompleted(DOCUMENT_ID, "ignored", NOW.plusSeconds(10));
    int failedRows = summaryRepository.markFailed(DOCUMENT_ID, "ignored", NOW.plusSeconds(10));

    assertThat(completedRows).isZero();
    assertThat(failedRows).isZero();
    assertThat(statusOf(DOCUMENT_ID)).isEqualTo(status.name());
  }

  private void insertDocument(UUID documentId, Instant createdAt, String status, String summary) {
    jdbcClient
        .sql(
            "insert into document (id, client_id, title, content, created_at, summary, "
                + "summary_status) "
                + "values (:id, :clientId, :title, :content, :createdAt, :summary, :status)")
        .param("id", documentId)
        .param("clientId", CLIENT_ID)
        .param("title", TITLE)
        .param("content", CONTENT)
        .param("createdAt", Timestamp.from(createdAt))
        .param("summary", summary)
        .param("status", status)
        .update();
  }

  private String statusOf(UUID documentId) {
    return readDocument(documentId).status();
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

  private record DocumentRow(String status, String summary, String error) {}
}
