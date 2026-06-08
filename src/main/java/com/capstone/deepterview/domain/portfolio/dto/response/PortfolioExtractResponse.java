package com.capstone.deepterview.domain.portfolio.dto.response;

public record PortfolioExtractResponse(
        Long portfolioId,
        String extractedText,
        boolean isScanned
) {
}
