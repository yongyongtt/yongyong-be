package com.example.yongyong.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.example.yongyong.common.exception.BusinessException;
import com.example.yongyong.common.exception.ErrorCode;
import com.example.yongyong.common.query.PeriodQuery;
import com.example.yongyong.dashboard.dto.DashboardMapResponse;
import com.example.yongyong.dashboard.dto.DashboardResponse;
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
  @InjectMocks private DashboardService dashboardService;

  @Test
  @DisplayName("월이 없으면 연간 행을 dong으로 조회해 개수와 평균을 계산한다")
  void annual() {
    given(rows(2025, 0))
        .willReturn(
            List.of(
                row(1, "4146134000", "원삼면", 68.1, true),
                row(2, "4146125600", "이동읍", 40.0, false),
                row(3, "4146125300", "모현읍", 9.1, false)));
    givenPeriods();
    given(mlModelInfoRepository.findLatestTrainedAt(2025)).willReturn("2026-11-05 11:02");

    DashboardResponse response = dashboardService.getDashboard(new PeriodQuery(2025, null));

    assertThat(response.period().year()).isEqualTo(2025);
    assertThat(response.period().month()).isNull();
    assertThat(response.period().updatedAt()).isEqualTo("2026-11-05 11:02");
    assertThat(response.summary().regionCount()).isEqualTo(3);
    assertThat(response.summary().vulnerableCount()).isEqualTo(1);
    // (68.1 + 40.0 + 9.1) / 3 = 39.0666...
    assertThat(response.summary().avgScore()).isEqualTo(39.1);
    assertThat(response.top10().getFirst())
        .isEqualTo(new DashboardResponse.TopRegion(1, "4146134000", "원삼면", "처인구", 68));
  }

  @Test
  @DisplayName("월을 고르면 그 달 점수와 순위를 내린다")
  void monthly() {
    given(rows(2025, 2)).willReturn(List.of(row(1, "4146134000", "원삼면", 67.4, true)));
    givenPeriods();

    DashboardResponse response = dashboardService.getDashboard(new PeriodQuery(2025, 2));

    assertThat(response.period().month()).isEqualTo(2);
    assertThat(response.summary().avgScore()).isEqualTo(67.4);
    assertThat(response.top10().getFirst().score()).isEqualTo(67);
  }

  @Test
  @DisplayName("기간 목록은 전체 연도와 고른 연도의 월만 내리고 연간(0)은 null로 맨 앞에 둔다")
  void availablePeriods() {
    given(rows(2025, 0)).willReturn(List.of(row(1, "4146134000", "원삼면", 68.1, true)));
    givenPeriods();

    DashboardResponse response = dashboardService.getDashboard(new PeriodQuery(2025, null));

    assertThat(response.period().availableYears()).containsExactly(2024, 2025);
    assertThat(response.period().availableMonths()).containsExactly(null, 2);
  }

  @Test
  @DisplayName("TOP 10은 rank_total 순, 공동 순위는 region_cd 순으로 정렬한다")
  void top10Order() {
    given(rows(2025, 0))
        .willReturn(
            List.of(
                row(2, "A", "2위", 60.0, false),
                row(1, "C", "공동 1위, 코드가 뒤", 60.0, false),
                row(1, "B", "공동 1위, 코드가 앞", 60.0, false),
                row(3, "D", "3위지만 점수가 가장 높음", 70.0, true)));
    givenPeriods();

    DashboardResponse response = dashboardService.getDashboard(new PeriodQuery(2025, null));

    assertThat(response.top10())
        .extracting(DashboardResponse.TopRegion::regionCode)
        .containsExactly("B", "C", "A", "D");
  }

  @Test
  @DisplayName("TOP 10 점수는 정수로 반올림하고 평균은 소수 첫째 자리를 유지한다")
  void top10ScoreRounded() {
    given(rows(2025, 0)).willReturn(List.of(row(1, "4146125300", "모현읍", 64.7, true)));
    givenPeriods();

    DashboardResponse response = dashboardService.getDashboard(new PeriodQuery(2025, null));

    assertThat(response.top10().getFirst().score()).isEqualTo(65);
    assertThat(response.summary().avgScore()).isEqualTo(64.7);
  }

  @Test
  @DisplayName("TOP 10은 10개만 내리지만 개수와 평균은 전체 행으로 계산한다")
  void top10Limit() {
    List<MlPriority> rows = new ArrayList<>();
    IntStream.rangeClosed(1, 11)
        .forEach(rank -> rows.add(row(rank, "R" + rank, "지역" + rank, 100.0 - rank, false)));
    given(rows(2025, 0)).willReturn(rows);
    givenPeriods();

    DashboardResponse response = dashboardService.getDashboard(new PeriodQuery(2025, null));

    assertThat(response.top10()).hasSize(10);
    assertThat(response.summary().regionCount()).isEqualTo(11);
    assertThat(response.summary().avgScore()).isEqualTo(94.0);
  }

  @Test
  @DisplayName("ml_model_info에 그 연도 행이 없으면 updatedAt을 null로 내린다")
  void updatedAtMissing() {
    given(rows(2025, 0)).willReturn(List.of(row(1, "4146134000", "원삼면", 68.1, true)));
    givenPeriods();
    given(mlModelInfoRepository.findLatestTrainedAt(2025)).willReturn(null);

    DashboardResponse response = dashboardService.getDashboard(new PeriodQuery(2025, null));

    assertThat(response.period().updatedAt()).isNull();
  }

  @Test
  @DisplayName("적재되지 않은 월이면 PERIOD_NOT_FOUND를 던진다")
  void monthNotLoaded() {
    given(rows(2025, 5)).willReturn(List.of());

    assertPeriodNotFound(new PeriodQuery(2025, 5));
  }

  @Test
  @DisplayName("적재되지 않은 연도면 PERIOD_NOT_FOUND를 던진다")
  void yearNotLoaded() {
    given(rows(2024, 0)).willReturn(List.of());

    assertPeriodNotFound(new PeriodQuery(2024, null));
  }

  @Test
  @DisplayName("지도는 연간 행을 dong으로 조회해 전체 지역을 DB 점수 그대로 내린다")
  void mapAnnual() {
    List<MlPriority> rows = new ArrayList<>();
    IntStream.rangeClosed(1, 11)
        .forEach(rank -> rows.add(row(rank, "R" + rank, "지역" + rank, 68.1, false)));
    given(rows(2025, 0)).willReturn(rows);

    DashboardMapResponse response = dashboardService.getMap(new PeriodQuery(2025, null));

    assertThat(response.regions()).hasSize(11);
    assertThat(response.regions().getFirst())
        .isEqualTo(new DashboardMapResponse.MapRegion("R1", "지역1", "처인구", 68.1, 1));
  }

  @Test
  @DisplayName("지도에서 월을 고르면 그 달 행을 조회한다")
  void mapMonthly() {
    given(rows(2025, 2)).willReturn(List.of(row(1, "4146134000", "원삼면", 67.4, true)));

    DashboardMapResponse response = dashboardService.getMap(new PeriodQuery(2025, 2));

    assertThat(response.regions().getFirst().score()).isEqualTo(67.4);
  }

  @Test
  @DisplayName("지도는 rank_total 순, 공동 순위는 region_cd 순으로 정렬한다")
  void mapOrder() {
    given(rows(2025, 0))
        .willReturn(
            List.of(
                row(2, "A", "2위", 60.0, false),
                row(1, "C", "공동 1위, 코드가 뒤", 60.0, false),
                row(1, "B", "공동 1위, 코드가 앞", 60.0, false)));

    DashboardMapResponse response = dashboardService.getMap(new PeriodQuery(2025, null));

    assertThat(response.regions())
        .extracting(DashboardMapResponse.MapRegion::regionCode)
        .containsExactly("B", "C", "A");
  }

  @Test
  @DisplayName("지도에서 적재되지 않은 월이면 PERIOD_NOT_FOUND를 던진다")
  void mapMonthNotLoaded() {
    given(rows(2025, 5)).willReturn(List.of());

    assertMapPeriodNotFound(new PeriodQuery(2025, 5));
  }

  @Test
  @DisplayName("지도에서 적재되지 않은 연도면 PERIOD_NOT_FOUND를 던진다")
  void mapYearNotLoaded() {
    given(rows(2024, 0)).willReturn(List.of());

    assertMapPeriodNotFound(new PeriodQuery(2024, null));
  }

  private void assertMapPeriodNotFound(PeriodQuery query) {
    assertThatThrownBy(() -> dashboardService.getMap(query))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.PERIOD_NOT_FOUND);
  }

  private List<MlPriority> rows(int year, int monthKey) {
    return mlPriorityRepository.findByYearAndMonthKeyAndUnit(year, monthKey, "dong");
  }

  private void givenPeriods() {
    given(mlPriorityRepository.findPeriods("dong"))
        .willReturn(List.of(period(2024, 0), period(2025, 0), period(2025, 2)));
  }

  private void assertPeriodNotFound(PeriodQuery query) {
    assertThatThrownBy(() -> dashboardService.getDashboard(query))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.PERIOD_NOT_FOUND);
  }

  private MlPriorityRepository.PeriodKey period(int year, int monthKey) {
    return new MlPriorityRepository.PeriodKey() {
      @Override
      public Integer getYear() {
        return year;
      }

      @Override
      public Integer getMonthKey() {
        return monthKey;
      }
    };
  }

  private MlPriority row(int rank, String code, String name, double score, boolean vulnerable) {
    MlPriority row = new MlPriority();
    ReflectionTestUtils.setField(row, "rankTotal", rank);
    ReflectionTestUtils.setField(row, "regionCd", code);
    ReflectionTestUtils.setField(row, "regionNm", name);
    ReflectionTestUtils.setField(row, "gu", "처인구");
    ReflectionTestUtils.setField(row, "scoreTotal", score);
    ReflectionTestUtils.setField(row, "isVulnerable", vulnerable);
    return row;
  }
}
