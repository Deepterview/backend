package com.capstone.deepterview.api;

import com.capstone.deepterview.domain.answer.repository.LlmFeedbackRepository;
import com.capstone.deepterview.domain.interview.domain.JobCategory;
import com.capstone.deepterview.domain.interview.repository.JobCategoryRepository;
import com.capstone.deepterview.domain.report.repository.FeedbackReportRepository;
import com.capstone.deepterview.global.ai.LlmAnalysisResult;
import com.capstone.deepterview.global.ai.LlmFeedbackService;
import com.capstone.deepterview.global.ai.LlmReportSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 같은 답변/세션에 대한 동시 요청이 Claude를 한 번만 호출하고 모두 성공하는지 검증합니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "local"})
class AnalysisConcurrencyApiTest {

    private static final int CONCURRENT_REQUESTS = 5;
    private static final long LLM_DELAY_MS = 500;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JobCategoryRepository jobCategoryRepository;
    @Autowired private LlmFeedbackRepository llmFeedbackRepository;
    @Autowired private FeedbackReportRepository feedbackReportRepository;

    @MockitoBean
    private LlmFeedbackService llmFeedbackService;

    @BeforeEach
    void stubLlm() {
        var feedbackPart = new LlmAnalysisResult.FeedbackPart(
                "강점", "약점", "개선", List.of("꼬리질문1")
        );
        var starPart = new LlmAnalysisResult.StarPart(
                7.0f, 8.0f, 7.5f, 8.5f, "상황", "과제", "행동", "결과"
        );
        when(llmFeedbackService.generateAnalysis(any(), any())).thenAnswer(inv -> {
            Thread.sleep(LLM_DELAY_MS);
            return new LlmAnalysisResult(feedbackPart, starPart);
        });
        when(llmFeedbackService.generateReportSummary(any(), any())).thenAnswer(inv -> {
            Thread.sleep(LLM_DELAY_MS);
            return new LlmReportSummary("강점 요약", "약점 요약", "개선 우선순위", "종합 평가");
        });
    }

    @Test
    @DisplayName("GET /analysis: 동시 요청 시 Claude는 1회만 호출되고 모두 200")
    void concurrentAnalysisRequests_callLlmOnce() throws Exception {
        String token = loginAndGetToken("analysis-concurrent-" + UUID.randomUUID() + "@test.com");
        Long sessionId = createSession(token, seededCategory().getId());
        long answerId = submitAnswer(token, getFirstQuestionId(token, sessionId));

        List<Integer> statuses = performConcurrently(() -> get("/api/v1/answers/{id}/analysis", answerId)
                .header("Authorization", "Bearer " + token));

        assertThat(statuses).containsOnly(200);
        verify(llmFeedbackService, times(1)).generateAnalysis(any(), any());
        assertThat(llmFeedbackRepository.findByAnswer_Id(answerId)).isPresent();
    }

    @Test
    @DisplayName("POST /report: 동시 요청 시 Claude는 1회만 호출되고 모두 200")
    void concurrentReportRequests_callLlmOnce() throws Exception {
        String token = loginAndGetToken("report-concurrent-" + UUID.randomUUID() + "@test.com");
        Long sessionId = createSession(token, seededCategory().getId());
        submitAnswer(token, getFirstQuestionId(token, sessionId));
        completeSession(token, sessionId);

        List<Integer> statuses = performConcurrently(() -> post("/api/v1/sessions/{sessionId}/report", sessionId)
                .header("Authorization", "Bearer " + token));

        assertThat(statuses).containsOnly(200);
        verify(llmFeedbackService, times(1)).generateReportSummary(any(), any());
        assertThat(feedbackReportRepository.findBySession_Id(sessionId)).isPresent();
    }

    private List<Integer> performConcurrently(Callable<RequestBuilder> requestFactory) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return mockMvc.perform(requestFactory.call()).andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();

            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : futures) {
                statuses.add(f.get(30, TimeUnit.SECONDS));
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }

    // DataInitializer가 시드하는 QuestionPool을 가진 카테고리를 재사용 — 새로 만든 카테고리는
    // QuestionPool이 비어 있어 세션 생성이 실패한다.
    private JobCategory seededCategory() {
        return jobCategoryRepository.findByName("백엔드 개발")
                .orElseThrow(() -> new IllegalStateException("DataInitializer 시드 카테고리를 찾을 수 없습니다."));
    }

    private String loginAndGetToken(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/test-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\"}".formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
    }

    private Long createSession(String token, long categoryId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/sessions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jobCategoryId": %d,
                                  "jobTitle": "백엔드 개발자",
                                  "careerYears": 0,
                                  "sessionType": "TECHNICAL",
                                  "totalQuestions": 1
                                }
                                """.formatted(categoryId)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("sessionId").asLong();
    }

    private Long getFirstQuestionId(String token, Long sessionId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/sessions/{id}", sessionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("questions").path(0).path("id").asLong();
    }

    private long submitAnswer(String token, Long questionId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/answers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "questionId": %d,
                                  "transcript": "Spring Boot와 JPA를 활용하여 결제 API 응답 속도를 개선한 경험이 있습니다.",
                                  "durationSec": 60,
                                  "completionStatus": "COMPLETED"
                                }
                                """.formatted(questionId)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("answerId").asLong();
    }

    private void completeSession(String token, Long sessionId) throws Exception {
        mockMvc.perform(patch("/api/v1/sessions/{sessionId}/start", sessionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/sessions/{sessionId}/end", sessionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
