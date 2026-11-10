package com.example.yongyong.ml.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.yongyong.common.exception.BusinessException;
import com.example.yongyong.common.exception.ErrorCode;
import com.example.yongyong.ml.dto.MlRunRequest;
import com.example.yongyong.ml.dto.MlRunStatus;
import com.example.yongyong.ml.service.MlService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MlController.class)
class MlControllerTest {

  private static final String RUN_URL = "/api/ml/run";

  @Autowired private MockMvc mockMvc;
  @MockitoBean private MlService mlService;

  @Test
  @DisplayName("실행 요청 결과를 ApiResponse로 내리고 null 필드도 null로 내린다")
  void returnsRunStatus() throws Exception {
    given(mlService.run(new MlRunRequest(2025, 1)))
        .willReturn(
            new MlRunStatus(2025, 1, "RUNNING", null, "2026-11-08T22:30:05+09:00", null, 0, 900));

    mockMvc
        .perform(
            post(RUN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"year\": 2025, \"month\": 1}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.error").isEmpty())
        .andExpect(jsonPath("$.data.year").value(2025))
        .andExpect(jsonPath("$.data.month").value(1))
        .andExpect(jsonPath("$.data.status").value("RUNNING"))
        .andExpect(jsonPath("$.data.exitCode").value((Object) null))
        .andExpect(jsonPath("$.data.startedAt").value("2026-11-08T22:30:05+09:00"))
        .andExpect(jsonPath("$.data.finishedAt").value((Object) null))
        .andExpect(jsonPath("$.data.elapsedSec").value(0))
        .andExpect(jsonPath("$.data.expectedSec").value(900));
  }

  @ParameterizedTest
  @ValueSource(strings = {"{\"month\": 1}", "{\"year\": 2025, \"month\": 13}"})
  @DisplayName("year가 없거나 month가 범위 밖이면 400 INVALID_REQUEST")
  void invalidRequest(String body) throws Exception {
    mockMvc
        .perform(post(RUN_URL).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
  }

  @Test
  @DisplayName("이미 실행 중이면 409 ML_RUNNING")
  void running() throws Exception {
    given(mlService.run(any())).willThrow(new BusinessException(ErrorCode.ML_RUNNING));

    mockMvc
        .perform(post(RUN_URL).contentType(MediaType.APPLICATION_JSON).content("{\"year\": 2025}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.data").isEmpty())
        .andExpect(jsonPath("$.error.code").value("ML_RUNNING"))
        .andExpect(jsonPath("$.error.message").value("이미 실행 중입니다."));
  }

  @Test
  @DisplayName("상태 조회 결과를 ApiResponse로 내린다")
  void returnsStatus() throws Exception {
    given(mlService.status())
        .willReturn(new MlRunStatus(null, null, "NONE", null, null, null, null, 900));

    mockMvc
        .perform(get(RUN_URL + "/status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.status").value("NONE"))
        .andExpect(jsonPath("$.data.year").isEmpty());
  }
}
