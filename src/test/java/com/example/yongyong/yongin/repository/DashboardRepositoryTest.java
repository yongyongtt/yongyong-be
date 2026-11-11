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
  @DisplayName("연간(DB month NULL) 행은 month 0으로 지역당 한 행씩 38개 읽고 컬럼을 매핑한다 (null 엔티티 없음)")
  void yearRows() {
    // SELECT COUNT(*) FROM ml_priority WHERE year=2025 AND unit='dong' AND month IS NULL; -- 38
    // SELECT region_nm, gu, score_total FROM ml_priority WHERE year=2025 AND unit='dong'
    //   AND month IS NULL AND region_cd='4146125000'; -- 포곡읍, 처인구, 60.3 (월 평균은 58.96)
    List<MlPriority> rows = mlPriorityRepository.findByYearAndMonthAndUnit(2025, 0, "dong");

    assertThat(rows).hasSize(38).doesNotContainNull();
    assertThat(rows).extracting(MlPriority::getRegionCd).doesNotHaveDuplicates();
    MlPriority pogok =
        rows.stream().filter(row -> row.getRegionCd().equals(POGOK)).findFirst().orElseThrow();
    assertThat(pogok.getRegionNm()).isEqualTo("포곡읍");
    assertThat(pogok.getGu()).isEqualTo("처인구");
    assertThat(pogok.getScoreTotal()).isCloseTo(60.3, within(0.0001));
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
  @DisplayName("최종 갱신 시각은 연간이면 month 0(DB NULL) 행, 월이면 그 달 최대값이고 행이 없으면 null이다")
  void latestTrainedAt() {
    // SELECT MAX(trained_at) FROM ml_model_info WHERE year=2025 AND month IS NULL; -- 2026-11-10
    // 17:00
    // ... AND month=2; -- 2026-11-07 14:49 / AND month=12; -- 2026-11-10 10:49 / year=2024; -- NULL
    assertThat(mlModelInfoRepository.findLatestTrainedAt(2025, 0)).isEqualTo("2026-11-10 17:00");
    assertThat(mlModelInfoRepository.findLatestTrainedAt(2025, 2)).isEqualTo("2026-11-07 14:49");
    assertThat(mlModelInfoRepository.findLatestTrainedAt(2025, 12)).isEqualTo("2026-11-10 10:49");
    assertThat(mlModelInfoRepository.findLatestTrainedAt(2024, 0)).isNull();
    assertThat(mlModelInfoRepository.findLatestTrainedAt(2024, 1)).isNull();
  }
}
