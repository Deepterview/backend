package com.capstone.deepterview.domain.member.controller;

import com.capstone.deepterview.domain.member.dto.response.MeResponse;
import com.capstone.deepterview.domain.member.dto.response.UserPrincipal;
import com.capstone.deepterview.domain.member.service.MemberService;
import com.capstone.deepterview.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {

	private final MemberService memberService;

	@GetMapping("/me")
	public ApiResponse<MeResponse> me(@AuthenticationPrincipal UserPrincipal principal) {
		return ApiResponse.success(memberService.getMe(principal.getId()));
	}

	@DeleteMapping("/me")
	public ApiResponse<Void> withdraw(@AuthenticationPrincipal UserPrincipal principal) {
		memberService.withdraw(principal.getId());
		return ApiResponse.successMessage("회원 탈퇴가 완료되었습니다.");
	}
}

