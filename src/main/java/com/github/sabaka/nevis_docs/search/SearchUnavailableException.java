package com.github.sabaka.nevis_docs.search;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
class SearchUnavailableException extends RuntimeException {

  SearchUnavailableException(String reason) {
    super(reason);
  }

  SearchUnavailableException(String reason, Throwable cause) {
    super(reason, cause);
  }
}
