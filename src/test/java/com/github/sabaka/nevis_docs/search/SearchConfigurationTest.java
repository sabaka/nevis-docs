package com.github.sabaka.nevis_docs.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SearchConfigurationTest {

  private final SearchConfiguration searchConfiguration = new SearchConfiguration();

  @Test
  void
      entityRetrieversByType_whenListHasOneRetrieverPerType_shouldReturnUnmodifiableMapWithBothConstantsWired() {
    EntityRetriever<?> clientRetriever = retrieverFor(EntityType.CLIENT);
    EntityRetriever<?> documentRetriever = retrieverFor(EntityType.DOCUMENT);

    Map<EntityType, EntityRetriever<?>> result =
        searchConfiguration.entityRetrieversByType(List.of(clientRetriever, documentRetriever));

    assertThat(result)
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(EntityType.CLIENT, clientRetriever, EntityType.DOCUMENT, documentRetriever))
        .isUnmodifiable();
  }

  @ParameterizedTest
  @MethodSource("invalidRetrieverLists")
  void entityRetrieversByType_whenListIsIncomplete_shouldThrowIllegalStateException(
      List<EntityRetriever<?>> retrievers) {
    ThrowingCallable buildRegistry = () -> searchConfiguration.entityRetrieversByType(retrievers);

    assertThatThrownBy(buildRegistry).isInstanceOf(IllegalStateException.class);
  }

  private static Stream<Arguments> invalidRetrieverLists() {
    EntityRetriever<?> client = retrieverFor(EntityType.CLIENT);
    EntityRetriever<?> document = retrieverFor(EntityType.DOCUMENT);
    EntityRetriever<?> duplicateClient = retrieverFor(EntityType.CLIENT);
    EntityRetriever<?> duplicateDocument = retrieverFor(EntityType.DOCUMENT);
    return Stream.of(
        Arguments.of(List.of(client, document, duplicateClient)),
        Arguments.of(List.of(client, document, duplicateDocument)),
        Arguments.of(List.of(document)),
        Arguments.of(List.of(client)));
  }

  @SuppressWarnings("unchecked")
  private static EntityRetriever<?> retrieverFor(EntityType entityType) {
    EntityRetriever<Object> retriever = mock(EntityRetriever.class);
    given(retriever.entityType()).willReturn(entityType);
    return retriever;
  }
}
