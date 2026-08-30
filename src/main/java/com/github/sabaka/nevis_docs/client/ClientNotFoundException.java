package com.github.sabaka.nevis_docs.client;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
class ClientNotFoundException extends RuntimeException {

  private final UUID clientId;

  ClientNotFoundException(UUID clientId) {
    super("Client not found: " + clientId);
    this.clientId = clientId;
  }

  UUID clientId() {
    return clientId;
  }
}
