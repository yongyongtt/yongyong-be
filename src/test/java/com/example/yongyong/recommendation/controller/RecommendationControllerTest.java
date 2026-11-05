package com.example.yongyong.recommendation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.yongyong.common.exception.BusinessException;
import com.example.yongyong.common.exception.ErrorCode;
import com.example.yongyong.recommendation.dto.RecommendationResponse;
import com.example.yongyong.recommendation.service.RecommendationService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RecommendationController.class)
class RecommendationControllerTest {

  private static final String URL = "/api/regions/4146125000/recommendation";

  @Autowired private MockMvc mockMvc;
  @MockitoBean private RecommendationService recommendationService;

  @Test
  @DisplayName("추천을 ApiResponse로 내리고 필드명과 null이 명세와 같다")
  void returnsRecommendation() throws Exception {
    given(recommendationService.getRecommendation("4146125000", 2025))
        .willReturn(
            new RecommendationResponse(
                false,
                "2026-11-05 11:03",
                List.of(
                    new RecommendationResponse.Option(
                        "DRT",
                        "똑버스",
                        "수요응답형 버스",
                        "설명",
                        "RECOMMENDED",
                        1,
                        81,
                        false,
                        List.of("이유"),
                        List.of(),
                        null),
                    new RecommendationResponse.Option(
                        "HAPPY_TAXI",
                        "행복택시",
                        "부제",
                        "설명",
                        "EXCLUDED",
                        null,
                        null,
                        true,
                        List.of(),
                        List.of("근거"),
                        null)),
                List.of("추천 이유"),
                List.of("주의"),
                false));

    mockMvc
        .perform(get(URL).param("year", "2025"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.isKeep").value(false))
        .andExpect(jsonPath("$.data.createdAt").value("2026-11-05 11:03"))
        .andExpect(jsonPath("$.data.options[0].mode").value("DRT"))
        .andExpect(jsonPath("$.data.options[0].name").value("똑버스"))
        .andExpect(jsonPath("$.data.options[0].subtitle").value("수요응답형 버스"))
        .andExpect(jsonPath("$.data.options[0].desc").value("설명"))
        .andExpect(jsonPath("$.data.options[0].status").value("RECOMMENDED"))
        .andExpect(jsonPath("$.data.options[0].rank").value(1))
        .andExpect(jsonPath("$.data.options[0].score").value(81))
        .andExpect(jsonPath("$.data.options[0].isCurrent").value(false))
        .andExpect(jsonPath("$.data.options[0].reasons[0]").value("이유"))
        .andExpect(jsonPath("$.data.options[0].facts").isEmpty())
        .andExpect(jsonPath("$.data.options[0].together").value((Object) null))
        .andExpect(jsonPath("$.data.options[1].rank").value((Object) null))
        .andExpect(jsonPath("$.data.options[1].score").value((Object) null))
        .andExpect(jsonPath("$.data.options[1].isCurrent").value(true))
        .andExpect(jsonPath("$.data.reasons[0]").value("추천 이유"))
        .andExpect(jsonPath("$.data.cautions[0]").value("주의"))
        .andExpect(jsonPath("$.data.lowReliability").value(false));
  }

  @Test
  @DisplayName("year가 없으면 400 INVALID_REQUEST를 내린다")
  void missingYear() throws Exception {
    mockMvc
        .perform(get(URL))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
  }

  @Test
  @DisplayName("year가 숫자가 아니면 400 INVALID_REQUEST를 내린다")
  void invalidYear() throws Exception {
    mockMvc
        .perform(get(URL).param("year", "abc"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
  }

  @Test
  @DisplayName("regionCode가 숫자 10자리가 아니면 400 INVALID_REQUEST를 내린다")
  void invalidRegionCode() throws Exception {
    mockMvc
        .perform(get("/api/regions/abc/recommendation").param("year", "2025"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
  }

  @Test
  @DisplayName("추천이 없으면 404 RECOMMENDATION_NOT_FOUND를 내린다")
  void recommendationNotFound() throws Exception {
    given(recommendationService.getRecommendation(any(), any()))
        .willThrow(new BusinessException(ErrorCode.RECOMMENDATION_NOT_FOUND));

    mockMvc
        .perform(get(URL).param("year", "2025"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("RECOMMENDATION_NOT_FOUND"));
  }
}
