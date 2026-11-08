# API 명세 v3 (API 1~7)

원본: Notion `API 명세서 > API 목록(AI) v3` (2026-11-05 기준). 노션과 이 문서가 다르면 사용자에게 알린다.
2026-11-08: 11/8 DB 월별 행 반영으로 연간 정의, 공통 `period`(availableYears·availableMonths 삭제, API 1 `updatedAt`은 `data` 바로 아래로), API 7 `month`, `SUITABILITY_NOT_READY` 추가 (decisions D16). 노션에도 반영 필요.
8번 `POST /api/regions/{regionCode}/recommendation/regenerate`(AI 추천 다시 생성, 내부용)는 저장 위치 미정이라 이번 범위에서 **제외**한다.

## 공통

- Base URL: `/api`
- 모든 응답은 `ApiResponse<T>`로 감싼다 (CLAUDE.md 3장). 아래 "Response data"는 `data` 부분만이다
- 공통 쿼리 파라미터

| 이름 | 필수 | 값 | 설명 |
| --- | --- | --- | --- |
| `year` | O | 2025 | 기준 연도 |
| `month` | X | 1~12 | 없으면 연간(전체). 아래 "연간·월 규칙"대로 서버가 월별 행으로 계산한다. 받는 API만 해당 |

- `unit` 파라미터는 없다. 서버가 항상 `unit = 'dong'`으로 조회한다 (decisions D6)

### 연간·월 규칙 (decisions D16)

- DB의 `month IS NULL`(연간) 행은 있든 없든 **쓰지 않는다**. 모든 `ml_` 조회는 `month IS NOT NULL` 행만 읽는다
- `month` 있음 → 그 달 행만 (`month = :month`)
- `month` 없음(연간)
  - 숫자(점수·순위·개수) → 그 연도의 월별 값으로 재계산. 점수 = 지역별 월 평균 `score_total`
  - 분류·문장(유형, 신뢰도, 추천 수단, 요약, AI 추천) → 그 테이블의 **최근 달**(그 `year`·`unit`에 행이 있는 가장 큰 month)
- 점수는 정수로 반올림해 내리고, 순위는 그 정수 점수로 다시 매긴다. 동점은 같은 순위, 다음 순위는 건너뛴다 (1, 2, 3, 3, 5). 월을 고른 경우도 DB `rank_total` 대신 재계산 순위를 쓴다
- 정렬: 순위 → 같은 순위면 반올림 전 점수(연간은 월 평균) 높은 순 → `region_cd` (decisions D12). 예: 연간 원삼면 66.01이 이동읍 65.83보다 먼저, 둘 다 1위
- 취약지역 = 정수 점수 50점 이상 (DB `is_vulnerable` 대신)
- 고를 수 있는 달(공통 달) = `ml_priority`·`ml_region`·`ml_explanation`에 공통으로 있는 달. 월 범위는 하드코딩하지 않고 조회 시점에 계산한다 (응답에는 내리지 않음)
- `month`를 골랐는데 공통 달에 없으면 **404 `SUITABILITY_NOT_READY`** (API 1~5, 7 공통, 한 메서드로 판단). 연간 조회는 이 규칙과 무관
- 판단 순서: 연도 데이터가 아예 없음(`PERIOD_NOT_FOUND`) → 공통 달에 없는 월(`SUITABILITY_NOT_READY`) → 없는 지역(`REGION_NOT_FOUND`)

### `period` (모든 API 공통)

응답 값이 몇 년 몇 월 데이터인지 내린다. 7개 API가 같은 모양이다.

```json
"period": { "year": 2025, "months": [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12], "type": "YEAR_ALL" }
```

| type | 뜻 | months |
| --- | --- | --- |
| `YEAR_ALL` | 연간 조회, 그 해 월별 값 전체를 반영 | 반영한 달 전부 |
| `YEAR_LATEST` | 연간 조회, 그 해 최근 달 값 | `[최근 달]` (최근 달이 없으면 `[]`) |
| `MONTH` | 월을 고른 조회 | `[고른 달]` |

- `months`는 항상 배열이다
- API 3·4는 점수와 유형의 기준이 달라 `period`(점수)와 `typePeriod`(유형·신뢰도·같은 유형 지역, API 3은 추천·요약도) 두 개를 내린다
- 고를 수 있는 연·월 목록(`availableYears`, `availableMonths`)은 응답에 없다. 프론트가 선택값을 가진다

### 에러

| 코드 | HTTP | 설명 |
| --- | --- | --- |
| `INVALID_REQUEST` | 400 | year 없음, month 1~12 밖, regionCode 형식 |
| `PERIOD_NOT_FOUND` | 404 | 연도 데이터가 아예 없음 |
| `SUITABILITY_NOT_READY` | 404 | 요청한 달의 데이터가 없음(존재하지 않는 달). 메시지 "존재하지 않는 달입니다." |
| `REGION_NOT_FOUND` | 404 | 연도 데이터는 있는데 regionCode가 없음 |
| `RECOMMENDATION_NOT_FOUND` | 404 | `ml_explanation`에 그 지역 행이 없음 |
- `regionCode`: 행정동 코드 10자리 (`ml_*.region_cd` = `feat_*.dong_cd`), 예 `4146125000`
- 비율은 % (0~100) 정수로 반올림 (decisions D14), 점수는 0~100 값으로 API별 표기를 따른다 (decisions D12)

| # | Method | Endpoint | 화면 | month |
| --- | --- | --- | --- | --- |
| 1 | GET | `/api/dashboard` | 대시보드 | O |
| 2 | GET | `/api/dashboard/map` | 대시보드 | O |
| 3 | GET | `/api/regions/{regionCode}/preview` | 대시보드 | O |
| 4 | GET | `/api/regions/{regionCode}` | 지역 상세 | O |
| 5 | GET | `/api/regions/{regionCode}/comparison` | 지역 상세 | O |
| 6 | GET | `/api/regions/{regionCode}/unmatched-trend` | 지역 상세 | X |
| 7 | GET | `/api/regions/{regionCode}/recommendation` | 지역 상세 | O |

---

## 1. 대시보드 요약 — `GET /api/dashboard`

상단 KPI 3개 + 우선 해결 TOP 10 + 최종 갱신일. 월을 바꾸면 KPI와 TOP10이 그 달 행 기준으로 바뀐다.

Query: `year`, `month`

```json
{
  "period": { "year": 2025, "months": [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12], "type": "YEAR_ALL" },
  "updatedAt": "2026-11-08 20:55",
  "summary": {
    "regionCount": 38,
    "vulnerableCount": 9,
    "avgScore": 38.3
  },
  "top10": [
    {
      "rank": 1,
      "regionCode": "4146134000",
      "regionName": "원삼면",
      "gu": "처인구",
      "score": 66
    },
    {
      "rank": 1,
      "regionCode": "4146125600",
      "regionName": "이동읍",
      "gu": "처인구",
      "score": 66
    }
  ]
}
```

| 필드 | 출처 |
| --- | --- |
| period | 연간 `YEAR_ALL` + 평균에 쓴 `ml_priority` 달 / 월 `MONTH` + `[month]` |
| updatedAt | 연간 `MAX(ml_model_info.trained_at) WHERE year = :year` / 월 `... AND month = :month`. 행이 없으면 null (decisions D12) |
| regionCount | 지역 수 |
| vulnerableCount | 정수 점수 50점 이상인 지역 수 |
| avgScore | 지역 점수(반올림 전, 연간은 지역별 월 평균)의 평균, 소수 첫째 자리 |
| top10 | 위 "연간·월 규칙"의 정렬로 상위 10개 |

공통 조건: `year = :year AND unit = 'dong' AND month IS NOT NULL` + 월 `month = :month`
에러: 연도 데이터가 없으면 `PERIOD_NOT_FOUND`, 공통 달에 없는 월이면 `SUITABILITY_NOT_READY`

---

## 2. 취약도 지도 — `GET /api/dashboard/map`

읍·면·동 38개 종합 취약도 지도 색칠.

Query: `year`, `month`

```json
{
  "period": { "year": 2025, "months": [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12], "type": "YEAR_ALL" },
  "regions": [
    {
      "regionCode": "4146134000",
      "regionName": "원삼면",
      "gu": "처인구",
      "score": 66,
      "rank": 1
    }
  ]
}
```

| 필드 | 출처 |
| --- | --- |
| regionCode, regionName, gu | `ml_priority.region_cd`, `region_nm`, `gu` |
| score | 종합 취약도 `score_total` (연간은 월 평균), 정수로 반올림 |
| rank | 정수 점수로 재계산한 순위 |
| period | 1번과 같다 |

- 조건과 정렬은 1번과 같다 (decisions D12)
- 종합 취약도만 내린다 (교통 수요·공급 부족도 없음)
- 읍·면·동 검색은 이 응답의 38개를 프론트가 `regionName`으로 거른다 → 검색 API 없음
- 색 구간(0~20, 20~40, …)은 프론트가 칠한다. 지도 경계는 프론트 정적 GeoJSON(`code` = regionCode)
- 에러: 1번과 같다

---

## 3. 지역 미리보기 — `GET /api/regions/{regionCode}/preview`

지도·TOP10에서 지역을 고르면 나오는 우측 패널. 월을 고르면 점수·유형·추천이 모두 그 달 값, 연간이면 점수는 월 평균이고 유형·추천은 각 테이블의 최근 달 값.

Query: `year`, `month`

```json
{
  "period": { "year": 2025, "months": [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12], "type": "YEAR_ALL" },
  "typePeriod": { "year": 2025, "months": [7], "type": "YEAR_LATEST" },
  "regionCode": "4146125000",
  "regionName": "포곡읍",
  "gu": "처인구",
  "score": 60,
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
| regionName, gu, score, rank | 1번과 같은 정수 점수·재계산 순위 |
| type | `ml_region.type_nm`, `type_desc` (월 또는 `ml_region` 최근 달 행) |
| recommended | `ml_explanation.options` 중 `status = '추천'`인 항목의 `mode`, `name` (1~2개, options 순서대로) |
| isKeep | `ml_explanation.is_keep = 1`. 이때 recommended는 `{ "mode": "KEEP", "name": "현행 유지" }` 하나 |
| summary | `ml_explanation.summary` |
| period | 점수 기준. 1번과 같다 |
| typePeriod | 유형·추천 기준. 연간 `YEAR_LATEST` + `[ml_region 최근 달]` / 월 `MONTH`. 추천·요약은 `ml_explanation` 최근 달 행을 쓴다 (11/8 DB는 두 테이블 최근 달이 같다) |

- `ml_region`·`ml_explanation` 행이 없으면 `type`, `recommended`, `summary`는 null, `isKeep`은 false (404 아님)
- 에러: 연도 데이터가 없으면 `PERIOD_NOT_FOUND`, 공통 달에 없는 월이면 `SUITABILITY_NOT_READY`, 없는 지역이면 `REGION_NOT_FOUND`

---

## 4. 지역 상세 개요·유형 — `GET /api/regions/{regionCode}`

상세 상단: 지역명·구·점수·순위 배지(연간/월), 사각지대 유형·칩·같은 유형 지역, 신뢰도 낮음 배지·배너. 기간 규칙은 3번과 같다.

Query: `year`, `month`

```json
{
  "period": { "year": 2025, "months": [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12], "type": "YEAR_ALL" },
  "typePeriod": { "year": 2025, "months": [7], "type": "YEAR_LATEST" },
  "header": {
    "regionCode": "4146125000",
    "regionName": "포곡읍",
    "gu": "처인구",
    "score": 60,
    "rank": 7
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
| header | 1번과 같은 정수 점수·재계산 순위 | 월 / 월 평균 |
| period, typePeriod | 3번과 같다 | — |
| type.name, desc | `ml_region.type_nm`, `type_desc` | `typePeriod` |
| type.chips | `ml_region.type_chips` (JSON 문자열 배열, 그대로) | `typePeriod` |
| sameTypeRegions | `ml_region`에서 같은 `year`, `typePeriod` 달, `type_nm`인 지역, 자기 자신 제외·중복 없음 (`unit = 'dong'`). 이름은 `ml_priority`. 정렬: 1번과 같다 | `typePeriod` |
| reliability | `ml_region.low_reliability`(1 → true), `reliability_note` | `typePeriod` |

- (i) 유형 안내 모달 6종은 프론트 고정 문구 → 응답에 없음
- 행정동은 현재 전부 `low_reliability = 0`이라 항상 `{ "low": false, "note": null }`
- `ml_region` 행이 없으면 404가 아니라 `type`은 null, `reliability`는 `{ "low": false, "note": null }`
- 에러: 3번과 같다

---

## 5. 시 평균 비교 — `GET /api/regions/{regionCode}/comparison`

'시 평균과 비교' 카드 4개 지표. 시 평균은 계산하지 않고 `feat_city`·`feat_city_month`에서 읽는다 (택시 성사율·생활인구 연간 값만 아래 식으로 집계).

Query: `year`, `month`

```json
{
  "period": { "year": 2025, "months": [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12], "type": "YEAR_ALL" },
  "metrics": [
    {
      "key": "TAXI_MATCH_RATE",
      "label": "택시 호출 성사율",
      "unit": "%",
      "region": 53,
      "cityAvg": 54
    },
    {
      "key": "BUS_STOP_COUNT",
      "label": "버스 정류장 수",
      "unit": "개",
      "region": 148,
      "cityAvg": 82
    },
    {
      "key": "ELDERLY_RATIO",
      "label": "고령인구 비율",
      "unit": "%",
      "region": 20,
      "cityAvg": 18
    },
    {
      "key": "FLOATING_POP",
      "label": "생활인구 (시간 평균)",
      "unit": "명",
      "region": 10403,
      "cityAvg": 6267
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
- 모든 지표는 소수점 없이 정수로 반올림한다. 비율(0~1)은 ×100 후 반올림 (decisions D14)
- FLOATING_POP은 `feat_region_month.fp_anomaly`와 관계없이 원본 `fp_avg`를 그대로 쓴다 (빼는 달 없음)
- 연간 지역 값은 `feat_region`(연간 전용 테이블)에 값이 있어 그대로 쓴다 (11/8 DB 확인: 택시 성사율은 월별 건수 가중 평균과 일치)
- `period`: 연간 `YEAR_ALL` + 그 지역 `feat_region_month`의 달 / 월 `MONTH`. 정류장·고령은 월을 골라도 항상 `feat_region` 연간 값이다 (응답에는 따로 표시하지 않음)
- 순서는 위 표 순서로 고정
- 시 평균 행(`feat_city`, `feat_city_month`)이 없으면 404가 아니라 해당 지표의 `cityAvg`만 null
- 에러: 공통 달에 없는 월이면 `SUITABILITY_NOT_READY` (`feat_region_month`에 그 달이 있어도), 연도 데이터가 아예 없으면 `PERIOD_NOT_FOUND`, 연도는 있는데 지역이 없으면 `REGION_NOT_FOUND`, 월을 골랐는데 `feat_region_month` 행이 없으면 `PERIOD_NOT_FOUND`

---

## 6. 미배차율 추이 — `GET /api/regions/{regionCode}/unmatched-trend`

'미배차율 추이' 라인차트(이 지역 vs 용인시 평균 점선) + 호버 툴팁. 그 연도에 DB에 있는 달을 모두 내린다.

Query: `year` (month 없음)

```json
{
  "period": { "year": 2025, "months": [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12], "type": "YEAR_ALL" },
  "points": [
    {
      "year": 2025,
      "month": 1,
      "unmatchedRate": 45,
      "requestCount": 2779,
      "matchedCount": 1538,
      "cityAvgRate": 45
    },
    {
      "year": 2025,
      "month": 2,
      "unmatchedRate": 37,
      "requestCount": 2571,
      "matchedCount": 1631,
      "cityAvgRate": 36
    }
  ]
}
```

(예시는 2개만. 실제로는 2025년 1~12월 12개)

| 필드 | 출처 |
| --- | --- |
| unmatchedRate | `feat_region_month.taxi_unmatched_rate` ×100, 정수로 반올림 |
| requestCount, matchedCount | `feat_region_month.taxi_req`, `taxi_matched` (정수로 반올림) |
| cityAvgRate | 같은 연·월 `feat_city_month.taxi_unmatched_rate` ×100, 정수로 반올림. 행이 없으면 null |

- 조건: `year = :year AND dong_cd = :regionCode`, 정렬 `month`
- `period`: 항상 `YEAR_ALL` + `points`에 있는 달
- y축 범위는 프론트가 자동으로 정한다
- 에러: 연도 데이터가 아예 없으면 `PERIOD_NOT_FOUND`, 연도는 있는데 지역 행이 없으면 `REGION_NOT_FOUND`

---

## 7. AI 추천 해결방안 — `GET /api/regions/{regionCode}/recommendation`

'AI 추천 해결방안' 전체. 월을 고르면 그 달 추천, 연간이면 `ml_explanation`의 최근 달 추천.

Query: `year`, `month`

```json
{
  "period": { "year": 2025, "months": [7], "type": "YEAR_LATEST" },
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
      "score": 81,
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
      "score": 47,
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
| lowReliability | `ml_region.low_reliability = 1` (`period`와 같은 달 행) |
| period | 연간 `YEAR_LATEST` + `[ml_explanation 최근 달]` / 월 `MONTH`. 위 모든 필드가 이 달 행 |

options 변환 규칙

- DB 키 → 응답 키: `is_current` → `isCurrent`, 나머지는 같은 이름
- `status`: DB `'추천'` → `RECOMMENDED`, `'제외'` → `EXCLUDED`. 그 밖의 값이 오면 예외(`INTERNAL_ERROR`)로 로그를 남긴다
- `mode`: `BUS` / `DRT` / `HAPPY_TAXI`, `is_keep = 1`이면 맨 앞에 `KEEP`이 이미 들어 있다 (서버가 추가하지 않고 DB 그대로)
- `facts`는 빈 배열 가능, `together`는 null 가능, `score`·`rank`는 null 가능 (KEEP은 score null)
- `score`는 값이 있으면 정수로 반올림한다 (decisions D12). 같은 달 `ml_suitability.score`와 같은 값이라 options 값을 그대로 쓴다 (11/8 DB 861건 일치 확인)
- 문장은 수정하지 않고 그대로 전달한다
- 에러: 공통 달에 없는 월이면 `SUITABILITY_NOT_READY`, 행이 없으면 `RECOMMENDATION_NOT_FOUND` (월 없이 부른 없는 연도, 없는 지역 포함)
