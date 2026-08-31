package com.github.sabaka.nevis_docs.search;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SearchController {

  private static final int QUERY_MAX_LENGTH = 500;

  private final SearchService searchService;

  SearchController(SearchService searchService) {
    this.searchService = searchService;
  }

  @Operation(summary = "Search clients and documents")
  @ApiResponse(responseCode = "200", description = "Successful search")
  @ApiResponse(responseCode = "400", description = "Invalid search query")
  @ApiResponse(responseCode = "503", description = "Search temporarily unavailable")
  @GetMapping("/search")
  List<SearchResponse> search(
      @RequestParam("q")
          @NotBlank(message = "must not be blank")
          @Size(max = QUERY_MAX_LENGTH, message = "must not exceed {max} characters")
          @Pattern(
              regexp = ".*[\\p{L}\\p{Nd}].*",
              flags = Pattern.Flag.DOTALL,
              message = "must contain at least one letter or digit")
          String q) {
    return searchService.search(q).stream().map(SearchController::toResponse).toList();
  }

  private static SearchResponse toResponse(SearchResult result) {
    return switch (result) {
      case ClientSearchResult client ->
          new ClientSearchResponse(
              client.id(),
              client.firstName(),
              client.lastName(),
              client.email(),
              client.description(),
              client.socialLinks());
      case DocumentSearchResult document ->
          new DocumentSearchResponse(
              document.id(),
              document.clientId(),
              document.title(),
              document.content(),
              document.createdAt(),
              document.summary());
    };
  }

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = ClientSearchResponse.class, name = "CLIENT"),
    @JsonSubTypes.Type(value = DocumentSearchResponse.class, name = "DOCUMENT")
  })
  private sealed interface SearchResponse permits ClientSearchResponse, DocumentSearchResponse {}

  private record ClientSearchResponse(
      @Schema(format = "uuid") UUID id,
      String firstName,
      String lastName,
      @Schema(format = "email") String email,
      @Nullable String description,
      List<String> socialLinks)
      implements SearchResponse {}

  private record DocumentSearchResponse(
      @Schema(format = "uuid") UUID id,
      @Schema(format = "uuid") UUID clientId,
      String title,
      String content,
      @Schema(format = "date-time") Instant createdAt,
      @Nullable String summary)
      implements SearchResponse {}
}
