package com.capstone.deepterview.domain.member.controller;

import com.capstone.deepterview.domain.member.dto.request.LogoutRequest;
import com.capstone.deepterview.domain.member.dto.request.TokenReissueRequest;
import com.capstone.deepterview.domain.member.dto.response.TokenResponse;
import com.capstone.deepterview.domain.member.service.AuthService;
import com.capstone.deepterview.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final AuthService authService;

	@PostMapping("/reissue")
	public ApiResponse<TokenResponse> reissue(@Valid @RequestBody TokenReissueRequest request) {
		return ApiResponse.success(authService.reissue(request.refreshToken()));
	}

	@PostMapping("/logout")
	public ApiResponse<Void> logout(@Valid @RequestBody LogoutRequest request) {
		authService.logout(request.refreshToken());
		return ApiResponse.successMessage("로그아웃 되었습니다.");
	}
}

