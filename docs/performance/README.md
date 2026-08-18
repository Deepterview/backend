# 성능 벤치마크 (전/후 비교)

관련 이슈: [#21](https://github.com/Deepterview/backend/issues/21) (#12, #13, #14와 연결)

## 준비물

- k6 (https://k6.io) 로컬 설치
- Docker Desktop 실행 중 (`monitoring/docker-compose.yml`)
- 로컬 MySQL 실행 중
- `local` 프로파일로 앱 실행: `SPRING_PROFILES_ACTIVE=local ./gradlew bootRun`
  (`/api/v1/auth/test-login`으로 인증 우회)

## 실행 순서

```bash
docker compose -f monitoring/docker-compose.yml up -d
# Grafana: http://localhost:3001 (admin/admin) - Prometheus 데이터소스와
# "Deepterview - Performance Benchmark" 대시보드가 자동 프로비저닝됨

SPRING_PROFILES_ACTIVE=local ./gradlew bootRun

k6 run perf/llm-concurrency.js --summary-export=docs/performance/baseline-result.json
```

측정 후 Grafana 대시보드(TPS / p95·p99 지연 / HikariCP active·pending)를 스크린샷으로 저장하고,
이 디렉터리에 `baseline.md`(전) / `after.md`(후) / `comparison.md`(비교)로 정리한다.

## 파일

- `baseline-result.json`, `baseline.md` — #12/#13/#14 작업 시작 전 측정
- `after-result.json`, `after.md` — #12/#13/#14 머지 후 동일 조건 재측정
- `comparison.md` — 전/후 비교 표 및 결론
