package com.github.sabaka.nevis_docs.search;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

@Service
class SearchService {

  private static final Logger log = LoggerFactory.getLogger(SearchService.class);
  private static final String QUERY_PREFIX =
      "Represent this sentence for searching relevant passages: ";

  private final SearchRepository searchRepository;
  private final EmbeddingModel embeddingModel;
  private final Map<EntityType, EntityRetriever<?>> entityRetrieversByType;
  private final SearchProperties searchProperties;

  SearchService(
      SearchRepository searchRepository,
      EmbeddingModel embeddingModel,
      Map<EntityType, EntityRetriever<?>> entityRetrieversByType,
      SearchProperties searchProperties) {
    this.searchRepository = searchRepository;
    this.embeddingModel = embeddingModel;
    this.entityRetrieversByType = entityRetrieversByType;
    this.searchProperties = searchProperties;
  }

  List<SearchResult> search(String query) {
    String trimmedQuery = query.strip();
    float[] queryEmbedding = embed(QUERY_PREFIX + trimmedQuery);
    List<SearchHit> hits =
        searchRepository.search(
            trimmedQuery,
            queryEmbedding,
            searchProperties.candidateLimit(),
            searchProperties.resultLimit(),
            searchProperties.rrfK(),
            searchProperties.maxSemanticDistance());
    Map<UUID, SearchResult> resultsById =
        hits.stream().collect(Collectors.groupingBy(SearchHit::entityType)).entrySet().stream()
            .map(byType -> hydrate(retrieverFor(byType.getKey()), byType.getValue()))
            .flatMap(hydrated -> hydrated.entrySet().stream())
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    return hits.stream()
        .map(hit -> resultsById.get(hit.entityId()))
        .filter(Objects::nonNull)
        .toList();
  }

  private EntityRetriever<?> retrieverFor(EntityType entityType) {
    return Objects.requireNonNull(entityRetrieversByType.get(entityType));
  }

  private float[] embed(String text) {
    float[] embedding;
    try {
      embedding = embeddingModel.embed(text);
    } catch (Exception exception) {
      log.error("Query embedding request failed", exception);
      throw new SearchUnavailableException("query embedding unavailable", exception);
    }
    if (!EmbeddingVector.isValid(embedding)) {
      log.error("Query embedding model returned an invalid embedding");
      throw new SearchUnavailableException("query embedding unavailable");
    }
    return embedding;
  }

  private static <T> Map<UUID, SearchResult> hydrate(
      EntityRetriever<T> retriever, List<SearchHit> hits) {
    Set<UUID> entityIds = hits.stream().map(SearchHit::entityId).collect(Collectors.toSet());
    Map<UUID, T> entities = retriever.retrieve(entityIds);
    Map<UUID, SearchResult> results = new HashMap<>();
    for (SearchHit hit : hits) {
      T entity = entities.get(hit.entityId());
      if (entity == null) {
        log.warn(
            "Search entry has no source entity entityType={} entityId={}",
            hit.entityType(),
            hit.entityId());
      } else {
        results.put(hit.entityId(), retriever.toResult(entity, hit.score()));
      }
    }
    return results;
  }
}
