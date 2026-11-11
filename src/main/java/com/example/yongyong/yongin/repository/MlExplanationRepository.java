package com.example.yongyong.yongin.repository;

import com.example.yongyong.yongin.entity.MlExplanation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface MlExplanationRepository extends Repository<MlExplanation, MlExplanation.Id> {

  Optional<MlExplanation> findByYearAndMonthAndUnitAndRegionCd(
      Integer year, Integer month, String unit, String regionCd);

  // 월별 달만 (연간 행 month 0 제외)
  @Query(
      "select distinct e.month from MlExplanation e"
          + " where e.year = :year and e.unit = :unit and e.month <> 0")
  List<Integer> findMonths(Integer year, String unit);
}
