package com.capstone.deepterview.domain.member.controller;

import com.capstone.deepterview.domain.member.dto.request.TestLoginRequest;
import com.capstone.deepterview.domain.member.dto.response.TokenResponse;
import com.capstone.deepterview.domain.member.service.AuthService;
import com.capstone.deepterview.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("local")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class LocalTestAuthController {

	private final AuthService authService;

	@PostMapping("/test-login")
	public ApiResponse<TokenResponse> testLogin(@Valid @RequestBody TestLoginRequest request) {
		return ApiResponse.success(authService.testLogin(request.email()));
	}
}

