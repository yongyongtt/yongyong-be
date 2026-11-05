package com.example.yongyong.region.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.example.yongyong.common.converter.OptionJson;
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

  @Mock private MlPriorityRepository mlPriorityRepository;
  @Mock private MlRegionRepository mlRegionRepository;
  @Mock private MlExplanationRepository mlExplanationRepository;
  @InjectMocks private RegionService regionService;

  @Test
  @DisplayName("연간 점수와 순위, 유형, 추천 수단, 요약을 dong으로 조회해 내린다")
  void annual() {
    givenPriority(0, 60.4, 7);
    givenRegion();
    givenExplanation(
        false,
        List.of(
            option("DRT", "똑버스", "추천"),
            option("BUS", "노선버스", "제외"),
            option("HAPPY_TAXI", "행복택시", "제외")));

    RegionPreviewResponse response = regionService.getPreview(CODE, new PeriodQuery(2025, null));

    assertThat(response)
        .isEqualTo(
            new RegionPreviewResponse(
                CODE,
                "포곡읍",
                "처인구",
                60,
                7,
                new RegionPreviewResponse.Type("정류장 접근 취약형", "유형 설명"),
                false,
                List.of(new RegionPreviewResponse.Recommended("DRT", "똑버스")),
                "한 줄 요약"));
  }

  @Test
  @DisplayName("월을 고르면 점수와 순위만 그 달 값으로 바뀐다")
  void monthly() {
    givenPriority(2, 60.4, 6);
    givenRegion();
    givenExplanation(false, List.of(option("DRT", "똑버스", "추천")));

    RegionPreviewResponse response = regionService.getPreview(CODE, new PeriodQuery(2025, 2));

    assertThat(response.rank()).isEqualTo(6);
    assertThat(response.type().name()).isEqualTo("정류장 접근 취약형");
    assertThat(response.summary()).isEqualTo("한 줄 요약");
  }

  @Test
  @DisplayName("점수는 소수점 없이 정수로 반올림한다")
  void scoreRounded() {
    givenPriority(0, 64.7, 3);
    givenRegion();
    givenExplanation(false, List.of(option("DRT", "똑버스", "추천")));

    RegionPreviewResponse response = regionService.getPreview(CODE, new PeriodQuery(2025, null));

    assertThat(response.score()).isEqualTo(65);
  }

  @Test
  @DisplayName("현행 유지 지역이면 isKeep이 true이고 추천은 KEEP 하나다")
  void keep() {
    givenPriority(0, 20.0, 30);
    givenRegion();
    givenExplanation(
        true,
        List.of(
            option("KEEP", "현행 유지", "추천"),
            option("DRT", "똑버스", "제외"),
            option("BUS", "노선버스", "제외")));

    RegionPreviewResponse response = regionService.getPreview(CODE, new PeriodQuery(2025, null));

    assertThat(response.isKeep()).isTrue();
    assertThat(response.recommended())
        .containsExactly(new RegionPreviewResponse.Recommended("KEEP", "현행 유지"));
  }

  @Test
  @DisplayName("ml_region과 ml_explanation 행이 없으면 404가 아니라 null로 내린다")
  void missingTypeAndExplanation() {
    givenPriority(0, 60.4, 7);
    given(mlRegionRepository.findByYearAndUnitAndRegionCd(2025, "dong", CODE))
        .willReturn(Optional.empty());
    given(mlExplanationRepository.findByYearAndUnitAndRegionCd(2025, "dong", CODE))
        .willReturn(Optional.empty());

    RegionPreviewResponse response = regionService.getPreview(CODE, new PeriodQuery(2025, null));

    assertThat(response.score()).isEqualTo(60);
    assertThat(response.type()).isNull();
    assertThat(response.isKeep()).isFalse();
    assertThat(response.recommended()).isNull();
    assertThat(response.summary()).isNull();
  }

  @Test
  @DisplayName("연도 데이터는 있는데 지역이 없으면 REGION_NOT_FOUND를 던진다")
  void regionNotFound() {
    givenNoPriority(2025, 0);
    givenLoadedYear();

    assertError(new PeriodQuery(2025, null), ErrorCode.REGION_NOT_FOUND);
  }

  @Test
  @DisplayName("지역은 있는데 그 월이 적재되지 않았으면 PERIOD_NOT_FOUND를 던진다")
  void monthNotLoaded() {
    givenNoPriority(2025, 5);
    givenLoadedYear();
    givenPriority(0, 60.4, 7);

    assertError(new PeriodQuery(2025, 5), ErrorCode.PERIOD_NOT_FOUND);
  }

  @Test
  @DisplayName("월을 골랐는데 연간에도 없는 지역이면 REGION_NOT_FOUND를 던진다")
  void monthAndRegionNotFound() {
    givenNoPriority(2025, 5);
    givenLoadedYear();
    givenNoPriority(2025, 0);

    assertError(new PeriodQuery(2025, 5), ErrorCode.REGION_NOT_FOUND);
  }

  @Test
  @DisplayName("연도 데이터가 아예 없으면 PERIOD_NOT_FOUND를 던진다")
  void yearNotLoaded() {
    givenNoPriority(2024, 0);
    givenLoadedYear();

    assertError(new PeriodQuery(2024, null), ErrorCode.PERIOD_NOT_FOUND);
  }

  @Test
  @DisplayName("연도 데이터가 없으면 월을 골라도 PERIOD_NOT_FOUND를 던진다")
  void yearNotLoadedWithMonth() {
    givenNoPriority(2024, 2);
    givenLoadedYear();
    givenNoPriority(2024, 0);

    assertError(new PeriodQuery(2024, 2), ErrorCode.PERIOD_NOT_FOUND);
  }

  @Test
  @DisplayName("상세는 헤더, 기간, 신뢰도, 유형과 칩을 내리고 점수는 정수로 반올림한다")
  void detailAnnual() {
    givenPriority(0, 64.7, 7);
    givenPeriods(period(2024, 0), period(2025, 0), period(2025, 2));
    givenDetailRegion(false, null);
    givenSameType(CODE);
    givenAnnualRows(priorityRow(7, CODE, "포곡읍"));

    RegionDetailResponse response = regionService.getDetail(CODE, new PeriodQuery(2025, null));

    assertThat(response.header())
        .isEqualTo(new RegionDetailResponse.Header(CODE, "포곡읍", "처인구", 65, 7));
    assertThat(response.period().year()).isEqualTo(2025);
    assertThat(response.period().month()).isNull();
    assertThat(response.period().availableMonths()).containsExactly(null, 2);
    assertThat(response.reliability()).isEqualTo(new RegionDetailResponse.Reliability(false, null));
    assertThat(response.type().name()).isEqualTo("정류장 접근 취약형");
    assertThat(response.type().desc()).isEqualTo("유형 설명");
    assertThat(response.type().chips()).containsExactly("칩 하나", "칩 둘");
  }

  @Test
  @DisplayName("상세에서 월을 고르면 헤더와 기간만 그 달 값이고 유형은 연간 값이다")
  void detailMonthly() {
    givenPriority(2, 60.4, 6);
    givenPeriods(period(2025, 0), period(2025, 2));
    givenDetailRegion(false, null);
    givenSameType(CODE, "4146125300");
    givenAnnualRows(priorityRow(7, CODE, "포곡읍"), priorityRow(3, "4146125300", "모현읍"));

    RegionDetailResponse response = regionService.getDetail(CODE, new PeriodQuery(2025, 2));

    assertThat(response.header().rank()).isEqualTo(6);
    assertThat(response.period().month()).isEqualTo(2);
    assertThat(response.type().sameTypeRegions())
        .containsExactly(new RegionDetailResponse.SameTypeRegion("4146125300", "모현읍"));
  }

  @Test
  @DisplayName("같은 유형 지역은 자기 자신을 빼고 rank_total, region_cd 순으로 내린다")
  void detailSameTypeOrder() {
    givenPriority(0, 60.4, 7);
    givenPeriods(period(2025, 0));
    givenDetailRegion(false, null);
    givenSameType(CODE, "C", "B", "A");
    givenAnnualRows(
        priorityRow(7, CODE, "포곡읍"),
        priorityRow(9, "A", "9위"),
        priorityRow(2, "C", "공동 2위, 코드가 뒤"),
        priorityRow(2, "B", "공동 2위, 코드가 앞"),
        priorityRow(1, "Z", "다른 유형"));

    RegionDetailResponse response = regionService.getDetail(CODE, new PeriodQuery(2025, null));

    assertThat(response.type().sameTypeRegions())
        .extracting(RegionDetailResponse.SameTypeRegion::regionCode)
        .containsExactly("B", "C", "A");
  }

  @Test
  @DisplayName("신뢰도가 낮으면 low가 true이고 안내 문구를 내린다")
  void detailLowReliability() {
    givenPriority(0, 60.4, 7);
    givenPeriods(period(2025, 0));
    givenDetailRegion(true, "표본이 적어요");
    givenSameType(CODE);
    givenAnnualRows(priorityRow(7, CODE, "포곡읍"));

    RegionDetailResponse response = regionService.getDetail(CODE, new PeriodQuery(2025, null));

    assertThat(response.reliability())
        .isEqualTo(new RegionDetailResponse.Reliability(true, "표본이 적어요"));
  }

  @Test
  @DisplayName("ml_region 행이 없으면 404가 아니라 type은 null, 신뢰도는 false로 내린다")
  void detailMissingRegion() {
    givenPriority(0, 60.4, 7);
    givenPeriods(period(2025, 0));
    given(mlRegionRepository.findByYearAndUnitAndRegionCd(2025, "dong", CODE))
        .willReturn(Optional.empty());

    RegionDetailResponse response = regionService.getDetail(CODE, new PeriodQuery(2025, null));

    assertThat(response.type()).isNull();
    assertThat(response.reliability()).isEqualTo(new RegionDetailResponse.Reliability(false, null));
  }

  @Test
  @DisplayName("상세도 연도는 있는데 지역이 없으면 REGION_NOT_FOUND를 던진다")
  void detailRegionNotFound() {
    givenNoPriority(2025, 0);
    givenLoadedYear();

    assertDetailError(new PeriodQuery(2025, null), ErrorCode.REGION_NOT_FOUND);
  }

  @Test
  @DisplayName("상세도 지역은 있는데 월이 적재되지 않았으면 PERIOD_NOT_FOUND를 던진다")
  void detailMonthNotLoaded() {
    givenNoPriority(2025, 5);
    givenLoadedYear();
    givenPriority(0, 60.4, 7);

    assertDetailError(new PeriodQuery(2025, 5), ErrorCode.PERIOD_NOT_FOUND);
  }

  @Test
  @DisplayName("상세도 연도 데이터가 없으면 PERIOD_NOT_FOUND를 던진다")
  void detailYearNotLoaded() {
    givenNoPriority(2024, 0);
    givenLoadedYear();

    assertDetailError(new PeriodQuery(2024, null), ErrorCode.PERIOD_NOT_FOUND);
  }

  private void assertDetailError(PeriodQuery query, ErrorCode errorCode) {
    assertThatThrownBy(() -> regionService.getDetail(CODE, query))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(errorCode);
  }

  private void givenPeriods(MlPriorityRepository.PeriodKey... periods) {
    given(mlPriorityRepository.findPeriods("dong")).willReturn(List.of(periods));
  }

  private MlPriorityRepository.PeriodKey period(int year, int monthKey) {
    return new MlPriorityRepository.PeriodKey() {
      @Override
      public Integer getYear() {
        return year;
      }

      @Override
      public Integer getMonthKey() {
        return monthKey;
      }
    };
  }

  private void givenDetailRegion(boolean low, String note) {
    MlRegion region = new MlRegion();
    ReflectionTestUtils.setField(region, "typeNm", "정류장 접근 취약형");
    ReflectionTestUtils.setField(region, "typeDesc", "유형 설명");
    ReflectionTestUtils.setField(region, "typeChips", List.of("칩 하나", "칩 둘"));
    ReflectionTestUtils.setField(region, "lowReliability", low);
    ReflectionTestUtils.setField(region, "reliabilityNote", note);
    given(mlRegionRepository.findByYearAndUnitAndRegionCd(2025, "dong", CODE))
        .willReturn(Optional.of(region));
  }

  private void givenSameType(String... regionCodes) {
    List<MlRegion> regions = new ArrayList<>();
    for (String regionCode : regionCodes) {
      MlRegion region = new MlRegion();
      ReflectionTestUtils.setField(region, "regionCd", regionCode);
      regions.add(region);
    }
    given(mlRegionRepository.findByYearAndUnitAndTypeNm(2025, "dong", "정류장 접근 취약형"))
        .willReturn(regions);
  }

  private void givenAnnualRows(MlPriority... rows) {
    given(mlPriorityRepository.findByYearAndMonthKeyAndUnit(2025, 0, "dong"))
        .willReturn(List.of(rows));
  }

  private MlPriority priorityRow(int rank, String code, String name) {
    MlPriority row = new MlPriority();
    ReflectionTestUtils.setField(row, "rankTotal", rank);
    ReflectionTestUtils.setField(row, "regionCd", code);
    ReflectionTestUtils.setField(row, "regionNm", name);
    return row;
  }

  private OptionJson option(String mode, String name, String status) {
    return new OptionJson(mode, name, null, null, status, null, null, null, null, null, null);
  }

  private void assertError(PeriodQuery query, ErrorCode errorCode) {
    assertThatThrownBy(() -> regionService.getPreview(CODE, query))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(errorCode);
  }

  private void givenNoPriority(int year, int monthKey) {
    given(
            mlPriorityRepository.findByYearAndMonthKeyAndUnitAndRegionCd(
                year, monthKey, "dong", CODE))
        .willReturn(Optional.empty());
  }

  private void givenLoadedYear() {
    given(mlPriorityRepository.findPeriods("dong"))
        .willReturn(
            List.of(
                new MlPriorityRepository.PeriodKey() {
                  @Override
                  public Integer getYear() {
                    return 2025;
                  }

                  @Override
                  public Integer getMonthKey() {
                    return 0;
                  }
                }));
  }

  private void givenPriority(int monthKey, double score, int rank) {
    MlPriority priority = new MlPriority();
    ReflectionTestUtils.setField(priority, "regionCd", CODE);
    ReflectionTestUtils.setField(priority, "regionNm", "포곡읍");
    ReflectionTestUtils.setField(priority, "gu", "처인구");
    ReflectionTestUtils.setField(priority, "scoreTotal", score);
    ReflectionTestUtils.setField(priority, "rankTotal", rank);
    given(
            mlPriorityRepository.findByYearAndMonthKeyAndUnitAndRegionCd(
                2025, monthKey, "dong", CODE))
        .willReturn(Optional.of(priority));
  }

  private void givenRegion() {
    MlRegion region = new MlRegion();
    ReflectionTestUtils.setField(region, "typeNm", "정류장 접근 취약형");
    ReflectionTestUtils.setField(region, "typeDesc", "유형 설명");
    given(mlRegionRepository.findByYearAndUnitAndRegionCd(2025, "dong", CODE))
        .willReturn(Optional.of(region));
  }

  private void givenExplanation(boolean keep, List<OptionJson> options) {
    MlExplanation explanation = new MlExplanation();
    ReflectionTestUtils.setField(explanation, "isKeep", keep);
    ReflectionTestUtils.setField(explanation, "options", options);
    ReflectionTestUtils.setField(explanation, "summary", "한 줄 요약");
    given(mlExplanationRepository.findByYearAndUnitAndRegionCd(2025, "dong", CODE))
        .willReturn(Optional.of(explanation));
  }
}
