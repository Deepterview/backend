package com.capstone.deepterview.domain.portfolio.dto.response;

import java.util.List;

public record PortfolioQuestionsResponse(
        Long portfolioId,
        List<String> questions
) {
}
