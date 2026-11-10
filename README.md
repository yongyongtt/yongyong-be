# 용인 교통 사각지대 진단 — 백엔드

용인시 읍·면·동의 대중교통 취약도와 지역 유형, AI 추천 해결방안을 제공하는 API 서버입니다.
분석 값은 ML(yong_ml)이 MariaDB `yongin`에 계산해 두고, 이 서버는 읽어서 내려줍니다.

## 구성

```
nginx(80) → app(Spring Boot, 8080) → db(MariaDB yongin)
                 └→ ml(yong_ml FastAPI, 8000) → db
```

- app은 `yongin`에 SELECT 전용 계정으로 접속합니다
- ml은 `ml_` 테이블 5개만 쓸 수 있는 별도 계정을 씁니다
- app·ml·db는 외부에 열지 않고 nginx만 80으로 공개합니다

## 기술

Java 21, Spring Boot 4.1, Spring Data JPA(읽기 전용 엔티티), MariaDB 12.2,
Docker Compose, GitHub Actions(CI·자동 배포), JUnit 5·Testcontainers, Spotless·JaCoCo

## API

| # | Method | Endpoint | 설명 |
| --- | --- | --- | --- |
| 1 | GET | `/api/dashboard` | 대시보드 요약 |
| 2 | GET | `/api/dashboard/map` | 취약도 지도 |
| 3 | GET | `/api/regions/{regionCode}/preview` | 지역 미리보기 |
| 4 | GET | `/api/regions/{regionCode}` | 지역 상세 개요·유형 |
| 5 | GET | `/api/regions/{regionCode}/comparison` | 시 평균 비교 |
| 6 | GET | `/api/regions/{regionCode}/unmatched-trend` | 미배차율 추이 |
| 7 | GET | `/api/regions/{regionCode}/recommendation` | AI 추천 해결방안 |
| 8 | POST·GET | `/api/ml/run`, `/api/ml/run/status` | ML 재계산 실행·상태 |

모든 응답은 `{ "success", "data", "error" }` 형식입니다. 자세한 명세는 Swagger(`/swagger-ui.html`)와 `docs/api-spec.md`를 봐 주세요.

## 로컬 실행

```bash
cp .env.example .env          # 값 채우기
./gradlew bootRun             # 기본 DB: localhost:3307/yongin
```

## 테스트

```bash
./gradlew spotlessApply check   # 포맷 + 전체 테스트 (Docker 필요: Testcontainers)
```

repository 테스트(`@Tag("db")`)는 공개하지 않는 seed 데이터가 필요해 CI에서는 제외됩니다.

## 배포

main 머지 → `ci` 통과 → `deploy` 워크플로가 서버에서 `scripts/deploy.sh` 실행(헬스체크·자동 롤백).
서버 준비와 ML 서버 설정은 `docs/deployment.md`를 봐 주세요.

## 문서

- `docs/api-spec.md` API 명세
- `docs/data-mapping.md` DB 테이블·컬럼 매핑
- `docs/decisions.md` 설계 결정 기록
- `docs/deployment.md` 배포 런북
