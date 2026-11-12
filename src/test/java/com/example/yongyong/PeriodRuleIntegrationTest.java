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

// 11/11 최종본 seed 기준. 기대값 SQL은 각 테스트 주석에
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
        "/api/regions/" + POGOK + "/recommendation",
        "/api/plans"
      })
  @DisplayName("공통 달(1~12)의 7월·12월과 연간은 200이다")
  void monthRule(String url) throws Exception {
    // 공통 달 = ml_priority ∩ ml_region ∩ ml_explanation의 DISTINCT month = 1~12
    // (공통 달에 없는 달은 seed에 없어 SUITABILITY_NOT_READY는 서비스 테스트에서 mock으로 본다)
    mockMvc.perform(get(url).param("year", "2025").param("month", "7")).andExpect(status().isOk());
    mockMvc.perform(get(url).param("year", "2025").param("month", "12")).andExpect(status().isOk());
    mockMvc.perform(get(url).param("year", "2025")).andExpect(status().isOk());
  }

  @Test
  @DisplayName("통합 최적안은 연도 데이터가 없으면 월을 골라도 SUITABILITY_NOT_READY보다 PERIOD_NOT_FOUND가 먼저다")
  void planYearFirst() throws Exception {
    // SELECT DISTINCT month FROM ml_priority WHERE year=2030; -- 0행 (seed는 2025만)
    mockMvc
        .perform(get("/api/plans").param("year", "2030").param("month", "3"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("PERIOD_NOT_FOUND"));
    mockMvc
        .perform(get("/api/plans").param("year", "2030"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("PERIOD_NOT_FOUND"));
  }

  @Test
  @DisplayName("연간 대시보드는 연간(NULL) 행 점수와 재계산 순위를 쓰고, 갱신 시각도 연간 행, period는 1~12월 YEAR_ALL이다")
  void annualDashboard() throws Exception {
    // SELECT region_nm, score_total, RANK() OVER (ORDER BY FLOOR(score_total+0.5) DESC) FROM
    // ml_priority
    //   WHERE year=2025 AND unit='dong' AND month IS NULL;
    //   -- 원삼면 67.9→68 1위, 이동읍 66.2→66 2위, 모현읍 64.7→65 3위, 백암면 63.9→64 4위, 양지면 61.7→62 5위
    // SUM(FLOOR(score_total+0.5)>=50) 9, AVG(score_total) 39.06
    // SELECT MAX(trained_at) FROM ml_model_info WHERE year=2025 AND month IS NULL; -- 2026-11-10
    // 17:00
    mockMvc
        .perform(get("/api/dashboard").param("year", "2025"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.period.year").value(2025))
        .andExpect(jsonPath("$.data.period.months", hasSize(12)))
        .andExpect(jsonPath("$.data.period.type").value("YEAR_ALL"))
        .andExpect(jsonPath("$.data.updatedAt").value("2026-11-10 17:00"))
        .andExpect(jsonPath("$.data.summary.regionCount").value(38))
        .andExpect(jsonPath("$.data.summary.vulnerableCount").value(9))
        .andExpect(jsonPath("$.data.summary.avgScore").value(39.1))
        .andExpect(jsonPath("$.data.top10[0].regionName").value("원삼면"))
        .andExpect(jsonPath("$.data.top10[0].score").value(68))
        .andExpect(jsonPath("$.data.top10[0].rank").value(1))
        .andExpect(jsonPath("$.data.top10[1].regionName").value("이동읍"))
        .andExpect(jsonPath("$.data.top10[1].rank").value(2))
        .andExpect(jsonPath("$.data.top10[2].regionName").value("모현읍"))
        .andExpect(jsonPath("$.data.top10[2].rank").value(3))
        .andExpect(jsonPath("$.data.top10[4].regionName").value("양지면"))
        .andExpect(jsonPath("$.data.top10[4].rank").value(5));
  }

  @Test
  @DisplayName("연간 미리보기는 점수·유형·요약이 모두 연간 행이고 typePeriod도 1~12월 YEAR_ALL이다")
  void annualPreview() throws Exception {
    // SELECT score_total FROM ml_priority WHERE ... month IS NULL AND region_cd='4146125000'; --
    // 60.3 → 60
    //   동부동 59.5 → 60과 공동 7위
    // SELECT summary FROM ml_explanation WHERE ... month IS NULL AND region_cd='4146125000';
    mockMvc
        .perform(get("/api/regions/" + POGOK + "/preview").param("year", "2025"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.score").value(60))
        .andExpect(jsonPath("$.data.rank").value(7))
        .andExpect(jsonPath("$.data.type.name").value("정류장 접근 취약형"))
        .andExpect(jsonPath("$.data.recommended[0].mode").value("DRT"))
        .andExpect(jsonPath("$.data.summary").value("정류장까지 멀고 고령 인구가 많아 똑버스 도입 검토"))
        .andExpect(jsonPath("$.data.period.type").value("YEAR_ALL"))
        .andExpect(jsonPath("$.data.typePeriod.months", hasSize(12)))
        .andExpect(jsonPath("$.data.typePeriod.type").value("YEAR_ALL"));
  }

  @Test
  @DisplayName("7월 미리보기는 그 달 행만 써서 60점, 동부동(60.2)과 동점 7위다")
  void monthlyPreview() throws Exception {
    // SELECT region_cd, score_total FROM ml_priority WHERE year=2025 AND unit='dong' AND month=7
    //   ORDER BY score_total DESC; -- 동부동 60.2, 포곡읍 60.1 → 둘 다 60점 7위
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
    // SELECT SUM(FLOOR(score_total+0.5)>=50) FROM ml_priority
    //   WHERE year=2025 AND month=7 AND unit='dong'; -- 9
    mockMvc
        .perform(get("/api/dashboard").param("year", "2025").param("month", "7"))
        .andExpect(jsonPath("$.data.summary.vulnerableCount").value(9));
  }

  @Test
  @DisplayName("연간 상세의 같은 유형 지역은 연간 행 기준 5곳이고 연간 점수 순이며 자기 자신은 없다")
  void annualDetail() throws Exception {
    // SELECT region_cd FROM ml_region WHERE year=2025 AND unit='dong' AND month IS NULL
    //   AND type_nm='정류장 접근 취약형'; -- 포곡읍 포함 6곳
    //   연간 점수 순: 이동읍 66, 모현읍 65, (포곡읍 60), 동천동 53, 보라동 44, 신봉동 40
    mockMvc
        .perform(get("/api/regions/" + POGOK).param("year", "2025"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.header.score").value(60))
        .andExpect(jsonPath("$.data.period.months", hasSize(12)))
        .andExpect(jsonPath("$.data.typePeriod.type").value("YEAR_ALL"))
        .andExpect(jsonPath("$.data.type.sameTypeRegions", hasSize(5)))
        .andExpect(jsonPath("$.data.type.sameTypeRegions[0].regionName").value("이동읍"))
        .andExpect(jsonPath("$.data.type.sameTypeRegions[4].regionName").value("신봉동"))
        .andExpect(
            jsonPath("$.data.type.sameTypeRegions[*].regionCode", not(Matchers.hasItem(POGOK))));
  }

  @Test
  @DisplayName("연간 추천은 연간 행 문장과 점수를 내리고 period는 1~12월 YEAR_ALL이다")
  void annualRecommendation() throws Exception {
    // SELECT created_at, JSON_EXTRACT(options,'$[*].score') FROM ml_explanation
    //   WHERE year=2025 AND unit='dong' AND month IS NULL AND region_cd='4146125000';
    //   -- 2026-11-10 17:00, [80.7, 46.8, 23.0]
    mockMvc
        .perform(get("/api/regions/" + POGOK + "/recommendation").param("year", "2025"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.createdAt").value("2026-11-10 17:00"))
        .andExpect(jsonPath("$.data.options[0].score").value(81))
        .andExpect(jsonPath("$.data.options[1].score").value(47))
        .andExpect(jsonPath("$.data.options[2].score").value(23))
        .andExpect(jsonPath("$.data.period.year").value(2025))
        .andExpect(jsonPath("$.data.period.months", hasSize(12)))
        .andExpect(jsonPath("$.data.period.type").value("YEAR_ALL"));
  }
}
