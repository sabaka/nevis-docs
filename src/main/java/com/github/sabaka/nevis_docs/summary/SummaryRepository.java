package com.github.sabaka.nevis_docs.summary;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class SummaryRepository {

  private final JdbcClient jdbcClient;

  SummaryRepository(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  List<SummaryCandidate> claimPendingDocuments(int batchSize, Instant now) {
    return jdbcClient
        .sql(
            """
            with candidate as (
                select id from document
                where summary_status = 'PENDING'
                order by created_at, id
                limit :batchSize
                for update skip locked
            )
            update document doc
            set summary_status = 'PROCESSING', summary_updated_at = :now
            from candidate
            where doc.id = candidate.id
            returning doc.id, doc.title, doc.content
            """)
        .param("batchSize", batchSize)
        .param("now", Timestamp.from(now))
        .query(
            (resultSet, _) ->
                new SummaryCandidate(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getString("title"),
                    resultSet.getString("content")))
        .list();
  }

  int markCompleted(UUID documentId, String summary, Instant updatedAt) {
    return jdbcClient
        .sql(
            """
            update document
            set summary_status = 'COMPLETED', summary = :summary,
                summary_error = null, summary_updated_at = :updatedAt
            where id = :id and summary_status = 'PROCESSING'
            """)
        .param("summary", summary)
        .param("updatedAt", Timestamp.from(updatedAt))
        .param("id", documentId)
        .update();
  }

  int markFailed(UUID documentId, String error, Instant updatedAt) {
    return jdbcClient
        .sql(
            """
            update document
            set summary_status = 'FAILED', summary = null,
                summary_error = :error, summary_updated_at = :updatedAt
            where id = :id and summary_status = 'PROCESSING'
            """)
        .param("error", error)
        .param("updatedAt", Timestamp.from(updatedAt))
        .param("id", documentId)
        .update();
  }
}
