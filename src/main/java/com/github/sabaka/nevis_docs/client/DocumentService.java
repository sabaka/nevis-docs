package com.github.sabaka.nevis_docs.client;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DocumentService {

  private final ClientRepository clientRepository;
  private final DocumentRepository documentRepository;
  private final Clock clock;

  DocumentService(
      ClientRepository clientRepository, DocumentRepository documentRepository, Clock clock) {
    this.clientRepository = clientRepository;
    this.documentRepository = documentRepository;
    this.clock = clock;
  }

  @Transactional
  Document create(UUID clientId, String title, String content) {
    if (!clientRepository.existsById(clientId)) {
      throw new ClientNotFoundException(clientId);
    }
    Document document =
        new Document(UUID.randomUUID(), clientId, title, content, Instant.now(clock));
    documentRepository.save(document);
    return document;
  }
}
