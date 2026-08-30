package com.github.sabaka.nevis_docs.client;

import com.github.sabaka.nevis_docs.search.EntityType;
import com.github.sabaka.nevis_docs.search.SearchIndexer;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ClientService {

  private final ClientRepository clientRepository;
  private final SearchIndexer searchIndexer;

  ClientService(ClientRepository clientRepository, SearchIndexer searchIndexer) {
    this.clientRepository = clientRepository;
    this.searchIndexer = searchIndexer;
  }

  @Transactional
  Client create(
      String firstName,
      String lastName,
      String email,
      @Nullable String description,
      @Nullable List<String> socialLinks) {
    String normalizedEmail = email.toLowerCase(Locale.ROOT);
    if (clientRepository.existsByEmail(normalizedEmail)) {
      throw new ClientEmailAlreadyExistsException(normalizedEmail);
    }
    List<String> normalizedSocialLinks = socialLinks == null ? List.of() : List.copyOf(socialLinks);
    Client client =
        new Client(
            UUID.randomUUID(),
            firstName,
            lastName,
            normalizedEmail,
            description,
            normalizedSocialLinks);
    clientRepository.save(client);
    searchIndexer.index(EntityType.CLIENT, client.id(), () -> searchableText(client));
    return client;
  }

  private static String searchableText(Client client) {
    return Stream.concat(
            Stream.of(client.firstName(), client.lastName(), client.email(), client.description()),
            client.socialLinks().stream())
        .filter(Objects::nonNull)
        .filter(part -> !part.isBlank())
        .collect(Collectors.joining(" "));
  }
}
