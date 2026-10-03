package com.capstone.deepterview.eval;

import com.capstone.deepterview.global.ai.LlmAnalysisResult;
import com.capstone.deepterview.global.ai.LlmFeedbackService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * STAR 점수 평가 테스트
 *
 * LlmFeedbackQualityEvalTest는 텍스트 피드백만 평가하므로, 사용자에게 노출되는 STAR 점수(0~10)를 별도로 검증한다.
 * 실제 Claude API를 호출하므로 일반 빌드에서는 제외됩니다.
 * 실행: ./gradlew evalTest --tests "*StarScoreEvalTest"
 *
 * 검증 항목
 *   - 순서: 답변 수준에 따라 STAR 총점(4개 항목 평균) 순서가 맞는가
 *   - 일관성: 같은 답변을 반복 호출했을 때 STAR 총점의 표준편차가 기준 이하인가
 */
@Tag("eval")
@SpringBootTest
@ActiveProfiles({"test", "local"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StarScoreEvalTest {

    private static final int RUNS_PER_SCENARIO = 5;
    private static final double MAX_STD_DEV = 1.0;

    @Autowired private LlmFeedbackService llmFeedbackService;

    private final Map<String, List<Double>> totalsByScenario = new LinkedHashMap<>();
    private final Map<String, Integer> parseFailuresByScenario = new LinkedHashMap<>();

    // ── 시나리오 정의 ──────────────────────────────────────────────────────
    // incomplete-answer(개념 질문)는 STAR 적용 대상이 아니므로 제외

    private record Scenario(String id, String question, String answer) {}

    private static final List<Scenario> SCENARIOS = List.of(
            new Scenario("good-star",
                    "어려운 기술적 문제를 해결한 경험을 말씀해주세요.",
                    """
                    이전 직장에서 결제 API 응답 시간이 3초를 넘는 문제가 있었습니다.
                    원인을 분석하니 외부 PG사 호출 시 타임아웃 설정이 없어 대기가 쌓이는 것이었습니다.
                    저는 비동기 처리와 Circuit Breaker 패턴을 도입하고 타임아웃을 500ms로 설정했습니다.
                    배포 후 응답 시간이 평균 0.4초로 개선되었고, 결제 실패율도 12%에서 1% 미만으로 줄었습니다.
                    """),
            new Scenario("excellent-technical",
                    "동시성 문제를 경험한 적이 있나요? 어떻게 해결했나요?",
                    """
                    재고 차감 로직에서 여러 스레드가 동시에 접근해 재고가 음수가 되는 문제를 경험했습니다.
                    처음에는 synchronized를 사용했지만 분산 서버 환경에서는 효과가 없었습니다.
                    Redis의 SETNX 명령어로 분산 락을 구현하고, 락 획득 실패 시 재시도 로직을 추가했습니다.
                    TTL을 설정해 데드락도 방지했고, 이후 재고 오류가 완전히 사라졌습니다.
                    """),
            new Scenario("vague-answer",
                    "본인의 강점과 약점에 대해 말씀해주세요.",
                    "저는 성실하고 책임감이 강합니다. 약점은 가끔 너무 완벽을 추구한다는 점입니다."),
            new Scenario("off-topic",
                    "팀에서 갈등이 발생했을 때 어떻게 해결했는지 경험을 말씀해주세요.",
                    "저는 Java와 Spring Boot를 주력으로 사용하며 백엔드 개발을 하고 있습니다.")
    );

    // ── 시나리오별 반복 호출 ───────────────────────────────────────────────

    @BeforeAll
    void collectScores() {
        for (Scenario scenario : SCENARIOS) {
            List<Double> totals = new ArrayList<>();
            int parseFailures = 0;
            for (int run = 1; run <= RUNS_PER_SCENARIO; run++) {
                LlmAnalysisResult.StarPart star = llmFeedbackService
                        .generateAnalysis(scenario.answer(), scenario.question())
                        .star();

                if (star == null) {
                    parseFailures++;
                    System.out.printf("[%s] %d회차  STAR 파싱 실패 — 통계에서 제외%n", scenario.id(), run);
                    continue;
                }

                double total = totalScore(star);
                totals.add(total);
                System.out.printf("[%s] %d회차  S=%.1f T=%.1f A=%.1f R=%.1f  총점=%.2f%n",
                        scenario.id(), run,
                        star.situationScore(), star.taskScore(), star.actionScore(), star.resultScore(),
                        total);
            }
            totalsByScenario.put(scenario.id(), totals);
            parseFailuresByScenario.put(scenario.id(), parseFailures);
        }

        System.out.printf("%n══ STAR 총점 요약 (시나리오별 %d회) ══%n", RUNS_PER_SCENARIO);
        totalsByScenario.forEach((id, totals) ->
                System.out.printf("  %-20s 평균 %.2f / 표준편차 %.2f / 범위 %.1f~%.1f / 파싱 실패 %d회%n",
                        id, mean(totals), stdDev(totals),
                        totals.stream().mapToDouble(Double::doubleValue).min().orElse(0),
                        totals.stream().mapToDouble(Double::doubleValue).max().orElse(0),
                        parseFailuresByScenario.get(id)));
    }

    @Test
    @DisplayName("시나리오마다 통계를 낼 수 있을 만큼(2회 이상) STAR 점수가 파싱된다")
    void starScore_isParsedEnoughForStats() {
        totalsByScenario.forEach((id, totals) ->
                assertThat(totals)
                        .as("[%s] STAR 파싱 성공 %d회 / 실패 %d회", id, totals.size(), parseFailuresByScenario.get(id))
                        .hasSizeGreaterThanOrEqualTo(2));
    }

    // ── 순서 검증 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("좋은 답변의 STAR 평균 총점이 클리셰 답변보다, 클리셰 답변이 무관한 답변보다 높다")
    void starScore_followsAnswerQuality() {
        double goodStar = mean(totalsByScenario.get("good-star"));
        double excellent = mean(totalsByScenario.get("excellent-technical"));
        double vague = mean(totalsByScenario.get("vague-answer"));
        double offTopic = mean(totalsByScenario.get("off-topic"));

        assertThat(Math.min(goodStar, excellent))
                .as("좋은 답변(good-star=%.2f, excellent-technical=%.2f)이 클리셰 답변(%.2f)보다 높아야 함",
                        goodStar, excellent, vague)
                .isGreaterThan(vague);
        assertThat(vague)
                .as("클리셰 답변(%.2f)이 무관한 답변(%.2f)보다 높아야 함", vague, offTopic)
                .isGreaterThan(offTopic);
    }

    // ── 일관성 검증 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("같은 답변을 반복 호출했을 때 STAR 총점의 표준편차가 기준 이하이다")
    void starScore_isConsistentAcrossRuns() {
        totalsByScenario.forEach((id, totals) ->
                assertThat(stdDev(totals))
                        .as("[%s] STAR 총점 표준편차가 기준(%.1f)을 초과: %s", id, MAX_STD_DEV, totals)
                        .isLessThanOrEqualTo(MAX_STD_DEV));
    }

    // ── 계산 ───────────────────────────────────────────────────────────────
    // 총점은 AnswerService.calculateStarTotalScore와 동일하게 null이 아닌 항목의 평균

    private double totalScore(LlmAnalysisResult.StarPart star) {
        List<Float> scores = new ArrayList<>();
        for (Float s : new Float[]{star.situationScore(), star.taskScore(), star.actionScore(), star.resultScore()}) {
            if (s != null) scores.add(s);
        }
        return scores.stream().mapToDouble(Float::doubleValue).average().orElse(0);
    }

    private double mean(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    private double stdDev(List<Double> values) {
        double m = mean(values);
        return Math.sqrt(values.stream().mapToDouble(v -> (v - m) * (v - m)).average().orElse(0));
    }
}
