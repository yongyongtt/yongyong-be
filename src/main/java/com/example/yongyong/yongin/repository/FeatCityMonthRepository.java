package com.example.yongyong.yongin.repository;

import com.example.yongyong.yongin.entity.FeatCityMonth;
import java.util.List;
import org.springframework.data.repository.Repository;

public interface FeatCityMonthRepository extends Repository<FeatCityMonth, FeatCityMonth.Id> {

  List<FeatCityMonth> findByYear(Integer year);
}
