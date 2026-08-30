package com.github.sabaka.nevis_docs;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.log;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
class OpenApiIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void openApiDocumentDescribesBothEndpointsWithSnakeCaseSchemaAndDeclaredFormats()
      throws Exception {
    mockMvc
        .perform(get("/v3/api-docs"))
        .andDo(log())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/clients']").exists())
        .andExpect(jsonPath("$.paths['/clients/{clientId}/documents']").exists())
        .andExpect(
            jsonPath("$.components.schemas.ClientResponse.properties['first_name']").exists())
        .andExpect(
            jsonPath("$.components.schemas.ClientResponse.properties['social_links']").exists())
        .andExpect(
            jsonPath("$.components.schemas.ClientResponse.properties['id'].format").value("uuid"))
        .andExpect(
            jsonPath("$.components.schemas.DocumentResponse.properties['created_at'].format")
                .value("date-time"));
  }
}
