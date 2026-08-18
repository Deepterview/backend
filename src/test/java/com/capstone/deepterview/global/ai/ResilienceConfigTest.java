package com.capstone.deepterview.global.ai;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * application.yml의 resilience4j 설정이 실제로 로드되어 claude/tavily 인스턴스가
 * 구성되는지 검증한다 (Claude/Tavily API를 직접 호출하지 않음).
 */
@SpringBootTest
@ActiveProfiles({"test", "local"})
class ResilienceConfigTest {

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private RetryRegistry retryRegistry;

    @Test
    @DisplayName("claude/tavily 서킷브레이커 인스턴스가 설정대로 로드된다")
    void circuitBreakerInstances_areConfigured() {
        var claude = circuitBreakerRegistry.circuitBreaker("claude");
        assertThat(claude.getCircuitBreakerConfig().getSlidingWindowSize()).isEqualTo(10);
        assertThat(claude.getCircuitBreakerConfig().getFailureRateThreshold()).isEqualTo(50f);
        assertThat(claude.getCircuitBreakerConfig().getWaitIntervalFunctionInOpenState().apply(1))
                .isEqualTo(Duration.ofSeconds(30).toMillis());

        var tavily = circuitBreakerRegistry.circuitBreaker("tavily");
        assertThat(tavily.getCircuitBreakerConfig().getWaitIntervalFunctionInOpenState().apply(1))
                .isEqualTo(Duration.ofSeconds(20).toMillis());
    }

    @Test
    @DisplayName("claude/tavily 재시도 인스턴스가 설정대로 로드된다")
    void retryInstances_areConfigured() {
        var claude = retryRegistry.retry("claude");
        assertThat(claude.getRetryConfig().getMaxAttempts()).isEqualTo(2);

        var tavily = retryRegistry.retry("tavily");
        assertThat(tavily.getRetryConfig().getMaxAttempts()).isEqualTo(2);
    }
}
