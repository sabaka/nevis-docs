package com.github.sabaka.nevis_docs.search.embedding;

import org.springframework.scheduling.annotation.Scheduled;

class EmbeddingScheduler {

  private final EmbeddingWorker embeddingWorker;

  EmbeddingScheduler(EmbeddingWorker embeddingWorker) {
    this.embeddingWorker = embeddingWorker;
  }

  @Scheduled(fixedDelayString = "${search.embedding.poll-delay}")
  void pollPendingEmbeddings() {
    embeddingWorker.processPendingEmbeddings();
  }
}
