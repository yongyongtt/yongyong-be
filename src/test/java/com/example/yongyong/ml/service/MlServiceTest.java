package com.example.yongyong.ml.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.example.yongyong.ml.client.MlClient;
import com.example.yongyong.ml.dto.MlRunRequest;
import com.example.yongyong.ml.dto.MlRunStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MlServiceTest {

  @Mock private MlClient mlClient;
  @InjectMocks private MlService mlService;

  @Test
  @DisplayName("실행 요청을 클라이언트에 그대로 넘기고 결과를 돌려준다")
  void run() {
    MlRunRequest request = new MlRunRequest(2025, 1);
    MlRunStatus running = new MlRunStatus(2025, 1, "RUNNING", null, "t", null, 0, 900);
    given(mlClient.run(request)).willReturn(running);

    assertThat(mlService.run(request)).isEqualTo(running);
  }
}
