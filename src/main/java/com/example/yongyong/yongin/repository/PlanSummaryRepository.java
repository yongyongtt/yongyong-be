package com.example.yongyong.yongin.repository;

import com.example.yongyong.yongin.entity.PlanSummary;
import java.util.List;
import org.springframework.data.repository.Repository;

public interface PlanSummaryRepository extends Repository<PlanSummary, PlanSummary.Id> {

  // month 0 = 연간 행
  List<PlanSummary> findByYearAndMonth(Integer year, Integer month);
}
