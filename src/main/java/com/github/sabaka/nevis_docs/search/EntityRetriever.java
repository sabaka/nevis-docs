package com.github.sabaka.nevis_docs.search;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface EntityRetriever<T> {

  EntityType entityType();

  Map<UUID, T> retrieve(Set<UUID> entityIds);

  SearchResult toResult(T entity, double score);
}
