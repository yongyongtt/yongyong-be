package com.example.yongyong.region.service;

import static com.example.yongyong.common.query.PeriodQuery.UNIT_DONG;

import com.example.yongyong.common.exception.BusinessException;
import com.example.yongyong.common.exception.ErrorCode;
import com.example.yongyong.common.query.PeriodQuery;
import com.example.yongyong.common.query.PeriodService;
import com.example.yongyong.common.response.PeriodResponse;
import com.example.yongyong.dashboard.service.DashboardService;
import com.example.yongyong.dashboard.service.DashboardService.RegionScore;
import com.example.yongyong.dashboard.service.DashboardService.Scores;
import com.example.yongyong.region.dto.RegionDetailResponse;
import com.example.yongyong.region.dto.RegionPreviewResponse;
import com.example.yongyong.yongin.entity.MlExplanation;
import com.example.yongyong.yongin.entity.MlRegion;
import com.example.yongyong.yongin.repository.MlExplanationRepository;
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

  private final DashboardService dashboardService;
  private final PeriodService periodService;
  private final MlRegionRepository mlRegionRepository;
  private final MlExplanationRepository mlExplanationRepository;

  public RegionPreviewResponse getPreview(String regionCode, PeriodQuery query) {
    periodService.checkMonth(query);
    Scores scores = dashboardService.scores(query);
    RegionScore score = findScore(scores, regionCode);
    Integer typeMonth = typeMonth(query);
    Integer recommendationMonth =
        PeriodService.monthOrLatest(
            query, mlExplanationRepository.findMonths(query.year(), UNIT_DONG));
    Optional<MlRegion> region = findRegion(query.year(), typeMonth, regionCode);
    Optional<MlExplanation> explanation =
        recommendationMonth == null
            ? Optional.empty()
            : mlExplanationRepository.findByYearAndMonthAndUnitAndRegionCd(
                query.year(), recommendationMonth, UNIT_DONG, regionCode);

    return new RegionPreviewResponse(
        scores.period(query),
        PeriodResponse.latest(query, typeMonth),
        score.regionCode(),
        score.regionName(),
        score.gu(),
        score.score(),
        score.rank(),
        region
            .map(r -> new RegionPreviewResponse.Type(r.getTypeNm(), r.getTypeDesc()))
            .orElse(null),
        explanation.map(MlExplanation::getIsKeep).orElse(false),
        explanation.map(this::recommended).orElse(null),
        explanation.map(MlExplanation::getSummary).orElse(null));
  }

  public RegionDetailResponse getDetail(String regionCode, PeriodQuery query) {
    periodService.checkMonth(query);
    Scores scores = dashboardService.scores(query);
    RegionScore score = findScore(scores, regionCode);
    Integer typeMonth = typeMonth(query);
    Optional<MlRegion> region = findRegion(query.year(), typeMonth, regionCode);

    return new RegionDetailResponse(
        scores.period(query),
        PeriodResponse.latest(query, typeMonth),
        new RegionDetailResponse.Header(
            score.regionCode(), score.regionName(), score.gu(), score.score(), score.rank()),
        region
            .map(r ->
                new RegionDetailResponse.Reliability(r.getLowReliability(), r.getReliabilityNote()))
            .orElse(new RegionDetailResponse.Reliability(false, null)),
        region.map(r -> toType(r, query.year(), scores, regionCode)).orElse(null));
  }

  private Integer typeMonth(PeriodQuery query) {
    return PeriodService.monthOrLatest(
        query, mlRegionRepository.findMonths(query.year(), UNIT_DONG));
  }

  private Optional<MlRegion> findRegion(Integer year, Integer month, String regionCode) {
    return month == null
        ? Optional.empty()
        : mlRegionRepository.findByYearAndMonthAndUnitAndRegionCd(
            year, month, UNIT_DONG, regionCode);
  }

  private RegionScore findScore(Scores scores, String regionCode) {
    return scores.regions().stream()
        .filter(r -> r.regionCode().equals(regionCode))
        .findFirst()
        .orElseThrow(() -> new BusinessException(ErrorCode.REGION_NOT_FOUND));
  }

  private RegionDetailResponse.Type toType(
      MlRegion region, Integer year, Scores scores, String regionCode) {
    Set<String> sameTypeCodes =
        mlRegionRepository
            .findByYearAndMonthAndUnitAndTypeNm(
                year, region.getMonth(), UNIT_DONG, region.getTypeNm())
            .stream()
            .map(MlRegion::getRegionCd)
            .collect(Collectors.toSet());
    List<RegionDetailResponse.SameTypeRegion> sameTypeRegions =
        scores.regions().stream()
            .filter(r -> sameTypeCodes.contains(r.regionCode()))
            .filter(r -> !r.regionCode().equals(regionCode))
            .sorted(DashboardService.RANK_ORDER)
            .map(r -> new RegionDetailResponse.SameTypeRegion(r.regionCode(), r.regionName()))
            .toList();
    return new RegionDetailResponse.Type(
        region.getTypeNm(), region.getTypeDesc(), region.getTypeChips(), sameTypeRegions);
  }

  private List<RegionPreviewResponse.Recommended> recommended(MlExplanation explanation) {
    return explanation.getOptions().stream()
        .filter(option -> STATUS_RECOMMENDED.equals(option.status()))
        .map(option -> new RegionPreviewResponse.Recommended(option.mode(), option.name()))
        .toList();
  }
}
