package com.github.sabaka.nevis_docs.client;

import com.github.sabaka.nevis_docs.search.ClientSearchResult;
import com.github.sabaka.nevis_docs.search.EntityRetriever;
import com.github.sabaka.nevis_docs.search.EntityType;
import com.github.sabaka.nevis_docs.search.SearchResult;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class ClientRetriever implements EntityRetriever<Client> {

  private final ClientRepository clientRepository;

  ClientRetriever(ClientRepository clientRepository) {
    this.clientRepository = clientRepository;
  }

  @Override
  public EntityType entityType() {
    return EntityType.CLIENT;
  }

  @Override
  public Map<UUID, Client> retrieve(Set<UUID> entityIds) {
    if (entityIds.isEmpty()) {
      return Map.of();
    }
    return clientRepository.findAllByIds(entityIds);
  }

  @Override
  public SearchResult toResult(Client client, double score) {
    return new ClientSearchResult(
        client.id(),
        score,
        client.firstName(),
        client.lastName(),
        client.email(),
        client.description(),
        client.socialLinks());
  }
}
