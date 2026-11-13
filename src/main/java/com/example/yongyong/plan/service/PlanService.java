package com.example.yongyong.plan.service;

import static com.example.yongyong.common.query.PeriodQuery.UNIT_DONG;

import com.example.yongyong.common.exception.BusinessException;
import com.example.yongyong.common.exception.ErrorCode;
import com.example.yongyong.common.query.PeriodQuery;
import com.example.yongyong.common.query.PeriodService;
import com.example.yongyong.common.response.PeriodResponse;
import com.example.yongyong.plan.dto.PlanMapResponse;
import com.example.yongyong.plan.dto.PlanResponse;
import com.example.yongyong.yongin.entity.PlanRegion;
import com.example.yongyong.yongin.entity.PlanSummary;
import com.example.yongyong.yongin.repository.PlanRegionRepository;
import com.example.yongyong.yongin.repository.PlanSummaryRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
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
  private static final Map<String, String> INCLUDED_CATEGORIES =
      Map.of("BUS", "노선버스", "DRT", "똑버스", "HAPPY_TAXI", "행복택시");

  private final PlanSummaryRepository planSummaryRepository;
  private final PlanRegionRepository planRegionRepository;
  private final PeriodService periodService;

  // 9-1 판단 순서: 연도 없음 → 공통 달에 없는 월 → 그 기간 plan_summary 없음 (decisions D18)
  public PlanResponse getPlans(PeriodQuery query) {
    List<Integer> months = checkPeriod(query);
    List<PlanSummary> summaries =
        planSummaryRepository.findByYearAndMonth(query.year(), query.monthKey());
    checkFound(summaries);
    List<PlanRegion> regions = findRegions(query);

    return new PlanResponse(
        PeriodResponse.all(query, months),
        summaries.stream()
            .sorted(Comparator.comparing(PlanSummary::getPlan))
            .map(summary -> toPlan(summary, regions))
            .toList());
  }

  // 9-2 판단 순서: 9-1과 같고, 마지막만 그 기간 plan_region 없음
  public PlanMapResponse getPlanMap(PeriodQuery query) {
    List<Integer> months = checkPeriod(query);
    List<PlanRegion> regions = findRegions(query);
    checkFound(regions);

    Map<Integer, List<PlanRegion>> byPlan =
        regions.stream()
            .collect(Collectors.groupingBy(PlanRegion::getPlan, TreeMap::new, Collectors.toList()));
    return new PlanMapResponse(
        PeriodResponse.all(query, months),
        byPlan.entrySet().stream()
            .map(e ->
                new PlanMapResponse.Plan(
                    e.getKey(),
                    e.getValue().stream()
                        .sorted(Comparator.comparing(PlanRegion::getRegionCd))
                        .map(this::toMapRegion)
                        .toList()))
            .toList());
  }

  // 연간이면 공통 달을 돌려주고, 월이면 null (PeriodResponse.all이 [month]로 바꿈)
  private List<Integer> checkPeriod(PeriodQuery query) {
    if (query.month() != null) {
      periodService.checkMonth(query);
      return null;
    }
    List<Integer> months = periodService.availableMonths(query.year());
    if (months.isEmpty()) {
      throw new BusinessException(ErrorCode.PERIOD_NOT_FOUND);
    }
    return months;
  }

  private void checkFound(List<?> rows) {
    if (rows.isEmpty()) {
      throw new BusinessException(ErrorCode.PLAN_NOT_FOUND);
    }
  }

  private List<PlanRegion> findRegions(PeriodQuery query) {
    return planRegionRepository.findByYearAndMonthAndUnit(
        query.year(), query.monthKey(), UNIT_DONG);
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
        summary.getDays(),
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
                    round(r.getScoreTotal()),
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
                    round(r.getScoreTotal()),
                    r.getMode(),
                    r.getModeNm(),
                    r.getAction(),
                    r.getCost(),
                    r.getBudgetLeftAtSkip()))
            .toList());
  }

  private Integer round(Double score) {
    return score == null ? null : (int) Math.round(score);
  }

  // status를 먼저 보고 mode를 본다. 후보 아님 행에도 BUS·DRT mode가 있어 mode만 보면 안 된다 (D18)
  private PlanMapResponse.Region toMapRegion(PlanRegion region) {
    String status = toStatus(region.getStatus());
    String mode = region.getMode();
    if (status.equals("INCLUDED")) {
      String name = mode == null ? null : INCLUDED_CATEGORIES.get(mode);
      if (name == null) {
        throw new IllegalStateException("알 수 없는 통합 최적안 포함 수단입니다: " + mode);
      }
      return new PlanMapResponse.Region(region.getRegionCd(), mode, name, status);
    }
    if (status.equals("SKIPPED")) {
      return new PlanMapResponse.Region(region.getRegionCd(), "SKIPPED", "예산 밖", status);
    }
    if ("KEEP".equals(mode)) {
      return new PlanMapResponse.Region(region.getRegionCd(), "KEEP", "현행 유지", status);
    }
    return new PlanMapResponse.Region(region.getRegionCd(), "NONE", "기타 미포함", status);
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
