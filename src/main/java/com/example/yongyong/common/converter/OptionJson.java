package com.example.yongyong.common.converter;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record OptionJson(
    String mode,
    String name,
    String subtitle,
    String desc,
    String status,
    Integer rank,
    Double score,
    @JsonProperty("is_current") Boolean isCurrent,
    List<String> reasons,
    List<String> facts,
    Object together) {}
