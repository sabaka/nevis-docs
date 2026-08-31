package com.github.sabaka.nevis_docs.summary;

import org.springframework.scheduling.annotation.Scheduled;

class SummaryScheduler {

  private final SummaryWorker summaryWorker;

  SummaryScheduler(SummaryWorker summaryWorker) {
    this.summaryWorker = summaryWorker;
  }

  @Scheduled(fixedDelayString = "${summary.poll-delay}")
  void pollPendingSummaries() {
    summaryWorker.processPendingSummaries();
  }
}
