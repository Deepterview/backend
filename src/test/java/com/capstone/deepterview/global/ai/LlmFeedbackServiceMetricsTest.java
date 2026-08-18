package com.capstone.deepterview.global.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * LlmFeedbackService.callChatClient()가 llm.call.duration 타이머를
 * operation/outcome 태그와 함께 실제로 기록하는지 검증한다.
 */
class LlmFeedbackServiceMetricsTest {

    private ChatClient chatClient;
    private SimpleMeterRegistry meterRegistry;
    private LlmFeedbackService llmFeedbackService;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        meterRegistry = new SimpleMeterRegistry();
        llmFeedbackService = new LlmFeedbackService(
                chatClient, mock(InterviewTools.class), new ObjectMapper(), meterRegistry
        );
    }

    @Test
    @DisplayName("Claude 호출 성공 시 llm.call.duration 타이머가 outcome=success로 기록된다")
    void generateFollowupQuestion_success_recordsSuccessTimer() {
        when(chatClient.prompt().user(anyString()).call().content())
                .thenReturn("{\"followupQuestion\": \"팀 협업 경험을 더 설명해주세요.\"}");

        String result = llmFeedbackService.generateFollowupQuestion("답변", "질문");

        assertThat(result).isEqualTo("팀 협업 경험을 더 설명해주세요.");
        var timer = meterRegistry.find("llm.call.duration")
                .tag("operation", "generateFollowupQuestion")
                .tag("outcome", "success")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Claude 호출 실패 시 llm.call.duration 타이머가 outcome=failure로 기록되고 예외가 전파된다")
    void generateFollowupQuestion_failure_recordsFailureTimer() {
        when(chatClient.prompt().user(anyString()).call().content())
                .thenThrow(new RuntimeException("Claude API 오류"));

        try {
            llmFeedbackService.generateFollowupQuestion("답변", "질문");
        } catch (RuntimeException ignored) {
        }

        var timer = meterRegistry.find("llm.call.duration")
                .tag("operation", "generateFollowupQuestion")
                .tag("outcome", "failure")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }
}
