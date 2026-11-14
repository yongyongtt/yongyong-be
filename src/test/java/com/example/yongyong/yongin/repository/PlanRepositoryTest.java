package com.example.yongyong.yongin.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.example.yongyong.MariaDbTestSupport;
import com.example.yongyong.yongin.entity.PlanRegion;
import com.example.yongyong.yongin.entity.PlanSummary;
import java.util.Comparator;
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
class PlanRepositoryTest extends MariaDbTestSupport {

  @Autowired private PlanSummaryRepository planSummaryRepository;
  @Autowired private PlanRegionRepository planRegionRepository;

  @Test
  @DisplayName("연간 plan_summary 3행을 month 0(DB NULL) 키로 읽고 예산·비용·개수·수단을 매핑한다")
  void summaryYear() {
    // SELECT plan, plan_nm, budget, total_cost, budget_left, n_included, n_skipped, pop65_total,
    //   n_bus, n_drt, n_happy FROM plan_summary WHERE year=2025 AND month IS NULL;
    //   -- 1안 711666667 / 671000000 / 40666667, 2·3안 711666667 / 701500000 / 10166667,
    //   세 안 6 / 8, 0·6·0, pop65 41553·24605·39118
    List<PlanSummary> rows = planSummaryRepository.findByYearAndMonth(2025, 0);

    assertThat(rows)
        .doesNotContainNull()
        .extracting(
            PlanSummary::getPlan,
            PlanSummary::getPlanNm,
            PlanSummary::getBudget,
            PlanSummary::getTotalCost,
            PlanSummary::getBudgetLeft,
            PlanSummary::getNIncluded,
            PlanSummary::getNSkipped,
            PlanSummary::getPop65Total,
            PlanSummary::getNBus,
            PlanSummary::getNDrt,
            PlanSummary::getNHappy)
        .containsExactlyInAnyOrder(
            tuple(1, "효율형", 711666667L, 671000000L, 40666667L, 6, 8, 41553, 0, 6, 0),
            tuple(2, "형평형", 711666667L, 701500000L, 10166667L, 6, 8, 24605, 0, 6, 0),
            tuple(3, "균형형", 711666667L, 701500000L, 10166667L, 6, 8, 39118, 0, 6, 0));
    // SELECT COUNT(*) FROM plan_summary WHERE year=2025 AND month=7; -- 3
    assertThat(planSummaryRepository.findByYearAndMonth(2025, 7)).hasSize(3);
    assertThat(planSummaryRepository.findByYearAndMonth(2024, 0)).isEmpty();
  }

  @Test
  @DisplayName("연간 plan_region dong 114행을 읽고, 안마다 포함 6·예산 밖 8·현행 유지 1이며 is_included가 status와 맞다")
  void regionYearCounts() {
    // SELECT plan, status, COUNT(*) FROM plan_region WHERE year=2025 AND month IS NULL
    //   AND unit='dong' GROUP BY plan, status; -- 안마다 포함 6, 예산 밖 8, 현행 유지 1, 후보 아님 23
    List<PlanRegion> rows = planRegionRepository.findByYearAndMonthAndUnit(2025, 0, "dong");

    assertThat(rows).hasSize(114).doesNotContainNull();
    for (int plan = 1; plan <= 3; plan++) {
      int p = plan;
      List<PlanRegion> ofPlan = rows.stream().filter(r -> r.getPlan() == p).toList();
      assertThat(ofPlan).filteredOn(PlanRegion::getIsIncluded).hasSize(6);
      assertThat(ofPlan).filteredOn(r -> r.getStatus().equals("예산 밖")).hasSize(8);
      assertThat(ofPlan).filteredOn(r -> r.getStatus().equals("현행 유지")).hasSize(1);
    }
    assertThat(rows).allMatch(r -> r.getIsIncluded() == r.getStatus().equals("포함"));
  }

  @Test
  @DisplayName("연간 3안과 1안의 투입 순서(seq), 3안 예산 밖(plan_rank)이 기준값과 같다")
  void regionYearOrder() {
    // SELECT seq, region_nm, action, cost FROM plan_region WHERE year=2025 AND month IS NULL
    //   AND plan=3 AND unit='dong' AND is_included=1 ORDER BY seq;
    //   -- 모현읍·이동읍·포곡읍·동천동·양지면 똑버스 4대 · 122000000, 구성동 똑버스 3대 · 91500000
    // ... plan=1 -- 구성동·모현읍·동천동·포곡읍·신봉동·이동읍
    // ... plan=3 AND status='예산 밖' ORDER BY plan_rank; -- 남사읍(6) 101666667,
    //   원삼면(8)·백암면(9)·동부동(10)·신봉동(11)·기흥동(12)·보라동(13)·삼가동(14) 10166667
    List<PlanRegion> rows = planRegionRepository.findByYearAndMonthAndUnit(2025, 0, "dong");

    assertThat(included(rows, 3))
        .extracting(PlanRegion::getRegionNm, PlanRegion::getAction, PlanRegion::getCost)
        .containsExactly(
            tuple("모현읍", "똑버스 4대", 122000000L),
            tuple("이동읍", "똑버스 4대", 122000000L),
            tuple("포곡읍", "똑버스 4대", 122000000L),
            tuple("동천동", "똑버스 4대", 122000000L),
            tuple("양지면", "똑버스 4대", 122000000L),
            tuple("구성동", "똑버스 3대", 91500000L));
    assertThat(included(rows, 1))
        .extracting(PlanRegion::getRegionNm)
        .containsExactly("구성동", "모현읍", "동천동", "포곡읍", "신봉동", "이동읍");
    assertThat(
            rows.stream()
                .filter(r -> r.getPlan() == 3 && r.getStatus().equals("예산 밖"))
                .sorted(Comparator.comparing(PlanRegion::getPlanRank))
                .toList())
        .extracting(
            PlanRegion::getPlanRank, PlanRegion::getRegionNm, PlanRegion::getBudgetLeftAtSkip)
        .containsExactly(
            tuple(6, "남사읍", 101666667L),
            tuple(8, "원삼면", 10166667L),
            tuple(9, "백암면", 10166667L),
            tuple(10, "동부동", 10166667L),
            tuple(11, "신봉동", 10166667L),
            tuple(12, "기흥동", 10166667L),
            tuple(13, "보라동", 10166667L),
            tuple(14, "삼가동", 10166667L));
  }

  @Test
  @DisplayName("연간 3안 원본은 38행이고 포함 6(모두 DRT)·예산 밖 8·현행 유지 1(영덕2동, KEEP)·후보 아님 23(그중 KEEP 8)이다")
  void regionYearByRegion() {
    // SELECT status, mode, COUNT(*) FROM plan_region WHERE year=2025 AND month IS NULL AND plan=3
    //   AND unit='dong' GROUP BY status, mode;
    //   -- 포함 DRT 6, 예산 밖 DRT 8, 현행 유지 KEEP 1, 후보 아님 KEEP 8 · BUS 14 · DRT 1
    List<PlanRegion> plan3 =
        planRegionRepository.findByYearAndMonthAndUnit(2025, 0, "dong").stream()
            .filter(r -> r.getPlan() == 3)
            .toList();

    assertThat(plan3).hasSize(38).extracting(PlanRegion::getRegionCd).doesNotHaveDuplicates();
    assertThat(plan3)
        .filteredOn(r -> r.getStatus().equals("포함"))
        .extracting(PlanRegion::getRegionNm, PlanRegion::getMode, PlanRegion::getModeNm)
        .containsExactlyInAnyOrder(
            tuple("모현읍", "DRT", "똑버스"),
            tuple("이동읍", "DRT", "똑버스"),
            tuple("포곡읍", "DRT", "똑버스"),
            tuple("동천동", "DRT", "똑버스"),
            tuple("양지면", "DRT", "똑버스"),
            tuple("구성동", "DRT", "똑버스"));
    assertThat(plan3)
        .filteredOn(r -> r.getStatus().equals("예산 밖"))
        .extracting(PlanRegion::getRegionNm)
        .containsExactlyInAnyOrder("남사읍", "원삼면", "백암면", "동부동", "신봉동", "기흥동", "보라동", "삼가동");
    assertThat(plan3)
        .filteredOn(r -> r.getStatus().equals("현행 유지"))
        .extracting(PlanRegion::getRegionNm, PlanRegion::getMode)
        .containsExactly(tuple("영덕2동", "KEEP"));
    List<PlanRegion> notCandidate =
        plan3.stream().filter(r -> r.getStatus().equals("후보 아님")).toList();
    assertThat(notCandidate).hasSize(23);
    assertThat(notCandidate).filteredOn(r -> r.getMode().equals("KEEP")).hasSize(8);
  }

  private List<PlanRegion> included(List<PlanRegion> rows, int plan) {
    return rows.stream()
        .filter(r -> r.getPlan() == plan && r.getIsIncluded())
        .sorted(Comparator.comparing(PlanRegion::getSeq))
        .toList();
  }
}
