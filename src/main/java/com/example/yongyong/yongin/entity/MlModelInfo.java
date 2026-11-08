package com.example.yongyong.yongin.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Immutable
@Table(catalog = "yongin", name = "ml_model_info")
@SQLRestriction("month is not null")
@IdClass(MlModelInfo.Id.class)
@Getter
public class MlModelInfo {

  public record Id(Integer year, Integer month, String model) {}

  @jakarta.persistence.Id private Integer year;
  @jakarta.persistence.Id private Integer month;
  @jakarta.persistence.Id private String model;
  private String trainedAt;
}
