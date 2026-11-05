package com.example.yongyong.yongin.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

import com.example.yongyong.MariaDbTestSupport;
import com.example.yongyong.yongin.entity.MlPriority;
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
class DashboardRepositoryTest extends MariaDbTestSupport {

  @Autowired private MlPriorityRepository mlPriorityRepository;
  @Autowired private MlModelInfoRepository mlModelInfoRepository;

  @Test
  @DisplayName("연도와 월 키 목록을 조회한다 (연간 행의 NULL month는 0)")
  void periods() {
    // SELECT DISTINCT year, month FROM ml_priority WHERE unit='dong'; -- (2025, NULL), (2025, 2)
    assertThat(mlPriorityRepository.findPeriods("dong"))
        .extracting(
            MlPriorityRepository.PeriodKey::getYear, MlPriorityRepository.PeriodKey::getMonthKey)
        .containsExactly(tuple(2025, 0), tuple(2025, 2));
  }

  @Test
  @DisplayName("연간 행 38개를 조회하고 컬럼을 매핑한다")
  void annualRows() {
    // SELECT COUNT(*), SUM(is_vulnerable), AVG(score_total) FROM ml_priority
    //   WHERE year=2025 AND month IS NULL AND unit='dong'; -- 38, 9, 39.0658
    List<MlPriority> rows = rows(0);

    assertThat(rows).hasSize(38);
    assertThat(rows).extracting(MlPriority::getScoreTotal).doesNotContainNull();
    assertThat(rows).filteredOn(MlPriority::getIsVulnerable).hasSize(9);
    assertThat(rows.stream().mapToDouble(MlPriority::getScoreTotal).average().orElseThrow())
        .isCloseTo(39.0658, within(0.0001));

    // SELECT rank_total, region_cd, region_nm, gu, score_total FROM ml_priority
    //   WHERE year=2025 AND month IS NULL AND region_cd IN ('4146134000', '4146125000');
    MlPriority first = row(rows, "4146134000");
    assertThat(first.getRankTotal()).isEqualTo(1);
    assertThat(first.getRegionCd()).isEqualTo("4146134000");
    assertThat(first.getRegionNm()).isEqualTo("원삼면");
    assertThat(first.getGu()).isEqualTo("처인구");
    assertThat(first.getScoreTotal()).isEqualTo(68.1);
    assertThat(first.getIsVulnerable()).isTrue();
    MlPriority pogok = row(rows, "4146125000");
    assertThat(pogok.getRegionNm()).isEqualTo("포곡읍");
    assertThat(pogok.getRankTotal()).isEqualTo(7);
    assertThat(pogok.getScoreTotal()).isEqualTo(60.4);
  }

  @Test
  @DisplayName("2월 행 38개는 그 달 점수와 순위를 쓴다")
  void monthlyRows() {
    // 같은 집계, month=2; -- 38, 9, 38.3000
    List<MlPriority> rows = rows(2);

    assertThat(rows).hasSize(38);
    assertThat(rows).extracting(MlPriority::getScoreTotal).doesNotContainNull();
    assertThat(rows).filteredOn(MlPriority::getIsVulnerable).hasSize(9);
    assertThat(rows.stream().mapToDouble(MlPriority::getScoreTotal).average().orElseThrow())
        .isCloseTo(38.3, within(0.0001));
    // SELECT score_total FROM ml_priority WHERE year=2025 AND month=2 AND region_cd='4146134000';
    assertThat(row(rows, "4146134000").getScoreTotal()).isEqualTo(67.4);
  }

  @Test
  @DisplayName("지도에 쓰는 행은 기간마다 지역 코드가 겹치지 않고 표시 값이 모두 있다")
  void mapRows() {
    // SELECT COUNT(DISTINCT region_cd), SUM(gu IS NULL OR region_nm IS NULL OR rank_total IS NULL)
    //   FROM ml_priority WHERE year=2025 AND unit='dong' GROUP BY COALESCE(month, 0); -- 38, 0
    for (int monthKey : new int[] {0, 2}) {
      List<MlPriority> rows = rows(monthKey);

      assertThat(rows).extracting(MlPriority::getRegionCd).doesNotHaveDuplicates().hasSize(38);
      assertThat(rows).extracting(MlPriority::getRegionNm).doesNotContainNull();
      assertThat(rows).extracting(MlPriority::getGu).doesNotContainNull();
      assertThat(rows).extracting(MlPriority::getRankTotal).doesNotContainNull();
    }
  }

  @Test
  @DisplayName("적재되지 않은 5월은 행이 0개다")
  void monthNotLoaded() {
    // SELECT COUNT(*) FROM ml_priority WHERE year=2025 AND month=5 AND unit='dong'; -- 0
    assertThat(rows(5)).isEmpty();
  }

  @Test
  @DisplayName("연도의 최종 갱신 시각을 조회한다")
  void latestTrainedAt() {
    // SELECT MAX(trained_at) FROM ml_model_info WHERE year=2025; -- 2026-11-05 11:02
    assertThat(mlModelInfoRepository.findLatestTrainedAt(2025)).isEqualTo("2026-11-05 11:02");
  }

  private MlPriority row(List<MlPriority> rows, String regionCd) {
    return rows.stream()
        .filter(row -> row.getRegionCd().equals(regionCd))
        .findFirst()
        .orElseThrow();
  }

  private List<MlPriority> rows(int monthKey) {
    return mlPriorityRepository.findByYearAndMonthKeyAndUnit(2025, monthKey, "dong");
  }
}
