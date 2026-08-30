package com.github.sabaka.nevis_docs.search;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class SearchEntryRepository {

  private final JdbcClient jdbcClient;

  SearchEntryRepository(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  void upsert(
      EntityType entityType,
      UUID entityId,
      String searchableText,
      EmbeddingStatus embeddingStatus,
      Instant now) {
    jdbcClient
        .sql(
            """
            insert into search_entry (entity_type, entity_id, searchable_text,
                                      embedding, embedding_status, embedding_error,
                                      created_at, updated_at)
            values (:entityType, :entityId, :searchableText,
                    null, :embeddingStatus, null,
                    :now, :now)
            on conflict (entity_type, entity_id) do update
            set searchable_text  = excluded.searchable_text,
                embedding        = null,
                embedding_status = excluded.embedding_status,
                embedding_error  = null,
                updated_at       = excluded.updated_at
            """)
        .param("entityType", entityType.name())
        .param("entityId", entityId)
        .param("searchableText", searchableText)
        .param("embeddingStatus", embeddingStatus.name())
        .param("now", Timestamp.from(now))
        .update();
  }
}
