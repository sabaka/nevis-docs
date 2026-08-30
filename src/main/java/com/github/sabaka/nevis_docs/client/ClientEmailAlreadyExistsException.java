package com.github.sabaka.nevis_docs.client;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
class ClientEmailAlreadyExistsException extends RuntimeException {

  private final String email;

  ClientEmailAlreadyExistsException(String email) {
    super("Client already exists with email: " + email);
    this.email = email;
  }

  String email() {
    return email;
  }
}
