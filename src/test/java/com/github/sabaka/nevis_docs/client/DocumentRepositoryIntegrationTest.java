package com.github.sabaka.nevis_docs.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.sabaka.nevis_docs.PostgresTestcontainersConfiguration;
import com.github.sabaka.nevis_docs.summary.DocumentSummaryStatus;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
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

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import({PostgresTestcontainersConfiguration.class, DocumentRepository.class})
@Sql(
    statements =
        "insert into client (id, first_name, last_name, email) "
            + "values ('31a67593-e39a-4e22-83df-f3494b55a439', 'John', 'Doe', 'john.doe@neviswealth.com')",
    executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class DocumentRepositoryIntegrationTest {

  private static final UUID CLIENT_ID = UUID.fromString("31a67593-e39a-4e22-83df-f3494b55a439");

  @Autowired private DocumentRepository documentRepository;
  @Autowired private JdbcClient jdbcClient;

  @Test
  void save_shouldPersistDocumentWithAllFields() {
    UUID documentId = UUID.randomUUID();
    Instant createdAt = Instant.parse("2026-08-29T14:00:00.123456Z");

    documentRepository.save(
        new Document(
            documentId,
            CLIENT_ID,
            "Electricity statement",
            "Utility bill for 10 Downing Street",
            createdAt,
            null,
            DocumentSummaryStatus.PENDING));

    DocumentRow row = readDocument(documentId);
    assertThat(row.clientId()).isEqualTo(CLIENT_ID);
    assertThat(row.title()).isEqualTo("Electricity statement");
    assertThat(row.content()).isEqualTo("Utility bill for 10 Downing Street");
    assertThat(row.createdAt()).isEqualTo(createdAt);
    assertThat(row.summary()).isNull();
    assertThat(row.summaryStatus()).isEqualTo("PENDING");
  }

  @ParameterizedTest
  @EnumSource(DocumentSummaryStatus.class)
  void findAllByIds_shouldRoundTripEveryDocumentSummaryStatus(DocumentSummaryStatus summaryStatus) {
    UUID documentId = UUID.randomUUID();
    Instant createdAt = Instant.parse("2026-08-29T14:00:00Z");
    String summary =
        summaryStatus == DocumentSummaryStatus.COMPLETED
            ? "An electricity utility bill for 10 Downing Street."
            : null;

    documentRepository.save(
        new Document(
            documentId,
            CLIENT_ID,
            "Electricity statement",
            "Utility bill for 10 Downing Street",
            createdAt,
            summary,
            summaryStatus));

    Map<UUID, Document> found = documentRepository.findAllByIds(Set.of(documentId));

    assertThat(found).containsOnlyKeys(documentId);
    Document document = found.get(documentId);
    assertThat(document.summary()).isEqualTo(summary);
    assertThat(document.summaryStatus()).isEqualTo(summaryStatus);
  }

  private DocumentRow readDocument(UUID id) {
    return jdbcClient
        .sql(
            "select client_id, title, content, created_at, summary, summary_status "
                + "from document where id = :id")
        .param("id", id)
        .query(
            (rs, _) ->
                new DocumentRow(
                    UUID.fromString(rs.getString("client_id")),
                    rs.getString("title"),
                    rs.getString("content"),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getString("summary"),
                    rs.getString("summary_status")))
        .single();
  }

  private record DocumentRow(
      UUID clientId,
      String title,
      String content,
      Instant createdAt,
      String summary,
      String summaryStatus) {}
}
