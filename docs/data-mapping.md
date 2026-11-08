# yongin DB 데이터 매핑 (API 1~7용)

기준 DB: 2026-11-08 적재본 (MariaDB 12.2, 이전 기준 `yongin_2026-11-05.sql`). Python(ML 팀원)이 적재하고 Spring은 읽기만 한다.
테스트용 seed: `src/test/resources/db/seed-dong-2025.sql` — 11/8 DB에서 아래 8개 테이블, 행정동(`unit = 'dong'`) 행만 (연간 NULL 행 포함, 섞이지 않는지 검증용). git에 올리지 않는다 (decisions D11).

## 11/8 DB 테이블별 월 (2025, dong)

| 테이블 | 월 | month NULL 행 | 비고 |
| --- | --- | --- | --- |
| `ml_priority` | 1~12 | 있음 (쓰지 않음) | 지역당 월 1행 |
| `ml_suitability` | 1~12 | 있음 (쓰지 않음) | 엔티티 없음. `ml_explanation.options`의 `score`와 같은 달 값이 같다 (861건 일치) |
| `ml_region`, `ml_explanation` | 1~7 | 없음 | 11/8에 `month` 컬럼 추가 |
| `ml_model_info` | 1~7 | 없음 | 11/8에 `month` 컬럼 추가 |
| `feat_region_month`, `feat_city_month` | 1~12 | 없음 | |

월 범위는 코드에 하드코딩하지 않는다. 연간 계산과 고를 수 있는 달(공통 달, 404 판단용)은 조회 시점에 테이블에 있는 달로 정한다 (decisions D16, api-spec "연간·월 규칙").

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
| `MlPriority` | `ml_priority` | year, month, unit, regionCd | `@SQLRestriction("month is not null")`. `rank_total`, `is_vulnerable`은 매핑하지 않음 (서버가 재계산) |
| `MlRegion` | `ml_region` | year, month, unit, regionCd | `@SQLRestriction("month is not null")`. `typeChips` → `List<String>` (StringListConverter) |
| `MlExplanation` | `ml_explanation` | year, month, unit, regionCd | `@SQLRestriction("month is not null")`. `options` → `List<OptionJson>` (OptionListConverter), `reasons`·`cautions` → `List<String>` |
| `MlModelInfo` | `ml_model_info` | year, month, model | `@SQLRestriction("month is not null")`. `trainedAt`만 사용 |
| `FeatRegion` | `feat_region` | year, dongCd | |
| `FeatRegionMonth` | `feat_region_month` | year, month, dongCd | month는 1~12 (NULL 없음) |
| `FeatCity` | `feat_city` | year, unit | |
| `FeatCityMonth` | `feat_city_month` | year, month | |

`MlPriority` 예시 (`ml_` 엔티티 4개 모두 같은 방식)

```java
@Entity
@Immutable
@Table(catalog = "yongin", name = "ml_priority")
@SQLRestriction("month is not null") // 연간 NULL 행은 어떤 조회에도 섞이지 않는다 (decisions D16)
@IdClass(MlPriority.Id.class)
public class MlPriority {
    public record Id(Integer year, Integer month, String unit, String regionCd) {}
    ...
}
```

repository 예시 (조회 메서드만, `Repository<T, ID>` 상속). 월 평균, 순위, 개수는 서비스에서 계산한다 (decisions D13)

```java
List<MlPriority> findByYearAndUnit(Integer year, String unit);                  // 연간: 그 연도 월별 행 전부
List<MlPriority> findByYearAndMonthAndUnit(Integer year, Integer month, String unit);

@Query("select distinct p.month from MlPriority p where p.year = :year and p.unit = :unit")
List<Integer> findMonths(Integer year, String unit);                           // 공통 달·최근 달 계산용
```

## 조회 조건 규칙

API에 `unit` 파라미터는 없다. 아래 `unit` 조건에는 서비스가 항상 `"dong"`을 넘긴다 (decisions D6).

| 테이블 묶음 | 연도 | 연간 / 월 | 지역 단위 | 지역 키 |
| --- | --- | --- | --- | --- |
| `ml_priority` | `year` | 월 `month = ?` / 연간 = 그 연도 월별 행 평균 (NULL 행 제외) | `unit` | `region_cd` |
| `ml_region`, `ml_explanation` | `year` | 월 `month = ?` / 연간 = 최근 달 | `unit` | `region_cd` |
| `ml_model_info` | `year` | 월 `month = ?` / 연간 = 연도 전체 | — | — |
| `feat_region` | `year` | 연간 전용 테이블 | (행정동 전용) | `dong_cd` |
| `feat_region_month` | `year` | `month` 1~12 (NULL 없음) | (행정동 전용) | `dong_cd` |
| `feat_city` | `year` | 연간 전용 | `unit` | 없음 (시 전체) |
| `feat_city_month` | `year` | `month` 1~12 | (시 전체) | 없음 |

## 테이블별 사용 컬럼

### ml_priority — 점수·순위 (API 1~4)
행정동: 연간 NULL 38행(쓰지 않음) + 월별 1~12월 × 38행

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| year, month | bigint | month NULL = 연간 (쓰지 않음) |
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
`year`, `month`, `trained_at`(text, `YYYY-MM-DD HH:mm`) → 연간 `MAX(trained_at)`, 월 `MAX(trained_at) WHERE month = ?`

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

## seed로 확인한 기준값 (11/8 DB, 2025, dong)

확인 SQL의 정수 반올림은 `FLOOR(x + 0.5)`로 쓴다. MariaDB `ROUND(double)`은 .5를 짝수 쪽으로 보내 (`ROUND(58.5e0)` = 58) 서버(Java `Math.round`, 58.5 → 59)와 다를 수 있다.

| 항목 | 값 | 확인 SQL |
| --- | --- | --- |
| 연간 지역 수 / 취약지역 / 평균 | 38 / 9 / 38.3 | `WITH a AS (SELECT region_cd, AVG(score_total) av FROM ml_priority WHERE year=2025 AND unit='dong' AND month IS NOT NULL GROUP BY region_cd) SELECT COUNT(*), SUM(FLOOR(av+0.5)>=50), ROUND(AVG(av),1) FROM a` |
| 연간 1위 | 이동읍(4146125600) 65.83→66, 원삼면(4146134000) 66.01→66 공동 1위 | 위 `a`에 `RANK() OVER (ORDER BY FLOOR(av+0.5) DESC)` |
| 포곡읍(4146125000) | 연간 58.96→59, 7위 (NULL 행은 60.3) / 7월 60.1→60, 동부동(60.2)과 공동 7위 | 위와 같음, `month=7` |
| 7월 취약지역 | 9 (DB `is_vulnerable`로는 8) | `SELECT SUM(FLOOR(score_total+0.5)>=50) FROM ml_priority WHERE year=2025 AND month=7 AND unit='dong'` |
| 공통 달 (고를 수 있는 달) | 1~7 | `ml_priority`·`ml_region`·`ml_explanation`의 `DISTINCT month` 교집합 |
| 최종 갱신 | 연간 2026-11-08 20:55 / 2월 2026-11-07 14:49 / 8월 없음 | `SELECT MAX(trained_at) FROM ml_model_info WHERE year=2025 [AND month=?]` |
| 포곡읍 7월 추천 | 2026-11-08 20:56, options 점수 80.5, 45.8, 27.5 (= `ml_suitability` 7월) | `SELECT created_at, JSON_EXTRACT(options,'$[*].score') FROM ml_explanation WHERE month=7 AND region_cd='4146125000'` |
| 같은 유형(7월, 정류장 접근 취약형) | 포곡읍 포함 6곳 | `SELECT region_cd FROM ml_region WHERE year=2025 AND month=7 AND type_nm='정류장 접근 취약형'` |
| 시 평균 택시 성사율(연간) | 53.8% | `SELECT ROUND(SUM(taxi_matched)/SUM(taxi_req)*100,1) FROM feat_city_month WHERE year=2025` |
| 시 평균 생활인구(연간) | 6,267 (11/5에는 5,938) | `SELECT ROUND(AVG(fp_avg)) FROM feat_city_month WHERE year=2025` |
| 시 평균 정류장 / 고령 | 81.6 / 17.7% | `SELECT n_stops, elderly_ratio FROM feat_city WHERE year=2025 AND unit='dong'` |
| 포곡읍 feat_region 연간 | 성사율 0.5337(월별 건수 가중 평균과 일치), 정류장 148, 고령 0.1984, 생활인구 10403.2 | `SELECT taxi_match_rate, n_stops, ratio65, fp_avg FROM feat_region WHERE dong_cd='4146125000'` |
