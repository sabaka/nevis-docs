package com.github.sabaka.nevis_docs.client;

import java.time.Instant;
import java.util.UUID;

record Document(UUID id, UUID clientId, String title, String content, Instant createdAt) {}
