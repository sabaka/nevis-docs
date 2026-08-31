package com.github.sabaka.nevis_docs.search;

import java.time.Instant;
import java.util.UUID;

public record DocumentSearchResult(
    UUID id, double score, UUID clientId, String title, String content, Instant createdAt)
    implements SearchResult {}
