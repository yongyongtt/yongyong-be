package com.example.yongyong.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.example.yongyong.common.converter.OptionJson;
import com.example.yongyong.common.exception.BusinessException;
import com.example.yongyong.common.exception.ErrorCode;
import com.example.yongyong.recommendation.dto.RecommendationResponse;
import com.example.yongyong.yongin.entity.MlExplanation;
import com.example.yongyong.yongin.entity.MlRegion;
import com.example.yongyong.yongin.repository.MlExplanationRepository;
import com.example.yongyong.yongin.repository.MlRegionRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

  private static final String CODE = "4146125000";

  @Mock private MlExplanationRepository mlExplanationRepository;
  @Mock private MlRegionRepository mlRegionRepository;
  @InjectMocks private RecommendationService recommendationService;

  @Test
  @DisplayName("options를 순서 그대로 변환하고 상태와 점수를 명세 형식으로 바꾼다")
  void convertsOptions() {
    givenExplanation(
        false,
        List.of(
            new OptionJson(
                "DRT", "똑버스", "부제", "설명", "추천", 1, 80.7, false, List.of("이유"), List.of("근거"), null),
            new OptionJson(
                "BUS", "노선버스", "부제", "설명", "제외", null, 46.2, true, List.of(), List.of(), null)));
    givenRegion(false);

    RecommendationResponse response = recommendationService.getRecommendation(CODE, 2025);

    assertThat(response.isKeep()).isFalse();
    assertThat(response.createdAt()).isEqualTo("2026-11-05 11:03");
    assertThat(response.reasons()).containsExactly("추천 이유");
    assertThat(response.cautions()).containsExactly("주의");
    assertThat(response.options())
        .containsExactly(
            new RecommendationResponse.Option(
                "DRT",
                "똑버스",
                "부제",
                "설명",
                "RECOMMENDED",
                1,
                81,
                false,
                List.of("이유"),
                List.of("근거"),
                null),
            new RecommendationResponse.Option(
                "BUS", "노선버스", "부제", "설명", "EXCLUDED", null, 46, true, List.of(), List.of(), null));
  }

  @Test
  @DisplayName("현행 유지 지역은 KEEP을 추가하지 않고 DB 그대로 맨 앞에 두며 null 점수를 유지한다")
  void keepsKeepOption() {
    givenExplanation(
        true,
        List.of(
            new OptionJson(
                "KEEP", "현행 유지", "부제", "설명", "추천", 1, null, false, List.of(), List.of(), null),
            new OptionJson(
                "DRT", "똑버스", "부제", "설명", "제외", null, 52.3, false, List.of(), List.of(), null)));
    givenRegion(false);

    RecommendationResponse response = recommendationService.getRecommendation(CODE, 2025);

    assertThat(response.isKeep()).isTrue();
    assertThat(response.options()).hasSize(2);
    assertThat(response.options().getFirst().mode()).isEqualTo("KEEP");
    assertThat(response.options().getFirst().score()).isNull();
  }

  @Test
  @DisplayName("ml_region의 신뢰도 값을 lowReliability로 내린다")
  void lowReliability() {
    givenExplanation(false, List.of());
    givenRegion(true);

    assertThat(recommendationService.getRecommendation(CODE, 2025).lowReliability()).isTrue();
  }

  @Test
  @DisplayName("ml_region 행이 없으면 lowReliability를 false로 내린다")
  void regionMissing() {
    givenExplanation(false, List.of());
    given(mlRegionRepository.findByYearAndUnitAndRegionCd(2025, "dong", CODE))
        .willReturn(Optional.empty());

    assertThat(recommendationService.getRecommendation(CODE, 2025).lowReliability()).isFalse();
  }

  @Test
  @DisplayName("ml_explanation 행이 없으면 RECOMMENDATION_NOT_FOUND를 던진다")
  void recommendationNotFound() {
    given(mlExplanationRepository.findByYearAndUnitAndRegionCd(2025, "dong", CODE))
        .willReturn(Optional.empty());

    assertThatThrownBy(() -> recommendationService.getRecommendation(CODE, 2025))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.RECOMMENDATION_NOT_FOUND);
  }

  @Test
  @DisplayName("추천과 제외 외의 상태가 오면 예외를 던져 500으로 처리되게 한다")
  void unknownStatus() {
    givenExplanation(
        false,
        List.of(
            new OptionJson(
                "DRT", "똑버스", "부제", "설명", "보류", 1, 80.7, false, List.of(), List.of(), null)));
    givenRegion(false);

    assertThatThrownBy(() -> recommendationService.getRecommendation(CODE, 2025))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("보류");
  }

  private void givenExplanation(boolean keep, List<OptionJson> options) {
    MlExplanation explanation = new MlExplanation();
    ReflectionTestUtils.setField(explanation, "isKeep", keep);
    ReflectionTestUtils.setField(explanation, "createdAt", "2026-11-05 11:03");
    ReflectionTestUtils.setField(explanation, "options", options);
    ReflectionTestUtils.setField(explanation, "reasons", List.of("추천 이유"));
    ReflectionTestUtils.setField(explanation, "cautions", List.of("주의"));
    given(mlExplanationRepository.findByYearAndUnitAndRegionCd(2025, "dong", CODE))
        .willReturn(Optional.of(explanation));
  }

  private void givenRegion(boolean lowReliability) {
    MlRegion region = new MlRegion();
    ReflectionTestUtils.setField(region, "lowReliability", lowReliability);
    given(mlRegionRepository.findByYearAndUnitAndRegionCd(2025, "dong", CODE))
        .willReturn(Optional.of(region));
  }
}
