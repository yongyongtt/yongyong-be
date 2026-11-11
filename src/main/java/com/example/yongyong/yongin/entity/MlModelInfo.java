package com.example.yongyong.yongin.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import lombok.Getter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;

// 연간(month NULL) 행은 month 0으로 읽는다 (MlPriority와 같은 이유, decisions D16)
@Entity
@Immutable
@Subselect("select year, coalesce(month, 0) as month, model, trained_at from yongin.ml_model_info")
@IdClass(MlModelInfo.Id.class)
@Getter
public class MlModelInfo {

  public record Id(Integer year, Integer month, String model) {}

  @jakarta.persistence.Id private Integer year;
  @jakarta.persistence.Id private Integer month;
  @jakarta.persistence.Id private String model;
  private String trainedAt;
}
