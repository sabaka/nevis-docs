package com.github.sabaka.nevis_docs.search;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("search")
@Validated
record SearchProperties(
    @Min(1) int candidateLimit,
    @Min(1) int resultLimit,
    @Min(1) int rrfK,
    @DecimalMin("0.0") @DecimalMax("2.0") double maxSemanticDistance) {

  @AssertTrue(message = "search.candidate-limit must be >= search.result-limit")
  boolean isCandidateLimitNotBelowResultLimit() {
    return candidateLimit >= resultLimit;
  }
}
