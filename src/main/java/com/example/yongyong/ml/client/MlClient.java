package com.example.yongyong.ml.client;

import com.example.yongyong.common.exception.BusinessException;
import com.example.yongyong.common.exception.ErrorCode;
import com.example.yongyong.ml.dto.MlRunRequest;
import com.example.yongyong.ml.dto.MlRunStatus;
import java.time.Duration;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class MlClient {

  private final RestClient restClient;

  @Autowired
  public MlClient(@Value("${app.ml.base-url}") String baseUrl) {
    this(RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory()));
  }

  // Tests bind MockRestServiceServer to this builder
  MlClient(RestClient.Builder builder) {
    this.restClient = builder.build();
  }

  public MlRunStatus run(MlRunRequest request) {
    return call(() ->
        restClient
            .post()
            .uri("/api/ml/run")
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .onStatus(s -> s.value() == 409, fail(ErrorCode.ML_RUNNING))
            .onStatus(s -> s.value() == 422, fail(ErrorCode.INVALID_REQUEST))
            .onStatus(HttpStatusCode::isError, fail(ErrorCode.ML_UNAVAILABLE))
            .body(MlRunStatus.class));
  }

  public MlRunStatus status() {
    return call(() ->
        restClient
            .get()
            .uri("/api/ml/run/status")
            .retrieve()
            .onStatus(HttpStatusCode::isError, fail(ErrorCode.ML_UNAVAILABLE))
            .body(MlRunStatus.class));
  }

  private static MlRunStatus call(Supplier<MlRunStatus> request) {
    try {
      return request.get();
    } catch (RestClientException e) {
      throw new BusinessException(ErrorCode.ML_UNAVAILABLE);
    }
  }

  private static RestClient.ResponseSpec.ErrorHandler fail(ErrorCode errorCode) {
    return (req, res) -> {
      throw new BusinessException(errorCode);
    };
  }

  // POST answers within 1-2s (the run itself continues in the background)
  private static SimpleClientHttpRequestFactory requestFactory() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(2));
    factory.setReadTimeout(Duration.ofSeconds(10));
    return factory;
  }
}
