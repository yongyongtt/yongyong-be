package com.example.yongyong.recommendation.service;

import static com.example.yongyong.common.query.PeriodQuery.UNIT_DONG;

import com.example.yongyong.common.converter.OptionJson;
import com.example.yongyong.common.exception.BusinessException;
import com.example.yongyong.common.exception.ErrorCode;
import com.example.yongyong.recommendation.dto.RecommendationResponse;
import com.example.yongyong.yongin.entity.MlExplanation;
import com.example.yongyong.yongin.entity.MlRegion;
import com.example.yongyong.yongin.repository.MlExplanationRepository;
import com.example.yongyong.yongin.repository.MlRegionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationService {

  private final MlExplanationRepository mlExplanationRepository;
  private final MlRegionRepository mlRegionRepository;

  public RecommendationResponse getRecommendation(String regionCode, Integer year) {
    MlExplanation explanation =
        mlExplanationRepository
            .findByYearAndUnitAndRegionCd(year, UNIT_DONG, regionCode)
            .orElseThrow(() -> new BusinessException(ErrorCode.RECOMMENDATION_NOT_FOUND));
    Boolean lowReliability =
        mlRegionRepository
            .findByYearAndUnitAndRegionCd(year, UNIT_DONG, regionCode)
            .map(MlRegion::getLowReliability)
            .orElse(false);

    return new RecommendationResponse(
        explanation.getIsKeep(),
        explanation.getCreatedAt(),
        explanation.getOptions().stream().map(this::toOption).toList(),
        explanation.getReasons(),
        explanation.getCautions(),
        lowReliability);
  }

  private RecommendationResponse.Option toOption(OptionJson option) {
    return new RecommendationResponse.Option(
        option.mode(),
        option.name(),
        option.subtitle(),
        option.desc(),
        toStatus(option.status()),
        option.rank(),
        option.score() == null ? null : (int) Math.round(option.score()),
        option.isCurrent(),
        option.reasons(),
        option.facts(),
        option.together());
  }

  private String toStatus(String status) {
    if ("추천".equals(status)) {
      return "RECOMMENDED";
    }
    if ("제외".equals(status)) {
      return "EXCLUDED";
    }
    throw new IllegalStateException("알 수 없는 추천 상태입니다: " + status);
  }
}
