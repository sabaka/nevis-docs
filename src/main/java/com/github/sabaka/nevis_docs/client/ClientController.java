package com.github.sabaka.nevis_docs.client;

import com.github.sabaka.nevis_docs.summary.DocumentSummaryStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/clients")
class ClientController {

  private final ClientService clientService;
  private final DocumentService documentService;

  ClientController(ClientService clientService, DocumentService documentService) {
    this.clientService = clientService;
    this.documentService = documentService;
  }

  @Operation(summary = "Create a client")
  @ApiResponse(responseCode = "201", description = "Client created")
  @ApiResponse(responseCode = "409", description = "Client already exists")
  @PostMapping
  ResponseEntity<ClientResponse> createClient(@Valid @RequestBody CreateClientRequest request) {
    Client client =
        clientService.create(
            request.firstName(),
            request.lastName(),
            request.email(),
            request.description(),
            request.socialLinks());
    return ResponseEntity.created(URI.create("/clients/" + client.id()))
        .body(ClientResponse.from(client));
  }

  @Operation(summary = "Create a document for a client")
  @ApiResponse(responseCode = "201", description = "Document created")
  @PostMapping(path = "/{clientId}/documents")
  ResponseEntity<DocumentResponse> createDocument(
      @PathVariable UUID clientId, @Valid @RequestBody CreateDocumentRequest request) {
    Document document = documentService.create(clientId, request.title(), request.content());
    return ResponseEntity.created(
            URI.create("/clients/" + clientId + "/documents/" + document.id()))
        .body(DocumentResponse.from(document));
  }

  private record CreateClientRequest(
      @NotBlank(message = "must not be blank") @Schema(example = "John") String firstName,
      @NotBlank(message = "must not be blank") @Schema(example = "Doe") String lastName,
      @NotBlank(message = "must not be blank")
          @Email(message = "must be a valid email address")
          @Schema(example = "john.doe@neviswealth.com")
          String email,
      @Nullable @Schema(example = "Private wealth client") String description,
      @Nullable List<@NotBlank(message = "must not be blank") String> socialLinks) {}

  private record ClientResponse(
      @Schema(format = "uuid") UUID id,
      String firstName,
      String lastName,
      @Schema(format = "email") String email,
      @Nullable String description,
      List<String> socialLinks) {

    static ClientResponse from(Client client) {
      return new ClientResponse(
          client.id(),
          client.firstName(),
          client.lastName(),
          client.email(),
          client.description(),
          client.socialLinks());
    }
  }

  private record CreateDocumentRequest(
      @NotBlank(message = "must not be blank") String title,
      @NotBlank(message = "must not be blank") String content) {}

  private record DocumentResponse(
      @Schema(format = "uuid") UUID id,
      @Schema(format = "uuid") UUID clientId,
      String title,
      String content,
      @Schema(format = "date-time") Instant createdAt,
      @Nullable String summary,
      DocumentSummaryStatus summaryStatus) {

    static DocumentResponse from(Document document) {
      return new DocumentResponse(
          document.id(),
          document.clientId(),
          document.title(),
          document.content(),
          document.createdAt(),
          document.summary(),
          document.summaryStatus());
    }
  }
}
