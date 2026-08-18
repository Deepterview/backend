# 베이스라인 측정 (#12/#13/#14 작업 시작 전)

- 측정일: 2026-08-13
- git commit: `321a7e9` (main, `Merge pull request #11 ...prometheus_grafana_monitoring`)
- 스크립트: `perf/llm-concurrency.js` (`k6 run perf/llm-concurrency.js --summary-export=docs/performance/baseline-result.json`)
- 파라미터: `llm_load` 10 VU / 45s (매 iteration마다 새 세션 생성 → 꼬리질문 생성 → 세션 종료 → 리포트
  생성, 두 곳 모두 매번 LLM 새로 호출), `control_poll` 초당 2회 (`GET /api/v1/job-categories`, 인증 불필요,
  단순 DB 조회)
- 환경: 로컬 Spring Boot(local 프로파일) + 로컬 MySQL + 로컬 Docker(Prometheus/Grafana), HikariCP 기본
  설정(`maximum-pool-size=10`)

## 결과 요약

| 항목 | avg | p90 | p95 | max |
|---|---|---|---|---|
| llm_followup (꼬리질문 생성) | 1.82s | 2.21s | 2.53s | 2.71s |
| llm_report (리포트 생성) | 4.16s | 4.57s | 4.80s | 5.45s |
| **control (job-categories, 무관한 단순 DB 조회)** | **551ms** | **1.57s** | **2.11s** | **3.49s** |

- HTTP 에러율: 0.00% (643건 중 0건 실패)
- checks: 485/485 통과

## HikariCP 커넥션 풀 관찰

**게이지 지표 (`hikaricp_connections_active` / `_pending`)**: `active`가 **10(=
`maximum-pool-size` 기본값)까지 포화**됨. 단, Prometheus `scrape_interval`이 15초라서 실제 포화
지속시간을 게이지 값만으로 정확히 잴 수는 없다 — 처음에 step=5로 조회해 "약 40초 지속"으로 잘못
추정했는데, 이는 15초 간격 스크레이프 값을 5초 간격으로 보간(interpolate)해서 생긴 착시였다. 실제로
`active=10`이 찍힌 스크레이프는 15초 간격으로 딱 2번뿐이라, Grafana 대시보드에서 육안으로 보이는 것도
15초 정도의 포화 구간이다 (정정: ~~약 40초~~ → 스크레이프 기준 약 15초, 실제 포화 시간은 그보다 조금
더 길거나 짧을 수 있음 — 15초 해상도의 한계). `pending`은 테스트 내내 0으로만 찍혔는데, 이 역시 같은
이유다: `pending`도 순간값 게이지라서, 대기가 15초 스크레이프 사이 구간에서 시작되고 끝나버리면
아예 안 잡힌다.

**누적 지표 (`hikaricp_connections_acquire_seconds_sum`/`_count`/`_max`) — 스크레이프 간격에 안
걸리는 더 확실한 증거**: 이건 게이지가 아니라 누적 카운터/타이머라서 스크레이프 사이의 짧은 대기도
놓치지 않는다. 테스트 구간(약 60초) 동안:
- 커넥션 획득 시도 1117건(1122-5), 누적 대기시간 약 48.28초 (평균 ≈ 43ms/건 — 대부분은 빠르게
  받았지만 일부가 크게 밀렸다는 뜻)
- **단일 최장 대기시간: 3.479초** (`hikaricp_connections_acquire_seconds_max`)

이 3.479초는 k6가 측정한 `control` 엔드포인트의 최대 지연(3.49초)과 사실상 일치한다. 즉 인증도
필요 없고 단순 DB 조회 한 번뿐인 `job-categories` 요청이 3.48초나 걸린 이유는 다른 오버헤드가 아니라
**거의 전부 "DB 커넥션을 받기까지 기다린 시간" 그 자체**였다는 뜻이다.

## 해석

`llm_report`/`llm_followup` 요청들이 `ReportService.generateOrGetReport` /
`InterviewService.getNextQuestion`의 `@Transactional` 경계 안에서 Claude 동기 호출이 끝날 때까지
DB 커넥션을 계속 물고 있고, HikariCP 풀(max=10)이 그 커넥션들로 가득 차면서 `job-categories`처럼
무관한 요청까지 커넥션을 못 받아 대기하게 된다. `active` 게이지의 순간 포화와, 무엇보다
`hikaricp_connections_acquire_seconds_max`(3.479s)가 k6가 관측한 `control` 엔드포인트 최대 지연
(3.49s)과 거의 정확히 일치한다는 점이 가장 직접적인 증거다.

→ #13(ReportService/PortfolioService LLM 호출 트랜잭션 경계 분리)이 머지된 뒤 동일 조건으로
재측정했을 때, `control` 엔드포인트의 p95/max 지연뿐 아니라 **`hikaricp_connections_acquire_seconds_max`
값이 유의미하게 줄어드는지**가 가장 신뢰할 수 있는 비교 지표가 될 것 (게이지 기반 `active`/`pending`
관찰보다 스크레이프 해상도에 덜 흔들림).
