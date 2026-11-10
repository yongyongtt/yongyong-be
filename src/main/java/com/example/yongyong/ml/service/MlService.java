package com.example.yongyong.ml.service;

import com.example.yongyong.ml.client.MlClient;
import com.example.yongyong.ml.dto.MlRunRequest;
import com.example.yongyong.ml.dto.MlRunStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MlService {

  private final MlClient mlClient;

  public MlRunStatus run(MlRunRequest request) {
    return mlClient.run(request);
  }

  public MlRunStatus status() {
    return mlClient.status();
  }
}
