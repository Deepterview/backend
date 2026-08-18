// LLM 호출 구간 동시성 벤치마크
//
// 목적: ReportService/PortfolioService의 LLM 호출이 @Transactional 경계 안에서 DB 커넥션을
// 물고 있는 문제(#13)를 재현/검증한다. 매번 새 세션을 만들어 캐시 없이 LLM을 항상 새로 호출하는
// 두 엔드포인트(꼬리질문 생성, 리포트 생성)에 동시 요청을 몰아넣고, 동시에 인증이 필요 없는 가벼운
// 엔드포인트(직무 카테고리 조회)를 별도 시나리오로 폴링해 "LLM 호출이 몰릴 때 무관한 API도
// 같이 느려지는지"를 관찰한다.
//
// 실행 예시:
//   k6 run perf/llm-concurrency.js --summary-export=docs/performance/baseline-result.json
//   BASE_URL=http://localhost:8080 VUS=10 DURATION=45s k6 run perf/llm-concurrency.js
//
// 사전 조건: local 프로파일로 앱 실행 (SPRING_PROFILES_ACTIVE=local ./gradlew bootRun)
//           -> /api/v1/auth/test-login 사용 가능해야 함

import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const VUS = parseInt(__ENV.VUS || '10', 10);
const DURATION = __ENV.DURATION || '45s';

const followupLatency = new Trend('llm_followup_duration', true);
const reportLatency = new Trend('llm_report_duration', true);
const controlLatency = new Trend('control_endpoint_duration', true);

export const options = {
  scenarios: {
    llm_load: {
      executor: 'constant-vus',
      vus: VUS,
      duration: DURATION,
      exec: 'llmLoad',
    },
    control_poll: {
      executor: 'constant-arrival-rate',
      rate: 2,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: 5,
      exec: 'controlPoll',
    },
  },
};

export function setup() {
  const res = http.get(`${BASE_URL}/api/v1/job-categories`);
  check(res, { 'job-categories 200': (r) => r.status === 200 });
  const categories = res.json('data');
  if (!categories || categories.length === 0) {
    throw new Error('직무 카테고리가 비어 있습니다. DataInitializer가 시드 데이터를 넣었는지 확인하세요.');
  }
  const backend = categories.find((c) => c.name === '백엔드 개발') || categories[0];
  return { jobCategoryId: backend.id };
}

function login(vuTag) {
  const email = `perf-test-vu${vuTag}@deepterview.local`;
  const res = http.post(
    `${BASE_URL}/api/v1/auth/test-login`,
    JSON.stringify({ email }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  check(res, { 'test-login 200': (r) => r.status === 200 });
  const token = res.json('data.accessToken');
  return { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
}

export function llmLoad(data) {
  const headers = login(__VU);

  // 1. 세션 생성 (LLM 호출 없음)
  const createRes = http.post(
    `${BASE_URL}/api/v1/sessions`,
    JSON.stringify({
      jobCategoryId: data.jobCategoryId,
      jobTitle: '백엔드 개발자',
      careerYears: 0,
      sessionType: 'TECHNICAL',
      totalQuestions: 3,
    }),
    { headers }
  );
  if (!check(createRes, { '세션 생성 200': (r) => r.status === 200 })) {
    sleep(1);
    return;
  }
  const sessionId = createRes.json('data.sessionId');
  const firstQuestionId = createRes.json('data.questions.0.id');

  // 2. 세션 시작
  http.patch(`${BASE_URL}/api/v1/sessions/${sessionId}/start`, null, { headers });

  // 3. 첫 질문에 답변 제출 (LLM 호출 없음)
  const answerRes = http.post(
    `${BASE_URL}/api/v1/answers`,
    JSON.stringify({
      questionId: firstQuestionId,
      transcript: '저는 Spring Boot로 REST API를 설계하고 JPA로 데이터를 다뤄본 경험이 있습니다.',
      durationSec: 45,
      completionStatus: 'COMPLETED',
    }),
    { headers }
  );
  if (!check(answerRes, { '답변 제출 200': (r) => r.status === 200 })) {
    sleep(1);
    return;
  }
  const answerId = answerRes.json('data.answerId');

  // 4. 꼬리질문 생성 - 항상 새로 LLM 호출
  group('llm_followup', () => {
    const res = http.post(
      `${BASE_URL}/api/v1/sessions/${sessionId}/questions/next`,
      JSON.stringify({ answerId }),
      { headers }
    );
    followupLatency.add(res.timings.duration);
    check(res, { '꼬리질문 생성 200': (r) => r.status === 200 });
  });

  // 5. 세션 종료 (리포트 생성 전 COMPLETED 상태 필요)
  http.patch(`${BASE_URL}/api/v1/sessions/${sessionId}/end`, null, { headers });

  // 6. 리포트 생성 - 세션이 새로 만들어졌으므로 캐시 없이 항상 새로 LLM 호출
  group('llm_report', () => {
    const res = http.post(`${BASE_URL}/api/v1/sessions/${sessionId}/report`, null, { headers });
    reportLatency.add(res.timings.duration);
    check(res, { '리포트 생성 200': (r) => r.status === 200 });
  });
}

export function controlPoll() {
  const res = http.get(`${BASE_URL}/api/v1/job-categories`);
  controlLatency.add(res.timings.duration);
  check(res, { '대조군(job-categories) 200': (r) => r.status === 200 });
}
