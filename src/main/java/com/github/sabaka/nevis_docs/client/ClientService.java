package com.github.sabaka.nevis_docs.client;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ClientService {

  private final ClientRepository clientRepository;

  ClientService(ClientRepository clientRepository) {
    this.clientRepository = clientRepository;
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
    return client;
  }
}
