package com.capstone.deepterview.global.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record LlmAnalysisResult(
        FeedbackPart feedback,
        StarPart star
) {
    public record FeedbackPart(
            String strength,
            String weakness,
            String improvement,
            List<String> followupQuestions
    ) {}

    public record StarPart(
            Float situationScore,
            Float taskScore,
            Float actionScore,
            Float resultScore,
            String situationFeedback,
            String taskFeedback,
            @JsonProperty("actionDetail") String actionFeedback,
            String resultFeedback
    ) {}
}
