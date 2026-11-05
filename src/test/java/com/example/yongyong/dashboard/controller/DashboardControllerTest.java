package com.example.yongyong.dashboard.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.yongyong.common.exception.BusinessException;
import com.example.yongyong.common.exception.ErrorCode;
import com.example.yongyong.dashboard.dto.DashboardMapResponse;
import com.example.yongyong.dashboard.dto.DashboardResponse;
import com.example.yongyong.dashboard.service.DashboardService;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DashboardController.class)
class DashboardControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private DashboardService dashboardService;

  @Test
  @DisplayName("연간 요약을 ApiResponse 형식으로 내리고 month의 null을 생략하지 않는다")
  void returnsApiResponse() throws Exception {
    given(dashboardService.getDashboard(any()))
        .willReturn(
            new DashboardResponse(
                new DashboardResponse.Period(
                    2025, null, List.of(2025), Arrays.asList(null, 2), "2026-11-05 11:02"),
                new DashboardResponse.Summary(38, 9, 39.1),
                List.of(new DashboardResponse.TopRegion(1, "4146134000", "원삼면", "처인구", 68))));

    mockMvc
        .perform(get("/api/dashboard").param("year", "2025"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.error").value((Object) null))
        .andExpect(jsonPath("$.data.period.year").value(2025))
        .andExpect(jsonPath("$.data.period.month").value((Object) null))
        .andExpect(jsonPath("$.data.period.availableYears[0]").value(2025))
        .andExpect(jsonPath("$.data.period.availableMonths[0]").value((Object) null))
        .andExpect(jsonPath("$.data.period.availableMonths[1]").value(2))
        .andExpect(jsonPath("$.data.period.updatedAt").value("2026-11-05 11:02"))
        .andExpect(jsonPath("$.data.summary.regionCount").value(38))
        .andExpect(jsonPath("$.data.summary.vulnerableCount").value(9))
        .andExpect(jsonPath("$.data.summary.avgScore").value(39.1))
        .andExpect(jsonPath("$.data.top10[0].rank").value(1))
        .andExpect(jsonPath("$.data.top10[0].regionCode").value("4146134000"))
        .andExpect(jsonPath("$.data.top10[0].regionName").value("원삼면"))
        .andExpect(jsonPath("$.data.top10[0].gu").value("처인구"))
        .andExpect(jsonPath("$.data.top10[0].score").value(68));
  }

  @Test
  @DisplayName("지도 응답을 ApiResponse 형식으로 내리고 점수는 소수점을 유지한다")
  void returnsMap() throws Exception {
    given(dashboardService.getMap(any()))
        .willReturn(
            new DashboardMapResponse(
                List.of(new DashboardMapResponse.MapRegion("4146134000", "원삼면", "처인구", 68.1, 1))));

    mockMvc
        .perform(get("/api/dashboard/map").param("year", "2025"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.error").value((Object) null))
        .andExpect(jsonPath("$.data.regions[0].regionCode").value("4146134000"))
        .andExpect(jsonPath("$.data.regions[0].regionName").value("원삼면"))
        .andExpect(jsonPath("$.data.regions[0].gu").value("처인구"))
        .andExpect(jsonPath("$.data.regions[0].score").value(68.1))
        .andExpect(jsonPath("$.data.regions[0].rank").value(1));
  }

  @Test
  @DisplayName("지도에 year가 없으면 400 INVALID_REQUEST를 내린다")
  void mapMissingYear() throws Exception {
    mockMvc
        .perform(get("/api/dashboard/map"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
  }

  @Test
  @DisplayName("지도에 범위 밖 month가 오면 400 INVALID_REQUEST를 내린다")
  void mapInvalidMonth() throws Exception {
    mockMvc
        .perform(get("/api/dashboard/map").param("year", "2025").param("month", "13"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
  }

  @Test
  @DisplayName("지도에서 적재되지 않은 기간이면 404 PERIOD_NOT_FOUND를 내린다")
  void mapPeriodNotFound() throws Exception {
    given(dashboardService.getMap(any()))
        .willThrow(new BusinessException(ErrorCode.PERIOD_NOT_FOUND));

    mockMvc
        .perform(get("/api/dashboard/map").param("year", "2025").param("month", "5"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("PERIOD_NOT_FOUND"));
  }

  @Test
  @DisplayName("year가 없으면 400 INVALID_REQUEST를 내린다")
  void missingYear() throws Exception {
    mockMvc
        .perform(get("/api/dashboard"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.data").value((Object) null))
        .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "13", "abc"})
  @DisplayName("month가 1~12 범위의 숫자가 아니면 400 INVALID_REQUEST를 내린다")
  void invalidMonth(String month) throws Exception {
    mockMvc
        .perform(get("/api/dashboard").param("year", "2025").param("month", month))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
  }

  @Test
  @DisplayName("적재되지 않은 기간이면 404 PERIOD_NOT_FOUND를 내린다")
  void periodNotFound() throws Exception {
    given(dashboardService.getDashboard(any()))
        .willThrow(new BusinessException(ErrorCode.PERIOD_NOT_FOUND));

    mockMvc
        .perform(get("/api/dashboard").param("year", "2025").param("month", "5"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("PERIOD_NOT_FOUND"));
  }
}
