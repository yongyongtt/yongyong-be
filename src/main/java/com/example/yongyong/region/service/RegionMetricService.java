package com.example.yongyong.region.service;

import static com.example.yongyong.common.query.PeriodQuery.UNIT_DONG;

import com.example.yongyong.common.exception.BusinessException;
import com.example.yongyong.common.exception.ErrorCode;
import com.example.yongyong.common.query.PeriodQuery;
import com.example.yongyong.region.dto.RegionComparisonResponse;
import com.example.yongyong.region.dto.RegionComparisonResponse.Metric;
import com.example.yongyong.region.dto.RegionTrendResponse;
import com.example.yongyong.yongin.entity.FeatCity;
import com.example.yongyong.yongin.entity.FeatCityMonth;
import com.example.yongyong.yongin.entity.FeatRegion;
import com.example.yongyong.yongin.entity.FeatRegionMonth;
import com.example.yongyong.yongin.repository.FeatCityMonthRepository;
import com.example.yongyong.yongin.repository.FeatCityRepository;
import com.example.yongyong.yongin.repository.FeatRegionMonthRepository;
import com.example.yongyong.yongin.repository.FeatRegionRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegionMetricService {

  private final FeatRegionRepository featRegionRepository;
  private final FeatRegionMonthRepository featRegionMonthRepository;
  private final FeatCityRepository featCityRepository;
  private final FeatCityMonthRepository featCityMonthRepository;

  public RegionComparisonResponse getComparison(String regionCode, PeriodQuery query) {
    Integer year = query.year();
    FeatRegion region =
        featRegionRepository
            .findByYearAndDongCd(year, regionCode)
            .orElseThrow(() -> regionMissing(year));
    FeatRegionMonth regionMonth = query.month() == null ? null : findRegionMonth(regionCode, query);
    Optional<FeatCity> city = featCityRepository.findByYearAndUnit(year, UNIT_DONG);
    List<FeatCityMonth> cityMonths = featCityMonthRepository.findByYear(year);

    Double regionMatchRate;
    Double cityMatchRate;
    Double regionFloatingPop;
    Double cityFloatingPop;
    if (regionMonth == null) {
      regionMatchRate = region.getTaxiMatchRate();
      cityMatchRate = annualMatchRate(cityMonths);
      regionFloatingPop = region.getFpAvg();
      cityFloatingPop = annualFloatingPop(cityMonths);
    } else {
      Optional<FeatCityMonth> cityMonth = findCityMonth(cityMonths, query.month());
      regionMatchRate = regionMonth.getTaxiMatchRate();
      cityMatchRate = cityMonth.map(FeatCityMonth::getTaxiMatchRate).orElse(null);
      regionFloatingPop =
          Boolean.TRUE.equals(regionMonth.getFpAnomaly()) ? null : regionMonth.getFpAvg();
      cityFloatingPop = cityMonth.map(FeatCityMonth::getFpAvg).orElse(null);
    }

    return new RegionComparisonResponse(
        List.of(
            new Metric(
                "TAXI_MATCH_RATE",
                "택시 호출 성사율",
                "%",
                toPercent(regionMatchRate),
                toPercent(cityMatchRate)),
            new Metric(
                "BUS_STOP_COUNT",
                "버스 정류장 수",
                "개",
                region.getNStops(),
                toWhole(city.map(FeatCity::getNStops).orElse(null))),
            new Metric(
                "ELDERLY_RATIO",
                "고령인구 비율",
                "%",
                toPercent(region.getRatio65()),
                toPercent(city.map(FeatCity::getElderlyRatio).orElse(null))),
            new Metric(
                "FLOATING_POP",
                "생활인구 (시간 평균)",
                "명",
                toWhole(regionFloatingPop),
                toWhole(cityFloatingPop))));
  }

  public RegionTrendResponse getUnmatchedTrend(String regionCode, Integer year) {
    List<FeatRegionMonth> rows = featRegionMonthRepository.findByYearAndDongCd(year, regionCode);
    if (rows.isEmpty()) {
      throw new BusinessException(
          featRegionMonthRepository.existsByYear(year)
              ? ErrorCode.REGION_NOT_FOUND
              : ErrorCode.PERIOD_NOT_FOUND);
    }
    List<FeatCityMonth> cityMonths = featCityMonthRepository.findByYear(year);

    return new RegionTrendResponse(
        rows.stream()
            .sorted(Comparator.comparing(FeatRegionMonth::getMonth))
            .map(row -> toPoint(row, cityMonths))
            .toList());
  }

  private RegionTrendResponse.Point toPoint(FeatRegionMonth row, List<FeatCityMonth> cityMonths) {
    return new RegionTrendResponse.Point(
        row.getYear(),
        row.getMonth(),
        toPercent(row.getTaxiUnmatchedRate()),
        toWhole(row.getTaxiReq()),
        toWhole(row.getTaxiMatched()),
        toPercent(
            findCityMonth(cityMonths, row.getMonth())
                .map(FeatCityMonth::getTaxiUnmatchedRate)
                .orElse(null)));
  }

  private BusinessException regionMissing(Integer year) {
    return new BusinessException(
        featRegionRepository.existsByYear(year)
            ? ErrorCode.REGION_NOT_FOUND
            : ErrorCode.PERIOD_NOT_FOUND);
  }

  private FeatRegionMonth findRegionMonth(String regionCode, PeriodQuery query) {
    return featRegionMonthRepository
        .findByYearAndMonthAndDongCd(query.year(), query.month(), regionCode)
        .orElseThrow(() -> new BusinessException(ErrorCode.PERIOD_NOT_FOUND));
  }

  private Optional<FeatCityMonth> findCityMonth(List<FeatCityMonth> cityMonths, Integer month) {
    return cityMonths.stream().filter(row -> row.getMonth().equals(month)).findFirst();
  }

  private Double annualMatchRate(List<FeatCityMonth> cityMonths) {
    if (cityMonths.isEmpty()) {
      return null;
    }
    double matched = cityMonths.stream().mapToDouble(FeatCityMonth::getTaxiMatched).sum();
    double requested = cityMonths.stream().mapToDouble(FeatCityMonth::getTaxiReq).sum();
    return matched / requested;
  }

  private Double annualFloatingPop(List<FeatCityMonth> cityMonths) {
    return cityMonths.isEmpty()
        ? null
        : cityMonths.stream().mapToDouble(FeatCityMonth::getFpAvg).average().orElseThrow();
  }

  private Integer toPercent(Double ratio) {
    return ratio == null ? null : (int) Math.round(ratio * 100);
  }

  private Integer toWhole(Double value) {
    return value == null ? null : (int) Math.round(value);
  }
}
