package com.github.sabaka.nevis_docs.client;

import java.sql.Timestamp;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class DocumentRepository {

  private final JdbcClient jdbcClient;

  DocumentRepository(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  void save(Document document) {
    jdbcClient
        .sql(
            "insert into document (id, client_id, title, content, created_at) "
                + "values (:id, :clientId, :title, :content, :createdAt)")
        .param("id", document.id())
        .param("clientId", document.clientId())
        .param("title", document.title())
        .param("content", document.content())
        .param("createdAt", Timestamp.from(document.createdAt()))
        .update();
  }
}
