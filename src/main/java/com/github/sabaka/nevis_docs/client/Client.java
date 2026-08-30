package com.github.sabaka.nevis_docs.client;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

record Client(
    UUID id,
    String firstName,
    String lastName,
    String email,
    @Nullable String description,
    List<String> socialLinks) {}
