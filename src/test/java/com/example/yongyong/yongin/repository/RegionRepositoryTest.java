package com.example.yongyong.yongin.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.example.yongyong.MariaDbTestSupport;
import com.example.yongyong.common.converter.OptionJson;
import com.example.yongyong.yongin.entity.MlExplanation;
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

  @Autowired private MlRegionRepository mlRegionRepository;
  @Autowired private MlExplanationRepository mlExplanationRepository;

  @Test
  @DisplayName("ml_region과 ml_explanation의 월 목록은 1~7월이다")
  void months() {
    // SELECT DISTINCT month FROM ml_region / ml_explanation WHERE year=2025 AND unit='dong'; -- 1~7
    assertThat(mlRegionRepository.findMonths(2025, "dong"))
        .containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6, 7);
    assertThat(mlExplanationRepository.findMonths(2025, "dong"))
        .containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6, 7);
  }

  @Test
  @DisplayName("ml_region을 월 키로 1건 조회하고 칩 JSON과 신뢰도를 매핑한다, 없는 달은 비어 있다")
  void regionByMonth() {
    // SELECT type_nm, type_desc, type_chips, low_reliability, reliability_note FROM ml_region
    //   WHERE year=2025 AND month=7 AND unit='dong' AND region_cd='4146125000';
    MlRegion region =
        mlRegionRepository
            .findByYearAndMonthAndUnitAndRegionCd(2025, 7, "dong", POGOK)
            .orElseThrow();

    assertThat(region.getMonth()).isEqualTo(7);
    assertThat(region.getTypeNm()).isEqualTo("정류장 접근 취약형");
    assertThat(region.getTypeDesc()).isEqualTo("버스는 다니지만 정류장까지 멀어 걷기 힘든 지역이에요");
    assertThat(region.getTypeChips())
        .containsExactly("정류장까지 도보 김 · 12분 (평균 7분)", "정류장 500m 안 면적 좁음 · 54% (평균 77%)");
    assertThat(region.getLowReliability()).isFalse();
    assertThat(region.getReliabilityNote()).isNull();
    assertThat(mlRegionRepository.findByYearAndMonthAndUnitAndRegionCd(2025, 8, "dong", POGOK))
        .isEmpty();
  }

  @Test
  @DisplayName("같은 달 같은 유형 지역 6곳을 중복 없이 조회한다")
  void sameType() {
    // SELECT region_cd FROM ml_region WHERE year=2025 AND month=7 AND unit='dong'
    //   AND type_nm='정류장 접근 취약형'; -- 6곳
    assertThat(mlRegionRepository.findByYearAndMonthAndUnitAndTypeNm(2025, 7, "dong", "정류장 접근 취약형"))
        .extracting(MlRegion::getRegionCd)
        .doesNotHaveDuplicates()
        .containsExactlyInAnyOrder(
            "4146125000", "4146125300", "4146125600", "4146353500", "4146553000", "4146556000");
  }

  @Test
  @DisplayName("ml_explanation을 월 키로 1건 조회하고 options·문장·생성 시각을 그 달 값으로 매핑한다")
  void explanationByMonth() {
    // SELECT is_keep, summary, created_at, JSON_EXTRACT(options,'$[*].score'), JSON_LENGTH(reasons)
    //   FROM ml_explanation WHERE year=2025 AND month=7 AND region_cd='4146125000';
    //   -- 0, '정류장이 멀고 걷기 힘들어 똑버스 도입 검토', 2026-11-08 20:56, [80.5, 45.8, 27.5], 2
    MlExplanation explanation =
        mlExplanationRepository
            .findByYearAndMonthAndUnitAndRegionCd(2025, 7, "dong", POGOK)
            .orElseThrow();

    assertThat(explanation.getIsKeep()).isFalse();
    assertThat(explanation.getSummary()).isEqualTo("정류장이 멀고 걷기 힘들어 똑버스 도입 검토");
    assertThat(explanation.getCreatedAt()).isEqualTo("2026-11-08 20:56");
    assertThat(explanation.getReasons()).hasSize(2);
    assertThat(explanation.getOptions())
        .extracting(OptionJson::mode, OptionJson::status, OptionJson::score)
        .containsExactly(
            tuple("DRT", "추천", 80.5), tuple("BUS", "제외", 45.8), tuple("HAPPY_TAXI", "제외", 27.5));
    assertThat(mlExplanationRepository.findByYearAndMonthAndUnitAndRegionCd(2025, 8, "dong", POGOK))
        .isEmpty();
  }

  @Test
  @DisplayName("현행 유지 지역은 is_keep이 true이고 options 맨 앞이 점수 없는 KEEP이다")
  void keepExplanation() {
    // SELECT is_keep, JSON_EXTRACT(options,'$[0].mode'), JSON_EXTRACT(options,'$[0].score')
    //   FROM ml_explanation WHERE year=2025 AND month=7 AND region_cd='4146151000'; -- 1, KEEP,
    // null
    MlExplanation explanation =
        mlExplanationRepository
            .findByYearAndMonthAndUnitAndRegionCd(2025, 7, "dong", KEEP_REGION)
            .orElseThrow();

    assertThat(explanation.getIsKeep()).isTrue();
    assertThat(explanation.getOptions().getFirst().mode()).isEqualTo("KEEP");
    assertThat(explanation.getOptions().getFirst().score()).isNull();
  }
}
