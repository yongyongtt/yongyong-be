package com.example.yongyong.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

import com.example.yongyong.common.exception.BusinessException;
import com.example.yongyong.common.exception.ErrorCode;
import com.example.yongyong.common.query.PeriodQuery;
import com.example.yongyong.common.query.PeriodService;
import com.example.yongyong.common.response.PeriodResponse;
import com.example.yongyong.dashboard.dto.DashboardMapResponse;
import com.example.yongyong.dashboard.dto.DashboardResponse;
import com.example.yongyong.dashboard.service.DashboardService.RegionScore;
import com.example.yongyong.dashboard.service.DashboardService.Scores;
import com.example.yongyong.yongin.entity.MlPriority;
import com.example.yongyong.yongin.repository.MlModelInfoRepository;
import com.example.yongyong.yongin.repository.MlPriorityRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

  @Mock private MlPriorityRepository mlPriorityRepository;
  @Mock private MlModelInfoRepository mlModelInfoRepository;
  @Mock private PeriodService periodService;
  @InjectMocks private DashboardService dashboardService;

  @Test
  @DisplayName("연간 점수는 연간(NULL) 행 점수를 정수로 반올림하고, 동점은 같은 순위·다음 순위는 건너뛴다. months는 공통 달")
  void annualScores() {
    given(mlPriorityRepository.findByYearAndMonthAndUnit(2025, 0, "dong"))
        .willReturn(
            List.of(
                yearRow("B", 65.6), // 66, A와 동점이지만 반올림 전 점수가 낮아 뒤
                yearRow("A", 66.4),
                yearRow("C", 62.5))); // 63, 3위
    given(periodService.availableMonths(2025)).willReturn(List.of(1, 2, 3));

    Scores scores = dashboardService.scores(new PeriodQuery(2025, null));

    assertThat(scores.months()).containsExactly(1, 2, 3);
    assertThat(scores.regions())
        .extracting(RegionScore::regionCode, RegionScore::score, RegionScore::rank)
        .containsExactly(tuple("A", 66, 1), tuple("B", 66, 1), tuple("C", 63, 3));
    assertThat(scores.regions().get(1).rawScore()).isEqualTo(65.6, within());
  }

  @Test
  @DisplayName("월을 고르면 그 달 행만 쓰고 순위도 정수 점수로 다시 매긴다, 같은 순위면 반올림 전 점수가 높은 지역이 먼저")
  void monthlyScores() {
    // 7월 동부동 60.2(DB 7위), 포곡읍 60.1(DB 8위) → 둘 다 60점 공동 1위
    given(mlPriorityRepository.findByYearAndMonthAndUnit(2025, 7, "dong"))
        .willReturn(List.of(row(7, "4146125000", 60.1), row(7, "4146154000", 60.2)));

    Scores scores = dashboardService.scores(new PeriodQuery(2025, 7));

    assertThat(scores.months()).containsExactly(7);
    assertThat(scores.regions())
        .extracting(RegionScore::regionCode, RegionScore::score, RegionScore::rank)
        .containsExactly(tuple("4146154000", 60, 1), tuple("4146125000", 60, 1));
  }

  @Test
  @DisplayName("같은 순위에서 반올림 전 점수까지 같으면 regionCode 순이다")
  void tieOrderByCode() {
    given(mlPriorityRepository.findByYearAndMonthAndUnit(2025, 7, "dong"))
        .willReturn(List.of(row(7, "B", 60.0), row(7, "A", 60.0), row(7, "C", 60.4)));

    Scores scores = dashboardService.scores(new PeriodQuery(2025, 7));

    assertThat(scores.regions())
        .extracting(RegionScore::regionCode, RegionScore::rank)
        .containsExactly(tuple("C", 1), tuple("A", 1), tuple("B", 1));
  }

  @Test
  @DisplayName("연간 행이 없으면 PERIOD_NOT_FOUND를 던진다")
  void scoresEmpty() {
    given(mlPriorityRepository.findByYearAndMonthAndUnit(2024, 0, "dong")).willReturn(List.of());

    assertThatThrownBy(() -> dashboardService.scores(new PeriodQuery(2024, null)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.PERIOD_NOT_FOUND);
  }

  @Test
  @DisplayName("연간 요약: 연간 행 점수로 취약지역(정수 50점 이상)과 평균을 내고, 갱신 시각도 연간 행, period는 YEAR_ALL과 공통 달")
  void annualDashboard() {
    given(mlPriorityRepository.findByYearAndMonthAndUnit(2025, 0, "dong"))
        .willReturn(
            List.of(
                yearRow("A", 69.0),
                yearRow("B", 49.6), // 화면 50점이라 취약지역에 센다
                yearRow("C", 10.5)));
    given(periodService.availableMonths(2025)).willReturn(List.of(1, 2));
    given(mlModelInfoRepository.findLatestTrainedAt(2025, 0)).willReturn("2026-11-10 17:00");

    DashboardResponse response = dashboardService.getDashboard(new PeriodQuery(2025, null));

    assertThat(response.period())
        .isEqualTo(new PeriodResponse(2025, List.of(1, 2), PeriodResponse.Type.YEAR_ALL));
    assertThat(response.updatedAt()).isEqualTo("2026-11-10 17:00");
    assertThat(response.summary().regionCount()).isEqualTo(3);
    assertThat(response.summary().vulnerableCount()).isEqualTo(2);
    // (69 + 49.6 + 10.5) / 3 = 43.0333
    assertThat(response.summary().avgScore()).isEqualTo(43.0);
    assertThat(response.top10())
        .containsExactly(
            new DashboardResponse.TopRegion(1, "A", "지역A", "처인구", 69),
            new DashboardResponse.TopRegion(2, "B", "지역B", "처인구", 50),
            new DashboardResponse.TopRegion(3, "C", "지역C", "처인구", 11));
  }

  @Test
  @DisplayName("월을 고르면 period는 MONTH와 그 달, 최종 갱신 시각도 그 달 값이다")
  void monthlyDashboard() {
    given(mlPriorityRepository.findByYearAndMonthAndUnit(2025, 7, "dong"))
        .willReturn(List.of(row(7, "A", 67.4)));
    given(mlModelInfoRepository.findLatestTrainedAt(2025, 7)).willReturn("2026-11-08 20:55");

    DashboardResponse response = dashboardService.getDashboard(new PeriodQuery(2025, 7));

    assertThat(response.period())
        .isEqualTo(new PeriodResponse(2025, List.of(7), PeriodResponse.Type.MONTH));
    assertThat(response.updatedAt()).isEqualTo("2026-11-08 20:55");
    assertThat(response.summary().avgScore()).isEqualTo(67.4);
  }

  @Test
  @DisplayName("TOP 10은 10개만 내리지만 개수와 평균은 전체 지역으로 계산한다")
  void top10Limit() {
    List<MlPriority> rows = new ArrayList<>();
    IntStream.rangeClosed(1, 11).forEach(i -> rows.add(yearRow("R" + (char) ('a' + i), 100.0 - i)));
    given(mlPriorityRepository.findByYearAndMonthAndUnit(2025, 0, "dong")).willReturn(rows);
    given(periodService.availableMonths(2025)).willReturn(List.of(1));

    DashboardResponse response = dashboardService.getDashboard(new PeriodQuery(2025, null));

    assertThat(response.top10()).hasSize(10);
    assertThat(response.summary().regionCount()).isEqualTo(11);
    assertThat(response.summary().avgScore()).isEqualTo(94.0);
  }

  @Test
  @DisplayName("요약에서 공통 달에 없는 월이면 checkMonth의 예외를 그대로 던진다")
  void monthNotReady() {
    PeriodQuery query = new PeriodQuery(2025, 8);
    willThrow(new BusinessException(ErrorCode.SUITABILITY_NOT_READY))
        .given(periodService)
        .checkMonth(query);

    assertThatThrownBy(() -> dashboardService.getDashboard(query))
        .extracting("errorCode")
        .isEqualTo(ErrorCode.SUITABILITY_NOT_READY);
    assertThatThrownBy(() -> dashboardService.getMap(query))
        .extracting("errorCode")
        .isEqualTo(ErrorCode.SUITABILITY_NOT_READY);
  }

  @Test
  @DisplayName("지도는 전체 지역을 정수 점수와 재계산 순위로 내리고 period를 함께 내린다")
  void map() {
    given(mlPriorityRepository.findByYearAndMonthAndUnit(2025, 0, "dong"))
        .willReturn(List.of(yearRow("B", 60.0), yearRow("A", 68.1)));
    given(periodService.availableMonths(2025)).willReturn(List.of(1, 2));

    DashboardMapResponse response = dashboardService.getMap(new PeriodQuery(2025, null));

    assertThat(response.regions())
        .containsExactly(
            new DashboardMapResponse.MapRegion("A", "지역A", "처인구", 68, 1),
            new DashboardMapResponse.MapRegion("B", "지역B", "처인구", 60, 2));
    assertThat(response.period())
        .isEqualTo(new PeriodResponse(2025, List.of(1, 2), PeriodResponse.Type.YEAR_ALL));
  }

  private static org.assertj.core.data.Offset<Double> within() {
    return org.assertj.core.data.Offset.offset(0.0001);
  }

  private MlPriority row(int month, String code, double score) {
    MlPriority row = new MlPriority();
    ReflectionTestUtils.setField(row, "year", 2025);
    ReflectionTestUtils.setField(row, "month", month);
    ReflectionTestUtils.setField(row, "regionCd", code);
    ReflectionTestUtils.setField(row, "regionNm", "지역" + code);
    ReflectionTestUtils.setField(row, "gu", "처인구");
    ReflectionTestUtils.setField(row, "scoreTotal", score);
    return row;
  }

  // 연간 행은 month 0 (DB month NULL)
  private MlPriority yearRow(String code, double score) {
    return row(0, code, score);
  }
}
