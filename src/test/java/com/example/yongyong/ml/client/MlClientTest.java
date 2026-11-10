package com.example.yongyong.ml.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.example.yongyong.common.exception.BusinessException;
import com.example.yongyong.common.exception.ErrorCode;
import com.example.yongyong.ml.dto.MlRunRequest;
import com.example.yongyong.ml.dto.MlRunStatus;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

// FastAPI responses copied from the yong_ml run API spec (2026-11-08); no real call is made
class MlClientTest {

  private static final String RUN_URL = "http://ml/api/ml/run";
  private static final String STATUS_URL = "http://ml/api/ml/run/status";
  private static final String RUNNING =
      """
      {"year": 2025, "month": 1, "status": "RUNNING", "exitCode": null,
       "startedAt": "2026-11-08T22:30:05+09:00", "finishedAt": null,
       "elapsedSec": 0, "expectedSec": 900}
      """;

  private MockRestServiceServer server;
  private MlClient mlClient;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://ml");
    server = MockRestServiceServer.bindTo(builder).build();
    mlClient = new MlClient(builder);
  }

  @Test
  @DisplayName("202를 받으면 실행 상태를 그대로 돌려주고 연·월을 body로 보낸다")
  void runAccepted() {
    server
        .expect(requestTo(RUN_URL))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().json("{\"year\": 2025, \"month\": 1}"))
        .andRespond(
            withStatus(HttpStatus.ACCEPTED).contentType(MediaType.APPLICATION_JSON).body(RUNNING));

    MlRunStatus result = mlClient.run(new MlRunRequest(2025, 1));

    assertThat(result)
        .isEqualTo(
            new MlRunStatus(2025, 1, "RUNNING", null, "2026-11-08T22:30:05+09:00", null, 0, 900));
    server.verify();
  }

  @Test
  @DisplayName("월 없이 보내면 month를 null로 넘겨 연간으로 실행한다")
  void runYear() {
    server
        .expect(requestTo(RUN_URL))
        .andExpect(content().json("{\"year\": 2025, \"month\": null}"))
        .andRespond(
            withStatus(HttpStatus.ACCEPTED).contentType(MediaType.APPLICATION_JSON).body(RUNNING));

    mlClient.run(new MlRunRequest(2025, null));

    server.verify();
  }

  @Test
  @DisplayName("409를 받으면 ML_RUNNING을 던진다")
  void runConflict() {
    expectRunError(HttpStatus.CONFLICT, RUNNING);

    assertError(ErrorCode.ML_RUNNING);
  }

  @Test
  @DisplayName("422를 받으면 INVALID_REQUEST를 던진다")
  void runUnprocessable() {
    expectRunError(
        HttpStatus.UNPROCESSABLE_CONTENT,
        "{\"detail\": [{\"type\": \"less_than_equal\", \"loc\": [\"body\", \"month\"]}]}");

    assertError(ErrorCode.INVALID_REQUEST);
  }

  @Test
  @DisplayName("500을 받으면 ML_UNAVAILABLE을 던진다")
  void runServerError() {
    expectRunError(HttpStatus.INTERNAL_SERVER_ERROR, RUNNING.replace("\"RUNNING\"", "\"FAILED\""));

    assertError(ErrorCode.ML_UNAVAILABLE);
  }

  @Test
  @DisplayName("ML 서버에 연결하지 못하면 ML_UNAVAILABLE을 던진다")
  void runConnectionFailed() {
    server.expect(requestTo(RUN_URL)).andRespond(withException(new IOException("refused")));

    assertError(ErrorCode.ML_UNAVAILABLE);
  }

  @Test
  @DisplayName("상태 조회는 200 응답을 그대로 돌려준다")
  void status() {
    server
        .expect(requestTo(STATUS_URL))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withStatus(HttpStatus.OK)
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                    """
                    {"year": null, "month": null, "status": "NONE", "exitCode": null,
                     "startedAt": null, "finishedAt": null, "elapsedSec": null, "expectedSec": 900}
                    """));

    assertThat(mlClient.status())
        .isEqualTo(new MlRunStatus(null, null, "NONE", null, null, null, null, 900));
  }

  private void expectRunError(HttpStatus status, String body) {
    server
        .expect(requestTo(RUN_URL))
        .andRespond(withStatus(status).contentType(MediaType.APPLICATION_JSON).body(body));
  }

  private void assertError(ErrorCode errorCode) {
    assertThatThrownBy(() -> mlClient.run(new MlRunRequest(2025, 1)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(errorCode);
  }
}
