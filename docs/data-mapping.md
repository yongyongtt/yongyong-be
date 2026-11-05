# yongin DB 데이터 매핑 (API 1~7용)

기준 덤프: `yongin_2026-11-05.sql` (MariaDB 12.2). Python(ML 팀원)이 적재하고 Spring은 읽기만 한다.
테스트용 seed: `src/test/resources/db/seed-dong-2025.sql` — 아래 8개 테이블, 행정동(`unit = 'dong'`) 행만.

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
| `MlPriority` | `ml_priority` | year, **monthKey**, unit, regionCd | `@Subselect`로 `COALESCE(month, 0) AS month_key`. 0 = 연간 (원본에 month = 0 행은 없음) |
| `MlRegion` | `ml_region` | year, unit, regionCd | `typeChips` → `List<String>` (StringListConverter) |
| `MlExplanation` | `ml_explanation` | year, unit, regionCd | `options` → `List<OptionJson>` (OptionListConverter), `reasons`·`cautions` → `List<String>` |
| `MlModelInfo` | `ml_model_info` | year, model | `trainedAt`만 사용 |
| `FeatRegion` | `feat_region` | year, dongCd | |
| `FeatRegionMonth` | `feat_region_month` | year, month, dongCd | month는 1~12 (NULL 없음) |
| `FeatCity` | `feat_city` | year, unit | |
| `FeatCityMonth` | `feat_city_month` | year, month | |

`MlPriority` 예시

```java
@Entity
@Immutable
@Subselect("""
    SELECT year, COALESCE(month, 0) AS month_key, unit, region_cd, region_nm, gu,
           score_total, rank_total, is_vulnerable
    FROM yongin.ml_priority
    """)
@Synchronize("ml_priority")
@IdClass(MlPriority.Id.class)
public class MlPriority {
    public record Id(Integer year, Integer monthKey, String unit, String regionCd) {}

    @jakarta.persistence.Id private Integer year;
    @jakarta.persistence.Id private Integer monthKey;   // 0 = 연간
    @jakarta.persistence.Id private String unit;
    @jakarta.persistence.Id private String regionCd;
    private String regionNm;
    private String gu;
    private Double scoreTotal;
    private Integer rankTotal;
    private Boolean isVulnerable;
    // getter만
}
```

repository 예시 (조회 메서드만, `Repository<T, ID>` 상속)

```java
public interface MlPriorityRepository extends Repository<MlPriority, MlPriority.Id> {
    List<MlPriority> findByYearAndMonthKeyAndUnitOrderByRankTotal(Integer year, Integer monthKey, String unit);
    Optional<MlPriority> findByYearAndMonthKeyAndUnitAndRegionCd(Integer year, Integer monthKey, String unit, String regionCd);

    @Query("select distinct p.monthKey from MlPriority p where p.year = :year and p.unit = :unit order by p.monthKey")
    List<Integer> findMonthKeys(Integer year, String unit);

    @Query("select count(p) from MlPriority p where p.year = :year and p.monthKey = :monthKey and p.unit = :unit and p.isVulnerable = true")
    long countVulnerable(Integer year, Integer monthKey, String unit);
}
```

## 조회 조건 규칙

| 테이블 묶음 | 연도 | 연간 / 월 | 지역 단위 | 지역 키 |
| --- | --- | --- | --- | --- |
| `ml_priority` | `year` | 연간 `month IS NULL` / 월 `month = ?` (엔티티에서는 `monthKey` 0 / 1~12) | `unit` | `region_cd` |
| `ml_region`, `ml_explanation` | `year` | month 컬럼 없음 (연간만) | `unit` | `region_cd` |
| `ml_model_info` | `year` | — | — | — |
| `feat_region` | `year` | 연간 전용 테이블 | (행정동 전용) | `dong_cd` |
| `feat_region_month` | `year` | `month` 1~12 (NULL 없음) | (행정동 전용) | `dong_cd` |
| `feat_city` | `year` | 연간 전용 | `unit` | 없음 (시 전체) |
| `feat_city_month` | `year` | `month` 1~12 | (시 전체) | 없음 |

## 테이블별 사용 컬럼

### ml_priority — 점수·순위 (API 1~4)
행정동: 연간 38행 + 월별(현재 2월만) 38행

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| year, month | bigint | month NULL = 연간 |
| unit | text | `dong` / `village` |
| region_cd, region_nm, gu | text | 지역 코드·이름·구 |
| score_total | double | 종합 취약도 0~100 |
| rank_total | bigint | 종합 순위 (1 = 가장 취약) |
| is_vulnerable | tinyint | score_total 50 이상이면 1 |

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
| is_keep | tinyint | 1이면 현행 유지 지역 (행정동은 현재 0개) |
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
`year`, `trained_at`(text, `YYYY-MM-DD HH:mm`) → `MAX(trained_at)`

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
| fp_avg | 생활인구 |
| fp_anomaly | 1이면 그 달 생활인구 값을 쓰지 않음 |

### feat_city — 시 평균 연간 (API 5)
`year`, `unit`, `n_stops`(2025 dong 81.6), `elderly_ratio`(0.1774)

### feat_city_month — 시 평균 월별 (API 5, 6)
`year`, `month`, `taxi_req`, `taxi_matched`, `taxi_match_rate`, `taxi_unmatched_rate`, `fp_avg`

## seed로 확인한 기준값 (2025, dong)

| 항목 | 값 | 확인 SQL |
| --- | --- | --- |
| 지역 수 / 취약지역 / 평균 | 38 / 9 / 39.1 | `SELECT COUNT(*), SUM(is_vulnerable), ROUND(AVG(score_total),1) FROM ml_priority WHERE year=2025 AND month IS NULL AND unit='dong'` |
| 1위 | 원삼면(4146134000) 68.1 | `... ORDER BY rank_total LIMIT 1` |
| 포곡읍(4146125000) | 60.4, 7위 | `SELECT score_total, rank_total FROM ml_priority WHERE region_cd='4146125000' AND month IS NULL` |
| 월 목록 | NULL, 2 | `SELECT DISTINCT month FROM ml_priority WHERE year=2025` |
| 최종 갱신 | 2026-11-05 11:02 | `SELECT MAX(trained_at) FROM ml_model_info WHERE year=2025` |
| 시 평균 택시 성사율(연간) | 53.8% | `SELECT ROUND(SUM(taxi_matched)/SUM(taxi_req)*100,1) FROM feat_city_month WHERE year=2025` |
| 시 평균 생활인구(연간) | 5,938 | `SELECT ROUND(AVG(fp_avg)) FROM feat_city_month WHERE year=2025` |
| 시 평균 정류장 / 고령 | 81.6 / 17.7% | `SELECT n_stops, elderly_ratio FROM feat_city WHERE year=2025 AND unit='dong'` |
| 포곡읍 월별 행 수 | 12 | `SELECT COUNT(*) FROM feat_region_month WHERE dong_cd='4146125000' AND year=2025` |
| 포곡읍 options | 3개, is_keep 0 | `SELECT JSON_LENGTH(options), is_keep FROM ml_explanation WHERE region_cd='4146125000'` |
