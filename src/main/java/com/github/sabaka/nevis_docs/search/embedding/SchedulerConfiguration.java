package com.github.sabaka.nevis_docs.search.embedding;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "search.embedding.enabled", havingValue = "true")
class SchedulerConfiguration {

  @Bean
  EmbeddingScheduler scheduler(EmbeddingWorker embeddingWorker) {
    return new EmbeddingScheduler(embeddingWorker);
  }
}
