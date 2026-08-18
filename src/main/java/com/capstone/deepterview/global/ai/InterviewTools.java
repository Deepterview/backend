package com.capstone.deepterview.global.ai;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class InterviewTools {

    private final String tavilyApiKey;
    private final RestClient restClient;

    public InterviewTools(@Value("${tavily.api-key}") String tavilyApiKey) {
        this.tavilyApiKey = tavilyApiKey;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(2_000);
        requestFactory.setReadTimeout(5_000);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Tool(description = "면접 답변에서 언급된 기술 키워드를 검색해서 관련 개념과 모범 답변 기준을 반환합니다.")
    @CircuitBreaker(name = "tavily", fallbackMethod = "searchTechDocsFallback")
    @Retry(name = "tavily")
    public String searchTechDocs(String keyword) {
        log.info("Tool 호출됨! keyword: {}", keyword);

        Map<String, Object> requestBody = Map.of(
            "api_key", tavilyApiKey,
            "query", keyword + " 기술 면접 개념",
            "search_depth", "basic",
            "max_results", 3,
            "include_answer", true
        );

        log.info("Tavily API 호출 시도");
        TavilyResponse response = restClient.post()
            .uri("https://api.tavily.com/search")
            .contentType(MediaType.APPLICATION_JSON)
            .body(requestBody)
            .retrieve()
            .body(TavilyResponse.class);
        log.info("Tavily API 응답 받기 성공!");

        if (response == null) return "검색 결과를 가져오지 못했습니다.";

        StringBuilder result = new StringBuilder();

        if (response.answer() != null) {
            result.append("[요약]\n").append(response.answer()).append("\n\n");
        }

        if (response.results() != null) {
            result.append("[관련 문서]\n");
            for (TavilyResult item : response.results()) {
                result.append("- ").append(item.title()).append("\n");
                result.append("  ").append(item.content()).append("\n");
            }
        }

        return result.toString();
    }

    // Tavily 서킷 오픈/재시도 소진 시 폴백. 이 메서드는 Claude의 tool-call 루프 안에서
    // 실행되므로 예외를 던지면 전체 응답이 실패한다 — 검색 없이 계속 답변하도록 문자열을 반환한다.
    private String searchTechDocsFallback(String keyword, Throwable t) {
        log.warn("Tavily 검색 실패, 폴백 응답 반환. keyword: {}", keyword, t);
        return "기술 키워드 검색을 사용할 수 없습니다. 일반적인 지식을 바탕으로 답변해주세요.";
    }

    @Tool(description = "면접 답변을 분석해서 면접관이 할 법한 꼬리 질문 3개를 생성합니다.")
    public String getFollowUpQuestions(String answerSummary) {
        log.info("꼬리 질문 생성 Tool 호출됨!");
        return "꼬리 질문은 Claude가 직접 생성합니다.";
    }

    private record TavilyResponse(String answer, List<TavilyResult> results) {}
    private record TavilyResult(String title, String url, String content, Double score) {}
}