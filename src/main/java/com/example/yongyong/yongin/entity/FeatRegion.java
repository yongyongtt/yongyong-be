package com.example.yongyong.yongin.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(catalog = "yongin", name = "feat_region")
@IdClass(FeatRegion.Id.class)
@Getter
public class FeatRegion {

  public record Id(Integer year, String dongCd) {}

  @jakarta.persistence.Id private Integer year;
  @jakarta.persistence.Id private String dongCd;
  private Double taxiMatchRate;
  private Integer nStops;
  private Double ratio65;
  private Double fpAvg;
}
