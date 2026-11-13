# 설계 결정 기록

형식: 무엇을 정했는지 / 고른 것과 고르지 않은 것 / 이유. 결정을 바꾸면 지우지 말고 "변경" 줄을 추가한다.
`상태: 확정`은 따른다. `상태: 제안`은 팀 확인 전이라 바뀔 수 있다.

## D1. yongin DB 접근: Spring Data JPA 읽기 전용 엔티티
- 상태: 확정 (2026-11-05)
- 고름: JPA 엔티티(`@Immutable`) + `@IdClass` 복합키, `ml_priority`만 `@Subselect` / 고르지 않음: `JdbcClient` + record
- 이유: 기존 프로젝트가 이미 `@Entity @Immutable @Table(schema = "yongin")` 방식으로 분석 테이블을 읽고 있어 팀원 코드와 방식을 맞춘다. 테이블에 DB PK는 없지만 JPA는 DB 제약이 아니라 `@Id` 조합의 유일성만 필요하고, 8개 테이블 모두 유일한 컬럼 조합이 있다(seed로 확인)
- 예외: `ml_priority`는 연간 행의 `month`가 NULL이라, 복합키에 그대로 넣으면 Hibernate가 그 행을 엔티티로 만들지 못한다. `@Subselect`에서 `COALESCE(month, 0) AS month_key`로 바꿔 키로 쓴다 (0 = 연간)
- 읽기 전용 보장: `@Immutable`, setter 없음, repository는 `Repository<T, ID>`만 상속(save/delete 없음), `ddl-auto=none`. ArchUnit으로 강제
- 변경 (2026-11-05): 처음에는 "PK가 없어 JPA 불가"로 보고 JdbcClient를 골랐으나, PK 없는 것 자체는 문제가 아니고 실제 문제는 `ml_priority`의 NULL month 하나뿐이라 JPA로 변경
- 변경 (2026-11-05): `@Table(schema = "yongin")` → `@Table(catalog = "yongin")` / MariaDB Dialect는 `catalog`만 한정자로 지원하고, 이 저장소에는 따를 기존 엔티티가 없다

## D2. 대시보드 지도는 종합 취약도 하나만
- 상태: 확정 (2026-11-05)
- 고름: `score_total` 하나 / 고르지 않음: 교통 수요·공급 부족도 탭 3개
- 이유: 화면 단순화 (팀 결정)

## D3. 읍·면·동 검색은 프론트에서 필터링
- 상태: 확정 (2026-11-05)
- 고름: 지도 응답(38개)을 프론트가 거름 / 고르지 않음: 검색 API
- 이유: 대상이 38개뿐이라 이미 받은 목록으로 충분

## D4. 대시보드·상세 모두 연간(전체)과 월 선택
- 상태: 확정 (2026-11-05)
- `month` 없음 = 연간(`month IS NULL`), 1~12 = 그 달. 유형·AI 추천(`ml_region`, `ml_explanation`)은 month 컬럼이 없어 항상 연간
- 현재 월별 ML 값은 2월만 있고 나머지 달은 추후 적재
- 변경 (2026-11-08): `month` 없음 = `month IS NULL` 행 → NULL 행은 쓰지 않고 서버가 월별 행으로 재계산(D16). 유형·추천도 월을 고르면 그 달 값 / 11/8 DB에서 `ml_region`·`ml_explanation`에도 month가 생겼고, 테이블마다 NULL 행 유무와 월 범위가 달라 한 규칙으로 통일

## D5. ML 실행(8번)은 범위 포함 (처음엔 AI 추천 다시 생성으로 제외)
- 상태: 확정 (2026-11-05)
- 이유: 결과 저장 위치(FastAPI가 `ml_explanation` 덮어쓰기 vs `yongin_app` 별도 테이블) 미정. 정해지면 `yongin_app` 필요 여부도 함께 결정
- 변경 (2026-11-10): 범위 제외 → 확정(범위 포함). 지역별 다시 생성 가안 대신 yong_ml FastAPI가 기간 전체를 다시 계산해 `ml_` 테이블을 덮어쓰고, Spring은 실행 요청·상태 조회만 한다 (D17). 저장은 ML이 하므로 `yongin_app`은 여전히 쓰지 않음

## D6. 이번 범위는 행정동만, unit 파라미터 없음
- 상태: 확정 (2026-11-05)
- `unit` 파라미터는 받되 `village`는 `INVALID_REQUEST`로 막는다
- 이유: 리는 `feat_village`·면 단위 값 등 매핑이 달라 별도 작업. 나중에 열 때 API 형태는 그대로
- 변경 (2026-11-05): `village`를 `INVALID_REQUEST`로 막기 → `unit` 파라미터를 없애고 항상 `dong`으로 조회 / 사용자 결정. 상태도 제안 → 확정

## D7. 적재 안 된 연·월 요청은 404 PERIOD_NOT_FOUND
- 상태: 확정 (2026-11-05)
- 고름: 404 + `PERIOD_NOT_FOUND` / 고르지 않음: 빈 목록 200
- 이유: 2월만 있는 지금, 5월 요청에 빈 화면을 주면 "데이터 없음"과 "취약지역 0개"를 구분할 수 없다. 프론트는 `availableMonths`로 고를 수 있는 달만 보여준다
- 변경 (2026-11-05): 상태 제안 → 확정 / 사용자 결정
- 변경 (2026-11-05): 지역 API도 연도 데이터가 아예 없으면 `PERIOD_NOT_FOUND`, 연도는 있는데 지역이 없으면 `REGION_NOT_FOUND` / API 1·2와 맞춘다. 사용자 결정
- 변경 (2026-11-08): 고른 월이 공통 달(`ml_priority`·`ml_region`·`ml_explanation`)에 없으면 `PERIOD_NOT_FOUND` → 404 `SUITABILITY_NOT_READY` "존재하지 않는 달입니다." (API 1~5, 7 공통, `PeriodService.checkMonth`) / 사용자 결정. 판단 순서는 연도 → 월 → 지역
- 변경 (2026-11-12): API 9(통합 최적안)는 연도 → 월(공통 달, `plan_` 테이블은 넣지 않음) → 그 기간 `plan_summary` 행이 없으면 404 `PLAN_NOT_FOUND` / 사용자 결정. plan 적재가 늦어도 API 1~7은 막히지 않게 한다 (D18)

## D8. 응답 형식 ApiResponse로 통일, 예외는 BusinessException + ControllerAdvice
- 상태: 확정 (2026-11-05)
- 이유: 프론트가 성공·실패를 같은 형태로 처리. 에러 응답 생성 위치를 한 곳으로 모아 AI가 만든 코드도 같은 형태를 내게 한다 (ArchUnit으로 강제)

## D9. 테스트 커버리지는 측정만 하고 CI 게이트로 쓰지 않음
- 상태: 확정 (2026-11-05)
- 고름: JaCoCo 리포트만 만들고 WORKLOG에 기록 / 고르지 않음: service 패키지 80% 미달 시 CI 실패
- 이유: 개발 기간이 약 10일인 해커톤이라, 마감 직전 커버리지 문턱 때문에 시연 기능 머지가 막히는 위험이 더 크다. 테스트 누락은 커버리지 숫자 대신 "API당 테스트 3종 + 실패 케이스 필수" 규칙(CLAUDE.md 8장)과 PR 체크리스트로 막는다. 커버리지 값은 규칙 버전별 비교용 지표로만 쓴다

## D10. 구현은 명세에 필요한 만큼만 간결하게
- 상태: 확정 (2026-11-05)
- 고름: 클래스 하나짜리 서비스·repository, 인터페이스·추상화·Mapper 클래스 없음 / 고르지 않음: 확장을 대비한 계층·추상화
- 이유: 해커톤 목표는 기한 내 동작하는 7개 API다. 코드가 짧을수록 AI가 만든 코드를 사람이 빨리 검토할 수 있고, 리뷰 비용이 줄어든다. 공통 규칙(응답 형식, 예외, 레이어 방향, 테스트 순서)은 유지한다
- 세부: 같은 로직이 두 번 나오면 메서드로 뺀다. 캐싱은 필요할 때만 쓰고 이유를 여기 남긴다. Swagger는 이미 세팅된 설정을 따르고 API마다 요약·파라미터 설명만 단다 (프론트 공유용)

## D11. seed는 git에 올리지 않고, repository 테스트는 CI에서 제외
- 상태: 확정 (2026-11-05)
- 고름: repository 테스트에 `@Tag("db")`, CI는 `./gradlew test -PexcludeTags=db`, 로컬 `./gradlew test`는 전부 실행 / 고르지 않음: `.gitignore` 예외로 seed 커밋
- 이유: 저장소가 공개라 seed의 분석 데이터를 올리지 않는다. 대신 CI는 엔티티 매핑과 쿼리를 검증하지 못하므로 PR 전에 로컬 전체 테스트를 통과시킨다

## D12. 대시보드 TOP 10 정렬과 updatedAt 출처
- 상태: 확정 (2026-11-05)
- 고름: TOP 10은 점수 소수 셋째 자리까지 내림차순, 같으면 `rank_total`, `region_cd` 순. `updatedAt`은 `ml_model_info.trained_at`, 행이 없으면 null / 고르지 않음: `rank_total`만으로 정렬, `updatedAt` 없을 때 에러
- 이유: 2월 3위처럼 동점이 있어 순서를 고정한다. `ml_priority`에는 갱신 시각 컬럼이 없고, 시각이 없어도 점수는 정상이라 에러로 막지 않는다
- 변경 (2026-11-05): TOP 10 정렬 `소수 셋째 자리 점수, rank_total, region_cd` → `rank_total, region_cd` / seed에서 `rank_total`과 점수 순서가 일치해 점수 정렬이 필요 없다
- 변경 (2026-11-05): TOP 10 `score` 소수 첫째 자리 → 정수로 반올림 / 사용자 결정. 소수점 없이 점수만 보여 준다
- 변경 (2026-11-05): 지역 미리보기(API 3) `score`도 정수로 반올림 / 사용자 결정. 바로 노출되는 값이라 API 1과 맞춘다
- 변경 (2026-11-06): 지역 상세(API 4) 헤더 `score`도 정수로 반올림, 같은 유형 지역은 `rank_total`, `region_cd` 순 / 사용자 결정. 미리보기와 같은 지역의 숫자를 맞춘다
- 변경 (2026-11-06): AI 추천(API 7) `options[].score`도 정수로 반올림, null은 유지 / 사용자 결정. 없는 연도와 지역은 구분하지 않고 `RECOMMENDATION_NOT_FOUND`
- 변경 (2026-11-05): 지도(API 2)도 `rank_total`, `region_cd` 순으로 정렬한다. 점수는 정수로 바꾸지 않고 DB 값 그대로 내린다 / 사용자 결정
- 변경 (2026-11-08): 지도 점수도 정수로 반올림하고, 모든 순위는 `rank_total` 대신 정수 점수로 재계산(동점 같은 순위, 다음 순위 건너뜀), 정렬은 재계산 순위, `region_cd` / 사용자 결정. 화면에 보이는 숫자가 기준
- 변경 (2026-11-09): 같은 순위 안의 정렬 `region_cd` → 반올림 전 점수 내림차순, 같으면 `region_cd` (API 1 TOP 10, API 2 지도, API 4 같은 유형 지역) / 사용자 결정. 공동 순위 안에서도 실제 점수가 높은 지역을 먼저 보여 준다. 순위 숫자는 그대로

## D13. 행 수가 적은 조회는 서비스에서 계산
- 상태: 확정 (2026-11-05)
- 고름: 행정동 38개 수준의 조회는 행을 읽고, 이미 읽어 온 행으로 구할 수 있는 값은 서비스에서 계산하고, 값만 따로 필요하면 max·count·avg·distinct 같은 집계는 JPQL로 쓴다 / 고르지 않음: 값마다 `count`, `avg` JPQL 메서드를 두는 방식
- 이유: 38행이면 전부 읽어도 부담이 작고 repository 메서드가 줄어든다. 계산이 서비스로 오면 CI에서 도는 단위 테스트로 검증된다. 행이 크게 늘면 다시 판단한다
- 변경 (2026-11-05): JPQL 대상 `distinct`처럼 행을 읽어 처리하기 어려운 것 → max·count·avg·distinct 같은 집계 / 사용자 결정. 쿼리 정렬도 빼고 정렬은 서비스에서만 한다
- 변경 (2026-11-05): 위 문장이 서비스 계산과 어긋나 "이미 읽어 온 행으로 구할 수 있는 값은 서비스, 값만 따로 필요하면 JPQL"로 구분 / 사용자 승인

## D14. 시 평균 비교 지표는 모두 정수로 표시
- 상태: 확정 (2026-11-06)
- 고름: 택시 성사율, 고령 비율, 정류장 수, 생활인구의 지역 값과 시 평균을 모두 정수로 반올림 / 고르지 않음: 비율을 소수 첫째 자리로 표시
- 이유: API 1~4 화면의 점수와 표시를 맞춘다. 시 평균 행이 없으면 404 없이 `cityAvg`만 null이다
- 변경 (2026-11-06): 미배차율 추이(API 6)의 비율도 정수로 반올림하고, 시 평균 행이 없으면 `cityAvgRate`만 null / 사용자 결정. 차트는 일단 정수로 시작한다

## D15. 앱은 yongin에 SELECT 전용 계정 하나로 접속
- 상태: 확정 (2026-11-07)
- 고름: 앱이 `yongin`에 바로 접속하고, 계정은 `GRANT SELECT ON yongin.*` 하나 / 고르지 않음: 빈 `yongin_app`에 접속하고 `yongin` SELECT + `yongin_app` CRUD 혼합 권한
- 이유: 엔티티 8개가 모두 `catalog = "yongin"`이고 `yongin_app`에는 테이블이 없다. 쓰지 않는 DB와 쓰기 권한을 없애면 읽기 전용이 계정 권한으로 확실해진다. 서버 계정은 `db/init/01-app-user.sh`가 만든다
- 다시 볼 때: API 8(D5)에서 결과를 별도 테이블에 저장하기로 하면 쓰기용 DB·권한을 다시 정한다
- 변경 (2026-11-10): 앱 계정은 그대로 SELECT 전용. ML 컨테이너용 계정을 따로 둔다: `yongin.*` SELECT + `ml_` 테이블 5개(`ml_priority`, `ml_suitability`, `ml_region`, `ml_explanation`, `ml_model_info`)에만 INSERT·DELETE·CREATE·DROP·ALTER (yong_ml `io_db.upsert`가 행 삭제·추가, 경우에 따라 테이블 교체·컬럼 추가를 함). 계정 값은 `.env`, 생성은 root / 이유: 앱의 읽기 전용은 유지하고 ML 쓰기는 `ml_`로만 제한 (D17)

## D16. 연간 = DB 연간 행 (`ml_` 테이블의 month NULL 행, 처음엔 서버가 월별 행으로 계산)
- 상태: 확정 (2026-11-08)
- 고름: `month IS NULL` 행은 쓰지 않는다. 연간 숫자(점수·순위·개수) = 월별 값 평균, 분류·문장(유형, 신뢰도, 추천, 요약) = 그 테이블의 최근 달. 점수는 정수, 순위는 정수 점수로 재계산, 취약지역 = 정수 50점 이상. 응답은 7개 API 공통 `period { year, months, type(YEAR_ALL / YEAR_LATEST / MONTH) }`, API 3·4는 `typePeriod` 추가. 고를 수 있는 연·월 목록은 응답에서 뺀다 / 고르지 않음: DB 연간 NULL 행 사용, 테이블마다 다른 연간 규칙, 기준 필드(`scoreBasis` 등)를 값마다 따로 내리기, `availableMonths`를 응답에 유지
- 이유: 11/8 DB에서 `ml_priority`·`ml_suitability`는 1~12월 + NULL 행, `ml_region`·`ml_explanation`·`ml_model_info`는 1~7월만(NULL 행 없음)이라 테이블마다 연간 행 유무와 월 범위가 달랐다. 한 규칙으로 통일하고, 월 범위는 조회 시점에 계산해 데이터가 들어오면 코드 수정 없이 반영된다. 기준 필드는 프론트가 쓸 데가 적어 연·월·종류만 남겼고, 선택 목록은 프론트가 가진다
- 세부: API 5 연간 지역 값은 `feat_region`(연간 전용 테이블, 택시 성사율이 월별 건수 가중 평균과 일치)을 그대로 쓴다. API 7 options 점수는 같은 달 `ml_suitability`와 861건 일치해 엔티티를 추가하지 않고 options 값을 쓴다
- 다시 볼 때: 프론트가 고를 수 있는 달을 알아야 하면 공통 달 목록을 응답에 다시 넣는다 (`PeriodService.availableMonths`는 남아 있음)
- 변경 (2026-11-11): 연간 = 숫자는 월 평균·분류와 문장은 최근 달 → `ml_` 값은 모두 각 테이블의 month NULL 행(점수·유형·신뢰도·추천·요약·`updatedAt`). NULL 행은 `ml_` 엔티티 4개를 `@Subselect`(`coalesce(month, 0)`)로 바꿔 month 0으로 읽고, 서비스는 `month == null ? 0 : month`로 기존 조회 메서드를 쓴다. 월 평균·최근 달 계산은 제거하고 순위·취약지역은 정수 점수 기준 유지(D12). 연간 `period`·`typePeriod`는 모두 `YEAR_ALL` + 공통 달, `YEAR_LATEST`는 enum에만 남김(프론트 호환). `feat_`는 그대로 / 11/11 최종본 DB에서 `ml_` 5개 테이블 모두 1~12월 + NULL 행이 생겨 테이블마다 다르다는 이유가 사라졌고, NULL 행이 월 평균·12월과 달라(점수 30/38, `options` 38/38, `summary` 22/38, 유형 0/38) ML이 연간을 따로 계산한 값으로 판단. 연간 카드도 LLM으로 가장 나중(11/10 17:00)에 생성. 월별 엔티티로 NULL 행을 읽으면 Hibernate가 null 엔티티를 돌려줘(38/38 확인) NULL을 0으로 바꿔 읽음. 처음엔 연간 전용 엔티티 4개 + 상위 클래스 3개 + repository 4개로 나눴다가 코드가 많아 `@Subselect` 하나로 단순화. 사용자 결정

## D17. ML 실행(API 8)은 Spring이 yong_ml FastAPI를 부르기만 한다
- 상태: 확정 (2026-11-10)
- 고름: `POST /api/ml/run`, `GET /api/ml/run/status`를 FastAPI와 같은 경로로 공개(nginx `/api/`). FastAPI 응답(RunStatus)을 그대로 `data`에 담고, 409 → `ML_RUNNING`(409, 실행 중 재요청 거절), 422 → `INVALID_REQUEST`, 500·연결 실패·타임아웃 → `ML_UNAVAILABLE`(502). Spring이 year·month를 먼저 검사. `period`는 내리지 않음, 시각은 ISO 그대로. ML 서버는 compose `ml` 서비스(외부 포트 없음), Dockerfile은 이 저장소에 두고 yong_ml은 서버에 클론만 / 고르지 않음: 지역별 다시 생성 가안, `/api/internal/` 비공개 경로, FastAPI 끝날 때까지 대기, yong_ml 저장소 수정, Dockerfile 없이 시작 시 pip 설치
- 이유: FastAPI는 기간 전체를 약 15분 동안 다시 계산하고 202로 바로 응답한다. 해커톤 시연에서는 호출 가능함만 보여주면 되므로 Spring은 얇게 중계만 한다. 시연에서 프론트가 눌러야 해서 공개하고, 동시 실행은 ML의 `GET_LOCK`으로 하나만 돈다. API 7의 `createdAt` 형식은 건드리지 않는다
- 다시 볼 때: 해커톤 후 공개 운영이면 인증 또는 비공개 경로로 바꾼다

## D18. 통합 최적안(API 9)은 세 안을 한 번에 내리는 API 하나
- 상태: 확정 (2026-11-12)
- 고름: `GET /api/plans`(`year`, `month`) 하나로 `plans[]` 3개(1 효율형, 2 형평형, 3 균형형)에 카드 요약 + 투입 순서(`regions`) + 예산 밖(`skipped`)을 모두 담는다. 예산은 안마다 `plans[].budget`, 수단 칩 `modeCounts`는 0도 포함해 항상 3개(BUS → DRT → HAPPY_TAXI). 그 기간 plan 행이 없으면 404 `PLAN_NOT_FOUND`. `plan_` 테이블은 공통 달 계산에 넣지 않는다 / 고르지 않음: 카드 요약 API + 안 상세 API 2개로 나누기, 예산을 맨 위 하나로 내리기, 0인 수단 빼기, `PERIOD_NOT_FOUND` 재사용, 공통 달에 `plan_` 포함
- 이유: 안은 3개로 고정이고 기간당 지역 줄이 연간 27개(안마다 포함 5 + 예산 밖 4) 수준이라 나눠서 얻는 게 없다. 한 번에 내리면 카드를 눌러도 요청 없이 바로 바뀌고, "안을 누르면 지도·TOP 10에 반영"도 `regions[].regionCode`로 프론트가 처리한다. 예산은 지금 세 안이 같지만 안마다 달라질 수 있어 안 단위로 둔다. plan 적재가 늦은 달이 있어도 API 1~7이 같이 막히지 않게 공통 달과 분리한다. 사용자 결정
- 세부: 12일자 DB(`yongin_2026-11-12.sql`)에서 `plan_summary`(39행)·`plan_region`(4,290행)만 추가되고 기존 32개 테이블은 그대로다. 엔티티 `PlanSummary`(year, month, plan), `PlanRegion`(year, month, plan, unit, regionCd)을 D16과 같이 `@Subselect`(`coalesce(month, 0)`)로 읽는다 (키 중복 0 확인). `unit = 'dong'`만(D6), `status = '후보 아님'` 행은 내리지 않는다. 금액은 원 단위 정수 그대로, 억 변환·막대 비율은 프론트. 응답 키는 기존 DTO에 맞춘다(`rank`, `regionCode`, `regionName`, `gu`, `mode`·`name`). 앱 계정은 `yongin.*` SELECT라 새 테이블에 GRANT가 필요 없다(D15)
- 범위 밖: "단가 ±30%" 줄(계산하지 않음), 하단 단가·출처 안내(프론트 고정 문구), 행정동 상세의 안별 한 줄(`plan_region.line`), 리 단위(`village`) 행
- 다시 볼 때: API 8(ML 실행)이 `plan_` 테이블도 다시 만들면 ML 계정에 `plan_` 쓰기 권한을 추가한다(D15). 행정동 상세의 안별 한 줄이 필요해지면 API 4에 붙일지 정한다
- 변경 (2026-11-12): 지도·TOP 10 통합 최적안 토글용으로 `plans[].byRegion` 추가 (38개 전체, status·mode). 색 규칙은 프론트 / 고르지 않음: API 1·2 응답에 plan 필드 추가, regions·skipped를 byRegion 하나로 합치기, 서버가 범례 값 계산 / 이유: 기존 JSON을 바꾸지 않고, 지도·TOP 10이 plan 데이터에 묶이지 않으며, 카드 전환 시 재호출이 없다. 이미 읽는 114행으로 만들어 조회도 늘지 않는다. 사용자 결정
- 변경 (2026-11-12): byRegion을 status·mode 원값 → category(서버 계산)·categoryName·status로 변경 / 고르지 않음: 프론트가 범례 계산 / 이유: 후보 아님 행에도 mode(BUS·DRT)가 있어 mode만 보면 잘못 칠한다. 사용자 결정
- 변경 (2026-11-13): 통합 최적안을 9-1 `/api/plans`(카드·표)와 9-2 `/api/plans/map`(지도·TOP 10 배지)으로 나눔. 9-2 category는 6종(BUS/DRT/HAPPY_TAXI/SKIPPED/KEEP/NONE "기타 미포함")으로 서버가 status → mode 순으로 계산. 9-1에 days, regions·skipped에 score 추가 / 고르지 않음: 9-1 하나에 byRegion까지 담기(응답이 길어짐), API 1·2 수정, 9-2에 plan 파라미터(카드 전환마다 재호출), ML 명세의 `status = '현행 유지'` 조건(DB status에 없는 값이라 mode KEEP으로 판단) / 이유: 최종 데모 범례가 예산 밖·현행 유지·기타 미포함을 나눠서 보여 주고, 화면 영역별로 응답을 나누면 각 응답이 짧아진다. 같은 repository 메서드를 써서 조회 로직은 하나다. 9-2는 `plan_region` 행이 없으면 `PLAN_NOT_FOUND`. 사용자 결정
