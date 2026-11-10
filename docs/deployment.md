# 배포 런북 (EC2 + Docker Compose, 수동 배포)

구성: `nginx(80)` → `app(8080, 외부 비공개)` → `db(MariaDB 12.2.2, 외부 비공개)`. 앱은 `yongin`에 SELECT 전용 계정으로 접속한다 (decisions D15).
`app` → `ml(yong_ml FastAPI 8000, 외부 비공개)` → `db`. ML은 `ml_` 테이블 5개만 쓰는 별도 계정으로 접속한다 (decisions D15, D17).

## 1. 서버 최초 준비

```bash
cd ~/app
cp .env.example .env && vi .env      # 실제 비밀번호 입력 (작은따옴표 ' 금지)
chmod 600 .env
```

`db` 컨테이너는 볼륨이 비어 있는 첫 실행 때만 `db/init/01-app-user.sh`로 앱 계정(`GRANT SELECT ON yongin.*`)을 만든다.
`.env`의 `DB_USERNAME`·`DB_PASSWORD`를 나중에 바꾸면 계정은 자동으로 바뀌지 않는다 (직접 `ALTER USER` 하거나 볼륨을 새로 만든다).

## 1-1. ML 서버(yong_ml) 준비 — API 8

**PR을 머지하기 전에** 아래 1~3을 끝낸다. `deploy.sh`의 `up -d --build`가 `ml`도 빌드하므로 `~/yong_ml`이 없으면 배포 전체가 실패한다.

1. yong_ml 클론 (`~/app` 옆, 수정하지 않는다. 다른 위치면 `.env`에 `YONG_ML_DIR=경로`)

```bash
cd ~ && git clone https://github.com/yongyongtt/yong_ml.git yong_ml
docker compose version                   # additional_contexts 때문에 2.17 이상
```

2. `~/app/.env`에 추가 (app·ml·db가 이 파일 하나를 쓰고, compose가 컨테이너마다 필요한 값만 넘긴다)

```
ML_DB_USERNAME=yongml
ML_DB_PASSWORD=...
LLM_API_KEY=                             # 비우면 Gemini를 부르지 않고 카드 문장은 템플릿 (저장된 LLM 카드는 다시 씀)
```

yong_ml 폴더에 `.env`를 따로 만들지 않는다. 담당자 `.env`의 `DB_HOST`·`DB_PORT`·`DB_NAME`은 compose가 정하고, `DB_USER`·`DB_PASSWORD`는 위 `ML_` 값으로 대신한다.

3. ML 계정 만들기. 새 볼륨이면 `db/init/01-app-user.sh`가 만들지만, 이미 떠 있는 DB에는 한 번 직접 실행한다

```bash
cd ~/app && set -a && . ./.env && set +a
docker compose exec -T db sh -c 'exec mariadb -uroot -p"$MARIADB_ROOT_PASSWORD"' <<SQL
CREATE USER IF NOT EXISTS '${ML_DB_USERNAME}'@'%' IDENTIFIED BY '${ML_DB_PASSWORD}';
GRANT SELECT ON yongin.* TO '${ML_DB_USERNAME}'@'%';
GRANT INSERT, DELETE, CREATE, DROP, ALTER ON yongin.ml_priority TO '${ML_DB_USERNAME}'@'%';
GRANT INSERT, DELETE, CREATE, DROP, ALTER ON yongin.ml_suitability TO '${ML_DB_USERNAME}'@'%';
GRANT INSERT, DELETE, CREATE, DROP, ALTER ON yongin.ml_region TO '${ML_DB_USERNAME}'@'%';
GRANT INSERT, DELETE, CREATE, DROP, ALTER ON yongin.ml_explanation TO '${ML_DB_USERNAME}'@'%';
GRANT INSERT, DELETE, CREATE, DROP, ALTER ON yongin.ml_model_info TO '${ML_DB_USERNAME}'@'%';
SHOW GRANTS FOR '${ML_DB_USERNAME}'@'%';
SQL
```

4. **배포할 때 `LLM_API_KEY`를 채울지 확인한다.** 키를 넣거나 바꾸면 ml만 다시 띄운다: `docker compose up -d ml`

yong_ml 코드가 바뀌면 (deploy.sh는 yong_ml을 받지 않는다):

```bash
cd ~/yong_ml && git pull && cd ~/app && docker compose up -d --build ml
```

yong_ml 최상위에 새 파일이 생기면 `ml/Dockerfile`의 `COPY` 목록에도 추가한다.

## 2. DB 덤프 → 서버 복원

로컬 (MariaDB 컨테이너 `mariadb-container`):

```bash
docker exec mariadb-container sh -c 'exec mariadb-dump -uroot -p"$MARIADB_ROOT_PASSWORD" \
  --single-transaction --default-character-set=utf8mb4 --databases yongin' > yongin.sql
grep -c 'CREATE TABLE' yongin.sql        # 테이블 수 확인
scp -i <키.pem> yongin.sql ubuntu@<서버IP>:~/app/
```

서버:

```bash
cd ~/app
docker compose up -d db                  # healthy 될 때까지 대기: docker compose ps
docker compose exec -T db sh -c 'exec mariadb -uroot -p"$MARIADB_ROOT_PASSWORD" --default-character-set=utf8mb4' < yongin.sql
rm yongin.sql                            # 덤프는 서버에 남기지 않는다

# 확인: 테이블과 앱 계정 권한(SELECT만 나와야 함)
docker compose exec db sh -c 'exec mariadb -uroot -p"$MARIADB_ROOT_PASSWORD" -e "SHOW TABLES FROM yongin; SHOW GRANTS FOR \`$DB_USERNAME\`@\`%\`;"'
```

## 3. 배포

main에 push → `ci` 통과 → `deploy` 워크플로가 SSH로 접속해 `scripts/deploy.sh` 실행.
수동으로 배포할 때도 같은 스크립트를 쓴다.

```bash
cd ~/app && ./scripts/deploy.sh
```

스크립트가 하는 일: `yong-app:latest` → `yong-app:prev` 태그 → main으로 이동·fast-forward → `up -d --build` → nginx 재생성(`--force-recreate`) → `/health` 3회 연속 성공 확인(최대 60초) → 실패하면 prev로 롤백하고 실패로 끝남.

nginx는 reload가 아니라 재생성한다: `git checkout`이 `nginx/` 폴더를 지웠다 다시 만들면, 떠 있는 컨테이너는 지워진 빈 폴더를 계속 마운트하고 있어 reload 시 80 포트가 닫힌다.

롤백만 따로 할 때:

```bash
docker tag yong-app:prev yong-app:latest
docker compose up -d --no-build app
```

GitHub Secrets: `SERVER_HOST`, `SERVER_USER`, `SERVER_SSH_KEY`(배포 전용 키).

## 4. 검증 체크리스트

| 확인 | 명령 | 기대값 |
| --- | --- | --- |
| health 200 + DB | `curl -s -w ' %{http_code}\n' http://localhost/health` | `"db":{"status":"UP"}`, 200 |
| 핵심 API | `curl -s 'http://localhost/api/dashboard?year=2025' \| head -c 300` | `"success":true` |
| Swagger 열림 | `curl -s -o /dev/null -w '%{http_code}\n' http://localhost/swagger-ui/index.html` | 200 |
| internal 차단 | `curl -s -o /dev/null -w '%{http_code}\n' http://localhost/api/internal/x` | 403 |
| actuator 비노출 | `curl -s -o /dev/null -w '%{http_code}\n' http://localhost/actuator/env` | 404 |
| Swagger 문서에 internal 없음 | `curl -s http://localhost/v3/api-docs \| grep -c '/api/internal'` | 0 |
| ML 연결 (실행 안 함) | `curl -s http://localhost/api/ml/run/status` | `"success":true`, `"status":"NONE"` |
| ML 입력 검사 (실행 안 함) | `curl -s -X POST -H 'Content-Type: application/json' -d '{"year":2025,"month":13}' http://localhost/api/ml/run` | 400 `INVALID_REQUEST` |
| 메모리 | `docker stats --no-stream` | app < 768MiB, db < 512MiB, ml < 768MiB (실행 중 사용량 기록) |
| 응답시간 10회 | `./scripts/metrics.sh latency 'http://localhost/api/dashboard?year=2025' 10` | p50·max 기록 |
| swap 사용량 | `free -h` | Swap used 기록 |

브라우저: `http://<서버IP>/swagger-ui/index.html` (보안 그룹 80 열림).
