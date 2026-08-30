package com.github.sabaka.nevis_docs.search;

import java.util.UUID;
import java.util.function.Supplier;

public interface SearchIndexer {

  void index(EntityType entityType, UUID entityId, Supplier<String> searchableText);
}
