package com.github.sabaka.nevis_docs.search;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.log;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SearchController.class)
class SearchQueryValidationIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private SearchService searchService;

  @ParameterizedTest
  @ValueSource(strings = {"---", "½", "Ⅰ"})
  void search_whenQueryHasNoLetterOrDigit_shouldRejectWithoutCallingService(String query)
      throws Exception {
    mockMvc
        .perform(get("/search").param("q", query))
        .andDo(log())
        .andExpect(status().isBadRequest());

    verifyNoInteractions(searchService);
  }

  @Test
  void search_whenQueryExceedsMaxLength_shouldRejectWithoutCallingService() throws Exception {
    mockMvc
        .perform(get("/search").param("q", "a".repeat(501)))
        .andDo(log())
        .andExpect(status().isBadRequest());

    verifyNoInteractions(searchService);
  }

  @Test
  void search_whenQueryIsExactlyMaxLength_shouldAcceptAndDelegate() throws Exception {
    String query = "a".repeat(500);
    given(searchService.search(query)).willReturn(List.of());

    mockMvc.perform(get("/search").param("q", query)).andDo(log()).andExpect(status().isOk());

    verify(searchService).search(query);
  }

  @ParameterizedTest
  @ValueSource(strings = {"日本語", "٠١", "multi\nline query"})
  void search_whenQueryContainsLetterOrDigit_shouldAcceptAndDelegate(String query)
      throws Exception {
    given(searchService.search(query)).willReturn(List.of());

    mockMvc.perform(get("/search").param("q", query)).andDo(log()).andExpect(status().isOk());

    verify(searchService).search(query);
  }
}
