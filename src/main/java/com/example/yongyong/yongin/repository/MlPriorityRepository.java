package com.example.yongyong.yongin.repository;

import com.example.yongyong.yongin.entity.MlPriority;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface MlPriorityRepository extends Repository<MlPriority, MlPriority.Id> {

  List<MlPriority> findByYearAndMonthAndUnit(Integer year, Integer month, String unit);

  // 월별 달만 (연간 행 month 0 제외)
  @Query(
      "select distinct p.month from MlPriority p"
          + " where p.year = :year and p.unit = :unit and p.month <> 0")
  List<Integer> findMonths(Integer year, String unit);
}
