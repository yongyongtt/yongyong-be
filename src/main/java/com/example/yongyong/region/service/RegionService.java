package com.example.yongyong.region.service;

import static com.example.yongyong.common.query.PeriodQuery.UNIT_DONG;

import com.example.yongyong.common.exception.BusinessException;
import com.example.yongyong.common.exception.ErrorCode;
import com.example.yongyong.common.query.PeriodQuery;
import com.example.yongyong.region.dto.RegionDetailResponse;
import com.example.yongyong.region.dto.RegionPreviewResponse;
import com.example.yongyong.yongin.entity.MlExplanation;
import com.example.yongyong.yongin.entity.MlPriority;
import com.example.yongyong.yongin.entity.MlRegion;
import com.example.yongyong.yongin.repository.MlExplanationRepository;
import com.example.yongyong.yongin.repository.MlPriorityRepository;
import com.example.yongyong.yongin.repository.MlRegionRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegionService {

  private static final String STATUS_RECOMMENDED = "추천";

  private final MlPriorityRepository mlPriorityRepository;
  private final MlRegionRepository mlRegionRepository;
  private final MlExplanationRepository mlExplanationRepository;

  public RegionPreviewResponse getPreview(String regionCode, PeriodQuery query) {
    Integer year = query.year();
    MlPriority priority =
        findPriority(year, query.monthKey(), regionCode)
            .orElseThrow(() -> notFound(regionCode, query));
    Optional<MlRegion> region =
        mlRegionRepository.findByYearAndUnitAndRegionCd(year, UNIT_DONG, regionCode);
    Optional<MlExplanation> explanation =
        mlExplanationRepository.findByYearAndUnitAndRegionCd(year, UNIT_DONG, regionCode);

    return new RegionPreviewResponse(
        priority.getRegionCd(),
        priority.getRegionNm(),
        priority.getGu(),
        roundScore(priority),
        priority.getRankTotal(),
        region
            .map(r -> new RegionPreviewResponse.Type(r.getTypeNm(), r.getTypeDesc()))
            .orElse(null),
        explanation.map(MlExplanation::getIsKeep).orElse(false),
        explanation.map(this::recommended).orElse(null),
        explanation.map(MlExplanation::getSummary).orElse(null));
  }

  public RegionDetailResponse getDetail(String regionCode, PeriodQuery query) {
    Integer year = query.year();
    MlPriority priority =
        findPriority(year, query.monthKey(), regionCode)
            .orElseThrow(() -> notFound(regionCode, query));
    Optional<MlRegion> region =
        mlRegionRepository.findByYearAndUnitAndRegionCd(year, UNIT_DONG, regionCode);

    return new RegionDetailResponse(
        new RegionDetailResponse.Header(
            priority.getRegionCd(),
            priority.getRegionNm(),
            priority.getGu(),
            roundScore(priority),
            priority.getRankTotal()),
        new RegionDetailResponse.Period(year, query.month(), availableMonths(year)),
        region
            .map(r ->
                new RegionDetailResponse.Reliability(r.getLowReliability(), r.getReliabilityNote()))
            .orElse(new RegionDetailResponse.Reliability(false, null)),
        region.map(r -> toType(r, year, regionCode)).orElse(null));
  }

  private List<Integer> availableMonths(Integer year) {
    return mlPriorityRepository.findPeriods(UNIT_DONG).stream()
        .filter(period -> period.getYear().equals(year))
        .map(period -> PeriodQuery.toMonth(period.getMonthKey()))
        .toList();
  }

  private RegionDetailResponse.Type toType(MlRegion region, Integer year, String regionCode) {
    Set<String> sameTypeCodes =
        mlRegionRepository.findByYearAndUnitAndTypeNm(year, UNIT_DONG, region.getTypeNm()).stream()
            .map(MlRegion::getRegionCd)
            .collect(Collectors.toSet());
    List<RegionDetailResponse.SameTypeRegion> sameTypeRegions =
        mlPriorityRepository.findByYearAndMonthKeyAndUnit(year, 0, UNIT_DONG).stream()
            .filter(row -> sameTypeCodes.contains(row.getRegionCd()))
            .filter(row -> !row.getRegionCd().equals(regionCode))
            .sorted(MlPriority.RANK_ORDER)
            .map(row ->
                new RegionDetailResponse.SameTypeRegion(row.getRegionCd(), row.getRegionNm()))
            .toList();
    return new RegionDetailResponse.Type(
        region.getTypeNm(), region.getTypeDesc(), region.getTypeChips(), sameTypeRegions);
  }

  private int roundScore(MlPriority priority) {
    return (int) Math.round(priority.getScoreTotal());
  }

  private Optional<MlPriority> findPriority(Integer year, int monthKey, String regionCode) {
    return mlPriorityRepository.findByYearAndMonthKeyAndUnitAndRegionCd(
        year, monthKey, UNIT_DONG, regionCode);
  }

  private BusinessException notFound(String regionCode, PeriodQuery query) {
    boolean yearLoaded =
        mlPriorityRepository.findPeriods(UNIT_DONG).stream()
            .anyMatch(period -> period.getYear().equals(query.year()));
    boolean monthMissing =
        query.monthKey() != 0 && findPriority(query.year(), 0, regionCode).isPresent();
    return new BusinessException(
        !yearLoaded || monthMissing ? ErrorCode.PERIOD_NOT_FOUND : ErrorCode.REGION_NOT_FOUND);
  }

  private List<RegionPreviewResponse.Recommended> recommended(MlExplanation explanation) {
    return explanation.getOptions().stream()
        .filter(option -> STATUS_RECOMMENDED.equals(option.status()))
        .map(option -> new RegionPreviewResponse.Recommended(option.mode(), option.name()))
        .toList();
  }
}
