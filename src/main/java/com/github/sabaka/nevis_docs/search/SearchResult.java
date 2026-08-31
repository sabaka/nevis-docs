package com.github.sabaka.nevis_docs.search;

import java.util.UUID;

public sealed interface SearchResult permits ClientSearchResult, DocumentSearchResult {

  UUID id();

  double score();
}
