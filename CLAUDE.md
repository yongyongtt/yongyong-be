# CLAUDE.md

용인 교통 사각지대 서비스 백엔드 (멋사 용용톤 2팀, 제출 2026-11-14).
지금 단계는 **기본 세팅**만 한다. 기능 API(지도, 우선순위, AI 추천 등)는 아직 만들지 않는다.

## 이번 작업 범위

1. 폴더(패키지) 구조 + Swagger
2. yongin DB(읽기 전용)와 yongin_app DB 연결 + yongin 엔티티 매핑
3. 공통 응답 포맷 + 전역 예외 처리

### 범위 밖 (만들지 않는다)

- yongin_app 엔티티 (ERD를 정한 뒤 따로 진행)
- 기능 API, 우선순위 계산, 가중치
- 민원, 로그인, LLM

## 실제 코드가 기준

프로젝트는 사용자가 직접 세팅하고 있다. 이 문서의 버전·패키지 이름·설정 키는 예정안이다.

- 작업 전에 `build.gradle`, 설정 파일(`application.yml` 또는 `.properties`), 실제 패키지 구조를 먼저 읽는다.
- 문서와 다르면 코드를 문서에 맞추지 말고, 다른 점을 알리고 이 문서를 실제에 맞게 고친다.
- 파일을 새로 만들거나 구조를 바꾸기 전에 계획(만들 파일 목록)을 먼저 보여주고 승인받는다.

## 기술 스택 (예정, 실제는 build.gradle 기준)

- Java 21, Spring Boot 4.1.x, Gradle
- Spring Web, Spring Data JPA, Validation, Lombok, MariaDB Driver
- springdoc-openapi `3.1.0` (Boot 4용. 2.x는 Boot 3용이라 쓰지 않는다)
- DB: MariaDB 12 (도커, `localhost:3307`), MariaDB Driver로 접속 (`jdbc:mariadb://`, `org.mariadb.jdbc.Driver`). MySQL Driver는 MariaDB 12에서 메타데이터 조회에 실패해(`Unknown column 'RESERVED'`) 쓰지 않는다.

## 1. 폴더 구조

```
com.example.yongyong
├── api/<기능>     컨트롤러, 서비스, 응답 DTO
├── yongin/<기능>  yongin 엔티티·리포지토리 (읽기 전용)
├── app/<기능>     yongin_app 엔티티·리포지토리 (나중에)
└── global
    ├── common     ApiResponse
    ├── error      ErrorCode, BusinessException, GlobalExceptionHandler
    └── config     SwaggerConfig, WebConfig(CORS)
```

- 엔티티는 DB별로 `yongin` / `app` 패키지에 나눠 둔다. 어느 DB의 테이블인지 코드에서 바로 구분하기 위해서다.

### Swagger

- `/swagger-ui.html`에서 열리게 한다.
- `SwaggerConfig`에 제목 "용인 교통 사각지대 서비스 API", 버전 "v1".
- 컨트롤러에는 `@Tag`, 메서드에는 `@Operation(summary = "...")`를 한국어로 붙인다.

## 2. yongin DB (읽기 전용)

yongin은 다른 백엔드 팀원이 Python으로 적재하는 분석 DB다. **Spring은 읽기만 한다.**

### 연결 규칙

- 두 DB가 같은 서버에 있으므로 Spring 기본 `spring.datasource`와 자동 설정으로 DataSource 하나만 쓴다. DB별 설정 클래스는 만들지 않는다.
  - 기본 접속 DB는 `yongin_app`이다 (`jdbc:mariadb://localhost:3307/yongin_app`).
  - 엔티티에는 `@Table(catalog = "yongin", name = "...")`처럼 DB를 명시한다. MariaDB Dialect는 `catalog`만 한정자로 지원하므로 `schema`를 쓰지 않는다.
  - 네이밍 전략을 명시한다: `spring.jpa.hibernate.naming.physical-strategy = org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy`
- 읽기 전용은 DB 계정 권한으로 보호한다. 앱 계정은 yongin에 `SELECT`만, yongin_app에 `SELECT, INSERT, UPDATE, DELETE`를 가진다. 테이블 생성은 root로 한다.
- `spring.jpa.hibernate.ddl-auto = none`. 커넥션 풀 `read-only`는 yongin_app 쓰기에 영향을 주므로 켜지 않는다.
- yongin을 읽는 서비스 메서드에는 `@Transactional(readOnly = true)`를 쓴다.
- DB 비밀번호는 환경 변수(`DB_PASSWORD`)로만 받는다. 값은 프로젝트 루트의 `.env`에 사용자가 직접 적고, Claude는 이 파일을 읽지 않는다.

### 절대 하지 않는 것

- yongin에 INSERT / UPDATE / DELETE, DDL, `ddl-auto` 변경
- 데이터가 이상하면 코드로 고치지 말고 사용자에게 알린다

### 테이블 규칙

- 덤프를 다시 받으면 테이블이 통째로 바뀐다.
- 연도별 누적: 연간 표는 `base_year`(현재 2025), 월별 표는 `ym`(`'202501'` 문자열), ml_ 결과는 `period`.
- `feat_` 테이블에는 **기본키가 없다.** `(base_year, 코드)` 복합키를 `@IdClass`로 매핑한다.
- 엔티티에는 `@Immutable`을 붙이고, 리포지토리는 조회 메서드만 둔다.
- 비율 컬럼은 0~1 값이다. 엔티티는 DB 값 그대로 두고, 변환(×100)은 API 응답을 만들 때 한다.
- 컬럼 이름은 `@Column(name = "...")`으로 명시한다.
- 조회 기준 연도는 코드에 하드코딩하지 않는다. 값이 바뀔 수 있어 설정 키(`app.data.base-year`)는 아직 넣지 않았고, 필요할 때 정한다.

### 이번에 매핑할 엔티티

| 테이블 | 키 | 매핑할 컬럼 |
| --- | --- | --- |
| feat_region (행정동 38개) | base_year, dong_cd | dong_nm, gu, is_eupmyeon, center_lat, center_lng, pop, ratio65, n_li |
| feat_village (리 72개) | base_year, li_cd | li_nm, emd, dong_cd(소속 행정동), pop, happy_taxi |

- 컬럼은 필요할 때 추가한다. 처음부터 70개를 다 매핑하지 않는다.
- 타입: `text` → String, `double` → Double, `bigint` → Long, `tinyint(1)` → Boolean.
- 전체 테이블·컬럼 정의는 `docs/database.md`(있으면) 또는 사용자에게 묻는다. 모르는 컬럼 의미를 추측하지 않는다.

### 코드 목록 API (보류, 프론트에서 필요할 때 만든다)

- `GET /api/meta/codes`: 행정동 38개와 각 행정동에 속한 리 목록, 교통수단 3종을 내린다.
  ```json
  {
    "success": true,
    "data": {
      "modes": [{ "code": "BUS", "name": "노선버스", "description": "증차·노선 조정" }],
      "regions": [{ "code": "4146125300", "name": "모현읍", "district": "처인구",
                    "villages": [{ "code": "4146125324", "name": "일산리" }] }]
    },
    "error": null
  }
  ```
- 교통수단은 enum `TransportMode` (`BUS` 노선버스 / `DRT` 똑버스 / `HAPPY_TAXI` 행복택시).
- 리는 `feat_village.dong_cd`로 행정동에 묶는다. 행정동(동)은 리가 없어서 빈 목록.

## 3. 공통 응답 + 예외 처리

### 응답 포맷

```json
{ "success": true, "data": { }, "error": null }
{ "success": false, "data": null, "error": { "code": "REGION_NOT_FOUND", "message": "존재하지 않는 지역입니다." } }
```

- `ApiResponse<T>`는 `record(boolean success, T data, ErrorBody error)`.
  - `ApiResponse.ok(data)`, `ApiResponse.fail(ErrorCode)`, `ApiResponse.fail(ErrorCode, message)`
- 컨트롤러는 항상 `ApiResponse.ok(...)`를 반환한다.

### 에러코드

| 코드 | HTTP | 메시지 |
| --- | --- | --- |
| INVALID_REQUEST | 400 | 요청 값이 올바르지 않습니다. |
| INVALID_WEIGHT | 400 | 가중치 합이 100이 아니거나 음수가 있습니다. |
| REGION_NOT_FOUND | 404 | 존재하지 않는 지역입니다. |
| SUITABILITY_NOT_READY | 404 | 적합성 평가 결과가 아직 없습니다. |
| RECOMMENDATION_NOT_FOUND | 404 | 생성된 AI 추천이 없습니다. |
| RESOURCE_NOT_FOUND | 404 | 요청한 경로를 찾을 수 없습니다. |
| METHOD_NOT_ALLOWED | 405 | 지원하지 않는 HTTP 메서드입니다. |
| INTERNAL_ERROR | 500 | 서버 오류가 발생했습니다. |

- `ErrorCode` enum에 HTTP 상태와 한국어 메시지를 같이 둔다. 새 에러는 여기에 추가한다.
- 실패는 `throw new BusinessException(ErrorCode.X)`.

### 전역 예외 처리 (`@RestControllerAdvice`)

| 예외 | 응답 |
| --- | --- |
| BusinessException | ErrorCode의 HTTP 상태 + 코드 |
| MethodArgumentNotValidException, ConstraintViolationException, MissingServletRequestParameterException, MethodArgumentTypeMismatchException, HttpMessageNotReadableException | 400 INVALID_REQUEST |
| NoResourceFoundException (없는 경로) | 404 RESOURCE_NOT_FOUND |
| HttpRequestMethodNotSupportedException | 405 METHOD_NOT_ALLOWED |
| 그 외 Exception | 500 INTERNAL_ERROR (로그는 error 레벨로 남기고, 응답에는 내부 메시지를 노출하지 않음) |

### CORS

- `/api/**`에 프론트 주소 허용. 주소는 설정값(`app.cors.allowed-origins`)으로, 기본 `http://localhost:5173,http://localhost:3000`.

## 코딩 규칙

- 주석·로그·에러 메시지는 한국어, 식별자는 영어.
- 단, `application.properties`의 주석은 영어로 쓴다. 한국어가 깨지기 때문이다.

## 검증

- 작업이 끝나면 `./gradlew build`로 컴파일과 테스트를 확인한다.
- DB가 필요한 테스트 때문에 DB 없이 `build`가 깨지면 안 된다. 기본 생성된 `contextLoads` 테스트가 DB 때문에 실패하면 사용자에게 알리고 처리 방법을 묻는다.
- 실행 확인: `./gradlew bootRun` → `/swagger-ui.html`에서 API 호출.

## 작업 기록 (WORKLOG.md)

큰 변경을 끝냈거나 포트폴리오에 쓸 만한 작업이 있으면, 사용자가 요청하지 않아도 `WORKLOG.md`를 갱신한다. 사소한 수정은 적지 않는다.

- 사용자가 확인하려고 보는 기록이다. 할 일, 남은 작업, 미검증 항목은 적지 않는다.
- `## 날짜별 변경 이력` 아래에 날짜를 `### YYYY-MM-DD`로 쓰고, 그날의 변경을 목록으로 적는다.
- 주요 변동사항은 그 날짜 아래에 볼드 제목(`**N. 주제: 전 → 후**`)으로 쓰고, 전후 비교 표와 `왜:` 한 줄을 붙인다. 번호는 이어서 매긴다.
- `## 포트폴리오용 초안`에는 실제로 완료된 내용만 적는다.
- 이 파일은 `.gitignore`에 있으며 git에 올리지 않는다.

## Git

- 커밋 메시지: `type: 한국어 요약` (feat, fix, refactor, docs, test, chore)
- 작업 단위별로 커밋 (예: `chore: Swagger 설정`, `feat: yongin 읽기 전용 데이터소스`, `feat: 공통 응답과 전역 예외 처리`)
- `*.sql`, `.env`, 비밀값은 커밋하지 않는다. `.gitignore`에 `*.sql`, `.env`, `CLAUDE.local.md`가 있는지 확인한다.
- 사용자가 요청하기 전에는 push하지 않는다.
