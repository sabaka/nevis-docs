package com.github.sabaka.nevis_docs;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class NevisDocsApplicationTests {

  @Test
  void contextLoads() {}
}
