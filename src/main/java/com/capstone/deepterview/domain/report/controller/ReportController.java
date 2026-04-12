package com.capstone.deepterview.domain.report.controller;

import com.capstone.deepterview.domain.interview.dto.response.SessionReportResponse;
import com.capstone.deepterview.domain.interview.service.InterviewService;
import com.capstone.deepterview.domain.member.dto.response.UserPrincipal;
import com.capstone.deepterview.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
