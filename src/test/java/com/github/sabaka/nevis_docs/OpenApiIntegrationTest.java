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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
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

  @Test
  void openApiDocumentDescribesSearchEndpointWithPolymorphicResponseSchema() throws Exception {
    mockMvc
        .perform(get("/v3/api-docs"))
        .andDo(log())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/search'].get.parameters[0].name").value("q"))
        .andExpect(jsonPath("$.paths['/search'].get.parameters[0].required").value(true))
        .andExpect(jsonPath("$.paths['/search'].get.responses['400']").exists())
        .andExpect(jsonPath("$.paths['/search'].get.responses['503']").exists())
        .andExpect(
            jsonPath(
                    "$.paths['/search'].get.responses['400'].content"
                        + "['*/*'].schema.items.oneOf[0]")
                .exists())
        .andExpect(
            jsonPath(
                    "$.paths['/search'].get.responses['400'].content"
                        + "['*/*'].schema.items.oneOf[1]")
                .exists())
        .andExpect(
            jsonPath(
                    "$.paths['/search'].get.responses['400'].content"
                        + "['*/*'].schema.items.oneOf[2]")
                .doesNotExist())
        .andExpect(
            jsonPath("$.components.schemas.SearchResponse.discriminator.propertyName")
                .value("type"))
        .andExpect(
            jsonPath("$.components.schemas.ClientSearchResponse.allOf[1].properties['first_name']")
                .exists())
        .andExpect(
            jsonPath(
                    "$.components.schemas.ClientSearchResponse.allOf[1].properties['social_links']")
                .exists())
        .andExpect(
            jsonPath(
                    "$.components.schemas.DocumentSearchResponse.allOf[1]"
                        + ".properties['created_at'].format")
                .value("date-time"));
  }
}
