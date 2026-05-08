package com.capstone.deepterview.domain.report.controller;

import com.capstone.deepterview.domain.interview.dto.response.SessionReportResponse;
import com.capstone.deepterview.domain.interview.service.InterviewService;
import com.capstone.deepterview.domain.member.dto.response.UserPrincipal;
import com.capstone.deepterview.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "피드백 리포트 컨트롤러")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/sessions")
public class ReportController {

	private final InterviewService interviewService;

	@GetMapping("/{sessionId}/report")
	public ApiResponse<SessionReportResponse> getSessionReport(
			@AuthenticationPrincipal UserPrincipal principal,
			@PathVariable Long sessionId
	) {
		return ApiResponse.success(interviewService.getSessionReport(principal.getId(), sessionId));
	}
}
