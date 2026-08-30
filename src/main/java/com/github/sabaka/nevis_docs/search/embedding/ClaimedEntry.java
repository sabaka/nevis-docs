package com.github.sabaka.nevis_docs.search.embedding;

import com.github.sabaka.nevis_docs.search.EntityType;
import java.util.UUID;

record ClaimedEntry(EntityType entityType, UUID entityId, String searchableText) {}
