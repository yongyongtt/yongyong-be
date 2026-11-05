package com.example.yongyong.yongin.repository;

import com.example.yongyong.yongin.entity.MlRegion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface MlRegionRepository extends Repository<MlRegion, MlRegion.Id> {

  Optional<MlRegion> findByYearAndUnitAndRegionCd(Integer year, String unit, String regionCd);

  List<MlRegion> findByYearAndUnitAndTypeNm(Integer year, String unit, String typeNm);
}
