package com.example.yongyong.yongin.repository;

import com.example.yongyong.yongin.entity.MlModelInfo;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface MlModelInfoRepository extends Repository<MlModelInfo, MlModelInfo.Id> {

  // month가 null이면 그 연도 전체에서 최대값
  @Query(
      "select max(m.trainedAt) from MlModelInfo m"
          + " where m.year = :year and (:month is null or m.month = :month)")
  String findLatestTrainedAt(Integer year, Integer month);
}
