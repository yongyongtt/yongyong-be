package com.example.yongyong.plan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

import com.example.yongyong.common.exception.BusinessException;
import com.example.yongyong.common.exception.ErrorCode;
import com.example.yongyong.common.query.PeriodQuery;
import com.example.yongyong.common.query.PeriodService;
import com.example.yongyong.common.response.PeriodResponse;
import com.example.yongyong.plan.dto.PlanResponse;
import com.example.yongyong.yongin.entity.PlanRegion;
import com.example.yongyong.yongin.entity.PlanSummary;
import com.example.yongyong.yongin.repository.PlanRegionRepository;
import com.example.yongyong.yongin.repository.PlanSummaryRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PlanServiceTest {

  private static final PeriodQuery ANNUAL = new PeriodQuery(2025, null);
  private static final PeriodQuery JULY = new PeriodQuery(2025, 7);

  @Mock private PlanSummaryRepository planSummaryRepository;
  @Mock private PlanRegionRepository planRegionRepository;
  @Mock private PeriodService periodService;
  @InjectMocks private PlanService planService;

  @Test
  @DisplayName("연간이면 month 0 행으로 안을 plan 순으로 내리고, 투입은 seq 순·예산 밖은 plan_rank 순, 후보 아님은 뺀다")
  void annual() {
    given(periodService.availableMonths(2025)).willReturn(List.of(1, 2, 3));
    given(planSummaryRepository.findByYearAndMonth(2025, 0))
        .willReturn(List.of(summary(3, "균형형", 1, 4, 0), summary(1, "효율형", 0, 5, 0)));
    given(planRegionRepository.findByYearAndMonthAndUnit(2025, 0, "dong"))
        .willReturn(
            List.of(
                region(3, "B", "포함", true, 2, 2, null),
                region(3, "Z", "후보 아님", false, null, null, null),
                region(3, "D", "예산 밖", false, null, 7, 100L),
                region(3, "A", "포함", true, 1, 1, null),
                region(3, "C", "예산 밖", false, null, 6, 100L),
                region(1, "E", "포함", true, 1, 1, null)));

    PlanResponse response = planService.getPlans(ANNUAL);

    assertThat(response.period())
        .isEqualTo(new PeriodResponse(2025, List.of(1, 2, 3), PeriodResponse.Type.YEAR_ALL));
    assertThat(response.plans()).extracting(PlanResponse.Plan::plan).containsExactly(1, 3);
    PlanResponse.Plan balanced = response.plans().get(1);
    assertThat(balanced.name()).isEqualTo("균형형");
    assertThat(balanced.desc()).isEqualTo("균형형 설명");
    assertThat(balanced.sortBasis()).isEqualTo("균형형 기준");
    assertThat(balanced.budget()).isEqualTo(711666667L);
    assertThat(balanced.totalCost()).isEqualTo(610000000L);
    assertThat(balanced.budgetLeft()).isEqualTo(101666667L);
    assertThat(balanced.includedCount()).isEqualTo(5);
    assertThat(balanced.skippedCount()).isEqualTo(4);
    assertThat(balanced.pop65Total()).isEqualTo(24999);
    assertThat(balanced.regions())
        .containsExactly(
            new PlanResponse.Region(1, 1, "A", "A동", "처인구", "DRT", "똑버스", "똑버스 4대", 122000000L),
            new PlanResponse.Region(2, 2, "B", "B동", "처인구", "DRT", "똑버스", "똑버스 4대", 122000000L));
    assertThat(balanced.skipped())
        .containsExactly(
            new PlanResponse.Skipped(6, "C", "C동", "처인구", "DRT", "똑버스", "똑버스 4대", 122000000L, 100L),
            new PlanResponse.Skipped(
                7, "D", "D동", "처인구", "DRT", "똑버스", "똑버스 4대", 122000000L, 100L));
    assertThat(response.plans().getFirst().regions())
        .extracting(PlanResponse.Region::regionCode)
        .containsExactly("E");
  }

  @Test
  @DisplayName(
      "byRegion은 그 안의 행 전체(후보 아님 포함)를 regionCode 순으로 내리고 status를 영문 코드로, mode는 DB 값 그대로 둔다")
  void byRegion() {
    given(periodService.availableMonths(2025)).willReturn(List.of(1));
    given(planSummaryRepository.findByYearAndMonth(2025, 0))
        .willReturn(List.of(summary(3, "균형형", 0, 2, 0)));
    PlanRegion keep = region(3, "B", "후보 아님", false, null, null, null);
    ReflectionTestUtils.setField(keep, "mode", "KEEP");
    ReflectionTestUtils.setField(keep, "modeNm", "현행 유지");
    PlanRegion bus = region(3, "D", "후보 아님", false, null, null, null);
    ReflectionTestUtils.setField(bus, "mode", "BUS");
    ReflectionTestUtils.setField(bus, "modeNm", "노선버스");
    given(planRegionRepository.findByYearAndMonthAndUnit(2025, 0, "dong"))
        .willReturn(
            List.of(
                region(3, "C", "예산 밖", false, null, 3, 100L),
                keep,
                region(3, "E", "포함", true, 2, 2, null),
                bus,
                region(3, "A", "포함", true, 1, 1, null)));

    assertThat(planService.getPlans(ANNUAL).plans().getFirst().byRegion())
        .containsExactly(
            new PlanResponse.ByRegion("A", "INCLUDED", "DRT", "똑버스"),
            new PlanResponse.ByRegion("B", "NOT_CANDIDATE", "KEEP", "현행 유지"),
            new PlanResponse.ByRegion("C", "SKIPPED", "DRT", "똑버스"),
            new PlanResponse.ByRegion("D", "NOT_CANDIDATE", "BUS", "노선버스"),
            new PlanResponse.ByRegion("E", "INCLUDED", "DRT", "똑버스"));
  }

  @Test
  @DisplayName("plan_region status가 포함·예산 밖·후보 아님 외의 값이면 예외를 던져 500으로 처리되게 한다")
  void unknownStatus() {
    given(planSummaryRepository.findByYearAndMonth(2025, 7))
        .willReturn(List.of(summary(1, "효율형", 0, 5, 0)));
    given(planRegionRepository.findByYearAndMonthAndUnit(2025, 7, "dong"))
        .willReturn(List.of(region(1, "A", "보류", false, null, null, null)));

    assertThatThrownBy(() -> planService.getPlans(JULY))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("보류");
  }

  @Test
  @DisplayName("modeCounts는 0인 수단도 포함해 항상 BUS → DRT → HAPPY_TAXI 3개다")
  void modeCounts() {
    given(periodService.availableMonths(2025)).willReturn(List.of(1));
    given(planSummaryRepository.findByYearAndMonth(2025, 0))
        .willReturn(List.of(summary(3, "균형형", 1, 4, 0)));
    given(planRegionRepository.findByYearAndMonthAndUnit(2025, 0, "dong")).willReturn(List.of());

    assertThat(planService.getPlans(ANNUAL).plans().getFirst().modeCounts())
        .containsExactly(
            new PlanResponse.ModeCount("BUS", "노선버스", 1),
            new PlanResponse.ModeCount("DRT", "똑버스", 4),
            new PlanResponse.ModeCount("HAPPY_TAXI", "행복택시", 0));
  }

  @Test
  @DisplayName("월을 고르면 그 달 행과 MONTH period를 내린다")
  void monthly() {
    given(planSummaryRepository.findByYearAndMonth(2025, 7))
        .willReturn(List.of(summary(1, "효율형", 0, 5, 0)));
    given(planRegionRepository.findByYearAndMonthAndUnit(2025, 7, "dong")).willReturn(List.of());

    PlanResponse response = planService.getPlans(JULY);

    assertThat(response.period())
        .isEqualTo(new PeriodResponse(2025, List.of(7), PeriodResponse.Type.MONTH));
    assertThat(response.plans()).hasSize(1);
  }

  @Test
  @DisplayName("연간인데 연도 데이터가 아예 없으면 PERIOD_NOT_FOUND를 던진다")
  void periodNotFound() {
    given(periodService.availableMonths(2024)).willReturn(List.of());

    assertThatThrownBy(() -> planService.getPlans(new PeriodQuery(2024, null)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.PERIOD_NOT_FOUND);
  }

  @Test
  @DisplayName("공통 달에 없는 월이면 checkMonth의 예외를 그대로 던진다")
  void monthNotReady() {
    willThrow(new BusinessException(ErrorCode.SUITABILITY_NOT_READY))
        .given(periodService)
        .checkMonth(JULY);

    assertThatThrownBy(() -> planService.getPlans(JULY))
        .extracting("errorCode")
        .isEqualTo(ErrorCode.SUITABILITY_NOT_READY);
  }

  @Test
  @DisplayName("그 기간 plan_summary 행이 없으면 PLAN_NOT_FOUND를 던진다")
  void planNotFound() {
    given(planSummaryRepository.findByYearAndMonth(2025, 7)).willReturn(List.of());

    assertThatThrownBy(() -> planService.getPlans(JULY))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.PLAN_NOT_FOUND);
  }

  private PlanSummary summary(int plan, String name, int bus, int drt, int happy) {
    PlanSummary summary = new PlanSummary();
    ReflectionTestUtils.setField(summary, "plan", plan);
    ReflectionTestUtils.setField(summary, "planNm", name);
    ReflectionTestUtils.setField(summary, "planDesc", name + " 설명");
    ReflectionTestUtils.setField(summary, "sortBasis", name + " 기준");
    ReflectionTestUtils.setField(summary, "budget", 711666667L);
    ReflectionTestUtils.setField(summary, "totalCost", 610000000L);
    ReflectionTestUtils.setField(summary, "budgetLeft", 101666667L);
    ReflectionTestUtils.setField(summary, "nIncluded", 5);
    ReflectionTestUtils.setField(summary, "nSkipped", 4);
    ReflectionTestUtils.setField(summary, "pop65Total", 24999);
    ReflectionTestUtils.setField(summary, "nBus", bus);
    ReflectionTestUtils.setField(summary, "nDrt", drt);
    ReflectionTestUtils.setField(summary, "nHappy", happy);
    return summary;
  }

  private PlanRegion region(
      int plan,
      String code,
      String status,
      boolean included,
      Integer seq,
      Integer rank,
      Long budgetLeftAtSkip) {
    PlanRegion region = new PlanRegion();
    ReflectionTestUtils.setField(region, "plan", plan);
    ReflectionTestUtils.setField(region, "regionCd", code);
    ReflectionTestUtils.setField(region, "regionNm", code + "동");
    ReflectionTestUtils.setField(region, "gu", "처인구");
    ReflectionTestUtils.setField(region, "status", status);
    ReflectionTestUtils.setField(region, "isIncluded", included);
    ReflectionTestUtils.setField(region, "seq", seq);
    ReflectionTestUtils.setField(region, "planRank", rank);
    ReflectionTestUtils.setField(region, "mode", "DRT");
    ReflectionTestUtils.setField(region, "modeNm", "똑버스");
    ReflectionTestUtils.setField(region, "action", "똑버스 4대");
    ReflectionTestUtils.setField(region, "cost", 122000000L);
    ReflectionTestUtils.setField(region, "budgetLeftAtSkip", budgetLeftAtSkip);
    return region;
  }
}
