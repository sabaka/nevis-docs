package com.github.sabaka.nevis_docs;

import org.springframework.boot.SpringApplication;

public class TestNevisDocsApplication {

  static void main(String[] args) {
    SpringApplication.from(NevisDocsApplication::main)
        .with(TestcontainersConfiguration.class)
        .run(args);
  }
}
