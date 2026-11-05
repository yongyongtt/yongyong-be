package com.example.yongyong.yongin.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.yongyong.MariaDbTestSupport;
import com.example.yongyong.common.converter.OptionJson;
import com.example.yongyong.yongin.entity.MlExplanation;
import com.example.yongyong.yongin.entity.MlPriority;
import com.example.yongyong.yongin.entity.MlRegion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

@Tag("db")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RegionRepositoryTest extends MariaDbTestSupport {

  private static final String POGOK = "4146125000";
  private static final String KEEP_REGION = "4146151000";

  @Autowired private MlPriorityRepository mlPriorityRepository;
  @Autowired private MlRegionRepository mlRegionRepository;
  @Autowired private MlExplanationRepository mlExplanationRepository;

  @Test
  @DisplayName("키로 연간과 월 점수를 각각 1건 조회한다")
  void priorityByKey() {
    // SELECT score_total, rank_total FROM ml_priority WHERE region_cd='4146125000'; -- 연간 60.4, 7 /
    // 2월 60.4, 6
    MlPriority annual = priority(0);
    assertThat(annual.getRegionNm()).isEqualTo("포곡읍");
    assertThat(annual.getGu()).isEqualTo("처인구");
    assertThat(annual.getScoreTotal()).isEqualTo(60.4);
    assertThat(annual.getRankTotal()).isEqualTo(7);
    assertThat(priority(2).getRankTotal()).isEqualTo(6);
  }

  @Test
  @DisplayName("적재되지 않은 월이나 없는 지역은 조회되지 않는다")
  void priorityMissing() {
    assertThat(mlPriorityRepository.findByYearAndMonthKeyAndUnitAndRegionCd(2025, 5, "dong", POGOK))
        .isEmpty();
    assertThat(
            mlPriorityRepository.findByYearAndMonthKeyAndUnitAndRegionCd(
                2025, 0, "dong", "0000000000"))
        .isEmpty();
  }

  @Test
  @DisplayName("지역의 사각지대 유형을 조회한다")
  void regionType() {
    // SELECT type_nm, type_desc FROM ml_region WHERE region_cd='4146125000';
    MlRegion region =
        mlRegionRepository.findByYearAndUnitAndRegionCd(2025, "dong", POGOK).orElseThrow();

    assertThat(region.getTypeNm()).isEqualTo("정류장 접근 취약형");
    assertThat(region.getTypeDesc()).isEqualTo("버스는 다니지만 정류장까지 멀어 걷기 힘든 지역이에요");
  }

  @Test
  @DisplayName("options JSON을 목록으로 변환하고 Boolean과 요약을 매핑한다")
  void explanation() {
    // SELECT is_keep, summary, JSON_EXTRACT(options,'$[*].mode') FROM ml_explanation WHERE
    // region_cd='4146125000';
    MlExplanation explanation =
        mlExplanationRepository.findByYearAndUnitAndRegionCd(2025, "dong", POGOK).orElseThrow();

    assertThat(explanation.getIsKeep()).isFalse();
    assertThat(explanation.getSummary()).isEqualTo("정류장까지 멀어 걷기 힘들어 똑버스 도입 검토");
    assertThat(explanation.getOptions())
        .containsExactly(
            new OptionJson("DRT", "똑버스", "추천"),
            new OptionJson("BUS", "노선버스", "제외"),
            new OptionJson("HAPPY_TAXI", "행복택시", "제외"));
  }

  @Test
  @DisplayName("현행 유지 지역은 is_keep이 true이고 options 맨 앞이 KEEP이다")
  void keepExplanation() {
    // SELECT is_keep, JSON_EXTRACT(options,'$[0].mode') FROM ml_explanation WHERE
    // region_cd='4146151000'; -- 1, KEEP
    MlExplanation explanation =
        mlExplanationRepository
            .findByYearAndUnitAndRegionCd(2025, "dong", KEEP_REGION)
            .orElseThrow();

    assertThat(explanation.getIsKeep()).isTrue();
    assertThat(explanation.getOptions().getFirst())
        .isEqualTo(new OptionJson("KEEP", "현행 유지", "추천"));
  }

  private MlPriority priority(int monthKey) {
    return mlPriorityRepository
        .findByYearAndMonthKeyAndUnitAndRegionCd(2025, monthKey, "dong", POGOK)
        .orElseThrow();
  }
}
