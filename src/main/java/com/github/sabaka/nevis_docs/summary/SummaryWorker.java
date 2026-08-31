package com.github.sabaka.nevis_docs.summary;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class SummaryWorker {

  private static final Logger log = LoggerFactory.getLogger(SummaryWorker.class);

  private final SummaryRepository summaryRepository;
  private final DocumentSummarizer documentSummarizer;
  private final Clock clock;
  private final int batchSize;

  SummaryWorker(
      SummaryRepository summaryRepository,
      DocumentSummarizer documentSummarizer,
      Clock clock,
      @Value("${summary.batch-size}") int batchSize) {
    this.summaryRepository = summaryRepository;
    this.documentSummarizer = documentSummarizer;
    this.clock = clock;
    this.batchSize = batchSize;
  }

  void processPendingSummaries() {
    for (SummaryCandidate candidate :
        summaryRepository.claimPendingDocuments(batchSize, Instant.now(clock))) {
      try {
        String summary = documentSummarizer.summarize(candidate.title(), candidate.content());
        int updatedRows =
            summaryRepository.markCompleted(candidate.documentId(), summary, Instant.now(clock));
        verifyTransition(candidate.documentId(), updatedRows, DocumentSummaryStatus.COMPLETED);
      } catch (Exception exception) {
        String error =
            exception instanceof SummaryGenerationException summaryException
                ? summaryException.reason()
                : exception.getClass().getSimpleName();
        log.error(
            "Summary generation failed documentId={} reason={}", candidate.documentId(), error);
        int updatedRows =
            summaryRepository.markFailed(candidate.documentId(), error, Instant.now(clock));
        verifyTransition(candidate.documentId(), updatedRows, DocumentSummaryStatus.FAILED);
      }
    }
  }

  private static void verifyTransition(
      UUID documentId, int updatedRows, DocumentSummaryStatus target) {
    if (updatedRows != 1) {
      log.error(
          "Summary transition did not apply documentId={} target={} updatedRows={}",
          documentId,
          target,
          updatedRows);
    }
  }
}
