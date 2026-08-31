package com.github.sabaka.nevis_docs.search;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record ClientSearchResult(
    UUID id,
    double score,
    String firstName,
    String lastName,
    String email,
    @Nullable String description,
    List<String> socialLinks)
    implements SearchResult {}
