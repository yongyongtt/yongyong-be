package com.example.yongyong.yongin.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.example.yongyong.MariaDbTestSupport;
import com.example.yongyong.yongin.entity.FeatCity;
import com.example.yongyong.yongin.entity.FeatCityMonth;
import com.example.yongyong.yongin.entity.FeatRegion;
import com.example.yongyong.yongin.entity.FeatRegionMonth;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

@Tag("db")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RegionMetricRepositoryTest extends MariaDbTestSupport {

  private static final String POGOK = "4146125000";

  @Autowired private FeatRegionRepository featRegionRepository;
  @Autowired private FeatRegionMonthRepository featRegionMonthRepository;
  @Autowired private FeatCityRepository featCityRepository;
  @Autowired private FeatCityMonthRepository featCityMonthRepository;

  @Test
  @DisplayName("feat_region을 키로 조회하고 컬럼을 매핑한다")
  void featRegion() {
    // SELECT taxi_match_rate, n_stops, ratio65, fp_avg FROM feat_region WHERE dong_cd='4146125000';
    FeatRegion region = featRegionRepository.findByYearAndDongCd(2025, POGOK).orElseThrow();

    assertThat(region.getTaxiMatchRate()).isEqualTo(0.5337);
    assertThat(region.getNStops()).isEqualTo(148);
    assertThat(region.getRatio65()).isEqualTo(0.1984);
    assertThat(region.getFpAvg()).isEqualTo(10403.2);
    assertThat(featRegionRepository.findByYearAndDongCd(2025, "0000000000")).isEmpty();
  }

  @Test
  @DisplayName("연도 데이터가 있는지 확인한다")
  void existsByYear() {
    // SELECT COUNT(*) FROM feat_region WHERE year=2025; -- 38 / year=2024; -- 0
    assertThat(featRegionRepository.existsByYear(2025)).isTrue();
    assertThat(featRegionRepository.existsByYear(2024)).isFalse();
  }

  @Test
  @DisplayName("feat_region_month를 키로 조회한다")
  void featRegionMonth() {
    // SELECT taxi_match_rate, fp_avg FROM feat_region_month WHERE dong_cd='4146125000'
    // AND month=2;
    FeatRegionMonth february =
        featRegionMonthRepository.findByYearAndMonthAndDongCd(2025, 2, POGOK).orElseThrow();
    assertThat(february.getTaxiMatchRate()).isEqualTo(0.6344);
    assertThat(february.getFpAvg()).isEqualTo(9557.2);
  }

  @Test
  @DisplayName("지역의 월별 행 12개를 조회하고 미배차율과 건수를 매핑한다")
  void featRegionMonthRows() {
    // SELECT month, taxi_unmatched_rate, taxi_req, taxi_matched FROM feat_region_month WHERE
    // dong_cd='4146125000' AND year=2025; -- 12행, 1월 0.4466, 2779, 1538
    List<FeatRegionMonth> rows = featRegionMonthRepository.findByYearAndDongCd(2025, POGOK);

    assertThat(rows).hasSize(12);
    FeatRegionMonth january =
        rows.stream().filter(row -> row.getMonth() == 1).findFirst().orElseThrow();
    assertThat(january.getTaxiUnmatchedRate()).isEqualTo(0.4466);
    assertThat(january.getTaxiReq()).isEqualTo(2779);
    assertThat(january.getTaxiMatched()).isEqualTo(1538);
    assertThat(featRegionMonthRepository.findByYearAndDongCd(2025, "0000000000")).isEmpty();
  }

  @Test
  @DisplayName("월별 표에 연도 데이터가 있는지 확인한다")
  void featRegionMonthExistsByYear() {
    // SELECT COUNT(*) FROM feat_region_month WHERE year=2025; -- 456 / year=2024; -- 0
    assertThat(featRegionMonthRepository.existsByYear(2025)).isTrue();
    assertThat(featRegionMonthRepository.existsByYear(2024)).isFalse();
  }

  @Test
  @DisplayName("feat_city_month의 미배차율을 매핑한다")
  void featCityMonthUnmatchedRate() {
    // SELECT taxi_unmatched_rate FROM feat_city_month WHERE year=2025 AND month=1; -- 0.4473
    assertThat(featCityMonthRepository.findByYear(2025))
        .filteredOn(row -> row.getMonth() == 1)
        .singleElement()
        .extracting(FeatCityMonth::getTaxiUnmatchedRate)
        .isEqualTo(0.4473);
  }

  @Test
  @DisplayName("feat_city를 연도와 unit으로 조회한다")
  void featCity() {
    // SELECT n_stops, elderly_ratio FROM feat_city WHERE year=2025 AND unit='dong'; -- 81.6, 0.1774
    FeatCity city = featCityRepository.findByYearAndUnit(2025, "dong").orElseThrow();

    assertThat(city.getNStops()).isEqualTo(81.6);
    assertThat(city.getElderlyRatio()).isEqualTo(0.1774);
  }

  @Test
  @DisplayName("feat_city_month 12개월을 읽어 시 평균 연간 값을 구할 수 있다")
  void featCityMonth() {
    // SELECT ROUND(SUM(taxi_matched)/SUM(taxi_req)*100,1), ROUND(AVG(fp_avg)) FROM feat_city_month
    // WHERE year=2025; -- 53.8, 5938
    List<FeatCityMonth> rows = featCityMonthRepository.findByYear(2025);

    assertThat(rows).hasSize(12);
    double matched = rows.stream().mapToDouble(FeatCityMonth::getTaxiMatched).sum();
    double requested = rows.stream().mapToDouble(FeatCityMonth::getTaxiReq).sum();
    double fpAvg = rows.stream().mapToDouble(FeatCityMonth::getFpAvg).average().orElseThrow();
    assertThat(matched / requested * 100).isCloseTo(53.8, within(0.05));
    assertThat(fpAvg).isCloseTo(5938, within(0.5));
  }
}
