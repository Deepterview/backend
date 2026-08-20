package com.capstone.deepterview.global.ai;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
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
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tavily 응답 지연/연속 실패 상황에서 실제로 재시도되고 서킷브레이커가 OPEN으로
 * 전환되는지, WireMock으로 실제 HTTP 계층을 모킹해 검증한다.
 *
 * fallbackMethod는 @CircuitBreaker가 아니라 @Retry에 붙어 있다 (InterviewTools):
 * CircuitBreaker(내부)는 실패를 기록만 하고 예외를 그대로 통과시키고 Retry(외부)가 그 예외를
 * 보고 실제로 재시도한 뒤, 재시도를 다 소진했을 때(또는 서킷이 열려 즉시 거절될 때)만 폴백
 * 문자열을 반환한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles({"test", "resilience-fast"})
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class TavilyResilienceWireMockTest {

    private static final WireMockServer wireMock =
            new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());

    static {
        wireMock.start();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("tavily.base-url", () -> "http://localhost:" + wireMock.port());
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @Autowired
    private InterviewTools interviewTools;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private static final String SUCCESS_BODY = """
            {
              "answer": "테스트 요약",
              "results": [
                {"title": "테스트 문서", "url": "https://example.com", "content": "내용", "score": 0.9}
              ]
            }
            """;

    private static final String FALLBACK_MESSAGE =
            "기술 키워드 검색을 사용할 수 없습니다. 일반적인 지식을 바탕으로 답변해주세요.";

    @BeforeEach
    void resetState() {
        wireMock.resetAll();
        circuitBreakerRegistry.circuitBreaker("tavily").reset();
    }

    @Test
    @DisplayName("첫 시도 실패 후 재시도로 성공하면 정상 결과를 반환한다")
    void retryThenSucceed() {
        wireMock.stubFor(post(urlEqualTo("/search"))
                .inScenario("tavily-retry")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("second-attempt"));

        wireMock.stubFor(post(urlEqualTo("/search"))
                .inScenario("tavily-retry")
                .whenScenarioStateIs("second-attempt")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(SUCCESS_BODY)));

        String result = interviewTools.searchTechDocs("스프링");

        assertThat(result).doesNotContain(FALLBACK_MESSAGE);
        assertThat(result).contains("테스트 요약");
        wireMock.verify(2, WireMock.postRequestedFor(urlEqualTo("/search")));
    }

    @Test
    @DisplayName("응답이 read-timeout보다 오래 걸리면 재시도 후 폴백이 반환된다")
    void delayTriggersTimeoutThenFallback() {
        wireMock.stubFor(post(urlEqualTo("/search"))
                .willReturn(aResponse().withStatus(200).withFixedDelay(2000)));

        String result = interviewTools.searchTechDocs("스프링");

        assertThat(result).isEqualTo(FALLBACK_MESSAGE);
        wireMock.verify(2, WireMock.postRequestedFor(urlEqualTo("/search")));
    }

    @Test
    @DisplayName("연속 실패가 임계값을 넘으면 서킷이 OPEN으로 전환되고 이후 호출은 실제 요청 없이 폴백된다")
    void consecutiveFailuresOpenCircuit() {
        wireMock.stubFor(post(urlEqualTo("/search"))
                .willReturn(aResponse().withStatus(500)));

        CircuitBreaker tavily = circuitBreakerRegistry.circuitBreaker("tavily");

        int guard = 0;
        while (tavily.getState() != CircuitBreaker.State.OPEN && guard < 20) {
            interviewTools.searchTechDocs("스프링");
            guard++;
        }

        assertThat(tavily.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        int requestsAtOpen = wireMock.getAllServeEvents().size();

        String resultAfterOpen = interviewTools.searchTechDocs("스프링");

        assertThat(resultAfterOpen).isEqualTo(FALLBACK_MESSAGE);
        assertThat(wireMock.getAllServeEvents().size()).isEqualTo(requestsAtOpen);
    }
}
