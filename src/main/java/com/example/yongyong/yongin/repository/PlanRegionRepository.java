package com.example.yongyong.yongin.repository;

import com.example.yongyong.yongin.entity.PlanRegion;
import java.util.List;
import org.springframework.data.repository.Repository;

public interface PlanRegionRepository extends Repository<PlanRegion, PlanRegion.Id> {

  // month 0 = 연간 행
  List<PlanRegion> findByYearAndMonthAndUnit(Integer year, Integer month, String unit);
}
