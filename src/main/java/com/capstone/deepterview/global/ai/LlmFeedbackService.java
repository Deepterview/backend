package com.capstone.deepterview.global.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LlmFeedbackService {

    private final ChatClient chatClient;
    private final InterviewTools interviewTools;
    private final ObjectMapper objectMapper;

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
                    "actionFeedback": "구체적 행동에 대한 피드백",
                    "resultFeedback": "결과 및 배운 점에 대한 피드백"
                  }
                }
                """.formatted(questionText, transcript);

        String response = chatClient.prompt()
                .user(prompt)
                .tools(interviewTools)
                .call()
                .content();

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
}
