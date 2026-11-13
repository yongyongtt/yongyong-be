# yongin DB 데이터 매핑 (API 1~7, 9용)

기준 DB: 2026-11-11 최종본 (MariaDB 12.2, 이전 기준 11/8 적재본, `yongin_2026-11-05.sql`). Python(ML 팀원)이 적재하고 Spring은 읽기만 한다.
2026-11-12: `yongin_2026-11-12.sql`에서 `plan_summary`(39행)·`plan_region`(4,290행) 2개만 추가됨. 기존 32개 테이블은 11/10 덤프와 스키마·데이터 모두 같다. Spring 계정 `yongyong`은 `yongin` DB 단위 SELECT 권한이라 새 테이블도 따로 GRANT 없이 읽힌다.
테스트용 seed: `src/test/resources/db/seed-dong-2025.sql` — 11/11 최종본에서 아래 8개 테이블 + 11/12 plan 테이블 2개, 2025년 행정동(`unit = 'dong'`) 행만 (연간 NULL 행 포함). `mariadb-dump --compact --skip-extended-insert --no-autocommit yongin <테이블> --where="year=2025 AND unit='dong'"`(`ml_model_info`·`feat_region*`·`feat_city_month`·`plan_summary`는 `year=2025`만)로 만들고 `/*!`·`/*M!` 줄을 뺀다. git에 올리지 않는다 (decisions D11).

## 11/11 최종본 테이블별 월 (2025, dong)

| 테이블 | 월 | month NULL 행 (연간) | 비고 |
| --- | --- | --- | --- |
| `ml_priority` | 1~12 | 38 (연간 값으로 씀) | 지역당 월 1행 + 연간 1행 |
| `ml_suitability` | 1~12 | 114 | 엔티티 없음. `ml_explanation.options`의 `score`와 같은 기간 값 (11/8 861건 일치) |
| `ml_region`, `ml_explanation` | 1~12 | 38 (연간 값으로 씀) | 11/8에는 1~7월, NULL 행 없음 |
| `ml_model_info` | 1~12 | 3 (연간 값으로 씀) | 모델 3개 × 기간 |
| `feat_region_month`, `feat_city_month` | 1~12 | 없음 | 연간은 `feat_region`·`feat_city` |
| `plan_summary` (11/12) | 1~12 | 3 (연간 값으로 씀) | 안 3개 × 기간 = 39행. 공통 달 계산에는 넣지 않음 |
| `plan_region` (11/12) | 1~12 | 114 (dong) | 안 3개 × 행정동 38개 × 기간. `village` 행도 있으나 쓰지 않음 |

월 범위는 코드에 하드코딩하지 않는다. 고를 수 있는 달(공통 달, 404 판단용)은 조회 시점에 테이블에 있는 달로 정한다. 연간 `ml_` 값은 NULL 행을 그대로 쓴다 (decisions D16, api-spec "연간·월 규칙").

## 공통 사항

- 모든 테이블에 **PK·인덱스가 없다** → JPA 엔티티는 행을 유일하게 구분하는 컬럼 조합을 `@IdClass`로 지정한다 (아래 "엔티티 매핑", decisions D1)
- 문자열 코드 컬럼(`region_cd`, `dong_cd`)은 `TEXT` 타입이다
- 비율 컬럼은 0~1 → API에서 ×100
- JSON은 `TEXT` 컬럼에 문자열로 들어 있다 → AttributeConverter로 `List`로 변환
- `tinyint(1)` 컬럼은 `Boolean`으로 매핑

## 엔티티 매핑

모두 `@Entity @Immutable`, 패키지 `yongin.entity`. `@Id` 컬럼 조합이 실제로 겹치지 않는지 seed에서 확인함 (행 수 = 키 조합 수).

| 엔티티 | 테이블 | `@IdClass` 키 | 비고 |
| --- | --- | --- | --- |
| `MlPriority` | `ml_priority` | year, month, unit, regionCd | `@Subselect`, 연간 = month 0. `rank_total`, `is_vulnerable`은 매핑하지 않음 (서버가 정수 점수로 계산) |
| `MlRegion` | `ml_region` | year, month, unit, regionCd | `@Subselect`, 연간 = month 0. `typeChips` → `List<String>` (StringListConverter) |
| `MlExplanation` | `ml_explanation` | year, month, unit, regionCd | `@Subselect`, 연간 = month 0. `options` → `List<OptionJson>` (OptionListConverter), `reasons`·`cautions` → `List<String>` |
| `MlModelInfo` | `ml_model_info` | year, month, model | `@Subselect`, 연간 = month 0. `trainedAt`만 사용 |
| `FeatRegion` | `feat_region` | year, dongCd | |
| `FeatRegionMonth` | `feat_region_month` | year, month, dongCd | month는 1~12 (NULL 없음) |
| `FeatCity` | `feat_city` | year, unit | |
| `FeatCityMonth` | `feat_city_month` | year, month | |
| `PlanSummary` | `plan_summary` | year, month, plan | `@Subselect`, 연간 = month 0 |
| `PlanRegion` | `plan_region` | year, month, plan, unit, regionCd | `@Subselect`, 연간 = month 0. `isIncluded` → Boolean |

`ml_` 테이블은 월별 행과 연간(month NULL) 행이 한 테이블에 있다. 복합 키 일부가 NULL이면 Hibernate가 null 엔티티를 돌려주므로(최종본 seed 38/38 확인) `@Subselect`에서 NULL을 0으로 바꿔 읽는다. 연간 = month 0 (decisions D16)

```java
@Entity
@Immutable
@Subselect("select year, coalesce(month, 0) as month, unit, region_cd, region_nm, gu, score_total"
    + " from yongin.ml_priority")
@IdClass(MlPriority.Id.class)
public class MlPriority {
    public record Id(Integer year, Integer month, String unit, String regionCd) {}
    ...
}
```

repository 예시 (조회 메서드만, `Repository<T, ID>` 상속). 순위, 개수는 서비스에서 계산한다 (decisions D13). 서비스는 `PeriodQuery.monthKey()`(월이 없으면 0)를 넘긴다

```java
List<MlPriority> findByYearAndMonthAndUnit(Integer year, Integer month, String unit);   // 연간은 month 0

@Query("select distinct p.month from MlPriority p where p.year = :year and p.unit = :unit and p.month <> 0")
List<Integer> findMonths(Integer year, String unit);                                    // 공통 달 계산용
```

## 조회 조건 규칙

API에 `unit` 파라미터는 없다. 아래 `unit` 조건에는 서비스가 항상 `"dong"`을 넘긴다 (decisions D6).

| 테이블 묶음 | 연도 | 연간 / 월 | 지역 단위 | 지역 키 |
| --- | --- | --- | --- | --- |
| `ml_priority` | `year` | 월 `month = ?` / 연간 `month IS NULL` 행 (엔티티에서는 month 0) | `unit` | `region_cd` |
| `ml_region`, `ml_explanation` | `year` | 월 `month = ?` / 연간 `month IS NULL` 행 | `unit` | `region_cd` |
| `ml_model_info` | `year` | 월 `month = ?` / 연간 `month IS NULL` 행 | — | — |
| `feat_region` | `year` | 연간 전용 테이블 | (행정동 전용) | `dong_cd` |
| `feat_region_month` | `year` | `month` 1~12 (NULL 없음) | (행정동 전용) | `dong_cd` |
| `feat_city` | `year` | 연간 전용 | `unit` | 없음 (시 전체) |
| `feat_city_month` | `year` | `month` 1~12 | (시 전체) | 없음 |
| `plan_summary` | `year` | 월 `month = ?` / 연간 `month IS NULL` 행 | (행정동 기준, `unit` 컬럼 없음) | 없음 (`plan` 1~3) |
| `plan_region` | `year` | 월 `month = ?` / 연간 `month IS NULL` 행 | `unit` | `region_cd` (+ `plan`) |

## 테이블별 사용 컬럼

### ml_priority — 점수·순위 (API 1~4)
행정동: 연간 NULL 38행(연간 값) + 월별 1~12월 × 38행

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| year, month | bigint | month NULL = 연간 |
| unit | text | `dong` / `village` |
| region_cd, region_nm, gu | text | 지역 코드·이름·구 |
| score_total | double | 종합 취약도 0~100 |
| rank_total | bigint | 종합 순위. 쓰지 않음 (정수 점수로 재계산) |
| is_vulnerable | tinyint | score_total 50 이상이면 1. 쓰지 않음 (정수 점수 50 이상으로 계산) |

그 밖의 `score_mismatch, score_access, score_service, score_vulnerable, score_complaint, score_demand, score_feasibility`는 이번 API에서 쓰지 않는다.

### ml_region — 사각지대 유형 (API 3, 4, 7)

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| type_nm, type_desc | text | 유형 이름·한 줄 설명 (6종) |
| type_chips | text(JSON) | 문자열 배열 1~3개, 예 `["정류장까지 도보 김 · 12분 (평균 7분)"]` |
| low_reliability | tinyint | 1이면 신뢰도 낮음 (행정동은 전부 0) |
| reliability_note | text | low_reliability = 1일 때만 값 |

유형 6종: 대중교통 양호형, 출퇴근 혼잡형, 버스 운행 부족형, 정류장 접근 취약형, 외곽 대중교통 공백형, 고령 이동 취약형

### ml_explanation — AI 추천 문장 (API 3, 7)

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| is_keep | tinyint | 1이면 현행 유지 지역 (행정동 38개 중 9개) |
| options | text(JSON) | 대안 카드 배열 (아래 구조) |
| reasons, cautions | text(JSON) | 문자열 배열 1~3개 |
| summary | text | 한 줄 요약 (45자 이내) |
| source | text | `llm` / `template` (API에서는 내리지 않음) |
| created_at | text | `YYYY-MM-DD HH:mm` |
| context | text | LLM 입력 원문. 8번(다시 생성)용, 이번 범위에서 안 씀 |

options 항목 구조 (DB 원문 키)

```json
{
  "mode": "DRT",
  "name": "똑버스",
  "subtitle": "수요응답형 버스",
  "desc": "정해진 노선 없이 호출에 따라 운행해요",
  "status": "추천",
  "rank": 1,
  "score": 80.7,
  "is_current": false,
  "reasons": ["..."],
  "facts": ["..."],
  "together": null
}
```

- `status`: `추천` / `제외`
- `mode`: `BUS`, `DRT`, `HAPPY_TAXI`, `KEEP`(is_keep = 1일 때 맨 앞)

### ml_model_info — 최종 갱신일 (API 1)
`year`, `month`, `trained_at`(text, `YYYY-MM-DD HH:mm`) → 연간 `MAX(trained_at) WHERE month IS NULL`, 월 `MAX(trained_at) WHERE month = ?`

### feat_region — 지역 연간 지표 (API 5)

| 컬럼 | 설명 |
| --- | --- |
| year, dong_cd | 키 |
| taxi_match_rate | 택시 호출 성사율 0~1 |
| n_stops | 버스 정류장 수 |
| ratio65 | 65세 이상 비율 0~1 |
| fp_avg | 생활인구 (시간 평균) |

### feat_region_month — 지역 월별 지표 (API 5, 6)
행정동 38 × 2025년 12개월 = 456행

| 컬럼 | 설명 |
| --- | --- |
| year, month, dong_cd | 키 |
| taxi_req, taxi_matched | 택시 호출 수, 배차 성공 수 |
| taxi_match_rate, taxi_unmatched_rate | 0~1 |
| fp_avg | 생활인구 (이상치 표시와 관계없이 원본 값) |

### feat_city — 시 평균 연간 (API 5)
`year`, `unit`, `n_stops`(2025 dong 81.6), `elderly_ratio`(0.1774)

### feat_city_month — 시 평균 월별 (API 5, 6)
`year`, `month`, `taxi_req`, `taxi_matched`, `taxi_match_rate`, `taxi_unmatched_rate`, `fp_avg`

### plan_summary — 통합 최적안 안별 요약 (API 9-1)
안 3개(1 효율형, 2 형평형, 3 균형형) × (연간 NULL 1행 + 월 12행) = 39행. `unit` 컬럼 없음 (행정동 기준)

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| year, month, plan | bigint | month NULL = 연간, plan 1~3 |
| plan_nm, plan_desc, sort_basis | text | 안 이름·설명·정렬 기준 문구 |
| budget, total_cost, budget_left | bigint | 원 단위. 예산 = 총비용 + 남은 예산 |
| budget_rule | text | `기본 예산`. 쓰지 않음 |
| n_included, n_candidates, n_skipped | bigint | 포함·후보·예산 밖 지역 수 (후보 = 포함 + 예산 밖) |
| pop65_total | bigint | 포함 지역 65세 이상 인구 합 |
| n_bus, n_drt, n_happy | bigint | 포함 지역의 수단별 개수 |
| days | bigint | 운영 일수(61). 9-1 `days` |

### plan_region — 통합 최적안 지역별 결과 (API 9-1, 9-2)
dong 1,482행 + village 2,808행. API 9는 `unit = 'dong'`만 쓴다

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| year, month, plan, plan_nm | | 기간·안 |
| unit, region_cd, region_nm, gu | text | 지역. `parent_cd`는 village만 값 있음. 9-1 regions·skipped, 9-2는 `region_cd`만 |
| status | text | `포함` / `예산 밖` / `후보 아님` (`현행 유지` 값은 없음). 9-1 regions·skipped 구분, 9-2 `status`(`INCLUDED` / `SKIPPED` / `NOT_CANDIDATE`)와 `category` 계산 |
| is_included | tinyint | `포함`이면 1 (status와 dong 전 행 일치 확인) |
| seq | bigint | 투입 순서. `포함`만 값, 나머지 NULL |
| plan_rank | bigint | 그 안의 정렬 기준 순위. `후보 아님`은 NULL |
| mode, mode_nm | text | 수단 코드·이름 (`DRT`/똑버스, `BUS`/노선버스, `KEEP`/현행 유지). `후보 아님` 행에도 값이 있음. 9-1 regions·skipped에는 그대로 내리고, 9-2에는 내리지 않고 `category` 계산에만 씀 (`mode_nm`은 9-2에서 안 씀) |
| action, cost | text, bigint | 투입안 문구, 비용(원). `후보 아님`은 NULL |
| budget_left_at_skip | bigint | `예산 밖`만 값 (건너뛸 때 남은 예산) |
| score_total | double | 9-1 regions·skipped `score` (정수 반올림, D12와 같은 방식) |
| line | text | 행정동 상세용 안별 한 줄. 이번 범위에서 안 씀 |

그 밖의 `units, cost_basis, vehicle_note, pop65, effect, eff_per_eok, rank_eff, rank_score, rank_balance, note`는 API 9에서 쓰지 않는다.

## seed로 확인한 기준값 (11/11 최종본, 2025, dong)

확인 SQL의 정수 반올림은 `FLOOR(x + 0.5)`로 쓴다. MariaDB `ROUND(double)`은 .5를 짝수 쪽으로 보내 (`ROUND(58.5e0)` = 58) 서버(Java `Math.round`, 58.5 → 59)와 다를 수 있다.

| 항목 | 값 | 확인 SQL |
| --- | --- | --- |
| 연간 지역 수 / 취약지역 / 평균 | 38 / 9 / 39.1 (11/8 월 평균 방식은 38.3) | `SELECT COUNT(*), SUM(FLOOR(score_total+0.5)>=50), ROUND(AVG(score_total),1) FROM ml_priority WHERE year=2025 AND unit='dong' AND month IS NULL` |
| 연간 1·2위 | 원삼면(4146134000) 67.9→68 단독 1위, 이동읍(4146125600) 66.2→66 2위 | 위 조건에 `RANK() OVER (ORDER BY FLOOR(score_total+0.5) DESC)` |
| 포곡읍(4146125000) | 연간 60.3→60, 동부동(59.5→60)과 공동 7위 / 7월 60.1→60, 동부동(60.2)과 공동 7위 | 위와 같음, 7월은 `month=7` |
| 연간 NULL 행 vs 서버 계산 | 정수 점수 30/38 다름(최대 2.9점), `options` 38/38·`summary` 22/38 다름(12월 대비), 유형 0/38 | D16 변경 근거 |
| 7월 취약지역 | 9 (DB `is_vulnerable`로는 8) | `SELECT SUM(FLOOR(score_total+0.5)>=50) FROM ml_priority WHERE year=2025 AND month=7 AND unit='dong'` |
| 공통 달 (고를 수 있는 달) | 1~12 | `ml_priority`·`ml_region`·`ml_explanation`의 `DISTINCT month` 교집합 |
| 최종 갱신 | 연간 2026-11-10 17:00 / 2월 2026-11-07 14:49 / 12월 2026-11-10 10:49 | `SELECT MAX(trained_at) FROM ml_model_info WHERE year=2025 AND month IS NULL` (월은 `month=?`) |
| 포곡읍 7월 / 연간 추천 | 7월 2026-11-08 20:56, options 점수 80.5, 45.8, 27.5 / 연간 2026-11-10 17:00, 80.7, 46.8, 23.0 | `SELECT month, created_at, JSON_EXTRACT(options,'$[*].score') FROM ml_explanation WHERE year=2025 AND unit='dong' AND region_cd='4146125000' AND (month=7 OR month IS NULL)` |
| 같은 유형(정류장 접근 취약형) | 7월·연간 모두 포곡읍 포함 6곳. 연간은 이동읍, 모현읍, 포곡읍, 동천동, 보라동, 신봉동 (연간 점수 순) | `SELECT region_cd FROM ml_region WHERE year=2025 AND unit='dong' AND type_nm='정류장 접근 취약형' AND month IS NULL` (7월은 `month=7`) |
| 시 평균 택시 성사율(연간) | 53.8% | `SELECT ROUND(SUM(taxi_matched)/SUM(taxi_req)*100,1) FROM feat_city_month WHERE year=2025` |
| 시 평균 생활인구(연간) | 6,267 (11/5에는 5,938) | `SELECT ROUND(AVG(fp_avg)) FROM feat_city_month WHERE year=2025` |
| 시 평균 정류장 / 고령 | 81.6 / 17.7% | `SELECT n_stops, elderly_ratio FROM feat_city WHERE year=2025 AND unit='dong'` |
| 포곡읍 feat_region 연간 | 성사율 0.5337(월별 건수 가중 평균과 일치), 정류장 148, 고령 0.1984, 생활인구 10403.2 | `SELECT taxi_match_rate, n_stops, ratio65, fp_avg FROM feat_region WHERE dong_cd='4146125000'` |

### 통합 최적안 기준값 (11/12, 2025, dong)

| 항목 | 값 | 확인 SQL |
| --- | --- | --- |
| 연간 예산 / 총비용 / 남은 예산 | 세 안 모두 711,666,667 / 610,000,000 / 101,666,667 (39행 모두 예산 같음) | `SELECT plan, budget, total_cost, budget_left FROM plan_summary WHERE year=2025 AND month IS NULL` |
| 연간 포함 / 후보 / 예산 밖 | 세 안 모두 5 / 9 / 4, 수단 칩 버스 0·똑버스 5·행복택시 0 | 위 조건 `n_included, n_candidates, n_skipped, n_bus, n_drt, n_happy` |
| 연간 65세 이상 합 | 1안 30,433 / 2안 21,848 / 3안 24,999 | 위 조건 `pop65_total` |
| 연간 3안 투입 순서 | 모현읍 → 이동읍 → 원삼면 → 포곡읍 → 양지면 (모두 똑버스 4대 · 122,000,000) | `SELECT seq, region_nm, action, cost FROM plan_region WHERE year=2025 AND month IS NULL AND plan=3 AND unit='dong' AND is_included=1 ORDER BY seq` |
| 연간 3안 예산 밖 | 남사읍(6) · 동천동(7) · 백암면(8) · 동부동(9), 남은 예산 101,666,667 | 위 조건 `status='예산 밖' ORDER BY plan_rank` |
| 연간 1안 투입 순서 | 모현읍 → 동천동 → 포곡읍 → 이동읍 → 남사읍 | 위와 같음, `plan=1` |
| 연간 3안 9-2 지도 | 38개: category DRT 5 (모현읍·이동읍·원삼면·포곡읍·양지면) / SKIPPED 4 (남사읍·동천동·백암면·동부동) / KEEP 9 / NONE 20 (후보 아님 BUS 14·DRT 6) / BUS 0 / HAPPY_TAXI 0, status INCLUDED 5 / SKIPPED 4 / NOT_CANDIDATE 29 | `SELECT status, mode, COUNT(*) FROM plan_region WHERE year=2025 AND month IS NULL AND plan=3 AND unit='dong' GROUP BY status, mode` |
| 연간 days / 3안 score | 세 안 모두 days 61 (39행 모두 61) / 3안 투입 1번 모현읍 64.7 → 65, 예산 밖 남사읍 61.5 → 62 | `SELECT plan, days FROM plan_summary WHERE year=2025 AND month IS NULL`, `SELECT region_nm, status, score_total FROM plan_region WHERE year=2025 AND month IS NULL AND plan=3 AND unit='dong' AND status<>'후보 아님'` |
| 키 중복 | (year, month, plan, unit, region_cd) 4,290행 중복 0 | `SELECT COUNT(*), COUNT(DISTINCT year, COALESCE(month,0), plan, unit, region_cd) FROM plan_region` |

확인 사실 (12일자 DB, 2025, dong, 덤프에서 전 기간·전 안 대조)

- 후보(`포함`·`예산 밖`) = 반올림 전 `score_total` 50 이상. 모든 기간·안에서 예외 0건, `score_total` NULL 0건
- `plan_region.mode`는 같은 기간·지역 `ml_explanation.options`의 1순위(`rank = 1`) mode와 1,482행 모두 일치. 후보의 1순위는 전부 DRT이고, 행정동에서 HAPPY_TAXI가 1순위인 경우는 없음 (2순위 3건뿐)
- 7월(동천동 49.8)·10월(동천동 49.5)은 대시보드 취약지역(정수 반올림 50 이상)과 최적안 후보가 1곳씩 다름. 나머지 기간은 같음 (ML 확인 중)
