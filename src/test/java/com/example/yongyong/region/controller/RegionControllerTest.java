package com.example.yongyong.region.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.yongyong.common.exception.BusinessException;
import com.example.yongyong.common.exception.ErrorCode;
import com.example.yongyong.common.response.PeriodResponse;
import com.example.yongyong.region.dto.RegionComparisonResponse;
import com.example.yongyong.region.dto.RegionDetailResponse;
import com.example.yongyong.region.dto.RegionPreviewResponse;
import com.example.yongyong.region.dto.RegionTrendResponse;
import com.example.yongyong.region.service.RegionMetricService;
import com.example.yongyong.region.service.RegionService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RegionController.class)
class RegionControllerTest {

  private static final String URL = "/api/regions/4146125000/preview";
  private static final String DETAIL_URL = "/api/regions/4146125000";
  private static final String COMPARISON_URL = "/api/regions/4146125000/comparison";
  private static final String TREND_URL = "/api/regions/4146125000/unmatched-trend";

  @Autowired private MockMvc mockMvc;
  @MockitoBean private RegionService regionService;
  @MockitoBean private RegionMetricService regionMetricService;

  @Test
  @DisplayName("미리보기를 ApiResponse 형식으로 내리고 필드명이 명세와 같다")
  void returnsPreview() throws Exception {
    given(regionService.getPreview(eq("4146125000"), any()))
        .willReturn(
            new RegionPreviewResponse(
                new PeriodResponse(2025, List.of(1, 2), PeriodResponse.Type.YEAR_ALL),
                new PeriodResponse(2025, List.of(1, 2), PeriodResponse.Type.YEAR_ALL),
                "4146125000",
                "포곡읍",
                "처인구",
                60,
                7,
                new RegionPreviewResponse.Type("정류장 접근 취약형", "설명"),
                false,
                List.of(new RegionPreviewResponse.Recommended("DRT", "똑버스")),
                "요약"));

    mockMvc
        .perform(get(URL).param("year", "2025"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.error").value((Object) null))
        .andExpect(jsonPath("$.data.regionCode").value("4146125000"))
        .andExpect(jsonPath("$.data.regionName").value("포곡읍"))
        .andExpect(jsonPath("$.data.gu").value("처인구"))
        .andExpect(jsonPath("$.data.score").value(60))
        .andExpect(jsonPath("$.data.rank").value(7))
        .andExpect(jsonPath("$.data.type.name").value("정류장 접근 취약형"))
        .andExpect(jsonPath("$.data.type.desc").value("설명"))
        .andExpect(jsonPath("$.data.isKeep").value(false))
        .andExpect(jsonPath("$.data.recommended[0].mode").value("DRT"))
        .andExpect(jsonPath("$.data.recommended[0].name").value("똑버스"))
        .andExpect(jsonPath("$.data.summary").value("요약"))
        .andExpect(jsonPath("$.data.period.year").value(2025))
        .andExpect(jsonPath("$.data.period.months[1]").value(2))
        .andExpect(jsonPath("$.data.period.type").value("YEAR_ALL"))
        .andExpect(jsonPath("$.data.typePeriod.year").value(2025))
        .andExpect(jsonPath("$.data.typePeriod.months[1]").value(2))
        .andExpect(jsonPath("$.data.typePeriod.type").value("YEAR_ALL"));
  }

  @Test
  @DisplayName("유형과 추천이 없으면 null을 생략하지 않고 내린다")
  void returnsNulls() throws Exception {
    given(regionService.getPreview(eq("4146125000"), any()))
        .willReturn(
            new RegionPreviewResponse(
                new PeriodResponse(2025, List.of(1, 2), PeriodResponse.Type.YEAR_ALL),
                new PeriodResponse(2025, List.of(1, 2), PeriodResponse.Type.YEAR_ALL),
                "4146125000",
                "포곡읍",
                "처인구",
                60,
                7,
                null,
                false,
                null,
                null));

    mockMvc
        .perform(get(URL).param("year", "2025"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.type").value((Object) null))
        .andExpect(jsonPath("$.data.recommended").value((Object) null))
        .andExpect(jsonPath("$.data.summary").value((Object) null));
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
  @DisplayName("범위 밖 month가 오면 400 INVALID_REQUEST를 내린다")
  void invalidMonth() throws Exception {
    mockMvc
        .perform(get(URL).param("year", "2025").param("month", "13"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"abc", "414612500", "41461250000"})
  @DisplayName("regionCode가 숫자 10자리가 아니면 400 INVALID_REQUEST를 내린다")
  void invalidRegionCode(String regionCode) throws Exception {
    mockMvc
        .perform(get("/api/regions/" + regionCode + "/preview").param("year", "2025"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
  }

  @Test
  @DisplayName("상세를 ApiResponse 형식으로 내리고 필드명과 null이 명세와 같다")
  void returnsDetail() throws Exception {
    given(regionService.getDetail(eq("4146125000"), any()))
        .willReturn(
            new RegionDetailResponse(
                new PeriodResponse(2025, List.of(1, 2), PeriodResponse.Type.YEAR_ALL),
                new PeriodResponse(2025, List.of(1, 2), PeriodResponse.Type.YEAR_ALL),
                new RegionDetailResponse.Header("4146125000", "포곡읍", "처인구", 60, 7),
                new RegionDetailResponse.Reliability(false, null),
                new RegionDetailResponse.Type(
                    "정류장 접근 취약형",
                    "설명",
                    List.of("칩"),
                    List.of(new RegionDetailResponse.SameTypeRegion("4146125300", "모현읍")))));

    mockMvc
        .perform(get(DETAIL_URL).param("year", "2025"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.header.regionCode").value("4146125000"))
        .andExpect(jsonPath("$.data.header.regionName").value("포곡읍"))
        .andExpect(jsonPath("$.data.header.gu").value("처인구"))
        .andExpect(jsonPath("$.data.header.score").value(60))
        .andExpect(jsonPath("$.data.header.rank").value(7))
        .andExpect(jsonPath("$.data.period.year").value(2025))
        .andExpect(jsonPath("$.data.period.months[1]").value(2))
        .andExpect(jsonPath("$.data.period.type").value("YEAR_ALL"))
        .andExpect(jsonPath("$.data.typePeriod.year").value(2025))
        .andExpect(jsonPath("$.data.typePeriod.months[1]").value(2))
        .andExpect(jsonPath("$.data.typePeriod.type").value("YEAR_ALL"))
        .andExpect(jsonPath("$.data.reliability.low").value(false))
        .andExpect(jsonPath("$.data.reliability.note").value((Object) null))
        .andExpect(jsonPath("$.data.type.name").value("정류장 접근 취약형"))
        .andExpect(jsonPath("$.data.type.desc").value("설명"))
        .andExpect(jsonPath("$.data.type.chips[0]").value("칩"))
        .andExpect(jsonPath("$.data.type.sameTypeRegions[0].regionCode").value("4146125300"))
        .andExpect(jsonPath("$.data.type.sameTypeRegions[0].regionName").value("모현읍"));
  }

  @Test
  @DisplayName("상세의 유형이 없으면 type을 null로 내린다")
  void returnsDetailWithoutType() throws Exception {
    given(regionService.getDetail(eq("4146125000"), any()))
        .willReturn(
            new RegionDetailResponse(
                new PeriodResponse(2025, List.of(2), PeriodResponse.Type.MONTH),
                new PeriodResponse(2025, List.of(2), PeriodResponse.Type.MONTH),
                new RegionDetailResponse.Header("4146125000", "포곡읍", "처인구", 60, 7),
                new RegionDetailResponse.Reliability(false, null),
                null));

    mockMvc
        .perform(get(DETAIL_URL).param("year", "2025"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.type").value((Object) null));
  }

  @Test
  @DisplayName("상세에 year가 없으면 400 INVALID_REQUEST를 내린다")
  void detailMissingYear() throws Exception {
    mockMvc
        .perform(get(DETAIL_URL))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
  }

  @Test
  @DisplayName("상세에 범위 밖 month가 오면 400 INVALID_REQUEST를 내린다")
  void detailInvalidMonth() throws Exception {
    mockMvc
        .perform(get(DETAIL_URL).param("year", "2025").param("month", "13"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
  }

  @Test
  @DisplayName("상세의 regionCode가 숫자 10자리가 아니면 400 INVALID_REQUEST를 내린다")
  void detailInvalidRegionCode() throws Exception {
    mockMvc
        .perform(get("/api/regions/abc").param("year", "2025"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
  }

  @Test
  @DisplayName("상세의 서비스 예외를 404 에러 코드로 내린다")
  void detailNotFound() throws Exception {
    given(regionService.getDetail(any(), any()))
        .willThrow(new BusinessException(ErrorCode.REGION_NOT_FOUND));

    mockMvc
        .perform(get(DETAIL_URL).param("year", "2025"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("REGION_NOT_FOUND"));
  }

  @Test
  @DisplayName("시 평균 비교를 ApiResponse로 내리고 정수와 null을 명세대로 내린다")
  void returnsComparison() throws Exception {
    given(regionMetricService.getComparison(eq("4146125000"), any()))
        .willReturn(
            new RegionComparisonResponse(
                new PeriodResponse(2025, List.of(1, 2), PeriodResponse.Type.YEAR_ALL),
                List.of(
                    new RegionComparisonResponse.Metric(
                        "TAXI_MATCH_RATE", "택시 호출 성사율", "%", 53, 54),
                    new RegionComparisonResponse.Metric("BUS_STOP_COUNT", "버스 정류장 수", "개", 148, 82),
                    new RegionComparisonResponse.Metric(
                        "FLOATING_POP", "생활인구 (시간 평균)", "명", null, 5938))));

    mockMvc
        .perform(get(COMPARISON_URL).param("year", "2025"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.metrics[0].key").value("TAXI_MATCH_RATE"))
        .andExpect(jsonPath("$.data.metrics[0].label").value("택시 호출 성사율"))
        .andExpect(jsonPath("$.data.metrics[0].unit").value("%"))
        .andExpect(jsonPath("$.data.metrics[0].region").value(53))
        .andExpect(jsonPath("$.data.metrics[0].cityAvg").value(54))
        .andExpect(jsonPath("$.data.period.type").value("YEAR_ALL"))
        .andExpect(jsonPath("$.data.metrics[1].region").value(148))
        .andExpect(jsonPath("$.data.metrics[1].cityAvg").value(82))
        .andExpect(jsonPath("$.data.metrics[2].region").value((Object) null))
        .andExpect(jsonPath("$.data.metrics[2].cityAvg").value(5938));
  }

  @Test
  @DisplayName("비교에 year가 없으면 400 INVALID_REQUEST를 내린다")
  void comparisonMissingYear() throws Exception {
    mockMvc
        .perform(get(COMPARISON_URL))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
  }

  @Test
  @DisplayName("비교에 범위 밖 month가 오면 400 INVALID_REQUEST를 내린다")
  void comparisonInvalidMonth() throws Exception {
    mockMvc
        .perform(get(COMPARISON_URL).param("year", "2025").param("month", "13"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
  }

  @Test
  @DisplayName("비교의 regionCode가 숫자 10자리가 아니면 400 INVALID_REQUEST를 내린다")
  void comparisonInvalidRegionCode() throws Exception {
    mockMvc
        .perform(get("/api/regions/abc/comparison").param("year", "2025"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
  }

  @Test
  @DisplayName("비교의 서비스 예외를 404 에러 코드로 내린다")
  void comparisonNotFound() throws Exception {
    given(regionMetricService.getComparison(any(), any()))
        .willThrow(new BusinessException(ErrorCode.PERIOD_NOT_FOUND));

    mockMvc
        .perform(get(COMPARISON_URL).param("year", "2024"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("PERIOD_NOT_FOUND"));
  }

  @Test
  @DisplayName("미배차율 추이를 ApiResponse로 내리고 필드명과 null이 명세와 같다")
  void returnsTrend() throws Exception {
    given(regionMetricService.getUnmatchedTrend("4146125000", 2025))
        .willReturn(
            new RegionTrendResponse(
                new PeriodResponse(2025, List.of(1, 2), PeriodResponse.Type.YEAR_ALL),
                List.of(
                    new RegionTrendResponse.Point(2025, 1, 45, 2779, 1538, 45),
                    new RegionTrendResponse.Point(2025, 2, 37, 2571, 1631, null))));

    mockMvc
        .perform(get(TREND_URL).param("year", "2025"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.period.type").value("YEAR_ALL"))
        .andExpect(jsonPath("$.data.points[0].year").value(2025))
        .andExpect(jsonPath("$.data.points[0].month").value(1))
        .andExpect(jsonPath("$.data.points[0].unmatchedRate").value(45))
        .andExpect(jsonPath("$.data.points[0].requestCount").value(2779))
        .andExpect(jsonPath("$.data.points[0].matchedCount").value(1538))
        .andExpect(jsonPath("$.data.points[0].cityAvgRate").value(45))
        .andExpect(jsonPath("$.data.points[1].cityAvgRate").value((Object) null));
  }

  @Test
  @DisplayName("추이에 year가 없으면 400 INVALID_REQUEST를 내린다")
  void trendMissingYear() throws Exception {
    mockMvc
        .perform(get(TREND_URL))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
  }

  @Test
  @DisplayName("추이의 year가 숫자가 아니면 400 INVALID_REQUEST를 내린다")
  void trendInvalidYear() throws Exception {
    mockMvc
        .perform(get(TREND_URL).param("year", "abc"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
  }

  @Test
  @DisplayName("추이의 regionCode가 숫자 10자리가 아니면 400 INVALID_REQUEST를 내린다")
  void trendInvalidRegionCode() throws Exception {
    mockMvc
        .perform(get("/api/regions/abc/unmatched-trend").param("year", "2025"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
  }

  @Test
  @DisplayName("추이의 서비스 예외를 404 에러 코드로 내린다")
  void trendNotFound() throws Exception {
    given(regionMetricService.getUnmatchedTrend(any(), any()))
        .willThrow(new BusinessException(ErrorCode.PERIOD_NOT_FOUND));

    mockMvc
        .perform(get(TREND_URL).param("year", "2024"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("PERIOD_NOT_FOUND"));
  }

  @Test
  @DisplayName("없는 지역이면 404 REGION_NOT_FOUND를 내린다")
  void regionNotFound() throws Exception {
    given(regionService.getPreview(any(), any()))
        .willThrow(new BusinessException(ErrorCode.REGION_NOT_FOUND));

    mockMvc
        .perform(get(URL).param("year", "2025"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("REGION_NOT_FOUND"));
  }

  @Test
  @DisplayName("적재되지 않은 월이면 404 PERIOD_NOT_FOUND를 내린다")
  void periodNotFound() throws Exception {
    given(regionService.getPreview(any(), any()))
        .willThrow(new BusinessException(ErrorCode.PERIOD_NOT_FOUND));

    mockMvc
        .perform(get(URL).param("year", "2025").param("month", "5"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("PERIOD_NOT_FOUND"));
  }
}
