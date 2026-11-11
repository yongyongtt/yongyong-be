package com.example.yongyong.recommendation.service;

import static com.example.yongyong.common.query.PeriodQuery.UNIT_DONG;

import com.example.yongyong.common.converter.OptionJson;
import com.example.yongyong.common.exception.BusinessException;
import com.example.yongyong.common.exception.ErrorCode;
import com.example.yongyong.common.query.PeriodQuery;
import com.example.yongyong.common.query.PeriodService;
import com.example.yongyong.common.response.PeriodResponse;
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
  private final PeriodService periodService;

  public RecommendationResponse getRecommendation(String regionCode, PeriodQuery query) {
    periodService.checkMonth(query);
    int month = query.monthKey();
    MlExplanation explanation =
        mlExplanationRepository
            .findByYearAndMonthAndUnitAndRegionCd(query.year(), month, UNIT_DONG, regionCode)
            .orElseThrow(() -> new BusinessException(ErrorCode.RECOMMENDATION_NOT_FOUND));
    Boolean lowReliability =
        mlRegionRepository
            .findByYearAndMonthAndUnitAndRegionCd(query.year(), month, UNIT_DONG, regionCode)
            .map(MlRegion::getLowReliability)
            .orElse(false);

    // options의 score는 같은 기간 ml_suitability와 같은 값 (11/8 DB 861건 일치 확인)
    return new RecommendationResponse(
        PeriodResponse.all(
            query, query.month() == null ? periodService.availableMonths(query.year()) : null),
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
