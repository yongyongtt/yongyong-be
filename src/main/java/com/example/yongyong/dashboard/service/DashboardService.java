package com.example.yongyong.dashboard.service;

import static com.example.yongyong.common.query.PeriodQuery.UNIT_DONG;

import com.example.yongyong.common.exception.BusinessException;
import com.example.yongyong.common.exception.ErrorCode;
import com.example.yongyong.common.query.PeriodQuery;
import com.example.yongyong.common.query.PeriodService;
import com.example.yongyong.common.response.PeriodResponse;
import com.example.yongyong.dashboard.dto.DashboardMapResponse;
import com.example.yongyong.dashboard.dto.DashboardResponse;
import com.example.yongyong.yongin.entity.MlPriority;
import com.example.yongyong.yongin.repository.MlModelInfoRepository;
import com.example.yongyong.yongin.repository.MlPriorityRepository;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

  // 같은 순위 안에서는 반올림 전 점수가 높은 지역이 먼저, 그것도 같으면 regionCode 순
  public static final Comparator<RegionScore> RANK_ORDER =
      Comparator.comparingInt(RegionScore::rank)
          .thenComparing(Comparator.comparingDouble(RegionScore::rawScore).reversed())
          .thenComparing(RegionScore::regionCode);

  private static final int TOP_LIMIT = 10;
  private static final int VULNERABLE_SCORE = 50;

  private final MlPriorityRepository mlPriorityRepository;
  private final MlModelInfoRepository mlModelInfoRepository;
  private final PeriodService periodService;

  // API 1~4가 같이 쓰는 지역별 점수. regions는 rank, regionCode 순
  public record Scores(List<Integer> months, List<RegionScore> regions) {

    public PeriodResponse period(PeriodQuery query) {
      return PeriodResponse.all(query, months);
    }
  }

  // rawScore: 그 기간 score_total (반올림 전), score: 정수 반올림, rank: score 기준 재계산
  public record RegionScore(
      String regionCode, String regionName, String gu, double rawScore, int score, int rank) {}

  public DashboardResponse getDashboard(PeriodQuery query) {
    periodService.checkMonth(query);
    Scores scores = scores(query);

    return new DashboardResponse(
        scores.period(query),
        mlModelInfoRepository.findLatestTrainedAt(query.year(), query.monthKey()),
        new DashboardResponse.Summary(
            scores.regions().size(),
            scores.regions().stream().filter(r -> r.score() >= VULNERABLE_SCORE).count(),
            averageScore(scores.regions())),
        scores.regions().stream()
            .limit(TOP_LIMIT)
            .map(r ->
                new DashboardResponse.TopRegion(
                    r.rank(), r.regionCode(), r.regionName(), r.gu(), r.score()))
            .toList());
  }

  public DashboardMapResponse getMap(PeriodQuery query) {
    periodService.checkMonth(query);
    Scores scores = scores(query);
    return new DashboardMapResponse(
        scores.period(query),
        scores.regions().stream()
            .map(r ->
                new DashboardMapResponse.MapRegion(
                    r.regionCode(), r.regionName(), r.gu(), r.score(), r.rank()))
            .toList());
  }

  // 그 달 또는 연간(month 0) 행. 행이 없으면 PERIOD_NOT_FOUND. 연간 months는 공통 달
  public Scores scores(PeriodQuery query) {
    List<MlPriority> rows =
        mlPriorityRepository.findByYearAndMonthAndUnit(query.year(), query.monthKey(), UNIT_DONG);
    if (rows.isEmpty()) {
      throw new BusinessException(ErrorCode.PERIOD_NOT_FOUND);
    }

    List<RegionScore> unranked =
        rows.stream()
            .map(row ->
                new RegionScore(
                    row.getRegionCd(),
                    row.getRegionNm(),
                    row.getGu(),
                    row.getScoreTotal(),
                    (int) Math.round(row.getScoreTotal()),
                    0))
            .toList();
    List<RegionScore> ranked =
        unranked.stream()
            .map(r -> {
              int rank = 1 + (int) unranked.stream().filter(o -> o.score() > r.score()).count();
              return new RegionScore(
                  r.regionCode(), r.regionName(), r.gu(), r.rawScore(), r.score(), rank);
            })
            .sorted(RANK_ORDER)
            .toList();
    List<Integer> months =
        query.month() == null
            ? periodService.availableMonths(query.year())
            : List.of(query.month());
    return new Scores(months, ranked);
  }

  private double averageScore(List<RegionScore> regions) {
    double average = regions.stream().mapToDouble(RegionScore::rawScore).average().orElseThrow();
    return Math.round(average * 10) / 10.0;
  }
}
