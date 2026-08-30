package com.github.sabaka.nevis_docs.search.embedding;

import com.github.sabaka.nevis_docs.search.EntityType;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class EmbeddingRepository {

  private final JdbcClient jdbcClient;

  EmbeddingRepository(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  List<ClaimedEntry> claimPendingDocuments(int batchSize, Instant now) {
    return jdbcClient
        .sql(
            """
            with candidate as (
                select entity_type, entity_id
                from search_entry
                where entity_type = :entityType
                  and embedding_status = 'PENDING'
                order by created_at
                limit :batchSize
                for update skip locked
            )
            update search_entry entry
            set embedding_status = 'PROCESSING',
                updated_at = :now
            from candidate
            where entry.entity_type = candidate.entity_type
              and entry.entity_id = candidate.entity_id
            returning entry.entity_type, entry.entity_id, entry.searchable_text
            """)
        .param("entityType", EntityType.DOCUMENT.name())
        .param("batchSize", batchSize)
        .param("now", Timestamp.from(now))
        .query(
            (resultSet, _) ->
                new ClaimedEntry(
                    EntityType.valueOf(resultSet.getString("entity_type")),
                    resultSet.getObject("entity_id", UUID.class),
                    resultSet.getString("searchable_text")))
        .list();
  }

  void markReady(EntityType entityType, UUID entityId, float[] embedding, Instant updatedAt) {
    jdbcClient
        .sql(
            """
            update search_entry
            set embedding = cast(:embedding as vector), embedding_status = 'READY',
                embedding_error = null, updated_at = :updatedAt
            where entity_type = :entityType and entity_id = :entityId
            """)
        .param("embedding", toVectorLiteral(embedding))
        .param("updatedAt", Timestamp.from(updatedAt))
        .param("entityType", entityType.name())
        .param("entityId", entityId)
        .update();
  }

  void markFailed(EntityType entityType, UUID entityId, String error, Instant updatedAt) {
    jdbcClient
        .sql(
            """
            update search_entry
            set embedding = null, embedding_status = 'FAILED',
                embedding_error = :error, updated_at = :updatedAt
            where entity_type = :entityType and entity_id = :entityId
            """)
        .param("error", error)
        .param("updatedAt", Timestamp.from(updatedAt))
        .param("entityType", entityType.name())
        .param("entityId", entityId)
        .update();
  }

  private static String toVectorLiteral(float[] embedding) {
    StringBuilder builder = new StringBuilder(embedding.length * 8 + 2).append('[');
    for (int index = 0; index < embedding.length; index++) {
      if (index > 0) {
        builder.append(',');
      }
      builder.append(embedding[index]);
    }
    return builder.append(']').toString();
  }
}
