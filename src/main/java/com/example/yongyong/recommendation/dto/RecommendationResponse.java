package com.example.yongyong.recommendation.dto;

import java.util.List;

public record RecommendationResponse(
    Boolean isKeep,
    String createdAt,
    List<Option> options,
    List<String> reasons,
    List<String> cautions,
    Boolean lowReliability) {

  public record Option(
      String mode,
      String name,
      String subtitle,
      String desc,
      String status,
      Integer rank,
      Integer score,
      Boolean isCurrent,
      List<String> reasons,
      List<String> facts,
      Object together) {}
}
