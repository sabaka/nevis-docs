package com.github.sabaka.nevis_docs.search;

import java.util.UUID;

record SearchHit(EntityType entityType, UUID entityId, double score) {}
