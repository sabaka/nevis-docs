package com.github.sabaka.nevis_docs.search;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.github.sabaka.nevis_docs.summary.DocumentSummaryStatus;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.context.WebApplicationContext;

@WebMvcTest(SearchController.class)
public abstract class SearchContractBase {

  private static final UUID CLIENT_ID = UUID.fromString("31a67593-e39a-4e22-83df-f3494b55a439");
  private static final UUID DOCUMENT_ID = UUID.fromString("66206f62-cff6-4e52-ad8e-978b8d8b9094");
  private static final Instant CREATED_AT = Instant.parse("2026-08-29T14:00:00Z");

  @Autowired private WebApplicationContext context;

  @MockitoBean private SearchService searchService;

  @BeforeEach
  public void setUp() {
    RestAssuredMockMvc.webAppContextSetup(context);
    given(searchService.search(anyString())).willReturn(List.of());
    given(searchService.search("empty")).willReturn(List.of());
    given(searchService.search("mixed"))
        .willReturn(
            List.of(
                new ClientSearchResult(
                    CLIENT_ID,
                    0.5d,
                    "John",
                    "Doe",
                    "john.doe@neviswealth.com",
                    "Private wealth client",
                    List.of("https://linkedin.com/in/john-doe")),
                new DocumentSearchResult(
                    DOCUMENT_ID,
                    0.25d,
                    CLIENT_ID,
                    "Electricity statement",
                    "Utility bill for 10 Downing Street",
                    CREATED_AT,
                    "An electricity utility bill for 10 Downing Street.",
                    DocumentSummaryStatus.COMPLETED)));
    given(searchService.search("unavailable"))
        .willThrow(new SearchUnavailableException("query embedding unavailable"));
  }
}
