package com.example.yongyong.region.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

import com.example.yongyong.common.converter.OptionJson;
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
import java.util.ArrayList;
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
class RegionServiceTest {

  private static final String CODE = "4146125000";
  private static final String TYPE = "정류장 접근 취약형";
  private static final PeriodQuery ANNUAL = new PeriodQuery(2025, null);
  private static final PeriodQuery FEBRUARY = new PeriodQuery(2025, 2);

  @Mock private DashboardService dashboardService;
  @Mock private PeriodService periodService;
  @Mock private MlRegionRepository mlRegionRepository;
  @Mock private MlExplanationRepository mlExplanationRepository;
  @InjectMocks private RegionService regionService;

  @Test
  @DisplayName("연간 미리보기: 점수·유형·추천이 모두 각 테이블의 연간(NULL) 행이고, typePeriod도 period와 같은 YEAR_ALL이다")
  void previewAnnual() {
    givenScores(ANNUAL, List.of(1, 2, 3), score(CODE, "포곡읍", 60, 7));
    givenYearRegion(CODE, TYPE);
    givenYearExplanation(false, List.of(option("DRT", "똑버스", "추천"), option("BUS", "노선버스", "제외")));

    RegionPreviewResponse response = regionService.getPreview(CODE, ANNUAL);

    assertThat(response.regionName()).isEqualTo("포곡읍");
    assertThat(response.score()).isEqualTo(60);
    assertThat(response.rank()).isEqualTo(7);
    assertThat(response.type()).isEqualTo(new RegionPreviewResponse.Type(TYPE, "연간 설명"));
    assertThat(response.isKeep()).isFalse();
    assertThat(response.recommended())
        .containsExactly(new RegionPreviewResponse.Recommended("DRT", "똑버스"));
    assertThat(response.summary()).isEqualTo("연간 요약");
    PeriodResponse annual =
        new PeriodResponse(2025, List.of(1, 2, 3), PeriodResponse.Type.YEAR_ALL);
    assertThat(response.period()).isEqualTo(annual);
    assertThat(response.typePeriod()).isEqualTo(annual);
  }

  @Test
  @DisplayName("월을 고르면 점수, 유형, 추천이 모두 그 달 행이다")
  void previewMonthly() {
    givenScores(FEBRUARY, List.of(2), score(CODE, "포곡읍", 60, 6));
    givenRegion(2, CODE, TYPE);
    givenExplanation(2, false, List.of(option("DRT", "똑버스", "추천")));

    RegionPreviewResponse response = regionService.getPreview(CODE, FEBRUARY);

    assertThat(response.type().desc()).isEqualTo("2월 설명");
    assertThat(response.summary()).isEqualTo("2월 요약");
    PeriodResponse february = new PeriodResponse(2025, List.of(2), PeriodResponse.Type.MONTH);
    assertThat(response.period()).isEqualTo(february);
    assertThat(response.typePeriod()).isEqualTo(february);
  }

  @Test
  @DisplayName("현행 유지 지역이면 isKeep이 true이고 추천은 KEEP 하나다")
  void previewKeep() {
    givenScores(FEBRUARY, List.of(2), score(CODE, "포곡읍", 60, 6));
    givenExplanation(2, true, List.of(option("KEEP", "현행 유지", "추천"), option("BUS", "노선버스", "제외")));

    RegionPreviewResponse response = regionService.getPreview(CODE, FEBRUARY);

    assertThat(response.isKeep()).isTrue();
    assertThat(response.recommended())
        .containsExactly(new RegionPreviewResponse.Recommended("KEEP", "현행 유지"));
  }

  @Test
  @DisplayName("연간 ml_region과 ml_explanation 행이 없으면 404가 아니라 null로 내린다")
  void previewWithoutTypeAndExplanation() {
    givenScores(ANNUAL, List.of(1), score(CODE, "포곡읍", 60, 7));
    given(mlRegionRepository.findByYearAndMonthAndUnitAndRegionCd(2025, 0, "dong", CODE))
        .willReturn(Optional.empty());
    given(mlExplanationRepository.findByYearAndMonthAndUnitAndRegionCd(2025, 0, "dong", CODE))
        .willReturn(Optional.empty());

    RegionPreviewResponse response = regionService.getPreview(CODE, ANNUAL);

    assertThat(response.type()).isNull();
    assertThat(response.isKeep()).isFalse();
    assertThat(response.recommended()).isNull();
    assertThat(response.summary()).isNull();
    assertThat(response.typePeriod())
        .isEqualTo(new PeriodResponse(2025, List.of(1), PeriodResponse.Type.YEAR_ALL));
  }

  @Test
  @DisplayName("점수 목록에 지역이 없으면 REGION_NOT_FOUND를 던진다")
  void regionNotFound() {
    givenScores(ANNUAL, List.of(1), score("4146134000", "원삼면", 66, 1));

    assertError(() -> regionService.getPreview(CODE, ANNUAL), ErrorCode.REGION_NOT_FOUND);
    assertError(() -> regionService.getDetail(CODE, ANNUAL), ErrorCode.REGION_NOT_FOUND);
  }

  @Test
  @DisplayName("공통 달에 없는 월이면 checkMonth의 예외를 그대로 던진다")
  void monthNotReady() {
    PeriodQuery august = new PeriodQuery(2025, 8);
    willThrow(new BusinessException(ErrorCode.SUITABILITY_NOT_READY))
        .given(periodService)
        .checkMonth(august);

    assertError(() -> regionService.getPreview(CODE, august), ErrorCode.SUITABILITY_NOT_READY);
    assertError(() -> regionService.getDetail(CODE, august), ErrorCode.SUITABILITY_NOT_READY);
  }

  @Test
  @DisplayName("연간 상세: 헤더·유형·신뢰도·같은 유형 지역이 모두 연간 행 기준이고 typePeriod는 YEAR_ALL이다")
  void detailAnnual() {
    givenScores(
        ANNUAL,
        List.of(1, 2, 3),
        score(CODE, "포곡읍", 60, 7),
        score("A", "같은 유형", 65, 3),
        score("B", "다른 유형", 50, 9));
    givenYearRegion(CODE, TYPE);
    given(mlRegionRepository.findByYearAndMonthAndUnitAndTypeNm(2025, 0, "dong", TYPE))
        .willReturn(List.of(yearRegion(CODE, TYPE), yearRegion("A", TYPE)));

    RegionDetailResponse response = regionService.getDetail(CODE, ANNUAL);

    assertThat(response.header())
        .isEqualTo(new RegionDetailResponse.Header(CODE, "포곡읍", "처인구", 60, 7));
    PeriodResponse annual =
        new PeriodResponse(2025, List.of(1, 2, 3), PeriodResponse.Type.YEAR_ALL);
    assertThat(response.period()).isEqualTo(annual);
    assertThat(response.typePeriod()).isEqualTo(annual);
    assertThat(response.reliability()).isEqualTo(new RegionDetailResponse.Reliability(false, null));
    assertThat(response.type().name()).isEqualTo(TYPE);
    assertThat(response.type().desc()).isEqualTo("연간 설명");
    assertThat(response.type().chips()).containsExactly("연간 칩");
    assertThat(response.type().sameTypeRegions())
        .containsExactly(new RegionDetailResponse.SameTypeRegion("A", "같은 유형"));
  }

  @Test
  @DisplayName("같은 유형 지역은 같은 달 기준, 자기 자신을 빼고 중복 없이 재계산 순위·코드 순으로 내린다")
  void sameTypeRegions() {
    givenScores(
        FEBRUARY,
        List.of(2),
        score("C", "공동3위 코드 뒤", 50, 3),
        score(CODE, "포곡읍", 60, 1),
        score("A", "2위", 55, 2),
        score("B", "공동3위 코드 앞", 50, 3),
        score("D", "다른 유형", 40, 5));
    givenRegion(2, CODE, TYPE);
    givenSameType(2, CODE, "C", "A", "B", "A");

    RegionDetailResponse response = regionService.getDetail(CODE, FEBRUARY);

    assertThat(response.typePeriod())
        .isEqualTo(new PeriodResponse(2025, List.of(2), PeriodResponse.Type.MONTH));
    assertThat(response.type().sameTypeRegions())
        .containsExactly(
            new RegionDetailResponse.SameTypeRegion("A", "2위"),
            new RegionDetailResponse.SameTypeRegion("B", "공동3위 코드 앞"),
            new RegionDetailResponse.SameTypeRegion("C", "공동3위 코드 뒤"));
  }

  @Test
  @DisplayName("ml_region 행이 없으면 404가 아니라 type은 null, 신뢰도는 false로 내린다")
  void detailWithoutRegion() {
    givenScores(FEBRUARY, List.of(2), score(CODE, "포곡읍", 60, 6));

    RegionDetailResponse response = regionService.getDetail(CODE, FEBRUARY);

    assertThat(response.type()).isNull();
    assertThat(response.reliability()).isEqualTo(new RegionDetailResponse.Reliability(false, null));
  }

  private void givenScores(PeriodQuery query, List<Integer> months, RegionScore... regions) {
    given(dashboardService.scores(query)).willReturn(new Scores(months, List.of(regions)));
  }

  private RegionScore score(String code, String name, int score, int rank) {
    return new RegionScore(code, name, "처인구", score, score, rank);
  }

  private void givenRegion(int month, String code, String type) {
    given(mlRegionRepository.findByYearAndMonthAndUnitAndRegionCd(2025, month, "dong", code))
        .willReturn(Optional.of(region(month, code, type)));
  }

  private void givenSameType(int month, String... codes) {
    List<MlRegion> regions = new ArrayList<>();
    for (String code : codes) {
      regions.add(region(month, code, TYPE));
    }
    given(mlRegionRepository.findByYearAndMonthAndUnitAndTypeNm(2025, month, "dong", TYPE))
        .willReturn(regions);
  }

  private MlRegion region(int month, String code, String type) {
    MlRegion region = new MlRegion();
    ReflectionTestUtils.setField(region, "month", month);
    ReflectionTestUtils.setField(region, "regionCd", code);
    ReflectionTestUtils.setField(region, "typeNm", type);
    ReflectionTestUtils.setField(region, "typeDesc", month + "월 설명");
    ReflectionTestUtils.setField(region, "typeChips", List.of(month + "월 칩"));
    ReflectionTestUtils.setField(region, "lowReliability", false);
    return region;
  }

  private void givenYearRegion(String code, String type) {
    given(mlRegionRepository.findByYearAndMonthAndUnitAndRegionCd(2025, 0, "dong", code))
        .willReturn(Optional.of(yearRegion(code, type)));
  }

  // 연간 행은 month 0 (DB month NULL)
  private MlRegion yearRegion(String code, String type) {
    MlRegion region = new MlRegion();
    ReflectionTestUtils.setField(region, "month", 0);
    ReflectionTestUtils.setField(region, "regionCd", code);
    ReflectionTestUtils.setField(region, "typeNm", type);
    ReflectionTestUtils.setField(region, "typeDesc", "연간 설명");
    ReflectionTestUtils.setField(region, "typeChips", List.of("연간 칩"));
    ReflectionTestUtils.setField(region, "lowReliability", false);
    return region;
  }

  private void givenYearExplanation(boolean keep, List<OptionJson> options) {
    MlExplanation explanation = new MlExplanation();
    ReflectionTestUtils.setField(explanation, "month", 0);
    ReflectionTestUtils.setField(explanation, "isKeep", keep);
    ReflectionTestUtils.setField(explanation, "options", options);
    ReflectionTestUtils.setField(explanation, "summary", "연간 요약");
    given(mlExplanationRepository.findByYearAndMonthAndUnitAndRegionCd(2025, 0, "dong", CODE))
        .willReturn(Optional.of(explanation));
  }

  private void givenExplanation(int month, boolean keep, List<OptionJson> options) {
    MlExplanation explanation = new MlExplanation();
    ReflectionTestUtils.setField(explanation, "month", month);
    ReflectionTestUtils.setField(explanation, "isKeep", keep);
    ReflectionTestUtils.setField(explanation, "options", options);
    ReflectionTestUtils.setField(explanation, "summary", month + "월 요약");
    given(mlExplanationRepository.findByYearAndMonthAndUnitAndRegionCd(2025, month, "dong", CODE))
        .willReturn(Optional.of(explanation));
  }

  private OptionJson option(String mode, String name, String status) {
    return new OptionJson(mode, name, null, null, status, null, null, null, null, null, null);
  }

  private void assertError(
      org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode errorCode) {
    assertThatThrownBy(call)
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(errorCode);
  }
}
