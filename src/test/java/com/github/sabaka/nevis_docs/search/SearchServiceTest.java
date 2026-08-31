package com.github.sabaka.nevis_docs.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.springframework.ai.embedding.EmbeddingModel;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

  private static final String QUERY_PREFIX =
      "Represent this sentence for searching relevant passages: ";
  private static final int EMBEDDING_DIMENSIONS = 1024;
  private static final float EMBEDDING_VALUE = 0.125f;
  private static final float[] QUERY_EMBEDDING = validEmbedding();
  private static final SearchProperties SEARCH_PROPERTIES = new SearchProperties(50, 20, 60, 0.5);

  @Mock private SearchRepository searchRepository;
  @Mock private EmbeddingModel embeddingModel;
  @Mock private EntityRetriever<Object> clientRetriever;
  @Mock private EntityRetriever<Object> documentRetriever;

  private SearchService searchService;

  @BeforeEach
  void setUp() {
    Map<EntityType, EntityRetriever<?>> retrievers = new EnumMap<>(EntityType.class);
    retrievers.put(EntityType.CLIENT, clientRetriever);
    retrievers.put(EntityType.DOCUMENT, documentRetriever);
    searchService =
        new SearchService(searchRepository, embeddingModel, retrievers, SEARCH_PROPERTIES);
  }

  @Test
  void
      search_whenQueryMatchesBothTypesWithMultipleHitsPerType_shouldStripEmbedOnceAndHydrateGroupedByType() {
    UUID clientId1 = UUID.randomUUID();
    UUID clientId2 = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    SearchHit clientHit1 = new SearchHit(EntityType.CLIENT, clientId1, 0.9);
    SearchHit clientHit2 = new SearchHit(EntityType.CLIENT, clientId2, 0.7);
    SearchHit documentHit = new SearchHit(EntityType.DOCUMENT, documentId, 0.5);
    given(embeddingModel.embed(anyString())).willReturn(QUERY_EMBEDDING);
    given(searchRepository.search("NevisWealth", QUERY_EMBEDDING, 50, 20, 60, 0.5))
        .willReturn(List.of(clientHit1, clientHit2, documentHit));
    given(clientRetriever.retrieve(Set.of(clientId1, clientId2)))
        .willReturn(Map.of(clientId1, new Object(), clientId2, new Object()));
    given(documentRetriever.retrieve(Set.of(documentId)))
        .willReturn(Map.of(documentId, new Object()));
    ClientSearchResult clientResult1 = clientResult(clientId1, 0.9);
    ClientSearchResult clientResult2 = clientResult(clientId2, 0.7);
    DocumentSearchResult documentResult = documentResult(documentId, 0.5);
    given(clientRetriever.toResult(any(), eq(0.9))).willReturn(clientResult1);
    given(clientRetriever.toResult(any(), eq(0.7))).willReturn(clientResult2);
    given(documentRetriever.toResult(any(), eq(0.5))).willReturn(documentResult);

    List<SearchResult> results = searchService.search("  NevisWealth  ");

    assertThat(results).containsExactly(clientResult1, clientResult2, documentResult);
    verify(embeddingModel).embed(QUERY_PREFIX + "NevisWealth");
    verify(searchRepository).search("NevisWealth", QUERY_EMBEDDING, 50, 20, 60, 0.5);
    verify(clientRetriever).retrieve(Set.of(clientId1, clientId2));
    verify(documentRetriever).retrieve(Set.of(documentId));
  }

  @Test
  void search_whenRetrievedEntitiesMapIsInReversedOrder_shouldPreserveOriginalHitOrder() {
    UUID id1 = UUID.randomUUID();
    UUID id2 = UUID.randomUUID();
    UUID id3 = UUID.randomUUID();
    SearchHit hit1 = new SearchHit(EntityType.DOCUMENT, id1, 0.9);
    SearchHit hit2 = new SearchHit(EntityType.DOCUMENT, id2, 0.6);
    SearchHit hit3 = new SearchHit(EntityType.DOCUMENT, id3, 0.3);
    given(embeddingModel.embed(anyString())).willReturn(QUERY_EMBEDDING);
    given(searchRepository.search("NevisWealth", QUERY_EMBEDDING, 50, 20, 60, 0.5))
        .willReturn(List.of(hit1, hit2, hit3));
    Map<UUID, Object> reversedEntities = new LinkedHashMap<>();
    reversedEntities.put(id3, new Object());
    reversedEntities.put(id2, new Object());
    reversedEntities.put(id1, new Object());
    given(documentRetriever.retrieve(Set.of(id1, id2, id3))).willReturn(reversedEntities);
    DocumentSearchResult result1 = documentResult(id1, 0.9);
    DocumentSearchResult result2 = documentResult(id2, 0.6);
    DocumentSearchResult result3 = documentResult(id3, 0.3);
    given(documentRetriever.toResult(any(), eq(0.9))).willReturn(result1);
    given(documentRetriever.toResult(any(), eq(0.6))).willReturn(result2);
    given(documentRetriever.toResult(any(), eq(0.3))).willReturn(result3);

    List<SearchResult> results = searchService.search("NevisWealth");

    assertThat(results).extracting(SearchResult::id).containsExactly(id1, id2, id3);
  }

  @Test
  void search_whenHitsOnlyIncludeOneType_shouldNotInteractWithOtherTypeRetriever() {
    UUID clientId = UUID.randomUUID();
    SearchHit clientHit = new SearchHit(EntityType.CLIENT, clientId, 0.5);
    given(embeddingModel.embed(anyString())).willReturn(QUERY_EMBEDDING);
    given(searchRepository.search("NevisWealth", QUERY_EMBEDDING, 50, 20, 60, 0.5))
        .willReturn(List.of(clientHit));
    given(clientRetriever.retrieve(Set.of(clientId))).willReturn(Map.of(clientId, new Object()));
    ClientSearchResult clientResult = clientResult(clientId, 0.5);
    given(clientRetriever.toResult(any(), eq(0.5))).willReturn(clientResult);

    List<SearchResult> results = searchService.search("NevisWealth");

    assertThat(results).containsExactly(clientResult);
    verifyNoInteractions(documentRetriever);
  }

  @Test
  void search_whenHydrationMissesAnEntity_shouldDropStaleHitAndKeepRemainingOrder() {
    UUID presentId1 = UUID.randomUUID();
    UUID staleId = UUID.randomUUID();
    UUID presentId2 = UUID.randomUUID();
    SearchHit hit1 = new SearchHit(EntityType.DOCUMENT, presentId1, 0.9);
    SearchHit staleHit = new SearchHit(EntityType.DOCUMENT, staleId, 0.6);
    SearchHit hit2 = new SearchHit(EntityType.DOCUMENT, presentId2, 0.3);
    given(embeddingModel.embed(anyString())).willReturn(QUERY_EMBEDDING);
    given(searchRepository.search("NevisWealth", QUERY_EMBEDDING, 50, 20, 60, 0.5))
        .willReturn(List.of(hit1, staleHit, hit2));
    given(documentRetriever.retrieve(Set.of(presentId1, staleId, presentId2)))
        .willReturn(Map.of(presentId1, new Object(), presentId2, new Object()));
    DocumentSearchResult result1 = documentResult(presentId1, 0.9);
    DocumentSearchResult result2 = documentResult(presentId2, 0.3);
    given(documentRetriever.toResult(any(), eq(0.9))).willReturn(result1);
    given(documentRetriever.toResult(any(), eq(0.3))).willReturn(result2);

    List<SearchResult> results = searchService.search("NevisWealth");

    assertThat(results).containsExactly(result1, result2);
  }

  @Test
  void search_whenRepositoryReturnsNoHits_shouldReturnEmptyListWithoutInvokingRetrievers() {
    given(embeddingModel.embed(anyString())).willReturn(QUERY_EMBEDDING);
    given(searchRepository.search("NevisWealth", QUERY_EMBEDDING, 50, 20, 60, 0.5))
        .willReturn(List.of());

    List<SearchResult> results = searchService.search("NevisWealth");

    assertThat(results).isEmpty();
    verifyNoInteractions(clientRetriever, documentRetriever);
  }

  @ParameterizedTest
  @MethodSource("invalidEmbeddingResponses")
  void
      search_whenEmbeddingModelFailsOrReturnsInvalidVector_shouldThrowSearchUnavailableExceptionWithoutQueryingRepository(
          Answer<float[]> modelBehaviour) {
    given(embeddingModel.embed(anyString())).willAnswer(modelBehaviour);

    ThrowingCallable searchCall = () -> searchService.search("NevisWealth");

    assertThatThrownBy(searchCall).isInstanceOf(SearchUnavailableException.class);
    verifyNoInteractions(searchRepository);
  }

  private static Stream<Answer<float[]>> invalidEmbeddingResponses() {
    return Stream.of(
        _ -> {
          throw new RuntimeException("embedding model unavailable");
        },
        _ -> new float[512],
        _ -> nonFiniteEmbedding(Float.NaN),
        _ -> nonFiniteEmbedding(Float.POSITIVE_INFINITY));
  }

  private static float[] nonFiniteEmbedding(float value) {
    float[] embedding = new float[EMBEDDING_DIMENSIONS];
    Arrays.fill(embedding, value);
    return embedding;
  }

  private static float[] validEmbedding() {
    float[] embedding = new float[EMBEDDING_DIMENSIONS];
    Arrays.fill(embedding, EMBEDDING_VALUE);
    return embedding;
  }

  private static ClientSearchResult clientResult(UUID id, double score) {
    return new ClientSearchResult(
        id,
        score,
        "John",
        "Doe",
        "john.doe@neviswealth.com",
        "Private wealth client",
        List.of("https://linkedin.com/in/john-doe"));
  }

  private static DocumentSearchResult documentResult(UUID id, double score) {
    return new DocumentSearchResult(
        id,
        score,
        UUID.randomUUID(),
        "Electricity statement",
        "Utility bill for 10 Downing Street",
        Instant.parse("2026-08-29T14:00:00Z"));
  }
}
