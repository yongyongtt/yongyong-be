package com.example.yongyong.common.response;

import com.example.yongyong.common.query.PeriodQuery;
import java.util.List;

// 응답 값이 몇 년 몇 월 데이터인지. months는 반영한 달 (월 조회면 [고른 달])
public record PeriodResponse(Integer year, List<Integer> months, Type type) {

  public enum Type {
    YEAR_ALL,
    YEAR_LATEST, // 2026-11-11부터 내리지 않음. 프론트 호환으로 남김 (decisions D16)
    MONTH
  }

  // 연간이면 YEAR_ALL + allMonths(그 해 전체를 반영한 값), 월이면 MONTH + [그 달]
  public static PeriodResponse all(PeriodQuery query, List<Integer> allMonths) {
    return query.month() == null
        ? new PeriodResponse(query.year(), allMonths, Type.YEAR_ALL)
        : new PeriodResponse(query.year(), List.of(query.month()), Type.MONTH);
  }
}
