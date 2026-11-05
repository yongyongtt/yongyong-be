# CLAUDE.md — 용인 교통 사각지대 진단 백엔드

RULES_VERSION: R6  (규칙을 바꾸면 버전을 올리고 맨 아래 "규칙 변경 이력"에 한 줄 남긴다)

이 파일은 이 저장소에서 일하는 Claude가 매번 따라야 하는 규칙이다.
규칙과 다른 방법이 더 낫다고 판단되면, 바로 바꾸지 말고 사용자에게 먼저 제안한다.

## 구현 원칙: 해커톤이다, 필요한 것만 간결하게

목표는 11/14 제출까지 API 1~7이 **안정적으로 동작하는 것**이다. 아래 규칙(응답 형식, 예외, 레이어 방향, 테스트 순서)은 지키되, 그 이상은 만들지 않는다.

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
| `docs/api-spec.md` | API 1~7 명세 (요청 파라미터, 응답 `data` 구조, 데이터 매핑). 8번(AI 추천 다시 생성)은 이번 범위 아님 |
| `docs/data-mapping.md` | `yongin` DB 테이블·컬럼, 조회 조건 규칙, 값 형식 |
| `docs/decisions.md` | 이미 정한 설계 결정과 이유. 여기 적힌 결정은 다시 묻지 않고 따른다 |
| `WORKLOG.md` | 작업 기록. 7장 규칙대로 매 작업마다 갱신 |

명세와 코드가 다르면 명세가 맞다. 명세가 모호하거나 DB와 맞지 않으면 추측해서 구현하지 말고 사용자에게 묻는다.

## 1. 기술 기준

- Java 21, Spring Boot 4.1.2, Gradle 9.8.2 (실제 버전은 build.gradle 기준)
- DB: MariaDB `yongin` (Python이 적재, Spring은 **읽기 전용**). INSERT/UPDATE/DELETE 쿼리를 쓰지 않는다
- 기본 접속 DB는 `yongin_app`이다. 앱 계정은 `yongin`에 `SELECT`만, `yongin_app`에 `SELECT, INSERT, UPDATE, DELETE`를 가진다. 테이블 생성은 root로 한다
- `.env`의 값은 사용자가 직접 적고, Claude는 이 파일을 읽지 않는다
- DB 접근: **Spring Data JPA, 읽기 전용 엔티티** (decisions D1). 엔티티는 `@Table(catalog = "yongin", name = "...")`로 DB를 지정한다. MariaDB Dialect는 `catalog`만 한정자로 지원하므로 `schema`를 쓰지 않는다
- `spring.jpa.hibernate.ddl-auto=none` 고정. `yongin` 스키마를 Hibernate가 만들거나 검사하지 않게 한다 (`update`, `create`, `validate` 금지)
- 테스트: JUnit 5, AssertJ, Mockito, Spring Boot Test, Testcontainers(MariaDB), ArchUnit
- 품질 도구: Spotless(google-java-format), JaCoCo(측정만, 기준 미달로 빌드를 막지 않음 — decisions D9)

## 2. 패키지 구조

```
{base}                         // 실제 패키지명은 build.gradle의 group 기준
├─ common
│  ├─ response    ApiResponse (ErrorBody는 안쪽 record)
│  ├─ exception   ErrorCode, BusinessException, GlobalExceptionHandler
│  ├─ config      SwaggerConfig, WebConfig
│  ├─ query       PeriodQuery(year, month) 등 공통 요청 값
│  └─ converter   JSON 컬럼용 AttributeConverter (StringListConverter, OptionListConverter)
├─ yongin         yongin DB 읽기 전용 엔티티 + repository (테이블당 하나, 여러 기능이 같이 씀)
│  ├─ entity      MlPriority, MlRegion, MlExplanation, MlModelInfo,
│  │              FeatRegion, FeatRegionMonth, FeatCity, FeatCityMonth
│  └─ repository  MlPriorityRepository ...
├─ dashboard      API 1, 2
│  ├─ controller / service / dto
├─ region         API 3, 4, 5, 6
│  ├─ controller / service / dto
└─ recommendation API 7
   ├─ controller / service / dto
```

- 의존 방향: `controller → service → yongin.repository`. 거꾸로 부르거나 건너뛰지 않는다 (controller가 repository·entity 직접 사용 금지)
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
| `PERIOD_NOT_FOUND` | 404 | 연도 데이터가 아예 없거나, 지역은 있는데 고른 월이 적재되지 않음 (decisions D7) |
| `RECOMMENDATION_NOT_FOUND` | 404 | `ml_explanation`에 그 지역 행이 없음 |
| `RESOURCE_NOT_FOUND` | 404 | 없는 경로 |
| `METHOD_NOT_ALLOWED` | 405 | 지원하지 않는 HTTP 메서드 |
| `INTERNAL_ERROR` | 500 | 그 밖의 예외. 메시지에 내부 정보(SQL, 스택)를 담지 않는다 |

## 5. 데이터 규칙 (요약, 자세한 것은 docs/data-mapping.md)

**엔티티 작성**
- 모든 엔티티에 `@Entity` + `@Immutable`(Hibernate). setter를 만들지 않는다
- 테이블에 PK가 없으므로 행을 유일하게 구분하는 컬럼 조합을 `@IdClass`로 지정한다 (테이블별 키는 data-mapping.md "엔티티 매핑" 표). Id 클래스는 엔티티 안의 `record`로 둔다
- 필요한 컬럼만 필드로 둔다. 쓰지 않는 컬럼은 매핑하지 않는다
- 컬럼명은 Spring Boot 기본 이름 규칙(camelCase → snake_case)으로 맞춘다. 규칙과 다를 때만 `@Column(name = ...)`

**ml_priority는 예외 (연간 행의 month가 NULL)**
- 복합키 컬럼에 NULL이 있으면 Hibernate가 그 행을 엔티티로 만들지 못한다. 그래서 `@Table` 대신 `@Subselect`로 `COALESCE(month, 0) AS month_key`를 만들어 `monthKey`를 키로 쓴다
- 코드에서는 `monthKey = 0`이 연간, 1~12가 월이다. `PeriodQuery`가 month null → 0으로 바꿔 넘긴다. 응답의 `month`/`availableMonths`에서는 다시 null로 바꾼다
- `@Synchronize("ml_priority")`를 같이 단다

**조회 규칙**
- repository는 `org.springframework.data.repository.Repository<T, ID>`를 상속하고 **조회 메서드만** 선언한다. `JpaRepository`·`CrudRepository`를 쓰지 않는다 (save/delete가 아예 없게)
- 파생 쿼리(`findByYearAndUnitAndRegionCd`)를 먼저 쓴다. 행 수가 적은 조회(행정동 38개 수준)는 행을 읽고, 이미 읽어 온 행으로 구할 수 있는 값은 서비스에서 계산하고, 값만 따로 필요하면 max·count·avg·distinct 같은 집계는 JPQL `@Query`로 쓴다 (decisions D13). 네이티브 쿼리는 JPQL로 안 될 때만, 이유를 주석 한 줄로
- 쿼리 문자열을 이어붙여 만들지 않는다. 파라미터는 항상 바인딩
- 서비스 클래스에 `@Transactional(readOnly = true)`

**조회 조건·값**
- `ml_` 조회 조건: `year`, `unit`, `regionCd` + `ml_priority`만 `monthKey`. `ml_region`, `ml_explanation`에는 month가 없다 → 월을 골라도 연간 값을 쓴다
- `feat_` 테이블은 키가 다르다: 지역 = `dongCd`, 연간 값은 `FeatRegion`(month 없음), 월별 값은 `FeatRegionMonth`(year, month)
- 비율 컬럼(0~1)은 서버가 ×100 해서 % 값으로 내리고 **소수 첫째 자리 반올림**. 점수(0~100)는 DB 값 그대로. 단, API 1 TOP 10, API 3, API 4 헤더의 `score`는 정수로 반올림 (decisions D12). API 5 지표는 비율을 포함해 모두 정수로 반올림 (decisions D14)
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
6. **검증**: `./gradlew spotlessApply check` (포맷, ArchUnit, 전체 테스트) 통과
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

- seed: `src/test/resources/db/seed-dong-2025.sql` (11/5 덤프에서 행정동만 추린 것). 공개 저장소라 git에 올리지 않는다 (decisions D11)
- `@DataJpaTest` repository 테스트에는 `@Tag("db")`를 붙인다. CI는 `-PexcludeTags=db`로 이 태그를 빼고 돌고, 로컬 `./gradlew test`는 전부 돈다. CI가 repository 테스트를 대신 돌려주지 않으므로 PR 전에 로컬에서 전체 테스트를 통과시킨다
- 컨테이너 DB 이름은 `yongin`으로 만든다 (`.withDatabaseName("yongin")`). 엔티티가 `catalog = "yongin"`, `@Subselect`가 `yongin.ml_priority`를 가리키기 때문
- MariaDB 컨테이너는 테스트 전체가 하나를 같이 쓴다 (`MariaDbTestSupport`의 static 컨테이너 하나). 테스트 클래스마다 새로 띄우지 않는다
- 엔티티를 새로 만들면 그 엔티티 repository 테스트에 매핑 확인을 넣는다: seed 행 수, 키 조회 1건, JSON·Boolean 필드 값 (특히 `MlPriority`는 `monthKey` 목록이 `[0, 2]`인지)
- 기대값은 **seed에서 SQL로 직접 확인한 값만** 쓴다. 숫자를 추측하거나 명세 예시에서 그냥 베끼지 않는다. 확인에 쓴 SQL을 테스트 주석에 한 줄로 남긴다
- 이미 확인된 기준값 (2025, dong, 연간): 지역 38개, 취약지역 9개, 평균 39.1, 1위 원삼면 68.1 / 포곡읍(4146125000) 60.4·7위 / 시 평균 택시 성사율 53.8%, 생활인구 5,938명, 정류장 81.6개, 고령 17.7%
- 테스트 이름은 `@DisplayName`에 한글 문장으로: `"월을 고르면 그 달 점수와 순위를 내린다"`
- 실패 케이스를 반드시 넣는다: 없는 regionCode, 범위 밖 month, 적재 안 된 month(예: 5월)
- 테스트도 간결하게: 정상 케이스 1~2개 + 위 실패 케이스면 충분하다. 같은 내용을 여러 층에서 반복 검증하지 않는다 (예: 파라미터 검증은 컨트롤러 테스트에서만, SQL 결과는 repository 테스트에서만)

## 9. 자동 검증 게이트 (CI)

GitHub Actions `ci.yml`이 PR마다 아래를 돌리고, 하나라도 실패하면 머지하지 않는다.

1. `spotlessCheck` — 포맷
2. `test -PexcludeTags=db` — 단위·통합 테스트 (Testcontainers). seed가 필요한 `db` 태그 테스트는 제외 (decisions D11)
3. ArchUnit 규칙 (test에 포함)
   - controller → service → repository 방향만 허용 (controller는 `yongin` 패키지에 의존 금지)
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
