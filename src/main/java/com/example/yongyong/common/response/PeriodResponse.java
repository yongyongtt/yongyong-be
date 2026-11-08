package com.example.yongyong.common.response;

import com.example.yongyong.common.query.PeriodQuery;
import java.util.List;

// 응답 값이 몇 년 몇 월 데이터인지. months는 반영한 달 (월 조회면 [고른 달])
public record PeriodResponse(Integer year, List<Integer> months, Type type) {

  public enum Type {
    YEAR_ALL,
    YEAR_LATEST,
    MONTH
  }

  // 연간이면 그 해 월별 값 전체(allMonths)를 반영한 값
  public static PeriodResponse all(PeriodQuery query, List<Integer> allMonths) {
    return query.month() == null
        ? new PeriodResponse(query.year(), allMonths, Type.YEAR_ALL)
        : month(query);
  }

  // 연간이면 그 해 최근 달(latestMonth) 값. 최근 달이 없으면 빈 목록
  public static PeriodResponse latest(PeriodQuery query, Integer latestMonth) {
    if (query.month() != null) {
      return month(query);
    }
    return new PeriodResponse(
        query.year(), latestMonth == null ? List.of() : List.of(latestMonth), Type.YEAR_LATEST);
  }

  private static PeriodResponse month(PeriodQuery query) {
    return new PeriodResponse(query.year(), List.of(query.month()), Type.MONTH);
  }
}
