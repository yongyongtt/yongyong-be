package com.example.yongyong.yongin.repository;

import static org.assertj.core.api.Assertions.assertThat;
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

  private static final String POGOK = "4146125000";

  @Autowired private MlPriorityRepository mlPriorityRepository;
  @Autowired private MlModelInfoRepository mlModelInfoRepository;

  @Test
  @DisplayName("월 목록에 연간 NULL 행이 섞이지 않는다")
  void yearsAndMonths() {
    // SELECT DISTINCT month FROM ml_priority WHERE year=2025 AND unit='dong'; -- NULL, 1~12
    assertThat(mlPriorityRepository.findMonths(2025, "dong"))
        .doesNotContainNull()
        .containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
  }

  @Test
  @DisplayName("연도 전체 조회는 월별 행 456개만 읽고 NULL 행은 읽지 않는다")
  void yearRows() {
    // SELECT COUNT(*) FROM ml_priority WHERE year=2025 AND unit='dong' AND month IS NOT NULL; --
    // 456
    // SELECT AVG(score_total) FROM ml_priority WHERE year=2025 AND unit='dong'
    //   AND month IS NOT NULL AND region_cd='4146125000'; -- 58.9583 (NULL 행은 60.3)
    List<MlPriority> rows = mlPriorityRepository.findByYearAndUnit(2025, "dong");

    assertThat(rows).hasSize(456);
    assertThat(rows).extracting(MlPriority::getMonth).doesNotContainNull();
    assertThat(
            rows.stream()
                .filter(row -> row.getRegionCd().equals(POGOK))
                .mapToDouble(MlPriority::getScoreTotal)
                .average()
                .orElseThrow())
        .isCloseTo(58.9583, within(0.0001));
  }

  @Test
  @DisplayName("월을 고르면 지역당 한 행씩 38개를 읽고 컬럼을 매핑한다")
  void monthRows() {
    // SELECT region_nm, gu, score_total FROM ml_priority WHERE year=2025 AND month=7
    //   AND unit='dong' AND region_cd='4146125000'; -- 포곡읍, 처인구, 60.1
    List<MlPriority> rows = mlPriorityRepository.findByYearAndMonthAndUnit(2025, 7, "dong");

    assertThat(rows).hasSize(38);
    assertThat(rows).extracting(MlPriority::getRegionCd).doesNotHaveDuplicates();
    MlPriority pogok =
        rows.stream().filter(row -> row.getRegionCd().equals(POGOK)).findFirst().orElseThrow();
    assertThat(pogok.getRegionNm()).isEqualTo("포곡읍");
    assertThat(pogok.getGu()).isEqualTo("처인구");
    assertThat(pogok.getScoreTotal()).isEqualTo(60.1);
  }

  @Test
  @DisplayName("최종 갱신 시각은 월이 없으면 연도 전체, 월이 있으면 그 달 최대값이고 없는 달은 null이다")
  void latestTrainedAt() {
    // SELECT MAX(trained_at) FROM ml_model_info WHERE year=2025; -- 2026-11-08 20:55
    // ... AND month=2; -- 2026-11-07 14:49 / AND month=8; -- NULL
    assertThat(mlModelInfoRepository.findLatestTrainedAt(2025, null)).isEqualTo("2026-11-08 20:55");
    assertThat(mlModelInfoRepository.findLatestTrainedAt(2025, 2)).isEqualTo("2026-11-07 14:49");
    assertThat(mlModelInfoRepository.findLatestTrainedAt(2025, 8)).isNull();
  }
}
