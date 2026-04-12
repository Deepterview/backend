package com.capstone.deepterview.domain.answer.controller;

import com.capstone.deepterview.domain.answer.domain.CompletionStatus;
import com.capstone.deepterview.domain.answer.dto.response.AnswerAnalysisResponse;
import com.capstone.deepterview.domain.answer.dto.response.SubmitAnswerResponse;
import com.capstone.deepterview.domain.answer.service.AnswerService;
import com.capstone.deepterview.domain.member.dto.response.UserPrincipal;
import com.capstone.deepterview.global.common.ApiResponse;
import com.capstone.deepterview.global.exception.CustomException;
import com.capstone.deepterview.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/answers")
public class AnswerController {

	private final AnswerService answerService;

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ApiResponse<SubmitAnswerResponse> submitAnswer(
			@AuthenticationPrincipal UserPrincipal principal,
			@RequestParam Long questionId,
			@RequestParam MultipartFile audio,
			@RequestParam int durationSec,
			@RequestParam String completionStatus
	) {
		CompletionStatus status;
		try {
			status = CompletionStatus.valueOf(completionStatus);
		} catch (IllegalArgumentException ex) {
			throw new CustomException(ErrorCode.VALIDATION_ERROR, "유효하지 않은 completionStatus 입니다.");
		}
		return ApiResponse.success(answerService.submitAnswer(
				principal.getId(),
				questionId,
				audio,
				durationSec,
				status
		));
	}

	@GetMapping("/{answerId}/analysis")
	public ApiResponse<AnswerAnalysisResponse> getAnalysis(
			@AuthenticationPrincipal UserPrincipal principal,
			@PathVariable Long answerId
	) {
		return ApiResponse.success(answerService.getAnalysis(principal.getId(), answerId));
	}
}
