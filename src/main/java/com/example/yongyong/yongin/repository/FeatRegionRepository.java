package com.example.yongyong.yongin.repository;

import com.example.yongyong.yongin.entity.FeatRegion;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface FeatRegionRepository extends Repository<FeatRegion, FeatRegion.Id> {

  Optional<FeatRegion> findByYearAndDongCd(Integer year, String dongCd);

  boolean existsByYear(Integer year);
}
