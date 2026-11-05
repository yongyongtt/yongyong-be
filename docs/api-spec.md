# API 명세 v3 (API 1~7)

원본: Notion `API 명세서 > API 목록(AI) v3` (2026-11-05 기준). 노션과 이 문서가 다르면 사용자에게 알린다.
8번 `POST /api/regions/{regionCode}/recommendation/regenerate`(AI 추천 다시 생성, 내부용)는 저장 위치 미정이라 이번 범위에서 **제외**한다.

## 공통

- Base URL: `/api`
- 모든 응답은 `ApiResponse<T>`로 감싼다 (CLAUDE.md 3장). 아래 "Response data"는 `data` 부분만이다
- 공통 쿼리 파라미터

| 이름 | 필수 | 값 | 설명 |
| --- | --- | --- | --- |
| `year` | O | 2025 | 기준 연도 |
| `month` | X | 1~12 | 없으면 연간(전체) = `month IS NULL` 행. 받는 API만 해당 |
| `unit` | X | `dong`(기본) / `village` | 이번 범위는 `dong`만. `village`는 `INVALID_REQUEST` |

- 아래 "출처"의 `month IS NULL` 조건은 DB 기준 설명이다. 코드에서는 `MlPriority` 엔티티의 `monthKey`(0 = 연간, 1~12 = 월)로 조회한다 (data-mapping.md "엔티티 매핑")
- `regionCode`: 행정동 코드 10자리 (`ml_*.region_cd` = `feat_*.dong_cd`), 예 `4146125000`
- 비율은 % (0~100, 소수 첫째 자리), 점수는 0~100 DB 값 그대로

| # | Method | Endpoint | 화면 | month |
| --- | --- | --- | --- | --- |
| 1 | GET | `/api/dashboard` | 대시보드 | O |
| 2 | GET | `/api/dashboard/map` | 대시보드 | O |
| 3 | GET | `/api/regions/{regionCode}/preview` | 대시보드 | O (점수·순위만) |
| 4 | GET | `/api/regions/{regionCode}` | 지역 상세 | O (헤더만) |
| 5 | GET | `/api/regions/{regionCode}/comparison` | 지역 상세 | O |
| 6 | GET | `/api/regions/{regionCode}/unmatched-trend` | 지역 상세 | X |
| 7 | GET | `/api/regions/{regionCode}/recommendation` | 지역 상세 | X |

---

## 1. 대시보드 요약 — `GET /api/dashboard`

상단 KPI 3개 + 우선 해결 TOP 10 + 기준 연·월 목록·최종 갱신일. 월을 바꾸면 KPI와 TOP10이 그 달 행 기준으로 바뀐다.

Query: `year`, `month`, `unit`

```json
{
  "period": {
    "year": 2025,
    "month": null,
    "availableYears": [2025],
    "availableMonths": [null, 2],
    "updatedAt": "2026-11-05 11:02"
  },
  "summary": {
    "regionCount": 38,
    "vulnerableCount": 9,
    "avgScore": 39.1
  },
  "top10": [
    {
      "rank": 1,
      "regionCode": "4146134000",
      "regionName": "원삼면",
      "gu": "처인구",
      "score": 68.1
    }
  ]
}
```

| 필드 | 출처 |
| --- | --- |
| availableYears | `SELECT DISTINCT year FROM ml_priority WHERE unit = :unit` |
| availableMonths | `SELECT DISTINCT month FROM ml_priority WHERE year = :year AND unit = :unit` (null = 연간, null을 맨 앞에) |
| updatedAt | `MAX(ml_model_info.trained_at) WHERE year = :year` |
| regionCount | `COUNT(*) FROM ml_priority` |
| vulnerableCount | `SUM(ml_priority.is_vulnerable)` (score_total 50점 이상) |
| avgScore | `AVG(ml_priority.score_total)`, 소수 첫째 자리 |
| top10 | `rank_total, region_cd, region_nm, gu, score_total` ORDER BY `rank_total` LIMIT 10 |

공통 조건: `year = :year AND unit = :unit` + 연간 `month IS NULL` / 월 `month = :month`
에러: 그 연·월 행이 0개면 `PERIOD_NOT_FOUND`
메모: 현재 월별 값은 2월만 있고 나머지 달은 추후 적재. availableMonths는 DB 기준이라 자동으로 늘어난다

---

## 2. 취약도 지도 — `GET /api/dashboard/map`

읍·면·동 38개 종합 취약도 지도 색칠.

Query: `year`, `month`, `unit`

```json
{
  "regions": [
    {
      "regionCode": "4146134000",
      "regionName": "원삼면",
      "gu": "처인구",
      "score": 68.1,
      "rank": 1
    }
  ]
}
```

| 필드 | 출처 |
| --- | --- |
| regionCode, regionName, gu | `ml_priority.region_cd`, `region_nm`, `gu` |
| score | `ml_priority.score_total` (종합 취약도) |
| rank | `ml_priority.rank_total` |

- 조건은 1번과 같다. 정렬: `rank_total`
- 종합 취약도만 내린다 (교통 수요·공급 부족도 없음)
- 읍·면·동 검색은 이 응답의 38개를 프론트가 `regionName`으로 거른다 → 검색 API 없음
- 색 구간(0~20, 20~40, …)은 프론트가 칠한다. 지도 경계는 프론트 정적 GeoJSON(`code` = regionCode)
- 에러: `PERIOD_NOT_FOUND`

---

## 3. 지역 미리보기 — `GET /api/regions/{regionCode}/preview`

지도·TOP10에서 지역을 고르면 나오는 우측 패널. 월을 바꾸면 점수·순위만 바뀌고 유형·추천은 연간 그대로.

Query: `year`, `month`, `unit`

```json
{
  "regionCode": "4146125000",
  "regionName": "포곡읍",
  "gu": "처인구",
  "score": 60.4,
  "rank": 7,
  "type": {
    "name": "정류장 접근 취약형",
    "desc": "버스는 다니지만 정류장까지 멀어 걷기 힘든 지역이에요"
  },
  "isKeep": false,
  "recommended": [
    {
      "mode": "DRT",
      "name": "똑버스"
    }
  ],
  "summary": "정류장까지 멀어 걷기 힘들어 똑버스 도입 검토"
}
```

| 필드 | 출처 |
| --- | --- |
| regionName, gu, score, rank | `ml_priority.region_nm, gu, score_total, rank_total` (연간 `month IS NULL` / 월 `month = :month`) |
| type | `ml_region.type_nm`, `type_desc` (연간만) |
| recommended | `ml_explanation.options` 중 `status = '추천'`인 항목의 `mode`, `name` (1~2개, options 순서대로) |
| isKeep | `ml_explanation.is_keep = 1`. 이때 recommended는 `{ "mode": "KEEP", "name": "현행 유지" }` 하나 |
| summary | `ml_explanation.summary` |

- `ml_region`·`ml_explanation` 행이 없으면 `type`, `recommended`, `summary`는 null (404 아님)
- 에러: `ml_priority`에 지역이 없으면 `REGION_NOT_FOUND`, 그 월 행이 없으면 `PERIOD_NOT_FOUND`

---

## 4. 지역 상세 개요·유형 — `GET /api/regions/{regionCode}`

상세 상단: 지역명·구·점수·순위 배지(연간/월), 기준 월 목록, 사각지대 유형·칩·같은 유형 지역(연간), 신뢰도 낮음 배지·배너.

Query: `year`, `month`, `unit`

```json
{
  "header": {
    "regionCode": "4146125000",
    "regionName": "포곡읍",
    "gu": "처인구",
    "score": 60.4,
    "rank": 7
  },
  "period": {
    "year": 2025,
    "month": null,
    "availableMonths": [null, 2]
  },
  "reliability": {
    "low": false,
    "note": null
  },
  "type": {
    "name": "정류장 접근 취약형",
    "desc": "버스는 다니지만 정류장까지 멀어 걷기 힘든 지역이에요",
    "chips": [
      "정류장까지 도보 김 · 12분 (평균 7분)",
      "정류장 500m 안 면적 좁음 · 54% (평균 77%)"
    ],
    "sameTypeRegions": [
      {
        "regionCode": "4146125300",
        "regionName": "모현읍"
      }
    ]
  }
}
```

| 필드 | 출처 | 기간 |
| --- | --- | --- |
| header | `ml_priority.region_nm, gu, score_total, rank_total` | 고른 연·월 |
| availableMonths | `SELECT DISTINCT month FROM ml_priority WHERE year = :year AND unit = :unit` | — |
| type.name, desc | `ml_region.type_nm`, `type_desc` | 연간 |
| type.chips | `ml_region.type_chips` (JSON 문자열 배열, 그대로) | 연간 |
| sameTypeRegions | `ml_region`에서 같은 `year, unit, type_nm`인 다른 지역. 이름은 `ml_priority`(연간 행)의 `region_nm`. 정렬: `rank_total` | 연간 |
| reliability | `ml_region.low_reliability`(1 → true), `reliability_note` | 연간 |

- (i) 유형 안내 모달 6종은 프론트 고정 문구 → 응답에 없음
- 행정동은 현재 전부 `low_reliability = 0`이라 항상 `{ "low": false, "note": null }`
- 에러: `REGION_NOT_FOUND`, `PERIOD_NOT_FOUND`

---

## 5. 시 평균 비교 — `GET /api/regions/{regionCode}/comparison`

'시 평균과 비교' 카드 4개 지표. 시 평균은 계산하지 않고 `feat_city`·`feat_city_month`에서 읽는다 (택시 성사율·생활인구 연간 값만 아래 식으로 집계).

Query: `year`, `month`, `unit`

```json
{
  "metrics": [
    {
      "key": "TAXI_MATCH_RATE",
      "label": "택시 호출 성사율",
      "unit": "%",
      "region": 53.4,
      "cityAvg": 53.8
    },
    {
      "key": "BUS_STOP_COUNT",
      "label": "버스 정류장 수",
      "unit": "개",
      "region": 148,
      "cityAvg": 81.6
    },
    {
      "key": "ELDERLY_RATIO",
      "label": "고령인구 비율",
      "unit": "%",
      "region": 19.8,
      "cityAvg": 17.7
    },
    {
      "key": "FLOATING_POP",
      "label": "생활인구 (시간 평균)",
      "unit": "명",
      "region": 10403,
      "cityAvg": 5938
    }
  ]
}
```

| key | 이 지역 | 시 평균 | 기간 |
| --- | --- | --- | --- |
| TAXI_MATCH_RATE | 월 `feat_region_month.taxi_match_rate` / 연간 `feat_region.taxi_match_rate` | 월 `feat_city_month.taxi_match_rate` / 연간 그 연도 `SUM(taxi_matched)/SUM(taxi_req)` | 월별 |
| BUS_STOP_COUNT | `feat_region.n_stops` | `feat_city.n_stops` (`unit = 'dong'`) | 연간만 |
| ELDERLY_RATIO | `feat_region.ratio65` | `feat_city.elderly_ratio` | 연간만 |
| FLOATING_POP | 월 `feat_region_month.fp_avg` / 연간 `feat_region.fp_avg` | 월 `feat_city_month.fp_avg` / 연간 `AVG(feat_city_month.fp_avg)` | 월별 |

- 조건: `feat_region`은 `year = :year AND dong_cd = :regionCode`, `feat_region_month`는 `year, month, dong_cd`
- 비율(0~1)은 ×100, 소수 첫째 자리. 생활인구는 정수로 반올림
- `feat_region_month.fp_anomaly = 1`인 달은 FLOATING_POP의 `region`을 null
- 순서는 위 표 순서로 고정
- 에러: `feat_region`에 지역이 없으면 `REGION_NOT_FOUND`, 월을 골랐는데 `feat_region_month` 행이 없으면 `PERIOD_NOT_FOUND`

---

## 6. 미배차율 추이 — `GET /api/regions/{regionCode}/unmatched-trend`

'미배차율 추이' 라인차트(이 지역 vs 용인시 평균 점선) + 호버 툴팁. 그 연도에 DB에 있는 달을 모두 내린다.

Query: `year`, `unit` (month 없음)

```json
{
  "points": [
    {
      "year": 2025,
      "month": 1,
      "unmatchedRate": 44.7,
      "requestCount": 2779,
      "matchedCount": 1538,
      "cityAvgRate": 44.7
    },
    {
      "year": 2025,
      "month": 2,
      "unmatchedRate": 36.6,
      "requestCount": 2571,
      "matchedCount": 1631,
      "cityAvgRate": 36.0
    }
  ]
}
```

(예시는 2개만. 실제로는 2025년 1~12월 12개)

| 필드 | 출처 |
| --- | --- |
| unmatchedRate | `feat_region_month.taxi_unmatched_rate` ×100 |
| requestCount, matchedCount | `feat_region_month.taxi_req`, `taxi_matched` (정수) |
| cityAvgRate | 같은 연·월 `feat_city_month.taxi_unmatched_rate` ×100 |

- 조건: `year = :year AND dong_cd = :regionCode`, 정렬 `month`
- y축 범위는 프론트가 자동으로 정한다
- 에러: 행이 0개면 `REGION_NOT_FOUND`

---

## 7. AI 추천 해결방안 — `GET /api/regions/{regionCode}/recommendation`

'AI 추천 해결방안' 전체. `ml_explanation`은 연간만 있어 월 선택과 무관.

Query: `year`, `unit`

```json
{
  "isKeep": false,
  "createdAt": "2026-11-05 11:03",
  "options": [
    {
      "mode": "DRT",
      "name": "똑버스",
      "subtitle": "수요응답형 버스",
      "desc": "정해진 노선 없이 호출에 따라 운행해요",
      "status": "RECOMMENDED",
      "rank": 1,
      "score": 80.7,
      "isCurrent": false,
      "reasons": [
        "정류장까지 걷는 시간이 12분으로 용인시 평균(7분)보다 길어 도입하기 좋아요."
      ],
      "facts": [
        "정류장까지 걷는 시간 12분 (평균 7분)",
        "정류장 500m 안에 드는 면적 54% (평균 77%)"
      ],
      "together": null
    },
    {
      "mode": "BUS",
      "name": "노선버스",
      "subtitle": "증차·노선 조정",
      "desc": "지금 다니는 버스를 더 자주, 더 가까이 다니게 해요",
      "status": "EXCLUDED",
      "rank": null,
      "score": 46.8,
      "isCurrent": true,
      "reasons": [
        "인구밀도가 743명/km²로 용인시 평균(7,364명/km²)보다 낮아 버스를 늘리기 어려워요."
      ],
      "facts": [
        "인구밀도 743명/km² (평균 7,364명/km²)"
      ],
      "together": null
    }
  ],
  "reasons": [
    "정류장까지 걷는 시간이 12분으로 용인시 평균(7분)보다 길어, 걸어서 이동하기 힘든 주민을 위한 수단이 필요해요."
  ],
  "cautions": [
    "똑버스 실제 운영 구역 자료가 없어, 기존 운영 구역과 겹치는지 확인이 필요해요."
  ],
  "lowReliability": false
}
```

| 필드 | 출처 |
| --- | --- |
| options | `ml_explanation.options` (JSON 배열, 순서 그대로) |
| isKeep | `ml_explanation.is_keep = 1` |
| reasons, cautions | `ml_explanation.reasons`, `cautions` (JSON 배열 1~3개) |
| createdAt | `ml_explanation.created_at` |
| lowReliability | `ml_region.low_reliability = 1` |

options 변환 규칙

- DB 키 → 응답 키: `is_current` → `isCurrent`, 나머지는 같은 이름
- `status`: DB `'추천'` → `RECOMMENDED`, `'제외'` → `EXCLUDED`. 그 밖의 값이 오면 예외(`INTERNAL_ERROR`)로 로그를 남긴다
- `mode`: `BUS` / `DRT` / `HAPPY_TAXI`, `is_keep = 1`이면 맨 앞에 `KEEP`이 이미 들어 있다 (서버가 추가하지 않고 DB 그대로)
- `facts`는 빈 배열 가능, `together`는 null 가능, `score`·`rank`는 null 가능 (KEEP은 score null)
- 문장은 수정하지 않고 그대로 전달한다
- 에러: 행이 없으면 `RECOMMENDATION_NOT_FOUND`
