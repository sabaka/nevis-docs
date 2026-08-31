package com.github.sabaka.nevis_docs.search;

import com.github.sabaka.nevis_docs.summary.DocumentSummaryStatus;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record DocumentSearchResult(
    UUID id,
    double score,
    UUID clientId,
    String title,
    String content,
    Instant createdAt,
    @Nullable String summary,
    DocumentSummaryStatus summaryStatus)
    implements SearchResult {}
