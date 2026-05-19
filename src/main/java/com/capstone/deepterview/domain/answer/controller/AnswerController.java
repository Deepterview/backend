package com.capstone.deepterview.domain.answer.controller;

import com.capstone.deepterview.domain.answer.dto.request.SubmitAnswerRequest;
import com.capstone.deepterview.domain.answer.dto.response.AnswerAnalysisResponse;
import com.capstone.deepterview.domain.answer.dto.response.SubmitAnswerResponse;
import com.capstone.deepterview.domain.answer.service.AnswerService;
import com.capstone.deepterview.domain.member.dto.response.UserPrincipal;
import com.capstone.deepterview.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "면접 답변 컨트롤러")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/answers")
public class AnswerController {

	private final AnswerService answerService;

	@PostMapping
	@Operation(
			summary = "면접 답변 제출 API",
			description = "면접 답변을 텍스트로 제출합니다."
	)
	public ApiResponse<SubmitAnswerResponse> submitAnswer(
			@AuthenticationPrincipal UserPrincipal principal,
			@Valid @RequestBody SubmitAnswerRequest request
	) {
		return ApiResponse.success(answerService.submitAnswer(principal.getId(), request));
	}

	@PostMapping(value = "/{answerId}/video", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@Operation(
			summary = "영상 파일 백그라운드로 업로드 API",
			description = "면접 답변 제출 API를 호출한 뒤에 백그라운드로 호출되는 API"
	)
	public ApiResponse<Void> uploadVideo(
			@AuthenticationPrincipal UserPrincipal principal,
			@PathVariable Long answerId,
			@RequestParam MultipartFile video
	) {
		answerService.uploadVideo(principal.getId(), answerId, video);
		return ApiResponse.successMessage("영상이 업로드 되었습니다.");
	}

	@GetMapping("/{answerId}/analysis")
	public ApiResponse<AnswerAnalysisResponse> getAnalysis(
			@AuthenticationPrincipal UserPrincipal principal,
			@PathVariable Long answerId
	) {
		return ApiResponse.success(answerService.getAnalysis(principal.getId(), answerId));
	}
}
