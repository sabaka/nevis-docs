package com.github.sabaka.nevis_docs.client;

import com.github.sabaka.nevis_docs.search.DocumentSearchResult;
import com.github.sabaka.nevis_docs.search.EntityRetriever;
import com.github.sabaka.nevis_docs.search.EntityType;
import com.github.sabaka.nevis_docs.search.SearchResult;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class DocumentRetriever implements EntityRetriever<Document> {

  private final DocumentRepository documentRepository;

  DocumentRetriever(DocumentRepository documentRepository) {
    this.documentRepository = documentRepository;
  }

  @Override
  public EntityType entityType() {
    return EntityType.DOCUMENT;
  }

  @Override
  public Map<UUID, Document> retrieve(Set<UUID> entityIds) {
    if (entityIds.isEmpty()) {
      return Map.of();
    }
    return documentRepository.findAllByIds(entityIds);
  }

  @Override
  public SearchResult toResult(Document document, double score) {
    return new DocumentSearchResult(
        document.id(),
        score,
        document.clientId(),
        document.title(),
        document.content(),
        document.createdAt());
  }
}
