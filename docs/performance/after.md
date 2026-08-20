# After 측정 (#8/#13/#27 머지 후)

- 측정일: 2026-08-20
- git commit: `1e7a366` (main, `Merge pull request #28 from Deepterview/Task/#27-1-interview_transaction_separation`)
- 스크립트: `perf/llm-concurrency.js` (`k6 run perf/llm-concurrency.js --summary-export=docs/performance/after-result.json`)
- 파라미터: `llm_load` 10 VU / 45s, `control_poll` 초당 2회 — baseline.md와 동일 (오버라이드 없음)
- 환경: 로컬 Spring Boot(local 프로파일) + 로컬 MySQL + 로컬 Docker(Prometheus/Grafana), HikariCP
  기본 설정(`maximum-pool-size=10`) — baseline과 동일 조건

## 결과 요약

| 항목 | avg | p90 | p95 | max |
|---|---|---|---|---|
| llm_followup (꼬리질문 생성) | 1.96s | 2.53s | 2.95s | 4.53s |
| llm_report (리포트 생성) | 4.44s | 5.41s | 5.64s | 6.01s |
| **control (job-categories, 무관한 단순 DB 조회)** | **10.89ms** | **19.4ms** | **22.26ms** | **73.02ms** |

- HTTP 에러율: 0.00% (589건 중 0건 실패)
- checks: 447/447 통과 (100%)

## HikariCP 커넥션 풀 관찰

**게이지 지표**: 테스트 구간 동안 `hikaricp_connections_active`의 관측된 최댓값은 **2**
(`max_over_time(hikaricp_connections_active[5m])`) — baseline 당시 풀이 10(=`maximum-pool-size`)
까지 포화됐던 것과 대조적으로, 이번엔 풀 용량의 5분의 1도 채 쓰지 않았다. `pending`은 baseline과
마찬가지로 테스트 내내 0으로 관측됐다(순간값 게이지의 스크레이프 해상도 한계는 baseline과 동일하게
적용됨).

**누적 지표 (스크레이프 간격에 안 걸리는 더 확실한 증거)**: 테스트 종료 직후 5분 윈도우 기준
(`increase(...[5m])`)으로:
- 커넥션 획득 시도 약 1555건, 누적 대기시간 약 **0.19초** (평균 ≈ 0.12ms/건)
- **단일 최장 대기시간: 약 58.9ms** (`hikaricp_connections_acquire_seconds_max` = 0.0588643)

baseline의 "커넥션 획득 시도 1117건, 누적 대기시간 48.28초, 최장 대기 3.479초"와 비교하면, 누적
대기시간은 약 **250배**, 최장 대기시간은 약 **59배** 줄었다.

## 해석

`control` 엔드포인트(`job-categories`)의 지연이 baseline 대비 극적으로 줄었다 — avg 551ms →
10.89ms, p95 2.11s → 22.26ms, max 3.49s → 73.02ms. `hikaricp_connections_acquire_seconds_max`
(3.479s → 0.059s)와 `active` 게이지 최댓값(10 → 2)이 이 개선을 그대로 뒷받침한다. 즉
`AnswerService`(#8) / `ReportService`·`PortfolioService`(#13) / `InterviewService`(#27)에서
Claude 동기 호출을 `@Transactional` 경계 밖으로 뺀 작업이, baseline.md가 지목했던 "LLM 호출이
DB 커넥션을 물고 있어 무관한 요청까지 지연되는 문제"를 실제로 해결했다는 강한 증거다.

반면 `llm_followup`/`llm_report` 자체의 지연(avg/p90/p95/max)은 baseline보다 오히려 소폭 늘었다
(예: llm_report avg 4.16s → 4.44s, max 5.45s → 6.01s). 이는 애초에 이번 작업이 개선을 노린
지표가 아니다 — 이 값은 Claude API 자체의 실제 응답 시간(측정 시점의 네트워크/모델 부하에 따라
변동)을 그대로 반영하며, 트랜잭션 경계 분리는 이 호출 자체를 빠르게 만들지 않는다(사용자가 Claude
응답을 기다리는 시간은 그대로). 이번 작업이 개선하는 것은 "이 요청 하나의 속도"가 아니라 "이 요청이
무관한 다른 요청들의 DB 접근을 얼마나 방해하는가"이며, 그 목표는 위 `control`/HikariCP 지표로
명확히 달성이 확인된다.
