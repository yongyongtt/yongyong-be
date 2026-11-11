package com.example.yongyong.yongin.repository;

import com.example.yongyong.yongin.entity.MlModelInfo;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface MlModelInfoRepository extends Repository<MlModelInfo, MlModelInfo.Id> {

  // month 0 = 연간 행
  @Query("select max(m.trainedAt) from MlModelInfo m where m.year = :year and m.month = :month")
  String findLatestTrainedAt(Integer year, Integer month);
}
