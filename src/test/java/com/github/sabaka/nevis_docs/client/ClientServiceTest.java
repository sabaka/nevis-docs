package com.github.sabaka.nevis_docs.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.assertArg;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.stream.Stream;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

  @Mock private ClientRepository clientRepository;

  private ClientService clientService;

  @BeforeEach
  void setUp() {
    clientService = new ClientService(clientRepository);
  }

  @ParameterizedTest
  @MethodSource("optionalFieldVariants")
  void create_whenOptionalFieldsVary_shouldPersistNormalisedClient(
      String description,
      List<String> socialLinks,
      String expectedDescription,
      List<String> expectedSocialLinks) {
    given(clientRepository.existsByEmail("john.doe@neviswealth.com")).willReturn(false);

    Client result =
        clientService.create("John", "Doe", "john.doe@neviswealth.com", description, socialLinks);

    verify(clientRepository)
        .save(
            assertArg(
                saved -> {
                  assertThat(saved.id()).isNotNull();
                  assertThat(saved)
                      .extracting(
                          Client::firstName,
                          Client::lastName,
                          Client::email,
                          Client::description,
                          Client::socialLinks)
                      .containsExactly(
                          "John",
                          "Doe",
                          "john.doe@neviswealth.com",
                          expectedDescription,
                          expectedSocialLinks);
                  assertThat(saved).isSameAs(result);
                }));
  }

  private static Stream<Arguments> optionalFieldVariants() {
    return Stream.of(
        Arguments.of(
            "Private wealth client",
            List.of("https://linkedin.com/in/john-doe"),
            "Private wealth client",
            List.of("https://linkedin.com/in/john-doe")),
        Arguments.of(null, null, null, List.of()),
        Arguments.of(null, List.of(), null, List.of()));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "John.Doe@NevisWealth.com",
        "JOHN.DOE@NEVISWEALTH.COM",
        "john.doe@neviswealth.com"
      })
  void create_whenEmailHasMixedCase_shouldLowercaseBeforeExistsCheckAndPersist(String email) {
    given(clientRepository.existsByEmail("john.doe@neviswealth.com")).willReturn(false);

    clientService.create(
        "John", "Doe", email, "Private wealth client", List.of("https://linkedin.com/in/john-doe"));

    verify(clientRepository).existsByEmail("john.doe@neviswealth.com");
    verify(clientRepository)
        .save(assertArg(saved -> assertThat(saved.email()).isEqualTo("john.doe@neviswealth.com")));
  }

  @Test
  void create_whenEmailAlreadyExists_shouldThrowClientEmailAlreadyExistsException() {
    String email = "john.doe@neviswealth.com";
    given(clientRepository.existsByEmail(email)).willReturn(true);

    ThrowableAssert.ThrowingCallable createClient =
        () ->
            clientService.create(
                "John",
                "Doe",
                email,
                "Private wealth client",
                List.of("https://linkedin.com/in/john-doe"));

    assertThatThrownBy(createClient)
        .asInstanceOf(InstanceOfAssertFactories.type(ClientEmailAlreadyExistsException.class))
        .extracting(ClientEmailAlreadyExistsException::email)
        .isEqualTo(email);
    verify(clientRepository, never()).save(any());
  }
}
