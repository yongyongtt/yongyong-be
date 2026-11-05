package com.example.yongyong.yongin.repository;

import com.example.yongyong.yongin.entity.FeatRegionMonth;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface FeatRegionMonthRepository extends Repository<FeatRegionMonth, FeatRegionMonth.Id> {

  Optional<FeatRegionMonth> findByYearAndMonthAndDongCd(Integer year, Integer month, String dongCd);

  List<FeatRegionMonth> findByYearAndDongCd(Integer year, String dongCd);

  boolean existsByYear(Integer year);
}
