package com.capstone.deepterview.global.ai;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.capstone.deepterview.global.exception.CustomException;
import com.capstone.deepterview.global.exception.ErrorCode;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Claude(Anthropic) 응답 지연/연속 실패 상황에서 실제로 재시도되고 서킷브레이커가
 * OPEN으로 전환되는지, WireMock으로 실제 HTTP 계층(/v1/messages)을 모킹해 검증한다.
 *
 * spring-ai-retry(SpringAiRetryAutoConfiguration)가 resilience4j @Retry와 별개로
 * AnthropicChatModel 호출을 자체적으로 exponential backoff 재시도하므로,
 * resilience-fast 프로파일에서 spring.ai.retry.max-attempts=1로 비활성화해야
 * 이 테스트들이 결정론적으로 빠르게 끝난다 (끄지 않으면 연속 실패 시나리오가
 * spring-ai의 자체 백오프 때문에 테스트당 수 분씩 걸릴 수 있음).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles({"test", "resilience-fast"})
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class ClaudeResilienceWireMockTest {

    private static final WireMockServer wireMock =
            new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());

    static {
        wireMock.start();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.anthropic.base-url", () -> "http://localhost:" + wireMock.port());
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @Autowired
    private LlmFeedbackService llmFeedbackService;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private static final String SUCCESS_BODY = """
            {
              "id": "msg_test123",
              "type": "message",
              "role": "assistant",
              "model": "claude-haiku-4-5-20251001",
              "content": [
                { "type": "text", "text": "{\\"followupQuestion\\": \\"그 상황에서 다른 대안은 없었나요?\\"}" }
              ],
              "stop_reason": "end_turn",
              "stop_sequence": null,
              "usage": { "input_tokens": 50, "output_tokens": 20 }
            }
            """;

    @BeforeEach
    void resetState() {
        wireMock.resetAll();
        circuitBreakerRegistry.circuitBreaker("claude").reset();
    }

    @Test
    @DisplayName("첫 시도 실패 후 재시도로 성공하면 정상 결과를 반환한다")
    void retryThenSucceed() {
        wireMock.stubFor(post(urlPathEqualTo("/v1/messages"))
                .inScenario("claude-retry")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("second-attempt"));

        wireMock.stubFor(post(urlPathEqualTo("/v1/messages"))
                .inScenario("claude-retry")
                .whenScenarioStateIs("second-attempt")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(SUCCESS_BODY)));

        String result = llmFeedbackService.generateFollowupQuestion("답변 내용", "질문 내용");

        assertThat(result).isEqualTo("그 상황에서 다른 대안은 없었나요?");
        wireMock.verify(2, WireMock.postRequestedFor(urlPathEqualTo("/v1/messages")));
    }

    @Test
    @DisplayName("응답이 read-timeout보다 오래 걸리면 재시도 후 SERVICE_UNAVAILABLE 예외가 발생한다")
    void delayTriggersTimeoutThenFallback() {
        wireMock.stubFor(post(urlPathEqualTo("/v1/messages"))
                .willReturn(aResponse().withStatus(200).withFixedDelay(2000)));

        assertThatThrownBy(() -> llmFeedbackService.generateFollowupQuestion("답변 내용", "질문 내용"))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.SERVICE_UNAVAILABLE));

        wireMock.verify(2, WireMock.postRequestedFor(urlPathEqualTo("/v1/messages")));
    }

    @Test
    @DisplayName("연속 실패가 임계값을 넘으면 서킷이 OPEN으로 전환되고 이후 호출은 실제 요청 없이 즉시 실패한다")
    void consecutiveFailuresOpenCircuit() {
        wireMock.stubFor(post(urlPathEqualTo("/v1/messages"))
                .willReturn(aResponse().withStatus(500)));

        CircuitBreaker claude = circuitBreakerRegistry.circuitBreaker("claude");

        int guard = 0;
        while (claude.getState() != CircuitBreaker.State.OPEN && guard < 20) {
            try {
                llmFeedbackService.generateFollowupQuestion("답변 내용", "질문 내용");
            } catch (CustomException ignored) {
                // 서킷 OPEN까지 반복 호출하는 과정에서 예상되는 실패
            }
            guard++;
        }

        assertThat(claude.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        int requestsAtOpen = wireMock.getAllServeEvents().size();

        assertThatThrownBy(() -> llmFeedbackService.generateFollowupQuestion("답변 내용", "질문 내용"))
                .isInstanceOf(CustomException.class);

        assertThat(wireMock.getAllServeEvents().size()).isEqualTo(requestsAtOpen);
    }
}
