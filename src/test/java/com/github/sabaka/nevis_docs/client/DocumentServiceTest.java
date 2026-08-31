package com.github.sabaka.nevis_docs.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.assertArg;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.github.sabaka.nevis_docs.search.EntityType;
import com.github.sabaka.nevis_docs.search.SearchIndexer;
import com.github.sabaka.nevis_docs.summary.DocumentSummaryStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

  private static final UUID CLIENT_ID = UUID.fromString("31a67593-e39a-4e22-83df-f3494b55a439");
  private static final UUID UNKNOWN_CLIENT_ID =
      UUID.fromString("4d0f2f1a-8b3c-4d5e-9f60-1a2b3c4d5e6f");
  private static final Instant CREATED_AT = Instant.parse("2026-08-29T14:00:00Z");

  @Mock private ClientRepository clientRepository;
  @Mock private DocumentRepository documentRepository;
  @Mock private SearchIndexer searchIndexer;

  private DocumentService documentService;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(CREATED_AT, ZoneOffset.UTC);
    documentService =
        new DocumentService(clientRepository, documentRepository, clock, searchIndexer);
  }

  @Test
  void create_whenClientExists_shouldPersistAndIndexDocumentWithGeneratedIdAndTimestamp() {
    given(clientRepository.existsById(CLIENT_ID)).willReturn(true);

    Document result =
        documentService.create(
            CLIENT_ID, "Electricity statement", "Utility bill for 10 Downing Street");

    verify(documentRepository)
        .save(
            assertArg(
                saved -> {
                  assertThat(saved.id()).isNotNull();
                  assertThat(saved)
                      .extracting(
                          Document::clientId,
                          Document::title,
                          Document::content,
                          Document::createdAt,
                          Document::summary,
                          Document::summaryStatus)
                      .containsExactly(
                          CLIENT_ID,
                          "Electricity statement",
                          "Utility bill for 10 Downing Street",
                          CREATED_AT,
                          null,
                          DocumentSummaryStatus.PENDING);
                  assertThat(saved).isSameAs(result);
                }));
    verify(searchIndexer)
        .index(
            eq(EntityType.DOCUMENT),
            eq(result.id()),
            assertArg(
                searchableText ->
                    assertThat(searchableText.get())
                        .isEqualTo("Electricity statement\nUtility bill for 10 Downing Street")));
  }

  @Test
  void create_whenClientDoesNotExist_shouldThrowClientNotFoundExceptionAndNotSave() {
    given(clientRepository.existsById(UNKNOWN_CLIENT_ID)).willReturn(false);

    ThrowableAssert.ThrowingCallable createDocument =
        () ->
            documentService.create(
                UNKNOWN_CLIENT_ID, "Electricity statement", "Utility bill for 10 Downing Street");

    assertThatThrownBy(createDocument)
        .asInstanceOf(InstanceOfAssertFactories.type(ClientNotFoundException.class))
        .extracting(ClientNotFoundException::clientId)
        .isEqualTo(UNKNOWN_CLIENT_ID);
    verifyNoInteractions(documentRepository);
    verifyNoInteractions(searchIndexer);
  }

  @Test
  void create_whenIndexingFails_shouldPropagateException() {
    given(clientRepository.existsById(CLIENT_ID)).willReturn(true);
    willThrow(new IllegalStateException("indexing failed"))
        .given(searchIndexer)
        .index(any(), any(), any());

    ThrowableAssert.ThrowingCallable createDocument =
        () ->
            documentService.create(
                CLIENT_ID, "Electricity statement", "Utility bill for 10 Downing Street");

    assertThatThrownBy(createDocument).isInstanceOf(IllegalStateException.class);
  }
}
