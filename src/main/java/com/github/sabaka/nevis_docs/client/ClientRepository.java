package com.github.sabaka.nevis_docs.client;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class ClientRepository {

  private final JdbcClient jdbcClient;

  ClientRepository(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  void save(Client client) {
    jdbcClient
        .sql(
            "insert into client (id, first_name, last_name, email, description, social_links) "
                + "values (:id, :firstName, :lastName, :email, :description, :socialLinks)")
        .param("id", client.id())
        .param("firstName", client.firstName())
        .param("lastName", client.lastName())
        .param("email", client.email())
        .param("description", client.description())
        .param("socialLinks", client.socialLinks().toArray(String[]::new))
        .update();
  }

  boolean existsById(UUID id) {
    return jdbcClient
        .sql("select exists(select 1 from client where id = :id)")
        .param("id", id)
        .query(Boolean.class)
        .single();
  }

  boolean existsByEmail(String email) {
    return jdbcClient
        .sql("select exists(select 1 from client where lower(email) = lower(:email))")
        .param("email", email)
        .query(Boolean.class)
        .single();
  }

  Map<UUID, Client> findAllByIds(Set<UUID> ids) {
    List<Client> clients =
        jdbcClient
            .sql(
                "select id, first_name, last_name, email, description, social_links "
                    + "from client where id in (:ids)")
            .param("ids", ids)
            .query((resultSet, _) -> mapClient(resultSet))
            .list();
    return clients.stream().collect(Collectors.toMap(Client::id, Function.identity()));
  }

  private static Client mapClient(ResultSet resultSet) throws SQLException {
    Array socialLinks = resultSet.getArray("social_links");
    return new Client(
        resultSet.getObject("id", UUID.class),
        resultSet.getString("first_name"),
        resultSet.getString("last_name"),
        resultSet.getString("email"),
        resultSet.getString("description"),
        List.of((String[]) socialLinks.getArray()));
  }
}
