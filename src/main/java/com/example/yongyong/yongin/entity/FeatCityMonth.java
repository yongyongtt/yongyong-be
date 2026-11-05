package com.example.yongyong.yongin.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(catalog = "yongin", name = "feat_city_month")
@IdClass(FeatCityMonth.Id.class)
@Getter
public class FeatCityMonth {

  public record Id(Integer year, Integer month) {}

  @jakarta.persistence.Id private Integer year;
  @jakarta.persistence.Id private Integer month;
  private Double taxiReq;
  private Double taxiMatched;
  private Double taxiMatchRate;
  private Double taxiUnmatchedRate;
  private Double fpAvg;
}
