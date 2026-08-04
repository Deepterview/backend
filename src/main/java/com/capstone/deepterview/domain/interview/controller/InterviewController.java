package com.capstone.deepterview.domain.interview.controller;

import com.capstone.deepterview.domain.interview.domain.SessionStatus;
import com.capstone.deepterview.domain.interview.dto.request.CreateSessionRequest;
import com.capstone.deepterview.domain.interview.dto.request.NextQuestionRequest;
import com.capstone.deepterview.domain.interview.dto.response.QuestionResponse;
import com.capstone.deepterview.domain.interview.dto.response.CreateSessionResponse;
import com.capstone.deepterview.domain.interview.dto.response.SessionDetailResponse;
import com.capstone.deepterview.domain.interview.dto.response.SessionListResponse;
import com.capstone.deepterview.domain.interview.dto.response.SessionStatusResponse;
import com.capstone.deepterview.domain.interview.service.InterviewService;
import com.capstone.deepterview.domain.member.dto.response.UserPrincipal;
import com.capstone.deepterview.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "면접 세션 컨트롤러")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/sessions")
public class InterviewController {

	private final InterviewService interviewService;

	@PostMapping
	@Operation(
			summary = "세션 생성 API",
			description = "세션을 생성합니다."
	)
	public ApiResponse<CreateSessionResponse> createSession(
			@AuthenticationPrincipal UserPrincipal principal,
			@Valid @RequestBody CreateSessionRequest request
	) {
		return ApiResponse.success(interviewService.createSession(principal.getId(), request));
	}

	@GetMapping
	@Operation(
			summary = "세션 리스트 조회 API",
			description = "전체 세션을 리스트 형태로 조회합니다."
	)
	public ApiResponse<SessionListResponse> getSessions(
			@AuthenticationPrincipal UserPrincipal principal,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size,
			@RequestParam(required = false) SessionStatus status
	) {
		Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
		return ApiResponse.success(interviewService.getSessions(principal.getId(), status, pageable));
	}

	@GetMapping("/{sessionId}")
	@Operation(
			summary = "세션 단건 조회",
			description = "세션에서 답변한 질문들도 모두 함께 조회됩니다."
	)
	public ApiResponse<SessionDetailResponse> getSessionDetail(
			@AuthenticationPrincipal UserPrincipal principal,
			@PathVariable Long sessionId
	) {
		return ApiResponse.success(interviewService.getSessionDetail(principal.getId(), sessionId));
	}

	@PatchMapping("/{sessionId}/start")
	@Operation(
			summary = "세션 시작 API",
			description = "세션 상태를 'IN_PROGRESS'로 변경합니다."
	)
	public ApiResponse<SessionStatusResponse> startSession(
			@AuthenticationPrincipal UserPrincipal principal,
			@PathVariable Long sessionId
	) {
		return ApiResponse.success(interviewService.startSession(principal.getId(), sessionId));
	}

	@PatchMapping("/{sessionId}/end")
	@Operation(
			summary = "세션 종료 API",
			description = "세션 상태를 'COMPLETED'로 변경합니다."
	)
	public ApiResponse<SessionStatusResponse> endSession(
			@AuthenticationPrincipal UserPrincipal principal,
			@PathVariable Long sessionId
	) {
		return ApiResponse.success(interviewService.endSession(principal.getId(), sessionId));
	}

	@DeleteMapping("/{sessionId}")
	@Operation(
			summary = "세션 삭제 API",
			description = "세션을 삭제합니다. Soft Delete 방식을 적용하여 deletedAt 필드를 now로 업데이트합니다."
	)
	public ApiResponse<Void> deleteSession(
			@AuthenticationPrincipal UserPrincipal principal,
			@PathVariable Long sessionId
	) {
		interviewService.deleteSession(principal.getId(), sessionId);
		return ApiResponse.successMessage("세션이 삭제되었습니다.");
	}

	@PostMapping("/{sessionId}/questions/next")
	@Operation(
			summary = "다음 꼬리질문 생성 API",
			description = "이전 답변을 바탕으로 LLM이 꼬리 질문을 생성하고 저장합니다."
	)
	public ApiResponse<QuestionResponse> getNextQuestion(
			@AuthenticationPrincipal UserPrincipal principal,
			@PathVariable Long sessionId,
			@Valid @RequestBody NextQuestionRequest request
	) {
		return ApiResponse.success(interviewService.getNextQuestion(principal.getId(), sessionId, request));
	}
}

