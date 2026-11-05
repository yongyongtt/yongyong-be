package com.example.yongyong.yongin.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(catalog = "yongin", name = "ml_region")
@IdClass(MlRegion.Id.class)
@Getter
public class MlRegion {

  public record Id(Integer year, String unit, String regionCd) {}

  @jakarta.persistence.Id private Integer year;
  @jakarta.persistence.Id private String unit;
  @jakarta.persistence.Id private String regionCd;
  private String typeNm;
  private String typeDesc;
}
