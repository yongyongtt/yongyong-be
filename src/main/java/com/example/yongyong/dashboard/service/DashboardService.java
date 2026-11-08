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
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
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

  // rawScore: 그 달 score_total 또는 월 평균 (반올림 전), score: 정수 반올림, rank: score 기준 재계산
  public record RegionScore(
      String regionCode, String regionName, String gu, double rawScore, int score, int rank) {}

  public DashboardResponse getDashboard(PeriodQuery query) {
    periodService.checkMonth(query);
    Scores scores = scores(query);

    return new DashboardResponse(
        scores.period(query),
        mlModelInfoRepository.findLatestTrainedAt(query.year(), query.month()),
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

  // month가 있으면 그 달 행, 없으면 월 평균. 행이 없으면 PERIOD_NOT_FOUND
  public Scores scores(PeriodQuery query) {
    List<MlPriority> rows =
        query.month() == null
            ? mlPriorityRepository.findByYearAndUnit(query.year(), UNIT_DONG)
            : mlPriorityRepository.findByYearAndMonthAndUnit(
                query.year(), query.month(), UNIT_DONG);
    if (rows.isEmpty()) {
      throw new BusinessException(ErrorCode.PERIOD_NOT_FOUND);
    }

    Map<String, List<MlPriority>> byRegion =
        rows.stream()
            .collect(
                Collectors.groupingBy(MlPriority::getRegionCd, TreeMap::new, Collectors.toList()));
    List<RegionScore> unranked =
        byRegion.values().stream()
            .map(regionRows -> {
              MlPriority first = regionRows.getFirst();
              double raw =
                  regionRows.stream()
                      .mapToDouble(MlPriority::getScoreTotal)
                      .average()
                      .orElseThrow();
              return new RegionScore(
                  first.getRegionCd(),
                  first.getRegionNm(),
                  first.getGu(),
                  raw,
                  (int) Math.round(raw),
                  0);
            })
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
    List<Integer> months = rows.stream().map(MlPriority::getMonth).distinct().sorted().toList();
    return new Scores(months, ranked);
  }

  private double averageScore(List<RegionScore> regions) {
    double average = regions.stream().mapToDouble(RegionScore::rawScore).average().orElseThrow();
    return Math.round(average * 10) / 10.0;
  }
}
