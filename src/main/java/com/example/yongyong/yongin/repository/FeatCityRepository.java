package com.example.yongyong.yongin.repository;

import com.example.yongyong.yongin.entity.FeatCity;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface FeatCityRepository extends Repository<FeatCity, FeatCity.Id> {

  Optional<FeatCity> findByYearAndUnit(Integer year, String unit);
}
