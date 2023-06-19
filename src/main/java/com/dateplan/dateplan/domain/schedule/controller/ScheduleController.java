package com.dateplan.dateplan.domain.schedule.controller;

import com.dateplan.dateplan.domain.schedule.dto.ScheduleRequest;
import com.dateplan.dateplan.domain.schedule.dto.ScheduleResponse;
import com.dateplan.dateplan.domain.schedule.dto.ScheduleServiceResponse;
import com.dateplan.dateplan.domain.schedule.service.ScheduleService;
import com.dateplan.dateplan.global.dto.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/schedules")
public class ScheduleController {

	private final ScheduleService scheduleService;

	@PostMapping("/{member_id}")
	public ApiResponse<Void> createSchedule(@PathVariable("member_id") Long memberId,
		@Valid @RequestBody ScheduleRequest request) {
		scheduleService.createSchedule(memberId, request.toScheduleServiceRequest());
		return ApiResponse.ofSuccess();
	}

	@GetMapping("/{member_id}")
	public ApiResponse<ScheduleResponse> readSchedule(
		@PathVariable("member_id") Long memberId,
		@RequestParam("year") Integer year,
		@RequestParam("month") Integer month
	) {
		ScheduleServiceResponse scheduleServiceResponse = scheduleService.readSchedule(memberId,
			year, month);

		return ApiResponse.ofSuccess(scheduleServiceResponse.toScheduleResponse());
	}
}
