package com.github.sabaka.nevis_docs.summary;

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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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

@ExtendWith(MockitoExtension.class)
class SummaryWorkerTest {

  private static final UUID DOCUMENT_ID = UUID.fromString("66206f62-cff6-4e52-ad8e-978b8d8b9094");
  private static final Instant CREATED_AT = Instant.parse("2026-08-29T14:00:00Z");
  private static final String TITLE = "Electricity statement";
  private static final String CONTENT = "Utility bill for 10 Downing Street";
  private static final String FAKE_SUMMARY = "An electricity utility bill for 10 Downing Street.";
  private static final int BATCH_SIZE = 10;

  private static final Logger SUMMARY_WORKER_LOGGER =
      (Logger) LoggerFactory.getLogger(SummaryWorker.class);

  @Mock private SummaryRepository summaryRepository;
  @Mock private DocumentSummarizer documentSummarizer;

  private SummaryWorker summaryWorker;
  private ListAppender<ILoggingEvent> logAppender;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(CREATED_AT, ZoneOffset.UTC);
    summaryWorker = new SummaryWorker(summaryRepository, documentSummarizer, clock, BATCH_SIZE);
    logAppender = new ListAppender<>();
    logAppender.start();
    SUMMARY_WORKER_LOGGER.addAppender(logAppender);
  }

  @AfterEach
  void detachLogAppender() {
    SUMMARY_WORKER_LOGGER.detachAppender(logAppender);
  }

  @Test
  void processPendingSummaries_whenNoPendingCandidates_shouldNotCallSummarizer() {
    given(summaryRepository.claimPendingDocuments(BATCH_SIZE, CREATED_AT)).willReturn(List.of());

    summaryWorker.processPendingSummaries();

    verifyNoInteractions(documentSummarizer);
  }

  @Test
  void processPendingSummaries_whenSummarizerSucceeds_shouldMarkCompletedWithSummary() {
    SummaryCandidate candidate = new SummaryCandidate(DOCUMENT_ID, TITLE, CONTENT);
    given(summaryRepository.claimPendingDocuments(BATCH_SIZE, CREATED_AT))
        .willReturn(List.of(candidate));
    given(documentSummarizer.summarize(TITLE, CONTENT)).willReturn(FAKE_SUMMARY);
    given(summaryRepository.markCompleted(DOCUMENT_ID, FAKE_SUMMARY, CREATED_AT)).willReturn(1);

    summaryWorker.processPendingSummaries();

    verify(summaryRepository).markCompleted(DOCUMENT_ID, FAKE_SUMMARY, CREATED_AT);
    verify(summaryRepository, never()).markFailed(any(), any(), any());
  }

  @Test
  void processPendingSummaries_whenOneCandidateFails_shouldStillProcessRemainingCandidates() {
    UUID failingDocumentId = UUID.randomUUID();
    SummaryCandidate failingCandidate =
        new SummaryCandidate(failingDocumentId, "Other statement", "Other content");
    SummaryCandidate succeedingCandidate = new SummaryCandidate(DOCUMENT_ID, TITLE, CONTENT);
    given(summaryRepository.claimPendingDocuments(BATCH_SIZE, CREATED_AT))
        .willReturn(List.of(failingCandidate, succeedingCandidate));
    given(documentSummarizer.summarize("Other statement", "Other content"))
        .willThrow(new RuntimeException("boom"));
    given(documentSummarizer.summarize(TITLE, CONTENT)).willReturn(FAKE_SUMMARY);
    given(summaryRepository.markFailed(failingDocumentId, "RuntimeException", CREATED_AT))
        .willReturn(1);
    given(summaryRepository.markCompleted(DOCUMENT_ID, FAKE_SUMMARY, CREATED_AT)).willReturn(1);

    summaryWorker.processPendingSummaries();

    verify(summaryRepository).markFailed(failingDocumentId, "RuntimeException", CREATED_AT);
    verify(summaryRepository).markCompleted(DOCUMENT_ID, FAKE_SUMMARY, CREATED_AT);
  }

  @ParameterizedTest
  @MethodSource("lostRaceScenarios")
  void processPendingSummaries_whenTerminalUpdateAffectsNoRows_shouldLogTransitionDidNotApply(
      boolean summarizerSucceeds, DocumentSummaryStatus expectedTarget) {
    SummaryCandidate candidate = new SummaryCandidate(DOCUMENT_ID, TITLE, CONTENT);
    given(summaryRepository.claimPendingDocuments(BATCH_SIZE, CREATED_AT))
        .willReturn(List.of(candidate));
    if (summarizerSucceeds) {
      given(documentSummarizer.summarize(TITLE, CONTENT)).willReturn(FAKE_SUMMARY);
      given(summaryRepository.markCompleted(DOCUMENT_ID, FAKE_SUMMARY, CREATED_AT)).willReturn(0);
    } else {
      given(documentSummarizer.summarize(TITLE, CONTENT)).willThrow(new RuntimeException("boom"));
      given(summaryRepository.markFailed(DOCUMENT_ID, "RuntimeException", CREATED_AT))
          .willReturn(0);
    }

    summaryWorker.processPendingSummaries();

    assertThat(logAppender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .filteredOn(message -> message.contains("Summary transition did not apply"))
        .singleElement()
        .satisfies(
            message ->
                assertThat(message)
                    .contains(DOCUMENT_ID.toString())
                    .contains("target=" + expectedTarget)
                    .contains("updatedRows=0"));
  }

  private static Stream<Arguments> lostRaceScenarios() {
    return Stream.of(
        Arguments.of(true, DocumentSummaryStatus.COMPLETED),
        Arguments.of(false, DocumentSummaryStatus.FAILED));
  }

  @ParameterizedTest
  @MethodSource("summaryFailureScenarios")
  void
      processPendingSummaries_whenSummarizerFails_shouldMarkFailedWithSanitisedReasonAndNotLogContentOrThrowable(
          Answer<String> summarizerBehaviour, String expectedError) {
    SummaryCandidate candidate = new SummaryCandidate(DOCUMENT_ID, TITLE, CONTENT);
    given(summaryRepository.claimPendingDocuments(BATCH_SIZE, CREATED_AT))
        .willReturn(List.of(candidate));
    given(documentSummarizer.summarize(TITLE, CONTENT)).willAnswer(summarizerBehaviour);
    given(summaryRepository.markFailed(eq(DOCUMENT_ID), any(), eq(CREATED_AT))).willReturn(1);

    summaryWorker.processPendingSummaries();

    verify(summaryRepository)
        .markFailed(
            eq(DOCUMENT_ID),
            assertArg(error -> assertThat(error).isEqualTo(expectedError).doesNotContain(CONTENT)),
            eq(CREATED_AT));
    assertThat(logAppender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .noneMatch(message -> message.contains(CONTENT));
    assertThat(logAppender.list)
        .allSatisfy(event -> assertThat(event.getThrowableProxy()).isNull());
  }

  private static Stream<Arguments> summaryFailureScenarios() {
    return Stream.of(
        Arguments.of(
            (Answer<String>)
                invocation -> {
                  throw new SummaryGenerationException("model returned a blank summary");
                },
            "model returned a blank summary"),
        Arguments.of(
            (Answer<String>)
                invocation -> {
                  throw new SummaryGenerationException(
                      "chat model call failed", new RuntimeException("boom " + CONTENT));
                },
            "chat model call failed"),
        Arguments.of(
            (Answer<String>)
                invocation -> {
                  throw new RuntimeException("boom " + CONTENT);
                },
            "RuntimeException"));
  }
}
