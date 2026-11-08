package com.example.yongyong;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

// 11/8 seed 기준. 기대값 SQL은 각 테스트 주석에
@Tag("db")
@SpringBootTest
@AutoConfigureMockMvc
class PeriodRuleIntegrationTest extends MariaDbTestSupport {

  private static final String POGOK = "4146125000";

  @Autowired private MockMvc mockMvc;

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/api/dashboard",
        "/api/dashboard/map",
        "/api/regions/" + POGOK + "/preview",
        "/api/regions/" + POGOK,
        "/api/regions/" + POGOK + "/comparison",
        "/api/regions/" + POGOK + "/recommendation"
      })
  @DisplayName("공통 달에 없는 8월은 404 SUITABILITY_NOT_READY, 7월과 연간은 200이다")
  void monthRule(String url) throws Exception {
    // 공통 달 = ml_priority(1~12) ∩ ml_region(1~7) ∩ ml_explanation(1~7) = 1~7
    mockMvc
        .perform(get(url).param("year", "2025").param("month", "8"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("SUITABILITY_NOT_READY"))
        .andExpect(jsonPath("$.error.message").value("존재하지 않는 달입니다."));
    mockMvc.perform(get(url).param("year", "2025").param("month", "7")).andExpect(status().isOk());
    mockMvc.perform(get(url).param("year", "2025")).andExpect(status().isOk());
  }

  @Test
  @DisplayName("연간 대시보드는 월 평균 점수와 재계산 순위(동점 공동 1위)를 쓰고 period는 1~12월 YEAR_ALL이다")
  void annualDashboard() throws Exception {
    // 지역별 AVG(score_total) (month IS NOT NULL) → ROUND → RANK():
    //   이동읍 65.83→66 1위, 원삼면 66.01→66 1위, 모현읍·백암면 63 3위
    // 평균 50 이상 9곳, 지역 평균들의 평균 38.31, MAX(trained_at) 2026-11-08 20:55
    mockMvc
        .perform(get("/api/dashboard").param("year", "2025"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.period.year").value(2025))
        .andExpect(jsonPath("$.data.period.months", hasSize(12)))
        .andExpect(jsonPath("$.data.period.type").value("YEAR_ALL"))
        .andExpect(jsonPath("$.data.updatedAt").value("2026-11-08 20:55"))
        .andExpect(jsonPath("$.data.summary.regionCount").value(38))
        .andExpect(jsonPath("$.data.summary.vulnerableCount").value(9))
        .andExpect(jsonPath("$.data.summary.avgScore").value(38.3))
        .andExpect(jsonPath("$.data.top10[0].regionName").value("이동읍"))
        .andExpect(jsonPath("$.data.top10[0].rank").value(1))
        .andExpect(jsonPath("$.data.top10[1].regionName").value("원삼면"))
        .andExpect(jsonPath("$.data.top10[1].rank").value(1))
        .andExpect(jsonPath("$.data.top10[2].rank").value(3))
        .andExpect(jsonPath("$.data.top10[3].rank").value(3))
        .andExpect(jsonPath("$.data.top10[4].rank").value(5));
  }

  @Test
  @DisplayName("연간 미리보기는 NULL 행(60.3)이 아닌 월 평균 59점·7위, 유형과 추천은 최근 달(7월)이다")
  void annualPreview() throws Exception {
    // SELECT AVG(score_total) ... region_cd='4146125000' AND month IS NOT NULL; -- 58.96 → 59, 7위
    // SELECT summary FROM ml_explanation WHERE month=7 AND region_cd='4146125000';
    mockMvc
        .perform(get("/api/regions/" + POGOK + "/preview").param("year", "2025"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.score").value(59))
        .andExpect(jsonPath("$.data.rank").value(7))
        .andExpect(jsonPath("$.data.type.name").value("정류장 접근 취약형"))
        .andExpect(jsonPath("$.data.summary").value("정류장이 멀고 걷기 힘들어 똑버스 도입 검토"))
        .andExpect(jsonPath("$.data.period.type").value("YEAR_ALL"))
        .andExpect(jsonPath("$.data.typePeriod.months", contains(7)))
        .andExpect(jsonPath("$.data.typePeriod.type").value("YEAR_LATEST"));
  }

  @Test
  @DisplayName("7월 미리보기는 그 달 행만 써서 60점, 동부동(60.2)과 동점 7위다")
  void monthlyPreview() throws Exception {
    // SELECT region_cd, score_total FROM ml_priority WHERE month=7 ORDER BY score_total DESC;
    //   -- 6위까지 67,66,63,63,62,61 / 동부동 60.2, 포곡읍 60.1 → 둘 다 60점 7위
    mockMvc
        .perform(
            get("/api/regions/" + POGOK + "/preview").param("year", "2025").param("month", "7"))
        .andExpect(jsonPath("$.data.score").value(60))
        .andExpect(jsonPath("$.data.rank").value(7))
        .andExpect(jsonPath("$.data.period.months", contains(7)))
        .andExpect(jsonPath("$.data.period.type").value("MONTH"))
        .andExpect(jsonPath("$.data.typePeriod.type").value("MONTH"));
  }

  @Test
  @DisplayName("취약지역 수는 화면 점수(정수 반올림) 50점 이상으로 센다")
  void vulnerableCountByRoundedScore() throws Exception {
    // SELECT SUM(FLOOR(score_total+0.5)>=50), SUM(is_vulnerable) FROM ml_priority
    //   WHERE year=2025 AND month=7 AND unit='dong'; -- 9, 8 (DB is_vulnerable보다 1곳 많음)
    mockMvc
        .perform(get("/api/dashboard").param("year", "2025").param("month", "7"))
        .andExpect(jsonPath("$.data.summary.vulnerableCount").value(9));
  }

  @Test
  @DisplayName("연간 상세의 같은 유형 지역은 최근 달 기준 5곳, 자기 자신은 없다")
  void annualDetail() throws Exception {
    // SELECT region_cd FROM ml_region WHERE month=7 AND type_nm='정류장 접근 취약형'; -- 포곡읍 포함 6곳
    mockMvc
        .perform(get("/api/regions/" + POGOK).param("year", "2025"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.header.score").value(59))
        .andExpect(jsonPath("$.data.period.months", hasSize(12)))
        .andExpect(jsonPath("$.data.typePeriod.months", contains(7)))
        .andExpect(jsonPath("$.data.type.sameTypeRegions", hasSize(5)))
        .andExpect(
            jsonPath("$.data.type.sameTypeRegions[*].regionCode", not(Matchers.hasItem(POGOK))));
  }

  @Test
  @DisplayName("연간 추천은 최근 달(7월) 문장과 같은 달 점수를 내린다")
  void annualRecommendation() throws Exception {
    // SELECT created_at, JSON_EXTRACT(options,'$[*].score') FROM ml_explanation
    //   WHERE month=7 AND region_cd='4146125000'; -- 2026-11-08 20:56, [80.5, 45.8, 27.5]
    // ml_suitability 같은 달 (DRT 80.5, BUS 45.8, HAPPY_TAXI 27.5)과 일치
    mockMvc
        .perform(get("/api/regions/" + POGOK + "/recommendation").param("year", "2025"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.createdAt").value("2026-11-08 20:56"))
        .andExpect(jsonPath("$.data.options[0].score").value(81))
        .andExpect(jsonPath("$.data.options[1].score").value(46))
        .andExpect(jsonPath("$.data.options[2].score").value(28))
        .andExpect(jsonPath("$.data.period.year").value(2025))
        .andExpect(jsonPath("$.data.period.months", contains(7)))
        .andExpect(jsonPath("$.data.period.type").value("YEAR_LATEST"));
  }
}
