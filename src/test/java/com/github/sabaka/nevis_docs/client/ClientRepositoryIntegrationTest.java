package com.github.sabaka.nevis_docs.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.sabaka.nevis_docs.PostgresTestcontainersConfiguration;
import java.util.List;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import({PostgresTestcontainersConfiguration.class, ClientRepository.class})
class ClientRepositoryIntegrationTest {

  @Autowired private ClientRepository clientRepository;
  @Autowired private JdbcClient jdbcClient;

  @Test
  void save_whenSocialLinksPopulated_shouldPersistArrayAndBeFoundById() {
    UUID id = UUID.randomUUID();
    Client client =
        new Client(
            id,
            "John",
            "Doe",
            "john.doe@neviswealth.com",
            "Private wealth client",
            List.of("https://linkedin.com/in/john-doe", "https://twitter.com/johndoe"));

    clientRepository.save(client);

    ClientRow row = readClient(id);
    assertThat(row.firstName()).isEqualTo("John");
    assertThat(row.lastName()).isEqualTo("Doe");
    assertThat(row.email()).isEqualTo("john.doe@neviswealth.com");
    assertThat(row.description()).isEqualTo("Private wealth client");
    assertThat(row.socialLinks())
        .containsExactly("https://linkedin.com/in/john-doe", "https://twitter.com/johndoe");
    assertThat(clientRepository.existsById(id)).isTrue();
  }

  @Test
  void save_whenSocialLinksEmpty_shouldPersistEmptyArrayNotNull() {
    UUID id = UUID.randomUUID();
    Client client = new Client(id, "Jane", "Smith", "jane.smith@neviswealth.com", null, List.of());

    clientRepository.save(client);

    ClientRow row = readClient(id);
    assertThat(row.description()).isNull();
    assertThat(row.socialLinks()).isEmpty();
  }

  @Test
  void existsById_whenClientUnknown_shouldReturnFalse() {
    assertThat(clientRepository.existsById(UUID.randomUUID())).isFalse();
  }

  @Test
  void existsByEmail_whenEmailExists_shouldReturnTrue() {
    String email = "existing@neviswealth.com";
    clientRepository.save(
        new Client(UUID.randomUUID(), "John", "Doe", email, "Private wealth client", List.of()));

    assertThat(clientRepository.existsByEmail(email)).isTrue();
  }

  @Test
  void existsByEmail_whenEmailUnknown_shouldReturnFalse() {
    assertThat(clientRepository.existsByEmail("unknown@neviswealth.com")).isFalse();
  }

  @Test
  void existsByEmail_whenEmailDiffersOnlyByCase_shouldReturnTrue() {
    clientRepository.save(
        new Client(
            UUID.randomUUID(),
            "John",
            "Doe",
            "case.check@neviswealth.com",
            "Private wealth client",
            List.of()));

    assertThat(clientRepository.existsByEmail("Case.Check@NevisWealth.com")).isTrue();
  }

  @Test
  void save_whenEmailDiffersOnlyByCase_shouldThrowDuplicateKeyException() {
    clientRepository.save(
        new Client(
            UUID.randomUUID(),
            "John",
            "Doe",
            "duplicate@neviswealth.com",
            "Private wealth client",
            List.of()));
    Client duplicate =
        new Client(
            UUID.randomUUID(),
            "Jane",
            "Smith",
            "Duplicate@NevisWealth.com",
            "Private wealth client",
            List.of());

    ThrowingCallable save = () -> clientRepository.save(duplicate);

    assertThatThrownBy(save).isInstanceOf(DuplicateKeyException.class);
  }

  private ClientRow readClient(UUID id) {
    return jdbcClient
        .sql(
            "select first_name, last_name, email, description, social_links "
                + "from client where id = :id")
        .param("id", id)
        .query(
            (rs, _) ->
                new ClientRow(
                    rs.getString("first_name"),
                    rs.getString("last_name"),
                    rs.getString("email"),
                    rs.getString("description"),
                    (String[]) rs.getArray("social_links").getArray()))
        .single();
  }

  private record ClientRow(
      String firstName, String lastName, String email, String description, String[] socialLinks) {}
}
