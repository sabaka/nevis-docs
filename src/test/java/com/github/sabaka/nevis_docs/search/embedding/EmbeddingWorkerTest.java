package com.github.sabaka.nevis_docs.search.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.assertArg;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.sabaka.nevis_docs.search.EntityType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;

@ExtendWith(MockitoExtension.class)
class EmbeddingWorkerTest {

  private static final Instant CREATED_AT = Instant.parse("2026-08-29T14:00:00Z");
  private static final String SEARCHABLE_TEXT =
      "Electricity statement\nUtility bill for 10 Downing Street";
  private static final int BATCH_SIZE = 10;
  private static final int EMBEDDING_DIMENSIONS = 1024;
  private static final float EMBEDDING_VALUE = 0.125f;

  private static final Logger EMBEDDING_WORKER_LOGGER =
      (Logger) LoggerFactory.getLogger(EmbeddingWorker.class);

  @Mock private EmbeddingRepository embeddingRepository;
  @Mock private EmbeddingModel embeddingModel;

  private EmbeddingWorker embeddingWorker;
  private ListAppender<ILoggingEvent> logAppender;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(CREATED_AT, ZoneOffset.UTC);
    embeddingWorker = new EmbeddingWorker(embeddingRepository, embeddingModel, clock, BATCH_SIZE);
  }

  @BeforeEach
  void attachLogAppender() {
    logAppender = new ListAppender<>();
    logAppender.start();
    EMBEDDING_WORKER_LOGGER.addAppender(logAppender);
  }

  @AfterEach
  void detachLogAppender() {
    EMBEDDING_WORKER_LOGGER.detachAppender(logAppender);
  }

  private static float[] fakeEmbedding() {
    float[] embedding = new float[EMBEDDING_DIMENSIONS];
    Arrays.fill(embedding, EMBEDDING_VALUE);
    return embedding;
  }

  @Test
  void processPendingEmbeddings_whenNoPendingEntries_shouldNotCallModel() {
    given(embeddingRepository.claimPendingDocuments(BATCH_SIZE, CREATED_AT)).willReturn(List.of());

    embeddingWorker.processPendingEmbeddings();

    verifyNoInteractions(embeddingModel);
  }

  @Test
  void processPendingEmbeddings_whenEmbeddingHasExpectedDimensions_shouldMarkReady() {
    UUID entityId = UUID.randomUUID();
    ClaimedEntry entry = new ClaimedEntry(EntityType.DOCUMENT, entityId, SEARCHABLE_TEXT);
    given(embeddingRepository.claimPendingDocuments(BATCH_SIZE, CREATED_AT))
        .willReturn(List.of(entry));
    float[] embedding = fakeEmbedding();
    given(embeddingModel.embed(SEARCHABLE_TEXT)).willReturn(embedding);

    embeddingWorker.processPendingEmbeddings();

    verify(embeddingRepository).claimPendingDocuments(BATCH_SIZE, CREATED_AT);
    verify(embeddingRepository).markReady(EntityType.DOCUMENT, entityId, embedding, CREATED_AT);
  }

  @ParameterizedTest
  @MethodSource("embeddingFailureScenarios")
  void
      processPendingEmbeddings_whenEmbeddingFails_shouldMarkFailedWithSanitisedReasonAndNotLogSearchableText(
          Answer<float[]> modelBehaviour, String expectedError) {
    UUID entityId = UUID.randomUUID();
    ClaimedEntry entry = new ClaimedEntry(EntityType.DOCUMENT, entityId, SEARCHABLE_TEXT);
    given(embeddingRepository.claimPendingDocuments(BATCH_SIZE, CREATED_AT))
        .willReturn(List.of(entry));
    given(embeddingModel.embed(SEARCHABLE_TEXT)).willAnswer(modelBehaviour);

    embeddingWorker.processPendingEmbeddings();

    verify(embeddingRepository)
        .markFailed(
            eq(EntityType.DOCUMENT),
            eq(entityId),
            assertArg(
                error ->
                    assertThat(error).isEqualTo(expectedError).doesNotContain(SEARCHABLE_TEXT)),
            eq(CREATED_AT));
    verify(embeddingRepository, never()).markReady(any(), any(), any(), any());
    assertThat(logAppender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .noneMatch(message -> message.contains(SEARCHABLE_TEXT));
  }

  private static Stream<Arguments> embeddingFailureScenarios() {
    return Stream.of(
        Arguments.of(
            (Answer<float[]>)
                invocation -> {
                  throw new RuntimeException("boom " + SEARCHABLE_TEXT);
                },
            "RuntimeException"),
        Arguments.of(
            (Answer<float[]>) invocation -> new float[512], "unexpected embedding dimensions: 512"),
        Arguments.of((Answer<float[]>) invocation -> null, "NullPointerException"));
  }

  @Test
  void processPendingEmbeddings_whenOneEntryFails_shouldStillProcessRemainingEntries() {
    UUID failingEntityId = UUID.randomUUID();
    UUID succeedingEntityId = UUID.randomUUID();
    ClaimedEntry failingEntry =
        new ClaimedEntry(EntityType.DOCUMENT, failingEntityId, "first text");
    ClaimedEntry succeedingEntry =
        new ClaimedEntry(EntityType.DOCUMENT, succeedingEntityId, SEARCHABLE_TEXT);
    given(embeddingRepository.claimPendingDocuments(BATCH_SIZE, CREATED_AT))
        .willReturn(List.of(failingEntry, succeedingEntry));
    given(embeddingModel.embed("first text")).willThrow(new RuntimeException("boom"));
    float[] embedding = fakeEmbedding();
    given(embeddingModel.embed(SEARCHABLE_TEXT)).willReturn(embedding);

    embeddingWorker.processPendingEmbeddings();

    verify(embeddingRepository)
        .markFailed(EntityType.DOCUMENT, failingEntityId, "RuntimeException", CREATED_AT);
    verify(embeddingRepository)
        .markReady(EntityType.DOCUMENT, succeedingEntityId, embedding, CREATED_AT);
  }
}
