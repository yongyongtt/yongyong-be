package com.example.yongyong.plan.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.yongyong.common.exception.BusinessException;
import com.example.yongyong.common.exception.ErrorCode;
import com.example.yongyong.common.query.PeriodQuery;
import com.example.yongyong.common.response.PeriodResponse;
import com.example.yongyong.plan.dto.PlanResponse;
import com.example.yongyong.plan.service.PlanService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PlanController.class)
class PlanControllerTest {

  private static final String URL = "/api/plans";

  @Autowired private MockMvc mockMvc;
  @MockitoBean private PlanService planService;

  @Test
  @DisplayName("통합 최적안을 ApiResponse로 내리고 필드명이 명세와 같다")
  void returnsPlans() throws Exception {
    given(planService.getPlans(new PeriodQuery(2025, null)))
        .willReturn(
            new PlanResponse(
                new PeriodResponse(2025, List.of(1, 2), PeriodResponse.Type.YEAR_ALL),
                List.of(
                    new PlanResponse.Plan(
                        3,
                        "균형형",
                        "효율과 취약도를 함께 고려한 절충안",
                        "효율 순위와 취약도 순위의 평균",
                        711666667L,
                        610000000L,
                        101666667L,
                        5,
                        4,
                        24999,
                        List.of(
                            new PlanResponse.ModeCount("BUS", "노선버스", 0),
                            new PlanResponse.ModeCount("DRT", "똑버스", 5),
                            new PlanResponse.ModeCount("HAPPY_TAXI", "행복택시", 0)),
                        List.of(
                            new PlanResponse.Region(
                                1,
                                1,
                                "4146125300",
                                "모현읍",
                                "처인구",
                                "DRT",
                                "똑버스",
                                "똑버스 4대",
                                122000000L)),
                        List.of(
                            new PlanResponse.Skipped(
                                6,
                                "4146125900",
                                "남사읍",
                                "처인구",
                                "DRT",
                                "똑버스",
                                "똑버스 4대",
                                122000000L,
                                101666667L)),
                        List.of(
                            new PlanResponse.ByRegion("4146125300", "INCLUDED", "DRT", "똑버스"))))));

    mockMvc
        .perform(get(URL).param("year", "2025"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.error").value((Object) null))
        .andExpect(jsonPath("$.data.period.year").value(2025))
        .andExpect(jsonPath("$.data.period.months[1]").value(2))
        .andExpect(jsonPath("$.data.period.type").value("YEAR_ALL"))
        .andExpect(jsonPath("$.data.plans[0].plan").value(3))
        .andExpect(jsonPath("$.data.plans[0].name").value("균형형"))
        .andExpect(jsonPath("$.data.plans[0].desc").value("효율과 취약도를 함께 고려한 절충안"))
        .andExpect(jsonPath("$.data.plans[0].sortBasis").value("효율 순위와 취약도 순위의 평균"))
        .andExpect(jsonPath("$.data.plans[0].budget").value(711666667))
        .andExpect(jsonPath("$.data.plans[0].totalCost").value(610000000))
        .andExpect(jsonPath("$.data.plans[0].budgetLeft").value(101666667))
        .andExpect(jsonPath("$.data.plans[0].includedCount").value(5))
        .andExpect(jsonPath("$.data.plans[0].skippedCount").value(4))
        .andExpect(jsonPath("$.data.plans[0].pop65Total").value(24999))
        .andExpect(jsonPath("$.data.plans[0].modeCounts[0].mode").value("BUS"))
        .andExpect(jsonPath("$.data.plans[0].modeCounts[0].name").value("노선버스"))
        .andExpect(jsonPath("$.data.plans[0].modeCounts[0].count").value(0))
        .andExpect(jsonPath("$.data.plans[0].regions[0].order").value(1))
        .andExpect(jsonPath("$.data.plans[0].regions[0].rank").value(1))
        .andExpect(jsonPath("$.data.plans[0].regions[0].regionCode").value("4146125300"))
        .andExpect(jsonPath("$.data.plans[0].regions[0].regionName").value("모현읍"))
        .andExpect(jsonPath("$.data.plans[0].regions[0].gu").value("처인구"))
        .andExpect(jsonPath("$.data.plans[0].regions[0].mode").value("DRT"))
        .andExpect(jsonPath("$.data.plans[0].regions[0].modeName").value("똑버스"))
        .andExpect(jsonPath("$.data.plans[0].regions[0].action").value("똑버스 4대"))
        .andExpect(jsonPath("$.data.plans[0].regions[0].cost").value(122000000))
        .andExpect(jsonPath("$.data.plans[0].skipped[0].rank").value(6))
        .andExpect(jsonPath("$.data.plans[0].skipped[0].regionName").value("남사읍"))
        .andExpect(jsonPath("$.data.plans[0].skipped[0].budgetLeftAtSkip").value(101666667))
        .andExpect(jsonPath("$.data.plans[0].byRegion[0].regionCode").value("4146125300"))
        .andExpect(jsonPath("$.data.plans[0].byRegion[0].status").value("INCLUDED"))
        .andExpect(jsonPath("$.data.plans[0].byRegion[0].mode").value("DRT"))
        .andExpect(jsonPath("$.data.plans[0].byRegion[0].modeName").value("똑버스"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "13", "abc"})
  @DisplayName("month가 1~12 범위의 숫자가 아니면 400 INVALID_REQUEST를 내린다")
  void invalidMonth(String month) throws Exception {
    mockMvc
        .perform(get(URL).param("year", "2025").param("month", month))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
  }

  @Test
  @DisplayName("year가 없으면 400 INVALID_REQUEST를 내린다")
  void missingYear() throws Exception {
    mockMvc
        .perform(get(URL))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
  }

  @ParameterizedTest
  @EnumSource(names = {"PERIOD_NOT_FOUND", "SUITABILITY_NOT_READY", "PLAN_NOT_FOUND"})
  @DisplayName("서비스가 기간·달·plan 없음 예외를 던지면 404와 그 에러코드를 내린다")
  void notFound(ErrorCode errorCode) throws Exception {
    given(planService.getPlans(any())).willThrow(new BusinessException(errorCode));

    mockMvc
        .perform(get(URL).param("year", "2025").param("month", "7"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value(errorCode.name()))
        .andExpect(jsonPath("$.error.message").value(errorCode.getMessage()));
  }
}
