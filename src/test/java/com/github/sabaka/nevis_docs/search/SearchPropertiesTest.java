package com.github.sabaka.nevis_docs.search;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SearchPropertiesTest {

  private static ValidatorFactory validatorFactory;
  private static Validator validator;

  @BeforeAll
  static void setUpValidator() {
    validatorFactory = Validation.buildDefaultValidatorFactory();
    validator = validatorFactory.getValidator();
  }

  @AfterAll
  static void closeValidatorFactory() {
    validatorFactory.close();
  }

  @ParameterizedTest
  @MethodSource("candidateLimitScenarios")
  void
      isCandidateLimitNotBelowResultLimit_whenCandidateLimitVaries_shouldOnlyViolateWhenBelowResultLimit(
          int candidateLimit, int resultLimit, int rrfK, List<String> expectedMessages) {
    SearchProperties searchProperties =
        new SearchProperties(candidateLimit, resultLimit, rrfK, 0.5);

    Set<ConstraintViolation<SearchProperties>> violations = validator.validate(searchProperties);

    assertThat(violations)
        .extracting(ConstraintViolation::getMessage)
        .containsExactlyInAnyOrderElementsOf(expectedMessages);
  }

  private static Stream<Arguments> candidateLimitScenarios() {
    return Stream.of(
        Arguments.of(50, 20, 60, List.of()),
        Arguments.of(10, 20, 60, List.of("search.candidate-limit must be >= search.result-limit")));
  }
}
