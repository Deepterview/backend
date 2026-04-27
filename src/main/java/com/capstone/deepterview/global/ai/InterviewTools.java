package com.capstone.deepterview.global.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class InterviewTools {

    // Tool 1: 기술 키워드 검색
    @Tool(description = "면접 답변에서 언급된 기술 키워드를 검색해서 관련 개념과 모범 답변 기준을 반환합니다.")
    public String searchTechDocs(String keyword) {
        // 일단 Mock으로 시작, 나중에 웹 검색 API로 교체
        return """
            [%s 관련 면접 기준]
            - 핵심 개념: %s의 동작 원리를 명확히 설명해야 함
            - 모범 답변: 실제 사용 경험과 트레이드오프 언급
            - 평가 포인트: 깊이 있는 이해와 실무 적용 능력
            """.formatted(keyword, keyword);
    }

    // Tool 2: 꼬리 질문 생성
    @Tool(description = "면접 답변을 분석해서 면접관이 할 법한 꼬리 질문 3개를 생성합니다.")
    public String getFollowUpQuestions(String answerSummary) {
        return "꼬리 질문은 Claude가 직접 생성합니다.";
    }
}