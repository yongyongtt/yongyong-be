#!/usr/bin/env bash
# WORKLOG.md 지표 계산 스크립트 (CLAUDE.md 7장)
#   ./scripts/metrics.sh coverage            JaCoCo 라인 커버리지 %
#   ./scripts/metrics.sh ai-ratio [base]     현재 브랜치의 Claude 추가 줄 / 사람 수정 줄 / 비율 (기본 base=main)
#   ./scripts/metrics.sh ci [n]              최근 n번 CI 실행의 실패율과 실패한 단계 (gh CLI 필요, 기본 n=20)
#   ./scripts/metrics.sh latency <url> [n]   url을 n번 호출한 응답시간 p50·max(ms) (기본 n=20, 첫 1회는 워밍업으로 제외)
set -euo pipefail

AI_MARK='Co-Authored-By: Claude'

coverage() {
  local csv="build/reports/jacoco/test/jacocoTestReport.csv"
  if [[ ! -f "$csv" ]]; then
    ./gradlew -q jacocoTestReport >/dev/null 2>&1 || true
  fi
  if [[ ! -f "$csv" ]]; then
    echo "coverage: 측정 못 함 ($csv 없음. build.gradle에 jacoco csv 리포트 설정 필요)"
    return 0
  fi
  # 열: GROUP,PACKAGE,CLASS,INSTRUCTION_MISSED,INSTRUCTION_COVERED,BRANCH_MISSED,BRANCH_COVERED,LINE_MISSED,LINE_COVERED,...
  awk -F, 'NR>1 { m+=$8; c+=$9 } END { if (m+c==0) print "coverage: 측정 못 함 (라인 0)"; else printf "coverage: line %.1f%% (%d/%d)\n", c*100/(m+c), c, m+c }' "$csv"
}

ai_ratio() {
  local base="${1:-main}"
  local range="${base}..HEAD"
  if ! git rev-parse --verify -q "$base" >/dev/null; then
    echo "ai-ratio: 측정 못 함 (base '$base' 없음)"; return 0
  fi
  local ai_commits human_commits
  ai_commits=$(git log --format='%H' --grep="$AI_MARK" -i "$range" || true)
  human_commits=$(git log --format='%H' --grep="$AI_MARK" -i --invert-grep "$range" || true)

  if [[ -z "$ai_commits" ]]; then
    echo "ai-ratio: Claude 커밋 없음 (트레일러 '$AI_MARK' 확인)"; return 0
  fi

  # Claude 커밋이 추가한 줄과 건드린 파일
  local ai_added=0 files_tmp
  files_tmp=$(mktemp)
  while read -r h; do
    [[ -z "$h" ]] && continue
    while read -r add del path; do
      [[ "$add" == "-" ]] && continue
      ai_added=$((ai_added + add))
      echo "$path" >> "$files_tmp"
    done < <(git show --numstat --format='' "$h")
  done <<< "$ai_commits"
  sort -u "$files_tmp" -o "$files_tmp"

  # 사람 커밋이 같은 파일에서 바꾼 줄(추가+삭제)
  local human_changed=0
  while read -r h; do
    [[ -z "$h" ]] && continue
    while read -r add del path; do
      [[ "$add" == "-" ]] && continue
      if grep -qxF "$path" "$files_tmp"; then
        human_changed=$((human_changed + add + del))
      fi
    done < <(git show --numstat --format='' "$h")
  done <<< "$human_commits"
  rm -f "$files_tmp"

  if [[ "$ai_added" -eq 0 ]]; then
    echo "ai-ratio: AI 추가 줄 0"; return 0
  fi
  awk -v a="$ai_added" -v h="$human_changed" 'BEGIN { printf "ai-ratio: AI 추가 %d줄 / 사람 수정 %d줄 / 비율 %.1f%%\n", a, h, h*100/a }'
}

ci() {
  local n="${1:-20}"
  if ! command -v gh >/dev/null; then
    echo "ci: 측정 못 함 (gh CLI 없음)"; return 0
  fi
  local runs
  if ! runs=$(gh run list --workflow ci.yml --limit "$n" --json databaseId,conclusion --jq '.[] | select(.conclusion != "" and .conclusion != null) | "\(.databaseId) \(.conclusion)"' 2>/dev/null); then
    echo "ci: 측정 못 함 (gh 실행 실패: GitHub 원격 저장소·gh auth login 확인)"; return 0
  fi
  local total fail
  total=$(grep -c . <<< "$runs" || true)
  fail=$(grep -c ' failure$' <<< "$runs" || true)
  if [[ "$total" -eq 0 ]]; then
    echo "ci: 완료된 실행 없음"; return 0
  fi
  awk -v t="$total" -v f="$fail" 'BEGIN { printf "ci: 최근 %d회 중 실패 %d회 (%.1f%%)\n", t, f, f*100/t }'
  # 실패한 단계 이름 집계 (format / test / coverage)
  grep ' failure$' <<< "$runs" | while read -r id _; do
    gh run view "$id" --json jobs --jq '.jobs[].steps[] | select(.conclusion == "failure") | .name'
  done | sort | uniq -c | sort -rn | awk '{ printf "  실패 단계 %s: %d회\n", $2, $1 }'
}

latency() {
  local url="${1:-}" n="${2:-20}"
  if [[ -z "$url" ]]; then echo "latency: URL을 넣어주세요"; return 1; fi
  local code
  code=$(curl -s -o /dev/null -w '%{http_code}' "$url" || true)   # 워밍업 1회 (결과에서 제외)
  if [[ "$code" != "200" ]]; then
    echo "latency: 측정 못 함 (응답 코드 ${code:-없음}, 서버가 떠 있는지 확인)"; return 0
  fi
  for ((i = 0; i < n; i++)); do
    curl -s -o /dev/null -w '%{time_total}\n' "$url"
  done | sort -n | awk -v n="$n" '{ v[NR] = $1 * 1000 } END {
      p50 = (NR % 2) ? v[(NR + 1) / 2] : (v[NR / 2] + v[NR / 2 + 1]) / 2
      printf "latency: p50 %.1fms / max %.1fms (%d회)\n", p50, v[NR], NR }'
}

case "${1:-}" in
  coverage) coverage ;;
  ai-ratio) ai_ratio "${2:-main}" ;;
  ci) ci "${2:-20}" ;;
  latency) latency "${2:-}" "${3:-20}" ;;
  *) echo "사용법: $0 {coverage|ai-ratio [base]|ci [n]|latency <url> [n]}"; exit 1 ;;
esac
