package com.example.yongyong.yongin.repository;

import com.example.yongyong.yongin.entity.MlExplanation;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface MlExplanationRepository extends Repository<MlExplanation, MlExplanation.Id> {

  Optional<MlExplanation> findByYearAndUnitAndRegionCd(Integer year, String unit, String regionCd);
}
