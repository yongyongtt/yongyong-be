package com.example.yongyong.yongin.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(catalog = "yongin", name = "feat_region_month")
@IdClass(FeatRegionMonth.Id.class)
@Getter
public class FeatRegionMonth {

  public record Id(Integer year, Integer month, String dongCd) {}

  @jakarta.persistence.Id private Integer year;
  @jakarta.persistence.Id private Integer month;
  @jakarta.persistence.Id private String dongCd;
  private Double taxiMatchRate;
  private Double fpAvg;
  private Boolean fpAnomaly;
}
