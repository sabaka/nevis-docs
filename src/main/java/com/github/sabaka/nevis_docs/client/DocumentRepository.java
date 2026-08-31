package com.github.sabaka.nevis_docs.client;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
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

  Map<UUID, Document> findAllByIds(Set<UUID> ids) {
    List<Document> documents =
        jdbcClient
            .sql(
                "select id, client_id, title, content, created_at from document where id in (:ids)")
            .param("ids", ids)
            .query((resultSet, _) -> mapDocument(resultSet))
            .list();
    return documents.stream().collect(Collectors.toMap(Document::id, Function.identity()));
  }

  private static Document mapDocument(ResultSet resultSet) throws SQLException {
    return new Document(
        resultSet.getObject("id", UUID.class),
        resultSet.getObject("client_id", UUID.class),
        resultSet.getString("title"),
        resultSet.getString("content"),
        resultSet.getObject("created_at", Timestamp.class).toInstant());
  }
}
