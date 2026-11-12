# CLAUDE.md — 용인 교통 사각지대 진단 백엔드

RULES_VERSION: R13  (규칙을 바꾸면 버전을 올리고 맨 아래 "규칙 변경 이력"에 한 줄 남긴다)

이 파일은 이 저장소에서 일하는 Claude가 매번 따라야 하는 규칙이다.
규칙과 다른 방법이 더 낫다고 판단되면, 바로 바꾸지 말고 사용자에게 먼저 제안한다.

## 구현 원칙: 해커톤이다, 필요한 것만 간결하게

목표는 11/14 제출까지 API 1~8이 **안정적으로 동작하는 것**이다. 아래 규칙(응답 형식, 예외, 레이어 방향, 테스트 순서)은 지키되, 그 이상은 만들지 않는다.

- 지금 명세에 필요한 코드만 쓴다. "나중에 쓸 수도 있는" 확장 지점, 설정, 옵션을 미리 만들지 않는다
- 구현체가 하나뿐인 인터페이스, 추상 클래스, 제네릭 유틸, 빌더·팩토리를 만들지 않는다. 서비스는 클래스 하나로 충분하다 (Spring Data repository 인터페이스는 예외)
- 변환이 몇 줄이면 별도 Mapper 클래스 없이 record의 정적 메서드(`from`)나 서비스 안에서 처리한다
- 같은 로직이 두 번 이상 나오면 메서드로 뺀다. 여러 클래스에서 쓰이게 되면 그때 `common`으로 옮긴다
- 캐싱은 필요할 때 쓴다 (같은 조회가 반복되거나 응답이 느릴 때. `yongin`은 읽기 전용이라 캐싱하기 좋다). 쓸 때는 `docs/decisions.md`에 이유를 한 줄 남긴다
- 페이지네이션, 비동기, 로깅 프레임워크 설정 등 명세에 없는 부가 기능은 넣지 않는다
- Swagger는 프론트와 공유하려고 이미 세팅되어 있다. 설정은 건드리지 않고, 새 API마다 기존 방식대로 요약·파라미터 설명만 단다
- 주석은 이유가 코드만 봐서 안 보일 때만 한 줄로. 형식적인 Javadoc을 달지 않는다
- 새 라이브러리 의존성을 추가해야 하면 먼저 사용자에게 묻는다
- 코드가 길어진다 싶으면 멈추고 더 짧은 방법이 있는지 먼저 생각한다. 판단이 어려우면 두 안을 짧게 비교해서 사용자에게 묻는다

## 0. 먼저 읽을 문서

| 문서 | 내용 |
| --- | --- |
| `docs/api-spec.md` | API 1~8 명세 (요청 파라미터, 응답 `data` 구조, 데이터 매핑). 8번은 yong_ml FastAPI ML 실행 요청·상태 조회 (decisions D17) |
| `docs/data-mapping.md` | `yongin` DB 테이블·컬럼, 조회 조건 규칙, 값 형식 |
| `docs/decisions.md` | 이미 정한 설계 결정과 이유. 여기 적힌 결정은 다시 묻지 않고 따른다 |
| `WORKLOG.md` | 작업 기록. 7장 규칙대로 매 작업마다 갱신 |

명세와 코드가 다르면 명세가 맞다. 명세가 모호하거나 DB와 맞지 않으면 추측해서 구현하지 말고 사용자에게 묻는다.

## 1. 기술 기준

- Java 21, Spring Boot 4.1.2, Gradle 9.8.2 (실제 버전은 build.gradle 기준)
- DB: MariaDB `yongin` (Python이 적재, Spring은 **읽기 전용**). INSERT/UPDATE/DELETE 쿼리를 쓰지 않는다
- 앱은 `yongin`에 바로 접속한다. 앱 계정은 `yongin`에 `SELECT`만 가진다 (`yongin_app`은 쓰지 않음, decisions D15). 계정·DB 생성은 root로 한다
- ML 컨테이너(yong_ml)는 별도 계정을 쓴다 (`ml_` 테이블 5개만 쓰기, decisions D15). Spring은 여전히 쓰기 쿼리를 쓰지 않고, ML 실행은 FastAPI 호출로만 한다 (decisions D17)
- `.env`의 값은 사용자가 직접 적고, Claude는 이 파일을 읽지 않는다
- DB 접근: **Spring Data JPA, 읽기 전용 엔티티** (decisions D1). 엔티티는 `@Table(catalog = "yongin", name = "...")`로 DB를 지정한다. MariaDB Dialect는 `catalog`만 한정자로 지원하므로 `schema`를 쓰지 않는다. 예외: `ml_` 엔티티 4개는 `@Subselect("select ... from yongin.ml_...")` (5장)
- `spring.jpa.hibernate.ddl-auto=none` 고정. `yongin` 스키마를 Hibernate가 만들거나 검사하지 않게 한다 (`update`, `create`, `validate` 금지)
- 테스트: JUnit 5, AssertJ, Mockito, Spring Boot Test, Testcontainers(MariaDB), ArchUnit
- 품질 도구: Spotless(google-java-format), JaCoCo(측정만, 기준 미달로 빌드를 막지 않음 — decisions D9)

## 2. 패키지 구조

```
{base}                         // 실제 패키지명은 build.gradle의 group 기준
├─ common
│  ├─ response    ApiResponse (ErrorBody는 안쪽 record), PeriodResponse (공통 period)
│  ├─ exception   ErrorCode, BusinessException, GlobalExceptionHandler
│  ├─ config      SwaggerConfig, WebConfig
│  ├─ query       PeriodQuery(year, month) 등 공통 요청 값, PeriodService (공통 달·월 검사)
│  └─ converter   JSON 컬럼용 AttributeConverter (StringListConverter, OptionListConverter)
├─ yongin         yongin DB 읽기 전용 엔티티 + repository (테이블당 하나, 여러 기능이 같이 씀)
│  ├─ entity      MlPriority, MlRegion, MlExplanation, MlModelInfo (@Subselect, 연간 = month 0),
│  │              PlanSummary, PlanRegion (@Subselect, 연간 = month 0),
│  │              FeatRegion, FeatRegionMonth, FeatCity, FeatCityMonth
│  └─ repository  MlPriorityRepository ...
├─ dashboard      API 1, 2
│  ├─ controller / service / dto
├─ region         API 3, 4, 5, 6
│  ├─ controller / service / dto
├─ recommendation API 7
│  ├─ controller / service / dto
├─ plan           API 9 (통합 최적안)
│  ├─ controller / service / dto
└─ ml             API 8 (yong_ml FastAPI 호출)
   ├─ controller / service / dto / client (MlClient, RestClient)
```

- 의존 방향: `controller → service → yongin.repository`. 거꾸로 부르거나 건너뛰지 않는다 (controller가 repository·entity 직접 사용 금지). `ml`은 `controller → service → client` (controller가 client 직접 사용 금지)
- 엔티티는 응답으로 내보내지 않는다. 서비스에서 `dto` record로 바꿔서 넘긴다
- `dto`는 응답·요청 record만 둔다
- 다른 기능 패키지의 service를 부를 수는 있다. 테이블 조회는 각 서비스가 `yongin.repository`를 직접 쓴다

## 3. 공통 응답 형식

모든 컨트롤러는 `ApiResponse<T>`를 반환한다. DTO를 그대로 반환하지 않는다.

```json
{ "success": true,  "data": { }, "error": null }
{ "success": false, "data": null, "error": { "code": "REGION_NOT_FOUND", "message": "존재하지 않는 지역입니다." } }
```

- `docs/api-spec.md`의 "Response data" 예시는 `data` 안에 들어갈 내용만 적은 것이다
- JSON 필드명은 camelCase. null 필드도 생략하지 않고 `null`로 내린다 (명세에 null이 있는 필드)

## 4. 예외·에러코드

- 에러는 `throw new BusinessException(ErrorCode.XXX)`로 던지고 `GlobalExceptionHandler`(`@RestControllerAdvice`)가 3장 형식으로 바꾼다
- 컨트롤러·서비스에서 try-catch로 에러 응답을 직접 만들지 않는다
- 에러코드는 `ErrorCode` enum에만 정의한다. 새 코드가 필요하면 추가하기 전에 사용자에게 묻는다

| 코드 | HTTP | 상황 |
| --- | --- | --- |
| `INVALID_REQUEST` | 400 | 파라미터 검증 실패 (year 없음, month 1~12 밖, regionCode 형식) |
| `REGION_NOT_FOUND` | 404 | 연도 데이터는 있는데 regionCode가 없음 |
| `PERIOD_NOT_FOUND` | 404 | 연도 데이터가 아예 없음. API 5는 `feat_region_month`에 그 달 행이 없을 때도 (decisions D7) |
| `SUITABILITY_NOT_READY` | 404 | 고른 month가 공통 달(`ml_priority`·`ml_region`·`ml_explanation`에 모두 있는 달)에 없음. 메시지 "존재하지 않는 달입니다." (decisions D7, D16) |
| `RECOMMENDATION_NOT_FOUND` | 404 | `ml_explanation`에 그 지역 행이 없음 (없는 연도, 없는 지역 포함) |
| `RESOURCE_NOT_FOUND` | 404 | 없는 경로 |
| `METHOD_NOT_ALLOWED` | 405 | 지원하지 않는 HTTP 메서드 |
| `ML_RUNNING` | 409 | ML 실행이 이미 돌고 있음 (API 8, FastAPI 409). 메시지 "이미 실행 중입니다." (decisions D17) |
| `ML_UNAVAILABLE` | 502 | ML 서버 연결 실패·타임아웃, FastAPI 500 등 (API 8). 메시지 "ML 서버에 연결할 수 없습니다." (decisions D17) |
| `INTERNAL_ERROR` | 500 | 그 밖의 예외. 메시지에 내부 정보(SQL, 스택)를 담지 않는다 |

## 5. 데이터 규칙 (요약, 자세한 것은 docs/data-mapping.md)

**엔티티 작성**
- 모든 엔티티에 `@Entity` + `@Immutable`(Hibernate). setter를 만들지 않는다
- 테이블에 PK가 없으므로 행을 유일하게 구분하는 컬럼 조합을 `@IdClass`로 지정한다 (테이블별 키는 data-mapping.md "엔티티 매핑" 표). Id 클래스는 엔티티 안의 `record`로 둔다
- 필요한 컬럼만 필드로 둔다. 쓰지 않는 컬럼은 매핑하지 않는다
- 컬럼명은 Spring Boot 기본 이름 규칙(camelCase → snake_case)으로 맞춘다. 규칙과 다를 때만 `@Column(name = ...)`

**`ml_` 엔티티의 month (연간 = month 0)**
- `MlPriority`, `MlRegion`, `MlExplanation`, `MlModelInfo`는 `@Table` 대신 `@Subselect("select ..., coalesce(month, 0) as month, ... from yongin.ml_...")`로 매핑하고 키에 `month`를 넣는다. DB의 연간(month NULL) 행은 month 0으로 읽는다. 복합 키 일부가 NULL이면 Hibernate가 null 엔티티를 돌려주기 때문이다 (38행 모두 null 확인, decisions D16)
- 서비스는 `PeriodQuery.monthKey()`(월이 없으면 0)로 기존 조회 메서드를 그대로 쓴다. 공통 달 계산(`findMonths`)은 0을 뺀다
- month 0은 서버 내부 표현이다. API 입력으로는 받지 않는다: 요청 검증은 `@Min(1) @Max(12)` 그대로, `month=0`은 400 `INVALID_REQUEST`, month 없음이 연간이다. 응답의 `period.months`에도 0을 내리지 않는다
- 컬럼을 SQL에 나열하므로 쓰는 컬럼을 추가하면 `@Subselect` 문자열도 고친다

**조회 규칙**
- repository는 `org.springframework.data.repository.Repository<T, ID>`를 상속하고 **조회 메서드만** 선언한다. `JpaRepository`·`CrudRepository`를 쓰지 않는다 (save/delete가 아예 없게)
- 파생 쿼리(`findByYearAndUnitAndRegionCd`)를 먼저 쓴다. 행 수가 적은 조회(행정동 38개 수준)는 행을 읽고, 이미 읽어 온 행으로 구할 수 있는 값은 서비스에서 계산하고, 값만 따로 필요하면 max·count·avg·distinct 같은 집계는 JPQL `@Query`로 쓴다 (decisions D13). 네이티브 쿼리는 JPQL로 안 될 때만, 이유를 주석 한 줄로
- 쿼리 문자열을 이어붙여 만들지 않는다. 파라미터는 항상 바인딩
- 서비스 클래스에 `@Transactional(readOnly = true)`

**연간·월 규칙 (decisions D16)**
- month 있음 → 그 달 행만. month 없음(연간) → `ml_` 값(점수, 유형, 신뢰도, 추천, 요약, 갱신 시각)은 모두 그 테이블의 연간 행(month 0 = DB NULL). ML이 연간을 따로 계산해 둔 값이라 서버가 월 평균·최근 달을 만들지 않는다 (decisions D16). `feat_`는 그대로(연간 = `FeatRegion`·`FeatCity`, 시 평균 연간 성사율·생활인구만 `FeatCityMonth` 집계)
- 점수는 정수로 반올림해 내리고(지도 포함), 순위는 그 정수 점수로 다시 매긴다. 동점은 같은 순위, 다음 순위는 건너뛴다 (1, 2, 3, 3, 5). DB `rank_total`, `is_vulnerable`은 쓰지 않는다. 취약지역 = 정수 점수 50점 이상
- 월 검사는 `PeriodService.checkMonth` 하나로 한다 (API 1~5, 7). 월 범위를 하드코딩하지 않고 조회 시점에 테이블에 있는 달로 계산한다
- API 1~7 응답은 `data` 맨 앞에 `period { year, months, type }`(`common.response.PeriodResponse`)를 내린다. type = `YEAR_ALL`(연간, months = 공통 달. API 5·6은 `feat_region_month`의 달) / `MONTH`(고른 달), months는 항상 배열. `YEAR_LATEST`는 프론트 호환으로 enum에만 남기고 내리지 않는다. API 3·4는 `typePeriod`를 하나 더 내린다(지금은 `period`와 같은 값). 고를 수 있는 연·월 목록은 응답에 넣지 않는다
- API 8은 예외: `period` 없이 FastAPI RunStatus를 그대로 `data`에 담는다 (시각은 ISO 그대로, decisions D17)

**조회 조건·값**
- `ml_` 조회 조건: `year`, `unit`, `regionCd` + `month`
- `feat_` 테이블은 키가 다르다: 지역 = `dongCd`, 연간 값은 `FeatRegion`(month 없음), 월별 값은 `FeatRegionMonth`(year, month)
- 비율 컬럼(0~1)은 서버가 ×100 해서 % 값으로 내리고 **정수로 반올림** (decisions D14). 점수는 API 1~4 모두(지도 포함) 정수로 반올림, API 7 `options`의 `score`도 정수 (decisions D12, D16). API 5 지표와 API 6 값도 모두 정수로 반올림 (decisions D14)
- 반올림은 사사오입(Java `Math.round`)이다. 기대값을 SQL로 확인할 때 MariaDB `ROUND(double)`은 .5를 짝수로 보내므로 `FLOOR(x + 0.5)`를 쓴다
- JSON 문자열 컬럼(`options`, `reasons`, `cautions`, `type_chips`)은 엔티티 필드에 `@Convert`로 `common.converter`의 AttributeConverter를 달아 `List`로 받는다. 변환기의 쓰기 방향(`convertToDatabaseColumn`)은 예외를 던진다 (읽기 전용)
- `tinyint(1)` 컬럼(`is_vulnerable`, `is_keep`, `low_reliability`)은 `Boolean`으로 매핑한다. 그래서 개수는 `SUM` 대신 서비스에서 `Boolean` 값으로 걸러 센다 (decisions D13)
- 이번 범위는 행정동 38개만 구현한다. API에 `unit` 파라미터를 두지 않고, 서비스가 항상 `unit = "dong"`으로 조회한다 (decisions D6). DB에는 `village` 행도 있으니 `unit` 조건을 빼지 않는다

## 6. 작업 순서 (API 하나 = 작업 하나)

API 하나를 만들 때 아래 순서를 지킨다. 순서를 건너뛰지 않는다.

1. **명세 확인**: `docs/api-spec.md`의 해당 API와 `docs/data-mapping.md`를 읽고, 모호한 점이 있으면 여기서 묻는다
2. **명세 코드화**: 응답 DTO record, 컨트롤러 메서드 시그니처(Swagger 요약·파라미터 설명 포함), 필요한 엔티티·repository 메서드 선언을 만든다. 서비스 본문은 `throw new UnsupportedOperationException()`. 엔티티·repository가 이미 있으면 재사용하고 필요한 메서드만 추가한다
3. **테스트 작성**: 8장 기준으로 테스트를 먼저 쓰고 실행해서 **실패하는 것을 확인**한다
4. **사람 확인 지점**: 테스트 목록(무엇을 검증하는지)을 사용자에게 요약해서 보여주고, 확인을 받은 뒤 구현한다
5. **구현**: 테스트가 통과할 때까지 구현한다. 테스트를 구현에 맞춰 고치지 않는다. 테스트가 틀렸다고 판단되면 이유를 말하고 사용자 확인 후 고친다
6. **검증**: `./gradlew spotlessApply check` (포맷, 전체 테스트) 통과. 9장 3번 구조 규칙은 리뷰에서 직접 확인
7. **기록·커밋**: `WORKLOG.md` 항목을 마무리하고(7장), 커밋한다

브랜치: `feat/api-{번호}` (예: `feat/api-1`). main에 직접 커밋하지 않는다.
커밋 메시지: `feat: 대시보드 요약 조회` 형식 (`type: 요약`, 괄호 없음). Claude가 만든 커밋에는 `Co-Authored-By: Claude` 트레일러를 반드시 남긴다 (사람 수정 비율 계산에 쓴다).
커밋 메시지 제목은 15자 안팎으로 짧게, 본문은 쓰지 않는다 (Co-Authored-By 트레일러는 유지).
커밋하기 전에 메시지와 커밋할 파일 목록을 사용자에게 보여주고, 허락받은 뒤에 커밋한다.
`*.sql`, `.env`, 비밀값은 커밋하지 않는다. `.gitignore`에 `*.sql`, `.env`, `CLAUDE.local.md`가 있는지 확인한다.
사용자가 요청하기 전에는 push하지 않는다.

## 7. 작업 기록 규칙 (WORKLOG.md) — 매 작업 자동

저장소 루트에 `WORKLOG.md`가 없으면 이 장의 기록은 하지 않는다 (개인 포폴 기록이라 git에서 제외되어 있다. 새로 만들지 않는다).

사용자가 따로 말하지 않아도 아래를 지킨다. 목적: 포트폴리오에 쓸 수치(규칙 버전별 개발 속도·품질, 응답시간, 배포)와 사례(트러블슈팅, 규칙 개선, 설계 결정)를 개발하면서 바로 남기는 것.

**작업 시작 시** (6장 1단계 전)
- `date '+%Y-%m-%d %H:%M'`를 실행해 시각을 얻고, `WORKLOG.md` ④ 작업 기록 맨 위에 새 항목을 만든다 (템플릿은 WORKLOG.md 안에 있음)
- 항목에 API 번호, 브랜치, 현재 RULES_VERSION, 시작 시각을 적고 상태를 `진행중`으로 둔다

**작업 중**
- 사용자가 내 코드를 고치라고 하거나 직접 고친 경우, 그때마다 "사람 수정" 칸에 한 줄씩 적는다: `무엇을 / 왜(원인 분류)`
  - 원인 분류는 다음 중 하나: `명세오해` `규칙위반` `테스트누락` `DB오해` `스타일` `기타`
- 테스트나 `check`가 실패하면 "로컬 실패" 칸에 `실패한 검사 / 원인 분류`를 적는다 (같은 원인이 반복되면 규칙 추가 후보로 표시)

**작업 끝날 때** (6장 7단계)
- `date`로 종료 시각을 얻어 소요 시간(분)을 적는다. 사용자가 자리를 비운 시간이 있었다고 말하면 그만큼 뺀다
- `./scripts/metrics.sh coverage` 결과(라인 커버리지 %)를 적는다
- `./scripts/metrics.sh ai-ratio main` 결과(AI 추가 줄 수, 사람 수정 줄 수, 비율)를 적는다
- 테스트 개수(전체/추가)를 적는다
- 로컬 서버가 떠 있으면 `./scripts/metrics.sh latency "<이 API의 로컬 URL>" 20`으로 응답시간(p50·max)을 적는다. 서버를 띄울 수 없으면 `측정 못 함`
- ② 요약 표 "API별"에 한 행을 추가한다
- 상태를 `완료`로 바꾼다

**포트폴리오 소재 (수시로)**
- WORKLOG.md ③의 "언제 남기나" 조건(규칙 추가, 2번 이상 시도한 문제 해결, 새 decisions, 응답시간 변화, 인프라 변경, 데이터 구조 문제 해결)에 해당하는 일이 생기면 ③ 표 맨 위에 한 건 추가한다
- 상황 / 원인·판단 / 해결 / 결과(수치) / 근거(커밋·PR·decisions 번호)를 채운다. 수치가 없으면 `—`
- 추가했다는 사실을 작업 마무리 때 사용자에게 한 줄로 알린다

**PR 머지 후** (사용자가 머지했다고 말하면)
- `./scripts/metrics.sh ci 20`으로 최근 CI 실패율과 실패한 검사 이름을 얻어, 해당 항목의 "CI" 칸과 ② 요약 표를 갱신한다
- 배포까지 했다면 ② "배포·운영" 표(시연 URL, 배포 소요 시간, 배포·실패 횟수)를 갱신한다

**규칙을 추가·변경할 때**
- 반복된 실패 원인(예: `규칙위반` 3회)을 근거로 사용자에게 규칙 추가를 제안한다
- 사용자가 동의하면 이 파일 규칙을 고치고 RULES_VERSION을 올리고(R1→R2), 아래 이력에 `날짜 / 버전 / 무엇을 / 왜(근거 항목)`를 남긴다
- 같은 내용을 WORKLOG.md ③에 `AI→규칙` 사례로 추가하고, ② "규칙 버전별 평균"에 새 버전 행을 만든다

기록은 사실만 적는다. 측정하지 못한 값은 비워두고 `측정 못 함`이라고 쓴다. 값을 추정해서 채우지 않는다.

## 8. 테스트 작성 규칙

API 하나당 최소 아래 세 종류를 만든다.

| 종류 | 도구 | 검증할 것 |
| --- | --- | --- |
| 컨트롤러 | `@WebMvcTest` + Mockito | 응답이 `ApiResponse` 형식인지, 필드명·null 처리, 파라미터 검증 실패 시 400과 에러코드 |
| 서비스 | 순수 단위 테스트 (Mockito) | 변환 규칙: ×100·반올림, '추천'/'제외' → `RECOMMENDED`/`EXCLUDED`, 연간/월 분기, 데이터 없을 때 예외 |
| repository | `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` + Testcontainers MariaDB | 엔티티 매핑과 쿼리 메서드가 seed 데이터에서 기대값을 내는지 |

- API 8은 repository 대신 client 테스트(`MockRestServiceServer`)로 FastAPI 응답(202·200·409·422·500, 연결 실패)이 우리 응답·에러로 바뀌는지 본다. 테스트에서 실제 FastAPI를 호출하지 않는다 (실행하면 `ml_` 덮어쓰기와 LLM 사용이 생김)

- seed: `src/test/resources/db/seed-dong-2025.sql` (11/11 최종본 DB에서 행정동만 추린 것, 연간 NULL 행 포함). 공개 저장소라 git에 올리지 않는다 (decisions D11)
- `@DataJpaTest` repository 테스트에는 `@Tag("db")`를 붙인다. CI는 `-PexcludeTags=db`로 이 태그를 빼고 돌고, 로컬 `./gradlew test`는 전부 돈다. CI가 repository 테스트를 대신 돌려주지 않으므로 PR 전에 로컬에서 전체 테스트를 통과시킨다
- 컨테이너 DB 이름은 `yongin`으로 만든다 (`.withDatabaseName("yongin")`). 엔티티가 `catalog = "yongin"`, `@Subselect`가 `yongin.ml_priority`를 가리키기 때문
- MariaDB 컨테이너는 테스트 전체가 하나를 같이 쓴다 (`MariaDbTestSupport`의 static 컨테이너 하나). 테스트 클래스마다 새로 띄우지 않는다
- 엔티티를 새로 만들면 그 엔티티 repository 테스트에 매핑 확인을 넣는다: seed 행 수, 키 조회 1건, JSON·Boolean 필드 값 (특히 `ml_` 엔티티는 연간 행이 month 0으로 null 엔티티 없이 읽히는지, 월 목록에 0이 없는지)
- 기대값은 **seed에서 SQL로 직접 확인한 값만** 쓴다. 숫자를 추측하거나 명세 예시에서 그냥 베끼지 않는다. 확인에 쓴 SQL을 테스트 주석에 한 줄로 남긴다
- 이미 확인된 기준값 (11/11 최종본 DB, 2025, dong, 연간 = NULL 행): 지역 38개, 취약지역 9개, 평균 39.1, 원삼면 68점 단독 1위·이동읍 66점 2위 / 포곡읍(4146125000) 60점·7위, 동부동과 공동 (7월도 60점·7위) / 연간 갱신 시각 2026-11-10 17:00 / 공통 달 1~12 / 시 평균 택시 성사율 53.8%, 생활인구 6,267명, 정류장 81.6개, 고령 17.7%
- 테스트 이름은 `@DisplayName`에 한글 문장으로: `"월을 고르면 그 달 점수와 순위를 내린다"`
- 실패 케이스를 반드시 넣는다: 없는 regionCode, 범위 밖 month, 공통 달에 없는 month (최종본 seed는 공통 달이 1~12라 서비스 테스트에서 mock으로)
- 테스트도 간결하게: 정상 케이스 1~2개 + 위 실패 케이스면 충분하다. 같은 내용을 여러 층에서 반복 검증하지 않는다 (예: 파라미터 검증은 컨트롤러 테스트에서만, SQL 결과는 repository 테스트에서만)

## 9. 자동 검증 게이트 (CI)

GitHub Actions `ci.yml`이 PR마다 아래를 돌리고, 하나라도 실패하면 머지하지 않는다.

1. `spotlessCheck` — 포맷
2. `test -PexcludeTags=db` — 단위·통합 테스트 (Testcontainers). seed가 필요한 `db` 태그 테스트는 제외 (decisions D11)
3. 구조 규칙 — **ArchUnit 테스트는 아직 없다** (의존성만 있음). CI가 강제하지 않으므로 코드 작성·리뷰 때 아래를 직접 지킨다
   - controller → service → repository 방향만 허용 (controller는 `yongin` 패키지에 의존 금지). `ml.client`는 service에서만 사용 (controller 의존 금지)
   - `@RestController`의 public 메서드 반환 타입은 `ApiResponse`
   - `@Entity` 클래스는 `yongin.entity` 패키지에만 있고, `@Immutable`이 붙어 있어야 함
   - `yongin.repository`의 인터페이스는 `JpaRepository`·`CrudRepository`를 상속하지 않음
   - `dto` 패키지는 `yongin.entity`에 의존하지 않음 (엔티티를 응답에 섞지 않게)
4. `jacocoTestReport` — 커버리지 리포트만 만든다. **커버리지 기준(문턱)은 두지 않고, 미달이어도 CI를 실패시키지 않는다** (decisions D9). 값은 WORKLOG에 기록만 한다
   - 커버리지 문턱 대신 8장의 "API당 테스트 3종 + 실패 케이스 필수"로 테스트 누락을 막는다
   - build.gradle에 `jacocoTestCoverageVerification` 규칙을 추가하지 않는다

로컬에서 `./gradlew spotlessApply check`가 통과하지 않은 상태로 커밋하지 않는다.

## 10. 하지 말 것

- 명세에 없는 필드·API·에러코드를 임의로 추가
- 지금 필요 없는 확장용 구조(인터페이스, 추상화, 공통 유틸)를 미리 만들기
- `yongin` DB에 쓰기, 스키마 변경, `ddl-auto`를 `none` 외의 값으로 바꾸기
- 엔티티에 setter 추가, 엔티티를 컨트롤러 응답으로 직접 반환
- 테스트를 통과시키려고 테스트 기대값을 바꾸기
- 실패한 테스트를 `@Disabled`로 꺼서 우회하기
- 비밀값(DB 비밀번호 등)을 코드나 `application.properties`에 넣기 (환경변수 사용)

## 규칙 변경 이력

| 날짜 | 버전 | 무엇을 | 왜 |
| --- | --- | --- | --- |
| 2026-11-05 | R1 | 최초 작성 (DB 접근은 JPA 읽기 전용 엔티티) | API 1~7 개발 시작 |
| 2026-11-05 | R2 | Boot 4.1.2 기준, `schema`→`catalog`, `global`→`common`, 에러코드 정리, `unit` 파라미터 제거, seed 미커밋과 `db` 태그 CI 제외, old 안전 규칙 4개 복원 | 사용자 결정 (규칙과 실제 프로젝트 대조 결과), decisions D1·D6·D7·D11 |
| 2026-11-05 | R3 | 행 수가 적은 조회는 읽어 온 행으로 서비스에서 계산, 값만 따로 필요하면 max·count·avg·distinct 같은 집계는 JPQL | 사용자 지적 (API 1 `MlPriorityRepository` 메서드 과다), decisions D13 |
| 2026-11-05 | R4 | 연도 데이터가 없으면 `PERIOD_NOT_FOUND`, 연도는 있는데 지역이 없으면 `REGION_NOT_FOUND`. API 3 `score` 정수 반올림 | 사용자 결정 (API 1·2와 일치), decisions D7, D12 |
| 2026-11-06 | R5 | API 4 헤더 `score`도 정수로 반올림 | 사용자 결정 (API 3과 같은 지역의 숫자 일치), decisions D12 |
| 2026-11-06 | R6 | API 5 지표를 비율까지 모두 정수로 반올림 | 사용자 결정 (API 1~4 표시와 일치), decisions D14 |
| 2026-11-06 | R7 | 비율 기본 표기를 정수 반올림으로 변경, API 6 값도 정수 | 사용자 결정, decisions D14 |
| 2026-11-06 | R8 | API 7 `options` `score` 정수 반올림, 없는 연도와 지역도 `RECOMMENDATION_NOT_FOUND` | 사용자 결정, decisions D12 |
| 2026-11-07 | R9 | 앱은 `yongin`에 SELECT 전용 계정 하나로 접속, `yongin_app` 제거 | 사용자 결정 (배포 준비 중 `yongin_app`을 쓰는 엔티티·테이블이 없음을 확인), decisions D15 |
| 2026-11-08 | R10 | 연간 = 서버 재계산(숫자 월 평균, 분류·문장 최근 달), NULL 행 미사용(`@SQLRestriction`), 정수 점수·재계산 순위, `SUITABILITY_NOT_READY`, 공통 `period`, seed 11/8 기준, 확인 SQL 반올림 | 사용자 결정 (11/8 DB에 테이블마다 다른 월별 행 추가), decisions D4·D7·D12 변경, D16 |
| 2026-11-11 | R12 | 연간 = `ml_` 연간(NULL) 행, `ml_` 엔티티 4개는 `@Subselect`로 NULL을 month 0으로 읽음(`@Table` 예외), 월 평균·최근 달 계산 제거(순위·취약지역은 정수 점수 기준 유지), 연간 period는 모두 `YEAR_ALL` + 공통 달(`YEAR_LATEST` 미사용), seed·기준값 11/11 최종본 | 사용자 결정 (최종본 DB에 `ml_` 5개 테이블 모두 NULL 행, 월 평균·12월과 값이 다름), decisions D16 변경 |
| 2026-11-12 | R13 | 2장에 `plan` 패키지(API 9)와 `PlanSummary`·`PlanRegion` 엔티티 추가, 9장 구조 규칙을 "ArchUnit 강제"에서 "테스트 없음, 직접 확인"으로, 6장 검증 문구에서 ArchUnit 제거 | 사용자 결정 (API 9 구현 중 2장에 패키지 없음, ArchUnit 규칙 테스트 파일 없음 확인), decisions D18 |
| 2026-11-10 | R11 | API 8 범위 포함(ML 실행 요청·상태), `ml` 패키지·`client`, 에러코드 `ML_RUNNING`·`ML_UNAVAILABLE`, API 8 `period` 예외, ML 컨테이너 별도 계정, API 8 client 테스트 | 사용자 결정 (yong_ml FastAPI 연동), decisions D5·D15 변경, D17 |
