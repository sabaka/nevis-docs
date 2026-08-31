package com.github.sabaka.nevis_docs.search.embedding;

import com.github.sabaka.nevis_docs.search.EmbeddingVector;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class EmbeddingWorker {

  private static final Logger log = LoggerFactory.getLogger(EmbeddingWorker.class);

  private final EmbeddingRepository embeddingRepository;
  private final EmbeddingModel embeddingModel;
  private final Clock clock;
  private final int batchSize;

  EmbeddingWorker(
      EmbeddingRepository embeddingRepository,
      EmbeddingModel embeddingModel,
      Clock clock,
      @Value("${search.embedding.batch-size}") int batchSize) {
    this.embeddingRepository = embeddingRepository;
    this.embeddingModel = embeddingModel;
    this.clock = clock;
    this.batchSize = batchSize;
  }

  void processPendingEmbeddings() {
    for (ClaimedEntry entry :
        embeddingRepository.claimPendingDocuments(batchSize, Instant.now(clock))) {
      try {
        embed(entry);
      } catch (RuntimeException exception) {
        log.error("Embedding failed for document entityId={}", entry.entityId(), exception);
        embeddingRepository.markFailed(
            entry.entityType(),
            entry.entityId(),
            exception.getClass().getSimpleName(),
            Instant.now(clock));
      }
    }
  }

  private void embed(ClaimedEntry entry) {
    float[] embedding = embeddingModel.embed(entry.searchableText());
    Instant now = Instant.now(clock);
    if (EmbeddingVector.isValid(embedding)) {
      embeddingRepository.markReady(entry.entityType(), entry.entityId(), embedding, now);
    } else {
      log.error(
          "Invalid embedding for document entityId={} dimensions={}",
          entry.entityId(),
          embedding.length);
      embeddingRepository.markFailed(
          entry.entityType(), entry.entityId(), "invalid embedding", now);
    }
  }
}
