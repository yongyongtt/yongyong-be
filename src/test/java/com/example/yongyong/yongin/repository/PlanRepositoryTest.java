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
    //   -- 세 안 711666667 / 610000000 / 101666667, 5 / 4, 0·5·0, pop65 30433·21848·24999
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
            tuple(1, "효율형", 711666667L, 610000000L, 101666667L, 5, 4, 30433, 0, 5, 0),
            tuple(2, "형평형", 711666667L, 610000000L, 101666667L, 5, 4, 21848, 0, 5, 0),
            tuple(3, "균형형", 711666667L, 610000000L, 101666667L, 5, 4, 24999, 0, 5, 0));
    // SELECT COUNT(*) FROM plan_summary WHERE year=2025 AND month=7; -- 3
    assertThat(planSummaryRepository.findByYearAndMonth(2025, 7)).hasSize(3);
    assertThat(planSummaryRepository.findByYearAndMonth(2024, 0)).isEmpty();
  }

  @Test
  @DisplayName("연간 plan_region dong 114행을 읽고, 안마다 포함 5·예산 밖 4이며 is_included가 status와 맞다")
  void regionYearCounts() {
    // SELECT plan, status, COUNT(*) FROM plan_region WHERE year=2025 AND month IS NULL
    //   AND unit='dong' GROUP BY plan, status; -- 안마다 포함 5, 예산 밖 4, 후보 아님 29
    List<PlanRegion> rows = planRegionRepository.findByYearAndMonthAndUnit(2025, 0, "dong");

    assertThat(rows).hasSize(114).doesNotContainNull();
    for (int plan = 1; plan <= 3; plan++) {
      int p = plan;
      List<PlanRegion> ofPlan = rows.stream().filter(r -> r.getPlan() == p).toList();
      assertThat(ofPlan).filteredOn(PlanRegion::getIsIncluded).hasSize(5);
      assertThat(ofPlan).filteredOn(r -> r.getStatus().equals("예산 밖")).hasSize(4);
    }
    assertThat(rows).allMatch(r -> r.getIsIncluded() == r.getStatus().equals("포함"));
  }

  @Test
  @DisplayName("연간 3안과 1안의 투입 순서(seq), 3안 예산 밖(plan_rank)이 기준값과 같다")
  void regionYearOrder() {
    // SELECT seq, region_nm, action, cost FROM plan_region WHERE year=2025 AND month IS NULL
    //   AND plan=3 AND unit='dong' AND is_included=1 ORDER BY seq;
    //   -- 모현읍·이동읍·원삼면·포곡읍·양지면, 모두 똑버스 4대 · 122000000
    // ... plan=1 -- 모현읍·동천동·포곡읍·이동읍·남사읍
    // ... plan=3 AND status='예산 밖' ORDER BY plan_rank; -- 남사읍(6)·동천동(7)·백암면(8)·동부동(9),
    // 101666667
    List<PlanRegion> rows = planRegionRepository.findByYearAndMonthAndUnit(2025, 0, "dong");

    assertThat(included(rows, 3))
        .extracting(PlanRegion::getRegionNm, PlanRegion::getAction, PlanRegion::getCost)
        .containsExactly(
            tuple("모현읍", "똑버스 4대", 122000000L),
            tuple("이동읍", "똑버스 4대", 122000000L),
            tuple("원삼면", "똑버스 4대", 122000000L),
            tuple("포곡읍", "똑버스 4대", 122000000L),
            tuple("양지면", "똑버스 4대", 122000000L));
    assertThat(included(rows, 1))
        .extracting(PlanRegion::getRegionNm)
        .containsExactly("모현읍", "동천동", "포곡읍", "이동읍", "남사읍");
    assertThat(
            rows.stream()
                .filter(r -> r.getPlan() == 3 && r.getStatus().equals("예산 밖"))
                .sorted(Comparator.comparing(PlanRegion::getPlanRank))
                .toList())
        .extracting(
            PlanRegion::getPlanRank, PlanRegion::getRegionNm, PlanRegion::getBudgetLeftAtSkip)
        .containsExactly(
            tuple(6, "남사읍", 101666667L),
            tuple(7, "동천동", 101666667L),
            tuple(8, "백암면", 101666667L),
            tuple(9, "동부동", 101666667L));
  }

  @Test
  @DisplayName("연간 3안 byRegion 원본은 38행이고 포함 5(모두 DRT)·예산 밖 4·후보 아님 29(그중 KEEP 9)다")
  void regionYearByRegion() {
    // SELECT status, mode, COUNT(*) FROM plan_region WHERE year=2025 AND month IS NULL AND plan=3
    //   AND unit='dong' GROUP BY status, mode;
    //   -- 포함 DRT 5, 예산 밖 DRT 4, 후보 아님 KEEP 9 · BUS 14 · DRT 6
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
            tuple("원삼면", "DRT", "똑버스"),
            tuple("포곡읍", "DRT", "똑버스"),
            tuple("양지면", "DRT", "똑버스"));
    assertThat(plan3)
        .filteredOn(r -> r.getStatus().equals("예산 밖"))
        .extracting(PlanRegion::getRegionNm)
        .containsExactlyInAnyOrder("남사읍", "동천동", "백암면", "동부동");
    List<PlanRegion> notCandidate =
        plan3.stream().filter(r -> r.getStatus().equals("후보 아님")).toList();
    assertThat(notCandidate).hasSize(29);
    assertThat(notCandidate).filteredOn(r -> r.getMode().equals("KEEP")).hasSize(9);
  }

  private List<PlanRegion> included(List<PlanRegion> rows, int plan) {
    return rows.stream()
        .filter(r -> r.getPlan() == plan && r.getIsIncluded())
        .sorted(Comparator.comparing(PlanRegion::getSeq))
        .toList();
  }
}
