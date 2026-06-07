package com.capstone.deepterview.domain.portfolio.controller;

import com.capstone.deepterview.domain.member.dto.response.UserPrincipal;
import com.capstone.deepterview.domain.portfolio.dto.response.PortfolioExtractResponse;
import com.capstone.deepterview.domain.portfolio.dto.response.PortfolioUploadResponse;
import com.capstone.deepterview.domain.portfolio.service.PortfolioService;
import com.capstone.deepterview.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "포트폴리오 컨트롤러")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/portfolios")
public class PortfolioController {

    private final PortfolioService portfolioService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "포트폴리오 PDF 업로드 API",
            description = "포트폴리오 PDF 파일을 업로드하여 저장합니다."
    )
    public ApiResponse<PortfolioUploadResponse> uploadPortfolio(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam MultipartFile file
    ) {
        return ApiResponse.success(portfolioService.uploadPortfolio(principal.getId(), file));
    }

    @PostMapping("/{portfolioId}/extract")
    @Operation(
            summary = "포트폴리오 텍스트 추출 API",
            description = "업로드된 포트폴리오 PDF에서 텍스트를 추출하여 저장하고 반환합니다. 스캔본 PDF인 경우 텍스트 추출이 불가능합니다."
    )
    public ApiResponse<PortfolioExtractResponse> extractPortfolio(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long portfolioId
    ) {
        return ApiResponse.success(portfolioService.extractPortfolio(principal.getId(), portfolioId));
    }
}
