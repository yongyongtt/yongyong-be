package com.example.yongyong.yongin.repository;

import com.example.yongyong.yongin.entity.MlRegion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface MlRegionRepository extends Repository<MlRegion, MlRegion.Id> {

  Optional<MlRegion> findByYearAndMonthAndUnitAndRegionCd(
      Integer year, Integer month, String unit, String regionCd);

  List<MlRegion> findByYearAndMonthAndUnitAndTypeNm(
      Integer year, Integer month, String unit, String typeNm);

  // 월별 달만 (연간 행 month 0 제외)
  @Query(
      "select distinct r.month from MlRegion r"
          + " where r.year = :year and r.unit = :unit and r.month <> 0")
  List<Integer> findMonths(Integer year, String unit);
}
