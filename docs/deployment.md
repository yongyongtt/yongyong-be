# 배포 런북 (EC2 + Docker Compose, 수동 배포)

구성: `nginx(80)` → `app(8080, 외부 비공개)` → `db(MariaDB 12.2.2, 외부 비공개)`. 앱은 `yongin`에 SELECT 전용 계정으로 접속한다 (decisions D15).

## 1. 서버 최초 준비

```bash
cd ~/app
cp .env.example .env && vi .env      # 실제 비밀번호 입력 (작은따옴표 ' 금지)
chmod 600 .env
```

`db` 컨테이너는 볼륨이 비어 있는 첫 실행 때만 `db/init/01-app-user.sh`로 앱 계정(`GRANT SELECT ON yongin.*`)을 만든다.
`.env`의 `DB_USERNAME`·`DB_PASSWORD`를 나중에 바꾸면 계정은 자동으로 바뀌지 않는다 (직접 `ALTER USER` 하거나 볼륨을 새로 만든다).

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

## 3. 수동 배포

```bash
cd ~/app
docker image inspect yong-app:latest >/dev/null 2>&1 && docker tag yong-app:latest yong-app:prev   # 롤백용
git pull
docker compose up -d --build
docker compose ps                        # app, db: healthy / nginx: running
curl -fsS http://localhost/health
```

롤백 (헬스체크 실패 시):

```bash
docker tag yong-app:prev yong-app:latest
docker compose up -d --no-build app
```

## 4. 검증 체크리스트

| 확인 | 명령 | 기대값 |
| --- | --- | --- |
| health 200 + DB | `curl -s -w ' %{http_code}\n' http://localhost/health` | `"db":{"status":"UP"}`, 200 |
| 핵심 API | `curl -s 'http://localhost/api/dashboard?year=2025' \| head -c 300` | `"success":true` |
| Swagger 열림 | `curl -s -o /dev/null -w '%{http_code}\n' http://localhost/swagger-ui/index.html` | 200 |
| internal 차단 | `curl -s -o /dev/null -w '%{http_code}\n' http://localhost/api/internal/x` | 403 |
| actuator 비노출 | `curl -s -o /dev/null -w '%{http_code}\n' http://localhost/actuator/env` | 404 |
| Swagger 문서에 internal 없음 | `curl -s http://localhost/v3/api-docs \| grep -c '/api/internal'` | 0 |
| 메모리 | `docker stats --no-stream` | app < 768MiB, db < 512MiB |
| 응답시간 10회 | `./scripts/metrics.sh latency 'http://localhost/api/dashboard?year=2025' 10` | p50·max 기록 |
| swap 사용량 | `free -h` | Swap used 기록 |

브라우저: `http://<서버IP>/swagger-ui/index.html` (보안 그룹 80 열림).
