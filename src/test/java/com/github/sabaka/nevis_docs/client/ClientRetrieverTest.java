package com.github.sabaka.nevis_docs.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.github.sabaka.nevis_docs.search.ClientSearchResult;
import com.github.sabaka.nevis_docs.search.EntityType;
import com.github.sabaka.nevis_docs.search.SearchResult;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClientRetrieverTest {

  private static final UUID CLIENT_ID = UUID.fromString("31a67593-e39a-4e22-83df-f3494b55a439");
  private static final UUID OTHER_CLIENT_ID =
      UUID.fromString("4d0f2f1a-8b3c-4d5e-9f60-1a2b3c4d5e6f");

  @Mock private ClientRepository clientRepository;

  private ClientRetriever clientRetriever;

  @BeforeEach
  void setUp() {
    clientRetriever = new ClientRetriever(clientRepository);
  }

  @Test
  void retrieve_whenEntityIdsIsEmpty_shouldReturnEmptyMapWithoutCallingRepository() {
    Map<UUID, Client> result = clientRetriever.retrieve(Set.of());

    assertThat(result).isEmpty();
    verifyNoInteractions(clientRepository);
  }

  @Test
  void
      retrieve_whenEntityIdsHasMultipleIdsAndOneIsMissing_shouldReturnFoundClientsKeyedByIdOmittingMissing() {
    Client client =
        new Client(
            CLIENT_ID,
            "John",
            "Doe",
            "john.doe@neviswealth.com",
            "Private wealth client",
            List.of("https://linkedin.com/in/john-doe"));
    given(clientRepository.findAllByIds(Set.of(CLIENT_ID, OTHER_CLIENT_ID)))
        .willReturn(Map.of(CLIENT_ID, client));

    Map<UUID, Client> result = clientRetriever.retrieve(Set.of(CLIENT_ID, OTHER_CLIENT_ID));

    assertThat(result).containsExactly(Map.entry(CLIENT_ID, client));
    verify(clientRepository).findAllByIds(Set.of(CLIENT_ID, OTHER_CLIENT_ID));
  }

  @Test
  void
      toResult_whenClientHasAllOptionalFields_shouldMapEveryFieldPositionallyAndReportClientEntityType() {
    Client client =
        new Client(
            CLIENT_ID,
            "John",
            "Doe",
            "john.doe@neviswealth.com",
            "Private wealth client",
            List.of("https://linkedin.com/in/john-doe"));

    SearchResult result = clientRetriever.toResult(client, 0.5);

    assertThat(clientRetriever.entityType()).isEqualTo(EntityType.CLIENT);
    assertThat(result)
        .isInstanceOf(ClientSearchResult.class)
        .isEqualTo(
            new ClientSearchResult(
                CLIENT_ID,
                0.5,
                "John",
                "Doe",
                "john.doe@neviswealth.com",
                "Private wealth client",
                List.of("https://linkedin.com/in/john-doe")));
  }

  @Test
  void toResult_whenClientHasNullDescriptionAndEmptySocialLinks_shouldMapNullAndEmptyValues() {
    Client client =
        new Client(CLIENT_ID, "John", "Doe", "john.doe@neviswealth.com", null, List.of());

    SearchResult result = clientRetriever.toResult(client, 0.5);

    assertThat(result)
        .isEqualTo(
            new ClientSearchResult(
                CLIENT_ID, 0.5, "John", "Doe", "john.doe@neviswealth.com", null, List.of()));
  }
}
