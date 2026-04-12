package com.capstone.deepterview.domain.answer.dto.response;

import java.util.List;

public record LlmFeedbackView(
		String strength,
		String weakness,
		String improvement,
		List<String> followupQuestions
) {
}
