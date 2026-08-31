package com.github.sabaka.nevis_docs.search;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SearchProperties.class)
class SearchConfiguration {

  @Bean
  Map<EntityType, EntityRetriever<?>> entityRetrieversByType(List<EntityRetriever<?>> retrievers) {
    Map<EntityType, EntityRetriever<?>> retrieversByType = new EnumMap<>(EntityType.class);
    for (EntityRetriever<?> retriever : retrievers) {
      EntityType entityType = retriever.entityType();
      if (retrieversByType.putIfAbsent(entityType, retriever) != null) {
        throw new IllegalStateException("Duplicate EntityRetriever for entityType=" + entityType);
      }
    }
    for (EntityType entityType : EntityType.values()) {
      if (!retrieversByType.containsKey(entityType)) {
        throw new IllegalStateException("Missing EntityRetriever for entityType=" + entityType);
      }
    }
    return Collections.unmodifiableMap(retrieversByType);
  }
}
