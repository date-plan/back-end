package com.dateplan.dateplan.domain.member.controller;

import com.dateplan.dateplan.domain.couple.service.CoupleService;
import com.dateplan.dateplan.domain.member.dto.ConnectionRequest;
import com.dateplan.dateplan.domain.member.dto.ConnectionResponse;
import com.dateplan.dateplan.domain.member.dto.ConnectionServiceResponse;
import com.dateplan.dateplan.domain.member.dto.PresignedURLResponse;
import com.dateplan.dateplan.domain.member.service.MemberService;
import com.dateplan.dateplan.domain.s3.S3ImageType;
import com.dateplan.dateplan.global.dto.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/members")
public class MemberController {

	private final CoupleService coupleService;
	private final MemberService memberService;

	@GetMapping("/profile/image/presigned-url")
	public ApiResponse<PresignedURLResponse> getPresignedURL() {

		PresignedURLResponse presingedURL = memberService.getPresignedURLForProfileImage();

		return ApiResponse.ofSuccess(presingedURL);
	}

	@PutMapping("/profile/image")
	public ApiResponse<Void> modifyProfileImage() {

		memberService.checkAndSaveProfileImage();

		return ApiResponse.ofSuccess();
	}

	@DeleteMapping("/profile/image")
	public ApiResponse<Void> deleteProfileImage() {

		memberService.deleteProfileImage();

		return ApiResponse.ofSuccess();
	}

	@GetMapping("/connect")
	public ApiResponse<ConnectionResponse> getConnectionCode() {
		ConnectionServiceResponse connectionServiceResponse = coupleService.getConnectionCode();
		return ApiResponse.ofSuccess(connectionServiceResponse.toConnectionResponse());
	}

	@PostMapping("/connect")
	public ApiResponse<Void> connectCouple(@Valid @RequestBody ConnectionRequest request) {
		coupleService.connectCouple(request.toConnectionServiceRequest());
		return ApiResponse.ofSuccess();
	}
}
