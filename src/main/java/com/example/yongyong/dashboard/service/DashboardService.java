package com.example.yongyong.dashboard.service;

import com.example.yongyong.common.exception.BusinessException;
import com.example.yongyong.common.exception.ErrorCode;
import com.example.yongyong.common.query.PeriodQuery;
import com.example.yongyong.dashboard.dto.DashboardMapResponse;
import com.example.yongyong.dashboard.dto.DashboardResponse;
import com.example.yongyong.yongin.entity.MlPriority;
import com.example.yongyong.yongin.repository.MlModelInfoRepository;
import com.example.yongyong.yongin.repository.MlPriorityRepository;
import com.example.yongyong.yongin.repository.MlPriorityRepository.PeriodKey;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

  private static final String UNIT_DONG = "dong";
  private static final int TOP_LIMIT = 10;
  private static final Comparator<MlPriority> RANK_ORDER =
      Comparator.comparing(MlPriority::getRankTotal).thenComparing(MlPriority::getRegionCd);

  private final MlPriorityRepository mlPriorityRepository;
  private final MlModelInfoRepository mlModelInfoRepository;

  public DashboardResponse getDashboard(PeriodQuery query) {
    Integer year = query.year();
    List<MlPriority> rows = findRows(query);

    List<PeriodKey> periods = mlPriorityRepository.findPeriods(UNIT_DONG);
    return new DashboardResponse(
        new DashboardResponse.Period(
            year,
            query.month(),
            periods.stream().map(PeriodKey::getYear).distinct().toList(),
            periods.stream()
                .filter(period -> period.getYear().equals(year))
                .map(period -> period.getMonthKey() == 0 ? null : period.getMonthKey())
                .toList(),
            mlModelInfoRepository.findLatestTrainedAt(year)),
        new DashboardResponse.Summary(
            rows.size(),
            rows.stream().filter(MlPriority::getIsVulnerable).count(),
            averageScore(rows)),
        rows.stream().sorted(RANK_ORDER).limit(TOP_LIMIT).map(this::toTopRegion).toList());
  }

  public DashboardMapResponse getMap(PeriodQuery query) {
    return new DashboardMapResponse(
        findRows(query).stream().sorted(RANK_ORDER).map(this::toMapRegion).toList());
  }

  private List<MlPriority> findRows(PeriodQuery query) {
    List<MlPriority> rows =
        mlPriorityRepository.findByYearAndMonthKeyAndUnit(
            query.year(), query.monthKey(), UNIT_DONG);
    if (rows.isEmpty()) {
      throw new BusinessException(ErrorCode.PERIOD_NOT_FOUND);
    }
    return rows;
  }

  private DashboardMapResponse.MapRegion toMapRegion(MlPriority priority) {
    return new DashboardMapResponse.MapRegion(
        priority.getRegionCd(),
        priority.getRegionNm(),
        priority.getGu(),
        priority.getScoreTotal(),
        priority.getRankTotal());
  }

  private double averageScore(List<MlPriority> rows) {
    double average = rows.stream().mapToDouble(MlPriority::getScoreTotal).average().orElseThrow();
    return Math.round(average * 10) / 10.0;
  }

  private DashboardResponse.TopRegion toTopRegion(MlPriority priority) {
    return new DashboardResponse.TopRegion(
        priority.getRankTotal(),
        priority.getRegionCd(),
        priority.getRegionNm(),
        priority.getGu(),
        (int) Math.round(priority.getScoreTotal()));
  }
}
