package com.github.sabaka.nevis_docs.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PostgresSearchIndexerTest {

  private static final Instant NOW = Instant.parse("2026-08-29T14:00:00Z");
  private static final String SEARCHABLE_TEXT =
      "Electricity statement\nUtility bill for 10 Downing Street";

  @Mock private SearchEntryRepository searchEntryRepository;

  private PostgresSearchIndexer searchIndexer;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    searchIndexer = new PostgresSearchIndexer(searchEntryRepository, clock);
  }

  @ParameterizedTest
  @MethodSource("entityTypeAndInitialEmbeddingStatus")
  void index_whenEntityTypeVaries_shouldInvokeSupplierOnceAndUpsertWithInitialStatus(
      EntityType entityType, EmbeddingStatus expectedStatus) {
    UUID entityId = UUID.randomUUID();
    AtomicInteger invocationCount = new AtomicInteger();
    Supplier<String> searchableText =
        () -> {
          invocationCount.incrementAndGet();
          return SEARCHABLE_TEXT;
        };

    searchIndexer.index(entityType, entityId, searchableText);

    assertThat(invocationCount).hasValue(1);
    verify(searchEntryRepository)
        .upsert(entityType, entityId, SEARCHABLE_TEXT, expectedStatus, NOW);
  }

  private static Stream<Arguments> entityTypeAndInitialEmbeddingStatus() {
    return Stream.of(
        Arguments.of(EntityType.CLIENT, EmbeddingStatus.NOT_REQUIRED),
        Arguments.of(EntityType.DOCUMENT, EmbeddingStatus.PENDING));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  void index_whenSupplierReturnsBlank_shouldThrowIllegalArgumentException(String blankText) {
    ThrowableAssert.ThrowingCallable indexCall =
        () -> searchIndexer.index(EntityType.DOCUMENT, UUID.randomUUID(), () -> blankText);

    assertThatThrownBy(indexCall).isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(searchEntryRepository);
  }
}
