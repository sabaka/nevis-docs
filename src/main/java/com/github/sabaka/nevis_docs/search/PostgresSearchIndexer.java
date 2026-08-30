package com.github.sabaka.nevis_docs.search;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class PostgresSearchIndexer implements SearchIndexer {

  private final SearchEntryRepository searchEntryRepository;
  private final Clock clock;

  PostgresSearchIndexer(SearchEntryRepository searchEntryRepository, Clock clock) {
    this.searchEntryRepository = searchEntryRepository;
    this.clock = clock;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void index(EntityType entityType, UUID entityId, Supplier<String> searchableText) {
    String text = searchableText.get();
    if (text.isBlank()) {
      throw new IllegalArgumentException("searchableText must not be blank");
    }
    searchEntryRepository.upsert(
        entityType, entityId, text, initialEmbeddingStatus(entityType), Instant.now(clock));
  }

  private static EmbeddingStatus initialEmbeddingStatus(EntityType entityType) {
    return switch (entityType) {
      case CLIENT -> EmbeddingStatus.NOT_REQUIRED;
      case DOCUMENT -> EmbeddingStatus.PENDING;
    };
  }
}
