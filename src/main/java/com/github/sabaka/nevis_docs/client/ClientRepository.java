package com.github.sabaka.nevis_docs.client;

import java.util.UUID;
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
}
