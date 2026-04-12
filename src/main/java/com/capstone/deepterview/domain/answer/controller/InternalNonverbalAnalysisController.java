package com.capstone.deepterview.domain.answer.controller;

import com.capstone.deepterview.domain.answer.dto.request.NonverbalAnalysisCallbackRequest;
import com.capstone.deepterview.domain.answer.dto.response.NonverbalAnalysisCallbackResponse;
import com.capstone.deepterview.domain.answer.service.NonverbalAnalysisCallbackService;
import com.capstone.deepterview.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/internal")
public class InternalNonverbalAnalysisController {

	private final NonverbalAnalysisCallbackService nonverbalAnalysisCallbackService;

	@PostMapping("/nonverbal-analysis")
	public ApiResponse<NonverbalAnalysisCallbackResponse> receiveNonverbal(
			@Valid @RequestBody NonverbalAnalysisCallbackRequest request
	) {
		return ApiResponse.success(nonverbalAnalysisCallbackService.upsert(request));
	}
}
