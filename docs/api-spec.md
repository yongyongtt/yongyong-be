# API 명세 v3 (API 1~9)

원본: Notion `API 명세서 > API 목록(AI) v3` (2026-11-05 기준). 노션과 이 문서가 다르면 사용자에게 알린다.
2026-11-08: 11/8 DB 월별 행 반영으로 연간 정의, 공통 `period`(availableYears·availableMonths 삭제, API 1 `updatedAt`은 `data` 바로 아래로), API 7 `month`, `SUITABILITY_NOT_READY` 추가 (decisions D16). 노션에도 반영 필요.
8번 `POST /api/regions/{regionCode}/recommendation/regenerate`(AI 추천 다시 생성, 내부용)는 저장 위치 미정이라 이번 범위에서 **제외**한다.
2026-11-10: 8번 가안을 폐기하고 yong_ml FastAPI(`api.py`) 기준 ML 실행 요청·상태 조회 2개로 다시 정함 (decisions D5, D17). 노션에도 반영 필요.
2026-11-11: 최종본 DB 반영으로 연간 = `ml_` 테이블의 연간(month NULL) 행, 연간 `period`·`typePeriod`는 모두 `YEAR_ALL` (decisions D16 변경). 노션에도 반영 필요.
2026-11-12: 12일자 DB에 `plan_summary`·`plan_region` 추가로 9번 통합 최적안(`GET /api/plans`) 추가, 에러 `PLAN_NOT_FOUND` 추가 (decisions D18). 노션에도 반영 필요.
2026-11-12: 9번 `plans[].byRegion` 추가 (지도·TOP 10 통합 최적안 토글용, 행정동 38개 전체, decisions D18 변경). 노션에도 반영 필요.

## 공통

- Base URL: `/api`
- 모든 응답은 `ApiResponse<T>`로 감싼다 (CLAUDE.md 3장). 아래 "Response data"는 `data` 부분만이다
- 공통 쿼리 파라미터

| 이름 | 필수 | 값 | 설명 |
| --- | --- | --- | --- |
| `year` | O | 2025 | 기준 연도 |
| `month` | X | 1~12 | 없으면 연간(전체). 아래 "연간·월 규칙"대로 `ml_` 연간(NULL) 행을 읽는다. 받는 API만 해당 |

- `unit` 파라미터는 없다. 서버가 항상 `unit = 'dong'`으로 조회한다 (decisions D6)

### 연간·월 규칙 (decisions D16)

- `month` 있음 → 그 달 행만 (`month = :month`)
- `month` 없음(연간) → `ml_` 값(점수, 유형, 신뢰도, 추천 수단, 요약, AI 추천, 갱신 시각)은 모두 그 테이블의 **연간 행**(`month IS NULL`). ML이 연간을 따로 계산해 둔 값이라 월 평균이나 12월 값과 다르다
- 월을 고른 조회에는 연간 행이 섞이지 않고, 연간 조회에는 월별 행이 섞이지 않는다
- 서버는 연간 행을 내부에서 month 0으로 다루지만 API 입력은 1~12만 받는다. `month=0`은 400 `INVALID_REQUEST`, 연간은 `month`를 빼고 보낸다
- `feat_`(API 5, 6)는 이 규칙과 무관하다 (연간 전용 테이블 `feat_region`·`feat_city`, 5번 참고)
- 점수는 정수로 반올림해 내리고, 순위는 그 정수 점수로 다시 매긴다. 동점은 같은 순위, 다음 순위는 건너뛴다 (1, 2, 3, 3, 5). 월을 고른 경우도 DB `rank_total` 대신 재계산 순위를 쓴다
- 정렬: 순위 → 같은 순위면 반올림 전 점수 높은 순 → `region_cd` (decisions D12). 예: 연간 포곡읍 60.3이 동부동 59.5보다 먼저, 둘 다 7위
- 취약지역 = 정수 점수 50점 이상 (DB `is_vulnerable` 대신)
- 고를 수 있는 달(공통 달) = `ml_priority`·`ml_region`·`ml_explanation`에 공통으로 있는 달. 월 범위는 하드코딩하지 않고 조회 시점에 계산한다 (응답에는 내리지 않음). `plan_` 테이블은 공통 달 계산에 넣지 않는다 (plan이 없는 달은 9번만 `PLAN_NOT_FOUND`)
- `month`를 골랐는데 공통 달에 없으면 **404 `SUITABILITY_NOT_READY`** (API 1~5, 7 공통, 한 메서드로 판단). 연간 조회는 이 규칙과 무관
- 판단 순서: 연도 데이터가 아예 없음(`PERIOD_NOT_FOUND`) → 공통 달에 없는 월(`SUITABILITY_NOT_READY`) → 없는 지역(`REGION_NOT_FOUND`). API 9는 지역 대신 그 기간 plan 행 없음(`PLAN_NOT_FOUND`)

### `period` (모든 API 공통)

응답 값이 몇 년 몇 월 데이터인지 내린다. 7개 API가 같은 모양이다.

```json
"period": { "year": 2025, "months": [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12], "type": "YEAR_ALL" }
```

| type | 뜻 | months |
| --- | --- | --- |
| `YEAR_ALL` | 연간 조회 (그 해 전체를 반영한 연간 행) | 공통 달 (API 5·6은 그 지역 `feat_region_month`의 달) |
| `MONTH` | 월을 고른 조회 | `[고른 달]` |

- `months`는 항상 배열이다
- `YEAR_LATEST`(연간 조회의 최근 달 값)는 2026-11-11부터 내리지 않는다. 프론트가 분기를 남겨 둬도 된다
- API 3·4는 `period`(점수)와 `typePeriod`(유형·신뢰도·같은 유형 지역, API 3은 추천·요약도) 두 개를 내린다. 지금은 둘이 항상 같은 값이다 (프론트 호환으로 유지)
- 고를 수 있는 연·월 목록(`availableYears`, `availableMonths`)은 응답에 없다. 프론트가 선택값을 가진다

### 에러

| 코드 | HTTP | 설명 |
| --- | --- | --- |
| `INVALID_REQUEST` | 400 | year 없음, month 1~12 밖, regionCode 형식 |
| `PERIOD_NOT_FOUND` | 404 | 연도 데이터가 아예 없음 |
| `SUITABILITY_NOT_READY` | 404 | 요청한 달의 데이터가 없음(존재하지 않는 달). 메시지 "존재하지 않는 달입니다." |
| `REGION_NOT_FOUND` | 404 | 연도 데이터는 있는데 regionCode가 없음 |
| `RECOMMENDATION_NOT_FOUND` | 404 | `ml_explanation`에 그 지역 행이 없음 |
| `PLAN_NOT_FOUND` | 404 | `plan_summary`에 그 기간 행이 없음 (API 9). 메시지 "통합 최적안 데이터가 없습니다." |
| `ML_RUNNING` | 409 | ML 실행이 이미 돌고 있음 (API 8). 메시지 "이미 실행 중입니다." |
| `ML_UNAVAILABLE` | 502 | ML 서버 연결 실패·타임아웃, ML 서버가 실행을 시작하지 못함 (API 8). 메시지 "ML 서버에 연결할 수 없습니다." |
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
| 8-1 | POST | `/api/ml/run` | (시연용) | body |
| 8-2 | GET | `/api/ml/run/status` | (시연용) | X |
| 9 | GET | `/api/plans` | 대시보드 (통합 최적안) | O |

---

## 1. 대시보드 요약 — `GET /api/dashboard`

상단 KPI 3개 + 우선 해결 TOP 10 + 최종 갱신일. 월을 바꾸면 KPI와 TOP10이 그 달 행 기준으로 바뀐다.

Query: `year`, `month`

```json
{
  "period": { "year": 2025, "months": [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12], "type": "YEAR_ALL" },
  "updatedAt": "2026-11-10 17:00",
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
      "score": 68
    },
    {
      "rank": 2,
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
| period | 연간 `YEAR_ALL` + 공통 달 / 월 `MONTH` + `[month]` |
| updatedAt | 연간 `MAX(ml_model_info.trained_at) WHERE year = :year AND month IS NULL` / 월 `... AND month = :month`. 행이 없으면 null (decisions D12) |
| regionCount | 지역 수 |
| vulnerableCount | 정수 점수 50점 이상인 지역 수 |
| avgScore | 지역 점수(반올림 전)의 평균, 소수 첫째 자리 |
| top10 | 위 "연간·월 규칙"의 정렬로 상위 10개 |

공통 조건: `year = :year AND unit = 'dong'` + 월 `month = :month` / 연간 `month IS NULL`
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
      "score": 68,
      "rank": 1
    }
  ]
}
```

| 필드 | 출처 |
| --- | --- |
| regionCode, regionName, gu | `ml_priority.region_cd`, `region_nm`, `gu` |
| score | 종합 취약도 `score_total` (연간은 연간 행), 정수로 반올림 |
| rank | 정수 점수로 재계산한 순위 |
| period | 1번과 같다 |

- 조건과 정렬은 1번과 같다 (decisions D12)
- 종합 취약도만 내린다 (교통 수요·공급 부족도 없음)
- 읍·면·동 검색은 이 응답의 38개를 프론트가 `regionName`으로 거른다 → 검색 API 없음
- 색 구간(0~20, 20~40, …)은 프론트가 칠한다. 지도 경계는 프론트 정적 GeoJSON(`code` = regionCode)
- 에러: 1번과 같다

---

## 3. 지역 미리보기 — `GET /api/regions/{regionCode}/preview`

지도·TOP10에서 지역을 고르면 나오는 우측 패널. 월을 고르면 점수·유형·추천이 모두 그 달 값, 연간이면 모두 각 테이블의 연간 행 값.

Query: `year`, `month`

```json
{
  "period": { "year": 2025, "months": [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12], "type": "YEAR_ALL" },
  "typePeriod": { "year": 2025, "months": [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12], "type": "YEAR_ALL" },
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
  "summary": "정류장까지 멀고 고령 인구가 많아 똑버스 도입 검토"
}
```

| 필드 | 출처 |
| --- | --- |
| regionName, gu, score, rank | 1번과 같은 정수 점수·재계산 순위 |
| type | `ml_region.type_nm`, `type_desc` (그 달 또는 연간 행) |
| recommended | `ml_explanation.options` 중 `status = '추천'`인 항목의 `mode`, `name` (1~2개, options 순서대로) |
| isKeep | `ml_explanation.is_keep = 1`. 이때 recommended는 `{ "mode": "KEEP", "name": "현행 유지" }` 하나 |
| summary | `ml_explanation.summary` |
| period | 점수 기준. 1번과 같다 |
| typePeriod | 유형·추천 기준. `period`와 같은 값 (연간 `YEAR_ALL` + 공통 달 / 월 `MONTH`). 추천·요약은 `ml_explanation`의 같은 기간 행 |

- `ml_region`·`ml_explanation` 행이 없으면 `type`, `recommended`, `summary`는 null, `isKeep`은 false (404 아님)
- 에러: 연도 데이터가 없으면 `PERIOD_NOT_FOUND`, 공통 달에 없는 월이면 `SUITABILITY_NOT_READY`, 없는 지역이면 `REGION_NOT_FOUND`

---

## 4. 지역 상세 개요·유형 — `GET /api/regions/{regionCode}`

상세 상단: 지역명·구·점수·순위 배지(연간/월), 사각지대 유형·칩·같은 유형 지역, 신뢰도 낮음 배지·배너. 기간 규칙은 3번과 같다.

Query: `year`, `month`

```json
{
  "period": { "year": 2025, "months": [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12], "type": "YEAR_ALL" },
  "typePeriod": { "year": 2025, "months": [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12], "type": "YEAR_ALL" },
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
| header | 1번과 같은 정수 점수·재계산 순위 | 월 / 연간 행 |
| period, typePeriod | 3번과 같다 | — |
| type.name, desc | `ml_region.type_nm`, `type_desc` | `typePeriod` |
| type.chips | `ml_region.type_chips` (JSON 문자열 배열, 그대로) | `typePeriod` |
| sameTypeRegions | `ml_region`에서 같은 `year`, 같은 기간(그 달 또는 연간 행), `type_nm`인 지역, 자기 자신 제외·중복 없음 (`unit = 'dong'`). 이름은 `ml_priority`. 정렬: 1번과 같다 | `typePeriod` |
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

'AI 추천 해결방안' 전체. 월을 고르면 그 달 추천, 연간이면 `ml_explanation`의 연간 행 추천.

Query: `year`, `month`

```json
{
  "period": { "year": 2025, "months": [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12], "type": "YEAR_ALL" },
  "isKeep": false,
  "createdAt": "2026-11-10 17:00",
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
| lowReliability | `ml_region.low_reliability = 1` (`period`와 같은 기간 행) |
| period | 연간 `YEAR_ALL` + 공통 달 / 월 `MONTH`. 위 모든 필드가 이 기간 행 (연간은 연간 행) |

options 변환 규칙

- DB 키 → 응답 키: `is_current` → `isCurrent`, 나머지는 같은 이름
- `status`: DB `'추천'` → `RECOMMENDED`, `'제외'` → `EXCLUDED`. 그 밖의 값이 오면 예외(`INTERNAL_ERROR`)로 로그를 남긴다
- `mode`: `BUS` / `DRT` / `HAPPY_TAXI`, `is_keep = 1`이면 맨 앞에 `KEEP`이 이미 들어 있다 (서버가 추가하지 않고 DB 그대로)
- `facts`는 빈 배열 가능, `together`는 null 가능, `score`·`rank`는 null 가능 (KEEP은 score null)
- `score`는 값이 있으면 정수로 반올림한다 (decisions D12). 같은 달 `ml_suitability.score`와 같은 값이라 options 값을 그대로 쓴다 (11/8 DB 861건 일치 확인)
- 문장은 수정하지 않고 그대로 전달한다
- 에러: 공통 달에 없는 월이면 `SUITABILITY_NOT_READY`, 행이 없으면 `RECOMMENDATION_NOT_FOUND` (월 없이 부른 없는 연도, 없는 지역 포함)

---

## 8. ML 실행 — yong_ml FastAPI 호출 (decisions D17)

yong_ml `run_all.py`(그 연·월의 `ml_` 테이블 5개를 다시 계산해 덮어씀, 약 15분)를 FastAPI로 시작하고 상태를 조회한다. Spring은 FastAPI를 부르기만 하고 DB에 쓰지 않는다. 결과 값은 API 1~7이 DB에서 읽는다.

- 원본: `yong_ml 실행 API 명세서` (2026-11-08), yong_ml `api.py`
- Spring → FastAPI: `{ML_BASE_URL}/api/ml/run`, `{ML_BASE_URL}/api/ml/run/status` (경로 동일). 타임아웃 연결 2초, 읽기 10초
- 공개 경로다 (nginx `/api/` 그대로, 시연에서 프론트가 호출). 인증은 없고, 동시에 하나만 실행되므로 실행 중 재요청은 거절한다 (`ML_RUNNING`)
- `period`를 내리지 않는다 (조회 API가 아니고 `year`·`month`가 응답에 있음, CLAUDE.md 5장 예외)
- 시각(`startedAt`, `finishedAt`)은 FastAPI 값(ISO 8601, 서버 시간대) 그대로 내린다

### 응답 `data` — RunStatus (8-1, 8-2 공통)

```json
{
  "year": 2025,
  "month": 1,
  "status": "RUNNING",
  "exitCode": null,
  "startedAt": "2026-11-08T22:30:05+09:00",
  "finishedAt": null,
  "elapsedSec": 0,
  "expectedSec": 900
}
```

| 필드 | 설명 |
| --- | --- |
| year | 연도. 서버에서 `run_all.py`를 직접 돌린 실행이면 null |
| month | 월. 연간이거나 서버에서 직접 돌린 실행이면 null |
| status | `NONE`(API를 켠 뒤 실행한 적 없음) / `RUNNING` / `SUCCEEDED` / `FAILED` / `BUSY`(서버에서 직접 돌린 실행과 겹쳐 아무것도 안 함) |
| exitCode | `run_all.py` 종료 코드. 실행 중이면 null |
| startedAt, finishedAt | ISO 8601. 실행 중이면 finishedAt null |
| elapsedSec | 걸린 시간(초). 실행 중이면 지금까지 |
| expectedSec | 예상 실행 시간(초), 900 고정 |

FastAPI 응답을 그대로 담는다 (필드 추가·변환 없음, null도 그대로).

### 8-1. ML 실행 요청 — `POST /api/ml/run`

Body (`application/json`)

| 이름 | 필수 | 값 | 설명 |
| --- | --- | --- | --- |
| `year` | O | 2000~2100 | 기준 연도 |
| `month` | X | 1~12 | 없거나 null이면 연간 |

```json
{ "year": 2025, "month": 1 }
```

- Spring이 먼저 검사한다: year 없음·범위 밖, month 1~12 밖이면 `INVALID_REQUEST` 400 (FastAPI를 부르지 않음)
- 접수되면 `success: true`, `data` = RunStatus (`status: RUNNING`). 끝났는지는 8-2로 확인한다

| FastAPI | 우리 응답 |
| --- | --- |
| 202 | 200 `success: true`, `data` = RunStatus |
| 409 (이미 실행 중) | 409 `ML_RUNNING` |
| 422 (형식 오류) | 400 `INVALID_REQUEST` (Spring 검사를 통과했으면 정상적으로는 오지 않음) |
| 500 (시작 못 함), 연결 실패, 타임아웃 | 502 `ML_UNAVAILABLE` |

### 8-2. ML 실행 상태 — `GET /api/ml/run/status`

파라미터 없음. 지금 실행 중이거나 FastAPI를 켠 뒤 마지막으로 끝난 실행 하나.

| FastAPI | 우리 응답 |
| --- | --- |
| 200 | 200 `success: true`, `data` = RunStatus |
| 연결 실패, 타임아웃, 그 밖의 응답 | 502 `ML_UNAVAILABLE` |

- 실행 기록은 FastAPI 메모리에만 있어 ML 서버를 다시 켜면 `NONE`이 된다

---

## 9. 통합 최적안 — `GET /api/plans`

대시보드 '통합 최적안' 영역. 같은 예산으로 1안 효율형, 2안 형평형, 3안 균형형 중 어느 지역부터 투입할지 비교한다. 세 안을 한 번에 내리고, 카드를 누르면 프론트가 `plans[]`에서 그 안을 골라 투입 순서 표·예산 밖 줄과 지도·TOP 10 강조를 바꾼다 (추가 요청 없음, decisions D18).

Query: `year`, `month`

```json
{
  "period": { "year": 2025, "months": [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12], "type": "YEAR_ALL" },
  "plans": [
    {
      "plan": 3,
      "name": "균형형",
      "desc": "효율과 취약도를 함께 고려한 절충안",
      "sortBasis": "효율 순위와 취약도 순위의 평균",
      "budget": 711666667,
      "totalCost": 610000000,
      "budgetLeft": 101666667,
      "includedCount": 5,
      "skippedCount": 4,
      "pop65Total": 24999,
      "modeCounts": [
        { "mode": "BUS", "name": "노선버스", "count": 0 },
        { "mode": "DRT", "name": "똑버스", "count": 5 },
        { "mode": "HAPPY_TAXI", "name": "행복택시", "count": 0 }
      ],
      "regions": [
        {
          "order": 1,
          "rank": 1,
          "regionCode": "4146125300",
          "regionName": "모현읍",
          "gu": "처인구",
          "mode": "DRT",
          "modeName": "똑버스",
          "action": "똑버스 4대",
          "cost": 122000000
        }
      ],
      "skipped": [
        {
          "rank": 6,
          "regionCode": "4146125900",
          "regionName": "남사읍",
          "gu": "처인구",
          "mode": "DRT",
          "modeName": "똑버스",
          "action": "똑버스 4대",
          "cost": 122000000,
          "budgetLeftAtSkip": 101666667
        }
      ],
      "byRegion": [
        { "regionCode": "4146125300", "status": "INCLUDED", "mode": "DRT", "modeName": "똑버스" }
      ]
    }
  ]
}
```

(예시는 3안만, `regions`·`skipped`·`byRegion`도 1개씩. 실제로는 `plans` 3개, 2025 연간 3안은 `regions` 5개·`skipped` 4개·`byRegion` 38개)

| 화면 | 필드 | 출처 |
| --- | --- | --- |
| 카드 (1안·2안·3안) | plan, name, desc, sortBasis | `plan_summary.plan`, `plan_nm`, `plan_desc`, `sort_basis` |
| 예산 칩, 카드 "추정 비용 / 예산", 막대 | budget, totalCost, budgetLeft | `plan_summary.budget`, `total_cost`, `budget_left`. 막대 비율(totalCost ÷ budget)과 억 단위 변환은 프론트 |
| 카드 "포함 지역" | includedCount, skippedCount | `n_included`, `n_skipped` |
| (화면 여유분) | pop65Total | `pop65_total` (포함 지역의 65세 이상 인구 합) |
| 카드 수단 칩 | modeCounts | `n_bus`, `n_drt`, `n_happy`. 항상 3개, 순서 BUS → DRT → HAPPY_TAXI, 0도 내림 (숨길지는 프론트) |
| 투입 순서 표 | regions | `plan_region`에서 그 안, `is_included = 1`, `seq` 순 |
| 예산 밖 줄 | skipped | `plan_region`에서 그 안, `status = '예산 밖'`, `plan_rank` 순 (여러 줄 가능) |
| 지도·TOP 10 "통합 최적안" 토글 | byRegion | `plan_region`에서 그 안의 행정동 38개 전체(`후보 아님` 포함), `region_cd` 오름차순 |

regions·skipped 항목

| 필드 | 출처 |
| --- | --- |
| order | `seq` (투입 순서). regions만 |
| rank | `plan_rank` (그 안의 정렬 기준 순위). 표의 "균형 1위"는 프론트가 안 번호로 앞말(효율·취약도·균형)을 붙인다 |
| regionCode, regionName, gu | `region_cd`, `region_nm`, `gu` |
| mode, modeName | `mode`, `mode_nm` |
| action, cost | `action`(투입안 문구), `cost`(원) |
| budgetLeftAtSkip | `budget_left_at_skip` (원). skipped만, "남은 예산 n억보다 커서 건너뜀"용 |

byRegion 항목

| 필드 | 출처 |
| --- | --- |
| regionCode | `region_cd` |
| status | `status`. `포함` → `INCLUDED`, `예산 밖` → `SKIPPED`, `후보 아님` → `NOT_CANDIDATE`. 그 밖의 값은 500 `INTERNAL_ERROR` (서버 로그) |
| mode, modeName | `mode`, `mode_nm` (DB 값 그대로, `DRT`·`BUS`·`KEEP` 등) |

지도 표시 규칙 (색은 프론트가 칠하고, 서버는 값만 전달한다)

- 지도는 38개 전부 그 안의 `byRegion`으로 칠하고, TOP 10 배지도 같은 값을 `regionCode`로 찾는다. API 1·2 응답은 바뀌지 않는다
- `status`를 먼저 본다: `INCLUDED` → `mode` 색 / `NOT_CANDIDATE` + `mode = KEEP` → 현행 유지 / 나머지(`SKIPPED`, 그 밖의 `NOT_CANDIDATE`) → 미포함
- `NOT_CANDIDATE` 행에도 `mode`에 `BUS`·`DRT` 값이 있으므로 `mode`만 보고 칠하면 안 된다

- 조건: `plan_summary`는 `year = :year` + 월 `month = :month` / 연간 `month IS NULL`, `plan` 순. `plan_region`은 같은 기간 + `unit = 'dong'` (decisions D6)
- `status = '후보 아님'` 행은 regions·skipped에는 넣지 않고 byRegion에만 넣는다
- 금액은 모두 원 단위 정수, 변환·반올림하지 않는다
- 예산은 안마다 `plans[].budget`으로 내린다 (지금은 세 안이 같은 값. 예산 칩은 프론트가 아무 안의 값이나 쓴다)
- 문구(`desc`, `sortBasis`, `action`)는 수정하지 않고 그대로 전달한다
- 내리지 않는 것: "단가 ±30%로 바꿔도 …" 줄(계산하지 않음, 화면에서 삭제), 하단 단가·출처 안내(프론트 고정 문구), 행정동 상세의 안별 한 줄(`line`)과 리 단위(`village`) 행 (이번 범위 제외)
- `period`: 연간 `YEAR_ALL` + 공통 달 / 월 `MONTH` + `[month]` (1번과 같다)
- 에러: 연도 데이터가 없으면 `PERIOD_NOT_FOUND`, 공통 달에 없는 월이면 `SUITABILITY_NOT_READY`, 그 기간 `plan_summary` 행이 없으면 `PLAN_NOT_FOUND`. plan 테이블은 공통 달 계산에 넣지 않는다
