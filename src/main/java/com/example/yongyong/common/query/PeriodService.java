package com.example.yongyong.common.query;

import static com.example.yongyong.common.query.PeriodQuery.UNIT_DONG;

import com.example.yongyong.common.exception.BusinessException;
import com.example.yongyong.common.exception.ErrorCode;
import com.example.yongyong.yongin.repository.MlExplanationRepository;
import com.example.yongyong.yongin.repository.MlPriorityRepository;
import com.example.yongyong.yongin.repository.MlRegionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PeriodService {

  private final MlPriorityRepository mlPriorityRepository;
  private final MlRegionRepository mlRegionRepository;
  private final MlExplanationRepository mlExplanationRepository;

  // ml_priority·ml_region·ml_explanation에 공통으로 있는 달 (오름차순)
  public List<Integer> availableMonths(Integer year) {
    List<Integer> regionMonths = mlRegionRepository.findMonths(year, UNIT_DONG);
    List<Integer> explanationMonths = mlExplanationRepository.findMonths(year, UNIT_DONG);
    return mlPriorityRepository.findMonths(year, UNIT_DONG).stream()
        .filter(regionMonths::contains)
        .filter(explanationMonths::contains)
        .sorted()
        .toList();
  }

  // 연간(month 없음)은 항상 통과. 연도 데이터가 아예 없으면 PERIOD_NOT_FOUND, 공통 달에 없으면 SUITABILITY_NOT_READY
  public void checkMonth(PeriodQuery query) {
    if (query.month() == null) {
      return;
    }
    List<Integer> months = availableMonths(query.year());
    if (months.isEmpty()) {
      throw new BusinessException(ErrorCode.PERIOD_NOT_FOUND);
    }
    if (!months.contains(query.month())) {
      throw new BusinessException(ErrorCode.SUITABILITY_NOT_READY);
    }
  }

  // month를 골랐으면 그 달, 아니면 months 중 가장 큰 달 (없으면 null)
  public static Integer monthOrLatest(PeriodQuery query, List<Integer> months) {
    if (query.month() != null) {
      return query.month();
    }
    return months.stream().max(Integer::compare).orElse(null);
  }
}
