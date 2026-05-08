package com.capstone.deepterview.global.ai;

import com.capstone.deepterview.domain.answer.dto.response.LlmFeedbackView;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LlmFeedbackService {

    private final ChatClient chatClient;
    private final InterviewTools interviewTools;

    public LlmFeedbackView generateFeedback(String transcript, String questionText) {

        String prompt = """
            다음은 면접 질문과 지원자의 답변입니다.
            
            [질문]
            %s
            
            [답변]
            %s
            
            답변이 짧거나 불완전하더라도 주어진 내용으로 반드시 피드백을 제공하세요.
            반드시 아래 JSON 형식으로만 응답하세요. 다른 텍스트는 절대 포함하지 마세요.
           
            {
              "strength": "잘한 점",
              "weakness": "부족한 점",
              "improvement": "개선 방향",
              "followupQuestions": ["꼬리질문1", "꼬리질문2", "꼬리질문3"]
            }
            """.formatted(questionText, transcript);

        String response = chatClient.prompt()
                .user(prompt)
                .tools(interviewTools)  // Tool 등록
                .call()
                .content();

        // 코드블록 제거
        String cleaned = response
                .replaceAll("```json\\s*", "")
                .replaceAll("```\\s*", "")
                .trim();

        return LlmFeedbackView.of(cleaned);
    }
}
