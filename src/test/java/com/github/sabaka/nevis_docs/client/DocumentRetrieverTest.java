package com.github.sabaka.nevis_docs.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.github.sabaka.nevis_docs.search.DocumentSearchResult;
import com.github.sabaka.nevis_docs.search.EntityType;
import com.github.sabaka.nevis_docs.search.SearchResult;
import com.github.sabaka.nevis_docs.summary.DocumentSummaryStatus;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentRetrieverTest {

  private static final UUID DOCUMENT_ID = UUID.fromString("66206f62-cff6-4e52-ad8e-978b8d8b9094");
  private static final UUID OTHER_DOCUMENT_ID =
      UUID.fromString("4d0f2f1a-8b3c-4d5e-9f60-1a2b3c4d5e6f");
  private static final UUID CLIENT_ID = UUID.fromString("31a67593-e39a-4e22-83df-f3494b55a439");
  private static final Instant CREATED_AT = Instant.parse("2026-08-29T14:00:00Z");

  @Mock private DocumentRepository documentRepository;

  private DocumentRetriever documentRetriever;

  @BeforeEach
  void setUp() {
    documentRetriever = new DocumentRetriever(documentRepository);
  }

  @Test
  void retrieve_whenEntityIdsIsEmpty_shouldReturnEmptyMapWithoutCallingRepository() {
    Map<UUID, Document> result = documentRetriever.retrieve(Set.of());

    assertThat(result).isEmpty();
    verifyNoInteractions(documentRepository);
  }

  @Test
  void
      retrieve_whenEntityIdsHasMultipleIdsAndOneIsMissing_shouldReturnFoundDocumentsKeyedByIdOmittingMissing() {
    Document document =
        new Document(
            DOCUMENT_ID,
            CLIENT_ID,
            "Electricity statement",
            "Utility bill for 10 Downing Street",
            CREATED_AT,
            null,
            DocumentSummaryStatus.PENDING);
    given(documentRepository.findAllByIds(Set.of(DOCUMENT_ID, OTHER_DOCUMENT_ID)))
        .willReturn(Map.of(DOCUMENT_ID, document));

    Map<UUID, Document> result = documentRetriever.retrieve(Set.of(DOCUMENT_ID, OTHER_DOCUMENT_ID));

    assertThat(result).containsExactly(Map.entry(DOCUMENT_ID, document));
    verify(documentRepository).findAllByIds(Set.of(DOCUMENT_ID, OTHER_DOCUMENT_ID));
  }

  @ParameterizedTest
  @MethodSource("summaryScenarios")
  void toResult_whenDocumentIsMapped_shouldMapEveryFieldPositionallyAndReportDocumentEntityType(
      String summary, DocumentSummaryStatus summaryStatus) {
    Document document =
        new Document(
            DOCUMENT_ID,
            CLIENT_ID,
            "Electricity statement",
            "Utility bill for 10 Downing Street",
            CREATED_AT,
            summary,
            summaryStatus);

    SearchResult result = documentRetriever.toResult(document, 0.25);

    assertThat(documentRetriever.entityType()).isEqualTo(EntityType.DOCUMENT);
    assertThat(result)
        .isInstanceOf(DocumentSearchResult.class)
        .isEqualTo(
            new DocumentSearchResult(
                DOCUMENT_ID,
                0.25,
                CLIENT_ID,
                "Electricity statement",
                "Utility bill for 10 Downing Street",
                CREATED_AT,
                summary,
                summaryStatus));
  }

  private static Stream<Arguments> summaryScenarios() {
    return Stream.of(
        Arguments.of((String) null, DocumentSummaryStatus.PENDING),
        Arguments.of(
            "An electricity utility bill for 10 Downing Street.", DocumentSummaryStatus.COMPLETED));
  }
}
