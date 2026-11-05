package com.example.yongyong.yongin.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(catalog = "yongin", name = "feat_city")
@IdClass(FeatCity.Id.class)
@Getter
public class FeatCity {

  public record Id(Integer year, String unit) {}

  @jakarta.persistence.Id private Integer year;
  @jakarta.persistence.Id private String unit;
  private Double nStops;
  private Double elderlyRatio;
}
