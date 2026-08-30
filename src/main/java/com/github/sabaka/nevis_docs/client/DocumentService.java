package com.github.sabaka.nevis_docs.client;

import com.github.sabaka.nevis_docs.search.EntityType;
import com.github.sabaka.nevis_docs.search.SearchIndexer;
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
  private final SearchIndexer searchIndexer;

  DocumentService(
      ClientRepository clientRepository,
      DocumentRepository documentRepository,
      Clock clock,
      SearchIndexer searchIndexer) {
    this.clientRepository = clientRepository;
    this.documentRepository = documentRepository;
    this.clock = clock;
    this.searchIndexer = searchIndexer;
  }

  @Transactional
  Document create(UUID clientId, String title, String content) {
    if (!clientRepository.existsById(clientId)) {
      throw new ClientNotFoundException(clientId);
    }
    Document document =
        new Document(UUID.randomUUID(), clientId, title, content, Instant.now(clock));
    documentRepository.save(document);
    searchIndexer.index(EntityType.DOCUMENT, document.id(), () -> searchableText(document));
    return document;
  }

  private static String searchableText(Document document) {
    return document.title() + "\n" + document.content();
  }
}
