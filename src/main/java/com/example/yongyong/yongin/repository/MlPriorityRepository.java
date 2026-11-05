package com.example.yongyong.yongin.repository;

import com.example.yongyong.yongin.entity.MlPriority;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface MlPriorityRepository extends Repository<MlPriority, MlPriority.Id> {

  interface PeriodKey {
    Integer getYear();

    Integer getMonthKey();
  }

  List<MlPriority> findByYearAndMonthKeyAndUnit(Integer year, Integer monthKey, String unit);

  Optional<MlPriority> findByYearAndMonthKeyAndUnitAndRegionCd(
      Integer year, Integer monthKey, String unit, String regionCd);

  @Query(
      "select distinct p.year as year, p.monthKey as monthKey from MlPriority p"
          + " where p.unit = :unit order by p.year, p.monthKey")
  List<PeriodKey> findPeriods(String unit);
}
