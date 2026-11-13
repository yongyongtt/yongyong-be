package com.example.yongyong.plan;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.yongyong.MariaDbTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

// days·score 매핑과 지도 category는 seed로 API를 호출해 확인한다
@Tag("db")
@SpringBootTest
@AutoConfigureMockMvc
class PlanIntegrationTest extends MariaDbTestSupport {

  private static final String PLAN3 = "$.data.plans[?(@.plan == 3)]";

  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("2025 연간 9-1은 days 61이고, 3안 투입 첫 줄 모현읍 score는 64.7을 반올림한 65다")
  void annualPlans() throws Exception {
    // SELECT plan, days FROM plan_summary WHERE year=2025 AND month IS NULL; -- 세 안 모두 61
    // SELECT seq, region_nm, score_total FROM plan_region WHERE year=2025 AND month IS NULL
    //   AND plan=3 AND unit='dong' AND is_included=1 ORDER BY seq; -- 1 모현읍 64.7
    mockMvc
        .perform(get("/api/plans").param("year", "2025"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.plans[*].days", containsInAnyOrder(61, 61, 61)))
        .andExpect(jsonPath(PLAN3 + ".regions[0].regionName").value("모현읍"))
        .andExpect(jsonPath(PLAN3 + ".regions[0].score").value(65))
        .andExpect(jsonPath("$.data.plans[*].byRegion").isEmpty());
  }

  @Test
  @DisplayName("2025 연간 9-2 3안은 38개이고 DRT 5·SKIPPED 4·KEEP 9·NONE 20·BUS 0·HAPPY_TAXI 0이다")
  void annualMapPlan3() throws Exception {
    // SELECT status, mode, region_nm FROM plan_region WHERE year=2025 AND month IS NULL AND plan=3
    //   AND unit='dong'; -- 포함 DRT 5, 예산 밖 4, 후보 아님 KEEP 9 · BUS 14 · DRT 6
    String regions = PLAN3 + ".regions";
    mockMvc
        .perform(get("/api/plans/map").param("year", "2025"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.plans[*].plan", containsInAnyOrder(1, 2, 3)))
        .andExpect(jsonPath(regions + "[*]", hasSize(38)))
        .andExpect(
            jsonPath(
                regions + "[?(@.category == 'DRT')].regionCode",
                containsInAnyOrder(
                    "4146125300", "4146125600", "4146134000", "4146125000", "4146136000")))
        .andExpect(
            jsonPath(
                regions + "[?(@.category == 'SKIPPED')].regionCode",
                containsInAnyOrder("4146125900", "4146556000", "4146135000", "4146154000")))
        .andExpect(jsonPath(regions + "[?(@.category == 'KEEP')]", hasSize(9)))
        .andExpect(jsonPath(regions + "[?(@.category == 'NONE')]", hasSize(20)))
        .andExpect(jsonPath(regions + "[?(@.category == 'BUS')]", hasSize(0)))
        .andExpect(jsonPath(regions + "[?(@.category == 'HAPPY_TAXI')]", hasSize(0)));
  }
}
