package com.example.yongyong.common.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.example.yongyong.common.exception.BusinessException;
import com.example.yongyong.common.exception.ErrorCode;
import com.example.yongyong.yongin.repository.MlExplanationRepository;
import com.example.yongyong.yongin.repository.MlPriorityRepository;
import com.example.yongyong.yongin.repository.MlRegionRepository;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PeriodServiceTest {

  @Mock private MlPriorityRepository mlPriorityRepository;
  @Mock private MlRegionRepository mlRegionRepository;
  @Mock private MlExplanationRepository mlExplanationRepository;
  @InjectMocks private PeriodService periodService;

  @Test
  @DisplayName("availableMonths는 세 테이블에 공통으로 있는 달을 오름차순으로 내린다")
  void availableMonths() {
    givenMonths();

    assertThat(periodService.availableMonths(2025)).containsExactly(1, 2, 3, 4, 5, 6, 7);
  }

  @Test
  @DisplayName("공통 달에 있는 월과 월 없는 연간 조회는 통과한다")
  void allowed() {
    givenMonths();

    assertThatCode(() -> periodService.checkMonth(new PeriodQuery(2025, 7)))
        .doesNotThrowAnyException();
    assertThatCode(() -> periodService.checkMonth(new PeriodQuery(2025, null)))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("공통 달에 없는 월(8월)이면 SUITABILITY_NOT_READY를 던진다")
  void monthNotReady() {
    givenMonths();

    assertError(new PeriodQuery(2025, 8), ErrorCode.SUITABILITY_NOT_READY);
  }

  @Test
  @DisplayName("연도 데이터가 아예 없으면 월을 골라도 PERIOD_NOT_FOUND를 던진다")
  void yearNotLoaded() {
    given(mlPriorityRepository.findMonths(2024, "dong")).willReturn(List.of());
    given(mlRegionRepository.findMonths(2024, "dong")).willReturn(List.of());
    given(mlExplanationRepository.findMonths(2024, "dong")).willReturn(List.of());

    assertError(new PeriodQuery(2024, 2), ErrorCode.PERIOD_NOT_FOUND);
  }

  private void givenMonths() {
    // 11/8 DB: ml_priority 1~12, ml_region·ml_explanation 1~7 (순서는 DB 그대로라 섞어 둔다)
    given(mlPriorityRepository.findMonths(2025, "dong"))
        .willReturn(IntStream.rangeClosed(1, 12).map(m -> 13 - m).boxed().toList());
    given(mlRegionRepository.findMonths(2025, "dong")).willReturn(List.of(7, 1, 2, 3, 4, 5, 6));
    given(mlExplanationRepository.findMonths(2025, "dong"))
        .willReturn(List.of(1, 2, 3, 4, 5, 6, 7));
  }

  private void assertError(PeriodQuery query, ErrorCode errorCode) {
    assertThatThrownBy(() -> periodService.checkMonth(query))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(errorCode);
  }
}
