package com.capstone.deepterview.domain.interview.controller;

import com.capstone.deepterview.domain.interview.dto.response.JobCategoryResponse;
import com.capstone.deepterview.domain.interview.service.InterviewService;
import com.capstone.deepterview.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "직무 카테고리 컨트롤러")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/job-categories")
public class JobCategoryController {

	private final InterviewService interviewService;

	@GetMapping
	public ApiResponse<List<JobCategoryResponse>> getJobCategories() {
		return ApiResponse.success(interviewService.getJobCategories());
	}
}

