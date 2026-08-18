package com.capstone.deepterview.global.ai;

import com.capstone.deepterview.domain.answer.domain.Answer;
import com.capstone.deepterview.domain.interview.domain.InterviewSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Slf4j
public class LlmFeedbackService {

    private final ChatClient chatClient;
    private final InterviewTools interviewTools;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    // Claude 호출 지연시간(llm.call.duration) 계측 + 시작/완료/실패 로깅을 한 곳에서 처리
    // requestId/도메인 ID(sessionId 등)는 호출부가 MDC에 미리 심어두면 로그 패턴에 자동 포함
    private String callChatClient(String operation, Supplier<String> call) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = "success";
        log.info("LLM 호출 시작 operation={}", operation);
        try {
            return call.get();
        } catch (RuntimeException e) {
            outcome = "failure";
            log.error("LLM 호출 실패 operation={}", operation, e);
            throw e;
        } finally {
            sample.stop(Timer.builder("llm.call.duration")
                    .description("Claude(LLM) 호출 소요시간")
                    .tag("operation", operation)
                    .tag("outcome", outcome)
                    .register(meterRegistry));
            log.info("LLM 호출 종료 operation={} outcome={}", operation, outcome);
        }
    }

    public LlmAnalysisResult generateAnalysis(String transcript, String questionText) {
        String prompt = """
                다음은 면접 질문과 지원자의 답변입니다.

                [질문]
                %s

                [답변]
                %s

                답변이 짧거나 불완전하더라도 주어진 내용으로 반드시 피드백을 제공하세요.
                반드시 아래 JSON 형식으로만 응답하세요. 다른 텍스트는 절대 포함하지 마세요.
                점수는 0.0~10.0 사이의 소수로 작성하세요.

                {
                  "feedback": {
                    "strength": "잘한 점",
                    "weakness": "부족한 점",
                    "improvement": "개선 방향",
                    "followupQuestions": ["꼬리질문1", "꼬리질문2", "꼬리질문3"]
                  },
                  "star": {
                    "situationScore": 0.0,
                    "taskScore": 0.0,
                    "actionScore": 0.0,
                    "resultScore": 0.0,
                    "situationFeedback": "상황 설명에 대한 피드백",
                    "taskFeedback": "과제/목표 설명에 대한 피드백",
                    "actionDetail": "구체적 행동에 대한 피드백",
                    "resultFeedback": "결과 및 배운 점에 대한 피드백"
                  }
                }
                """.formatted(questionText, transcript);

        String response = callChatClient("generateAnalysis", () -> chatClient.prompt()
                .user(prompt)
                .tools(interviewTools)
                .call()
                .content());

        if (response == null || response.isBlank()) {
            return new LlmAnalysisResult(null, null);
        }

        String cleaned = response
                .replaceAll("```json\\s*", "")
                .replaceAll("```\\s*", "")
                .trim();

        try {
            return objectMapper.readValue(cleaned, LlmAnalysisResult.class);
        } catch (Exception e) {
            var feedbackPart = new LlmAnalysisResult.FeedbackPart(cleaned, null, null, List.of());
            return new LlmAnalysisResult(feedbackPart, null);
        }
    }

    public String generateFollowupQuestion(String transcript, String questionText) {
        String prompt = """
                다음은 면접 질문과 지원자의 답변입니다.

                [질문]
                %s

                [답변]
                %s

                위 답변을 바탕으로 면접관이 할 법한 꼬리 질문 1개를 생성해주세요.
                반드시 아래 JSON 형식으로만 응답하세요. 다른 텍스트는 절대 포함하지 마세요.

                {
                  "followupQuestion": "꼬리질문"
                }
                """.formatted(questionText, transcript);

        String response = callChatClient("generateFollowupQuestion", () -> chatClient.prompt()
                .user(prompt)
                .call()
                .content());

        if (response == null || response.isBlank()) {
            return null;
        }

        String cleaned = response
                .replaceAll("```json\\s*", "")
                .replaceAll("```\\s*", "")
                .trim();

        try {
            var node = objectMapper.readTree(cleaned);
            var q = node.get("followupQuestion");
            return q != null ? q.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    public List<String> generatePortfolioQuestions(String portfolioText) {
        String prompt = """
                다음은 지원자의 포트폴리오에서 추출한 내용입니다.

                [포트폴리오 내용]
                %s

                위 포트폴리오 내용을 바탕으로 면접관이 할 법한 면접 질문을 5개 생성해주세요.
                반드시 아래 JSON 형식으로만 응답하세요. 다른 텍스트는 절대 포함하지 마세요.

                {
                  "questions": ["질문1", "질문2", "질문3", "질문4", "질문5"]
                }
                """.formatted(portfolioText);

        String response = callChatClient("generatePortfolioQuestions", () -> chatClient.prompt()
                .user(prompt)
                .call()
                .content());

        if (response == null || response.isBlank()) {
            return List.of();
        }

        String cleaned = response
                .replaceAll("```json\\s*", "")
                .replaceAll("```\\s*", "")
                .trim();

        try {
            var node = objectMapper.readTree(cleaned);
            var questionsNode = node.get("questions");
            if (questionsNode == null || !questionsNode.isArray()) {
                return List.of();
            }
            List<String> questions = new ArrayList<>();
            questionsNode.forEach(q -> questions.add(q.asText()));
            return questions;
        } catch (Exception e) {
            return List.of();
        }
    }

    public LlmReportSummary generateReportSummary(InterviewSession session, List<Answer> answers) {
        StringBuilder qaSection = new StringBuilder();
        AtomicInteger index = new AtomicInteger(1);

        answers.forEach(answer -> {
            int i = index.getAndIncrement();
            qaSection.append("### Q").append(i).append(". ")
                    .append(answer.getQuestion().getContent()).append("\n");
            qaSection.append("답변: ").append(answer.getTranscript() != null ? answer.getTranscript() : "(없음)").append("\n");

            if (answer.getLlmFeedback() != null) {
                var f = answer.getLlmFeedback();
                qaSection.append("강점: ").append(f.getStrength()).append("\n");
                qaSection.append("약점: ").append(f.getWeakness()).append("\n");
                qaSection.append("개선: ").append(f.getImprovement()).append("\n");
            }
            if (answer.getStarAnalysis() != null) {
                var s = answer.getStarAnalysis();
                qaSection.append(String.format("STAR 점수: %.1f/10\n", s.getTotalScore() != null ? s.getTotalScore() : 0f));
            }
            qaSection.append("\n");
        });

        String prompt = """
                다음은 면접 세션 전체 내용입니다.

                [직무] %s
                [면접 유형] %s
                [총 문항 수] %d

                %s

                위 내용을 종합하여 반드시 아래 JSON 형식으로만 응답하세요. 다른 텍스트는 절대 포함하지 마세요.

                {
                  "strengthSummary": "면접 전반에 걸친 지원자의 강점 (2~3문장)",
                  "weaknessSummary": "면접 전반에 걸친 지원자의 약점 (2~3문장)",
                  "improvementPriority": "가장 우선적으로 개선해야 할 사항 (1~2문장)",
                  "aiSummary": "면접 전체에 대한 종합 평가 한 줄 요약"
                }
                """.formatted(
                session.getJobTitle(),
                session.getSessionType(),
                answers.size(),
                qaSection
        );

        String response = callChatClient("generateReportSummary", () -> chatClient.prompt()
                .user(prompt)
                .call()
                .content());

        if (response == null || response.isBlank()) {
            return new LlmReportSummary(null, null, null, null);
        }

        String cleaned = response
                .replaceAll("```json\\s*", "")
                .replaceAll("```\\s*", "")
                .trim();

        try {
            return objectMapper.readValue(cleaned, LlmReportSummary.class);
        } catch (Exception e) {
            return new LlmReportSummary(cleaned, null, null, null);
        }
    }
}
