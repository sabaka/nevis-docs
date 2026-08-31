package com.github.sabaka.nevis_docs.summary;

class SummaryGenerationException extends RuntimeException {

  private final String reason;

  SummaryGenerationException(String reason) {
    super(reason);
    this.reason = reason;
  }

  SummaryGenerationException(String reason, Throwable cause) {
    super(reason, cause);
    this.reason = reason;
  }

  String reason() {
    return reason;
  }
}
