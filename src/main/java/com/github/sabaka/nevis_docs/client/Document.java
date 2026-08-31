package com.github.sabaka.nevis_docs.client;

import com.github.sabaka.nevis_docs.summary.DocumentSummaryStatus;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

record Document(
    UUID id,
    UUID clientId,
    String title,
    String content,
    Instant createdAt,
    @Nullable String summary,
    DocumentSummaryStatus summaryStatus) {}
