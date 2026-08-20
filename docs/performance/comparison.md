# 성능 개선 전/후 비교 (#21)

관련 이슈: [#21](https://github.com/Deepterview/backend/issues/21) · 작업: [#8](https://github.com/Deepterview/backend/issues/8), [#13](https://github.com/Deepterview/backend/issues/13), [#27](https://github.com/Deepterview/backend/issues/27)

동일한 스크립트(`perf/llm-concurrency.js`)와 파라미터(`llm_load` 10 VU / 45s, `control_poll`
초당 2회), 동일한 로컬 환경(HikariCP 기본 `maximum-pool-size=10`)으로 측정한 두 결과를 비교한다.

- Before: `baseline.md` (2026-08-13, 커밋 `321a7e9`, #12/#13/#14 작업 시작 전)
- After: `after.md` (2026-08-20, 커밋 `1e7a366`, #8/#13/#27로 모든 LLM 호출을 `@Transactional`
  경계 밖으로 뺀 뒤)

## 지연시간 비교

| 항목 | avg (전→후) | p90 (전→후) | p95 (전→후) | max (전→후) |
|---|---|---|---|---|
| llm_followup | 1.82s → 1.96s | 2.21s → 2.53s | 2.53s → 2.95s | 2.71s → 4.53s |
| llm_report | 4.16s → 4.44s | 4.57s → 5.41s | 4.80s → 5.64s | 5.45s → 6.01s |
| **control (무관한 단순 DB 조회)** | **551ms → 10.89ms** | **1.57s → 19.4ms** | **2.11s → 22.26ms** | **3.49s → 73.02ms** |

- HTTP 에러율: 0.00% → 0.00% (두 측정 모두 실패 없음)
- checks 통과율: 485/485 → 447/447 (두 측정 모두 100%)

## HikariCP 커넥션 풀 비교

| 지표 | Before | After | 변화 |
|---|---|---|---|
| `active` 게이지 관측 최댓값 | 10 (풀 포화) | 2 | 풀 용량의 20%만 사용 |
| 누적 커넥션 획득 대기시간 (테스트 구간) | 48.28초 (1117건) | 0.19초 (1555건) | 약 **250배 감소** |
| 단일 최장 획득 대기시간 | 3.479초 | 0.059초 | 약 **59배 감소** |

## 결론

**#8/#13/#27이 목표했던 문제(무관한 요청까지 지연시키는 DB 커넥션 풀 경합)는 실제로 해결됐다.**
`control` 엔드포인트의 p95/max와 HikariCP 누적 획득 대기시간이 모두 극적으로 줄었고, `active` 풀
사용량도 포화(10) → 여유(2)로 바뀌었다. baseline.md가 제시했던 가장 신뢰할 수 있는 지표
(`hikaricp_connections_acquire_seconds_max`가 k6가 관측한 `control` 엔드포인트 최대 지연과
거의 일치했던 것)가 이번에도 같은 방식으로 개선을 뒷받침한다.

**의도적으로 개선되지 않은 지표**: `llm_followup`/`llm_report`의 자체 지연은 오히려 소폭
늘었다(after.md 참고). 이는 회귀가 아니라 애초에 이 작업의 목표가 아니었던 지표다 — 트랜잭션
경계 분리는 Claude 응답을 기다리는 시간 자체를 줄이지 않으며, 이 값은 측정 시점 Claude API의
실제 응답 속도를 그대로 반영한다.

## 측정상의 한계

두 측정은 서로 다른 날짜(2026-08-13 vs 2026-08-20)에 이뤄졌고, 클라우드 API(Claude) 호출이 섞여
있어 절대적인 수치 하나하나를 엄밀한 벤치마크로 보기는 어렵다. 다만 이번 비교에서 가장 중요한
신호(`control` 엔드포인트 지연, HikariCP 풀 포화 여부, 누적 획득 대기시간)는 개선 폭이 워낙 커서
(수십~수백 배) 측정 노이즈로 설명되지 않는다. `llm_followup`/`llm_report` 자체 지연처럼 원래도
Claude API 응답 속도에 좌우되는 지표는 전/후 비교의 근거로 쓰지 않았다.

baseline.md가 스스로 기록했던 것과 같은 한계도 동일하게 적용된다: Prometheus `scrape_interval`이
15초라 게이지 지표(`active`/`pending`)의 순간값은 정확한 지속시간을 보여주지 못하며, 누적 카운터/
타이머 지표(`acquire_seconds_sum`/`_count`/`_max`)가 더 신뢰할 수 있는 증거로 사용됐다.
