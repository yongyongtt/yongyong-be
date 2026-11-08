package com.example.yongyong.region.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

import com.example.yongyong.common.exception.BusinessException;
import com.example.yongyong.common.exception.ErrorCode;
import com.example.yongyong.common.query.PeriodQuery;
import com.example.yongyong.common.query.PeriodService;
import com.example.yongyong.common.response.PeriodResponse;
import com.example.yongyong.region.dto.RegionComparisonResponse;
import com.example.yongyong.region.dto.RegionComparisonResponse.Metric;
import com.example.yongyong.region.dto.RegionTrendResponse;
import com.example.yongyong.region.dto.RegionTrendResponse.Point;
import com.example.yongyong.yongin.entity.FeatCity;
import com.example.yongyong.yongin.entity.FeatCityMonth;
import com.example.yongyong.yongin.entity.FeatRegion;
import com.example.yongyong.yongin.entity.FeatRegionMonth;
import com.example.yongyong.yongin.repository.FeatCityMonthRepository;
import com.example.yongyong.yongin.repository.FeatCityRepository;
import com.example.yongyong.yongin.repository.FeatRegionMonthRepository;
import com.example.yongyong.yongin.repository.FeatRegionRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RegionMetricServiceTest {

  private static final String CODE = "4146125000";

  @Mock private FeatRegionRepository featRegionRepository;
  @Mock private FeatRegionMonthRepository featRegionMonthRepository;
  @Mock private FeatCityRepository featCityRepository;
  @Mock private FeatCityMonthRepository featCityMonthRepository;
  @Mock private PeriodService periodService;
  @InjectMocks private RegionMetricService regionMetricService;

  @Test
  @DisplayName("연간은 feat_region과 feat_city_month 집계로 지표 4개를 순서대로 내리고 period는 YEAR_ALL이다")
  void annual() {
    givenRegion();
    givenCity();
    givenCityMonths();
    given(featRegionMonthRepository.findByYearAndDongCd(2025, CODE))
        .willReturn(List.of(regionTrendRow(2, 0.3, 1.0, 1.0), regionTrendRow(1, 0.3, 1.0, 1.0)));

    RegionComparisonResponse response =
        regionMetricService.getComparison(CODE, new PeriodQuery(2025, null));

    assertThat(response.period())
        .isEqualTo(new PeriodResponse(2025, List.of(1, 2), PeriodResponse.Type.YEAR_ALL));
    assertThat(response.metrics())
        .containsExactly(
            new Metric("TAXI_MATCH_RATE", "택시 호출 성사율", "%", 53, 60),
            new Metric("BUS_STOP_COUNT", "버스 정류장 수", "개", 148, 82),
            new Metric("ELDERLY_RATIO", "고령인구 비율", "%", 20, 18),
            new Metric("FLOATING_POP", "생활인구 (시간 평균)", "명", 10403, 6000));
  }

  @Test
  @DisplayName("월을 고르면 택시와 생활인구만 그 달 값이고 정류장과 고령은 연간 값이다")
  void monthly() {
    givenRegion();
    givenRegionMonth(0.6344, 9557.2);
    givenCity();
    givenCityMonths();

    RegionComparisonResponse response =
        regionMetricService.getComparison(CODE, new PeriodQuery(2025, 2));

    assertThat(response.period())
        .isEqualTo(new PeriodResponse(2025, List.of(2), PeriodResponse.Type.MONTH));
    assertThat(response.metrics())
        .containsExactly(
            new Metric("TAXI_MATCH_RATE", "택시 호출 성사율", "%", 63, 64),
            new Metric("BUS_STOP_COUNT", "버스 정류장 수", "개", 148, 82),
            new Metric("ELDERLY_RATIO", "고령인구 비율", "%", 20, 18),
            new Metric("FLOATING_POP", "생활인구 (시간 평균)", "명", 9557, 6129));
  }

  @Test
  @DisplayName("모든 지표는 소수점 없이 정수로 반올림한다")
  void roundsToWhole() {
    givenRegion();
    givenCity();
    givenCityMonths();

    RegionComparisonResponse response =
        regionMetricService.getComparison(CODE, new PeriodQuery(2025, null));

    assertThat(response.metrics().get(1).cityAvg()).isEqualTo(82);
    assertThat(response.metrics().get(2).region()).isEqualTo(20);
    assertThat(response.metrics().get(2).cityAvg()).isEqualTo(18);
  }

  @Test
  @DisplayName("시 평균 행이 없으면 404가 아니라 cityAvg만 null로 내린다")
  void cityMissing() {
    givenRegion();
    given(featCityRepository.findByYearAndUnit(2025, "dong")).willReturn(Optional.empty());
    given(featCityMonthRepository.findByYear(2025)).willReturn(List.of());

    RegionComparisonResponse response =
        regionMetricService.getComparison(CODE, new PeriodQuery(2025, null));

    assertThat(response.metrics())
        .containsExactly(
            new Metric("TAXI_MATCH_RATE", "택시 호출 성사율", "%", 53, null),
            new Metric("BUS_STOP_COUNT", "버스 정류장 수", "개", 148, null),
            new Metric("ELDERLY_RATIO", "고령인구 비율", "%", 20, null),
            new Metric("FLOATING_POP", "생활인구 (시간 평균)", "명", 10403, null));
  }

  @Test
  @DisplayName("고른 달의 시 평균 행만 없으면 그 달 지표의 cityAvg만 null로 내린다")
  void cityMonthMissing() {
    givenRegion();
    givenRegionMonth(0.6344, 9557.2);
    givenCity();
    given(featCityMonthRepository.findByYear(2025))
        .willReturn(List.of(cityMonth(1, 100.0, 50.0, 0.5, 5871.0)));

    RegionComparisonResponse response =
        regionMetricService.getComparison(CODE, new PeriodQuery(2025, 2));

    assertThat(response.metrics())
        .containsExactly(
            new Metric("TAXI_MATCH_RATE", "택시 호출 성사율", "%", 63, null),
            new Metric("BUS_STOP_COUNT", "버스 정류장 수", "개", 148, 82),
            new Metric("ELDERLY_RATIO", "고령인구 비율", "%", 20, 18),
            new Metric("FLOATING_POP", "생활인구 (시간 평균)", "명", 9557, null));
  }

  @Test
  @DisplayName("공통 달에 없는 월이면 feat_region_month에 있어도 checkMonth의 예외를 그대로 던진다")
  void monthNotReady() {
    PeriodQuery august = new PeriodQuery(2025, 8);
    willThrow(new BusinessException(ErrorCode.SUITABILITY_NOT_READY))
        .given(periodService)
        .checkMonth(august);

    assertError(august, ErrorCode.SUITABILITY_NOT_READY);
  }

  @Test
  @DisplayName("연도 데이터가 없으면 PERIOD_NOT_FOUND를 던진다")
  void yearNotLoaded() {
    given(featRegionRepository.findByYearAndDongCd(2024, CODE)).willReturn(Optional.empty());
    given(featRegionRepository.existsByYear(2024)).willReturn(false);

    assertError(new PeriodQuery(2024, null), ErrorCode.PERIOD_NOT_FOUND);
  }

  @Test
  @DisplayName("연도는 있는데 지역이 없으면 REGION_NOT_FOUND를 던진다")
  void regionNotFound() {
    given(featRegionRepository.findByYearAndDongCd(2025, CODE)).willReturn(Optional.empty());
    given(featRegionRepository.existsByYear(2025)).willReturn(true);

    assertError(new PeriodQuery(2025, 2), ErrorCode.REGION_NOT_FOUND);
  }

  @Test
  @DisplayName("지역은 있는데 그 달 행이 없으면 PERIOD_NOT_FOUND를 던진다")
  void monthNotLoaded() {
    givenRegion();
    given(featRegionMonthRepository.findByYearAndMonthAndDongCd(2025, 2, CODE))
        .willReturn(Optional.empty());

    assertError(new PeriodQuery(2025, 2), ErrorCode.PERIOD_NOT_FOUND);
  }

  @Test
  @DisplayName("추이는 월 순서대로 비율과 건수를 정수로 반올림해 내리고 period는 YEAR_ALL과 그 달들이다")
  void trend() {
    given(featRegionMonthRepository.findByYearAndDongCd(2025, CODE))
        .willReturn(
            List.of(
                regionTrendRow(2, 0.3656, 2571.0, 1631.0),
                regionTrendRow(1, 0.4466, 2778.6, 1537.5)));
    given(featCityMonthRepository.findByYear(2025))
        .willReturn(List.of(cityTrendRow(1, 0.4473), cityTrendRow(2, 0.3596)));

    RegionTrendResponse response = regionMetricService.getUnmatchedTrend(CODE, 2025);

    assertThat(response.period())
        .isEqualTo(new PeriodResponse(2025, List.of(1, 2), PeriodResponse.Type.YEAR_ALL));
    assertThat(response.points())
        .containsExactly(
            new Point(2025, 1, 45, 2779, 1538, 45), new Point(2025, 2, 37, 2571, 1631, 36));
  }

  @Test
  @DisplayName("추이에서 그 달의 시 평균 행이 없으면 cityAvgRate만 null로 내린다")
  void trendCityMissing() {
    given(featRegionMonthRepository.findByYearAndDongCd(2025, CODE))
        .willReturn(List.of(regionTrendRow(1, 0.4466, 2779.0, 1538.0)));
    given(featCityMonthRepository.findByYear(2025)).willReturn(List.of());

    RegionTrendResponse response = regionMetricService.getUnmatchedTrend(CODE, 2025);

    assertThat(response.points()).containsExactly(new Point(2025, 1, 45, 2779, 1538, null));
  }

  @Test
  @DisplayName("추이에서 연도 데이터가 없으면 PERIOD_NOT_FOUND를 던진다")
  void trendYearNotLoaded() {
    given(featRegionMonthRepository.findByYearAndDongCd(2024, CODE)).willReturn(List.of());
    given(featRegionMonthRepository.existsByYear(2024)).willReturn(false);

    assertTrendError(2024, ErrorCode.PERIOD_NOT_FOUND);
  }

  @Test
  @DisplayName("추이에서 연도는 있는데 지역이 없으면 REGION_NOT_FOUND를 던진다")
  void trendRegionNotFound() {
    given(featRegionMonthRepository.findByYearAndDongCd(2025, CODE)).willReturn(List.of());
    given(featRegionMonthRepository.existsByYear(2025)).willReturn(true);

    assertTrendError(2025, ErrorCode.REGION_NOT_FOUND);
  }

  private void assertTrendError(int year, ErrorCode errorCode) {
    assertThatThrownBy(() -> regionMetricService.getUnmatchedTrend(CODE, year))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(errorCode);
  }

  private FeatRegionMonth regionTrendRow(int month, double rate, double req, double matched) {
    FeatRegionMonth row = new FeatRegionMonth();
    ReflectionTestUtils.setField(row, "year", 2025);
    ReflectionTestUtils.setField(row, "month", month);
    ReflectionTestUtils.setField(row, "taxiUnmatchedRate", rate);
    ReflectionTestUtils.setField(row, "taxiReq", req);
    ReflectionTestUtils.setField(row, "taxiMatched", matched);
    return row;
  }

  private FeatCityMonth cityTrendRow(int month, double rate) {
    FeatCityMonth row = new FeatCityMonth();
    ReflectionTestUtils.setField(row, "month", month);
    ReflectionTestUtils.setField(row, "taxiUnmatchedRate", rate);
    return row;
  }

  private void assertError(PeriodQuery query, ErrorCode errorCode) {
    assertThatThrownBy(() -> regionMetricService.getComparison(CODE, query))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(errorCode);
  }

  private void givenRegion() {
    FeatRegion region = new FeatRegion();
    ReflectionTestUtils.setField(region, "taxiMatchRate", 0.5337);
    ReflectionTestUtils.setField(region, "nStops", 148);
    ReflectionTestUtils.setField(region, "ratio65", 0.1984);
    ReflectionTestUtils.setField(region, "fpAvg", 10403.2);
    given(featRegionRepository.findByYearAndDongCd(2025, CODE)).willReturn(Optional.of(region));
  }

  private void givenRegionMonth(double matchRate, double fpAvg) {
    FeatRegionMonth month = new FeatRegionMonth();
    ReflectionTestUtils.setField(month, "taxiMatchRate", matchRate);
    ReflectionTestUtils.setField(month, "fpAvg", fpAvg);
    given(featRegionMonthRepository.findByYearAndMonthAndDongCd(2025, 2, CODE))
        .willReturn(Optional.of(month));
  }

  private void givenCity() {
    FeatCity city = new FeatCity();
    ReflectionTestUtils.setField(city, "nStops", 81.6);
    ReflectionTestUtils.setField(city, "elderlyRatio", 0.1774);
    given(featCityRepository.findByYearAndUnit(2025, "dong")).willReturn(Optional.of(city));
  }

  private void givenCityMonths() {
    given(featCityMonthRepository.findByYear(2025))
        .willReturn(
            List.of(
                cityMonth(1, 100.0, 50.0, 0.5, 5871.0), cityMonth(2, 100.0, 70.0, 0.64, 6129.0)));
  }

  private FeatCityMonth cityMonth(
      int month, double req, double matched, double matchRate, double fpAvg) {
    FeatCityMonth row = new FeatCityMonth();
    ReflectionTestUtils.setField(row, "month", month);
    ReflectionTestUtils.setField(row, "taxiReq", req);
    ReflectionTestUtils.setField(row, "taxiMatched", matched);
    ReflectionTestUtils.setField(row, "taxiMatchRate", matchRate);
    ReflectionTestUtils.setField(row, "fpAvg", fpAvg);
    return row;
  }
}
