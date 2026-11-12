package com.example.yongyong.plan.service;

import static com.example.yongyong.common.query.PeriodQuery.UNIT_DONG;

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
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlanService {

  private static final String INCLUDED_STATUS = "포함";
  private static final String SKIPPED_STATUS = "예산 밖";
  private static final String NOT_CANDIDATE_STATUS = "후보 아님";

  private final PlanSummaryRepository planSummaryRepository;
  private final PlanRegionRepository planRegionRepository;
  private final PeriodService periodService;

  // 판단 순서: 연도 없음 → 공통 달에 없는 월 → 그 기간 plan 없음 (decisions D18)
  public PlanResponse getPlans(PeriodQuery query) {
    List<Integer> months = null;
    if (query.month() == null) {
      months = periodService.availableMonths(query.year());
      if (months.isEmpty()) {
        throw new BusinessException(ErrorCode.PERIOD_NOT_FOUND);
      }
    } else {
      periodService.checkMonth(query);
    }

    List<PlanSummary> summaries =
        planSummaryRepository.findByYearAndMonth(query.year(), query.monthKey());
    if (summaries.isEmpty()) {
      throw new BusinessException(ErrorCode.PLAN_NOT_FOUND);
    }
    List<PlanRegion> regions =
        planRegionRepository.findByYearAndMonthAndUnit(query.year(), query.monthKey(), UNIT_DONG);

    return new PlanResponse(
        PeriodResponse.all(query, months),
        summaries.stream()
            .sorted(Comparator.comparing(PlanSummary::getPlan))
            .map(summary -> toPlan(summary, regions))
            .toList());
  }

  private PlanResponse.Plan toPlan(PlanSummary summary, List<PlanRegion> regions) {
    List<PlanRegion> ofPlan =
        regions.stream().filter(r -> r.getPlan().equals(summary.getPlan())).toList();
    return new PlanResponse.Plan(
        summary.getPlan(),
        summary.getPlanNm(),
        summary.getPlanDesc(),
        summary.getSortBasis(),
        summary.getBudget(),
        summary.getTotalCost(),
        summary.getBudgetLeft(),
        summary.getNIncluded(),
        summary.getNSkipped(),
        summary.getPop65Total(),
        List.of(
            new PlanResponse.ModeCount("BUS", "노선버스", summary.getNBus()),
            new PlanResponse.ModeCount("DRT", "똑버스", summary.getNDrt()),
            new PlanResponse.ModeCount("HAPPY_TAXI", "행복택시", summary.getNHappy())),
        ofPlan.stream()
            .filter(r -> Boolean.TRUE.equals(r.getIsIncluded()))
            .sorted(Comparator.comparing(PlanRegion::getSeq))
            .map(r ->
                new PlanResponse.Region(
                    r.getSeq(),
                    r.getPlanRank(),
                    r.getRegionCd(),
                    r.getRegionNm(),
                    r.getGu(),
                    r.getMode(),
                    r.getModeNm(),
                    r.getAction(),
                    r.getCost()))
            .toList(),
        ofPlan.stream()
            .filter(r -> SKIPPED_STATUS.equals(r.getStatus()))
            .sorted(Comparator.comparing(PlanRegion::getPlanRank))
            .map(r ->
                new PlanResponse.Skipped(
                    r.getPlanRank(),
                    r.getRegionCd(),
                    r.getRegionNm(),
                    r.getGu(),
                    r.getMode(),
                    r.getModeNm(),
                    r.getAction(),
                    r.getCost(),
                    r.getBudgetLeftAtSkip()))
            .toList(),
        ofPlan.stream()
            .sorted(Comparator.comparing(PlanRegion::getRegionCd))
            .map(r ->
                new PlanResponse.ByRegion(
                    r.getRegionCd(), toStatus(r.getStatus()), r.getMode(), r.getModeNm()))
            .toList());
  }

  private String toStatus(String status) {
    if (INCLUDED_STATUS.equals(status)) {
      return "INCLUDED";
    }
    if (SKIPPED_STATUS.equals(status)) {
      return "SKIPPED";
    }
    if (NOT_CANDIDATE_STATUS.equals(status)) {
      return "NOT_CANDIDATE";
    }
    throw new IllegalStateException("알 수 없는 통합 최적안 상태입니다: " + status);
  }
}
