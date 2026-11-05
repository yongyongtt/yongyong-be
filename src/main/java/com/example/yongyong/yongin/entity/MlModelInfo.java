package com.example.yongyong.yongin.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(catalog = "yongin", name = "ml_model_info")
@IdClass(MlModelInfo.Id.class)
@Getter
public class MlModelInfo {

  public record Id(Integer year, String model) {}

  @jakarta.persistence.Id private Integer year;
  @jakarta.persistence.Id private String model;
  private String trainedAt;
}
