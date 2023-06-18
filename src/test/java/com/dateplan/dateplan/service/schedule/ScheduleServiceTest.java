package com.dateplan.dateplan.service.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dateplan.dateplan.domain.member.entity.Member;
import com.dateplan.dateplan.domain.member.repository.MemberRepository;
import com.dateplan.dateplan.domain.schedule.dto.ScheduleServiceRequest;
import com.dateplan.dateplan.domain.schedule.entity.Schedule;
import com.dateplan.dateplan.domain.schedule.entity.SchedulePattern;
import com.dateplan.dateplan.domain.schedule.repository.SchedulePatternRepository;
import com.dateplan.dateplan.domain.schedule.repository.ScheduleRepository;
import com.dateplan.dateplan.domain.schedule.service.ScheduleService;
import com.dateplan.dateplan.global.auth.MemberThreadLocal;
import com.dateplan.dateplan.global.constant.Gender;
import com.dateplan.dateplan.global.constant.Operation;
import com.dateplan.dateplan.global.constant.RepeatRule;
import com.dateplan.dateplan.global.constant.Resource;
import com.dateplan.dateplan.global.exception.ErrorCode.DetailMessage;
import com.dateplan.dateplan.global.exception.auth.NoPermissionException;
import com.dateplan.dateplan.global.util.ScheduleDateUtil;
import com.dateplan.dateplan.service.ServiceTestSupport;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class ScheduleServiceTest extends ServiceTestSupport {

	@Autowired
	private ScheduleService scheduleService;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private SchedulePatternRepository schedulePatternRepository;

	@Autowired
	private ScheduleRepository scheduleRepository;

	@Nested
	@DisplayName("개인 일정을 생성할 때")
	class CreateSchedule {

		Member member;

		@BeforeEach
		void setUp() {
			member = createMember("nickname");
			MemberThreadLocal.set(member);
			memberRepository.save(member);
		}

		@AfterEach
		void tearDown() {
			MemberThreadLocal.remove();
			scheduleRepository.deleteAllInBatch();
			schedulePatternRepository.deleteAllInBatch();
			memberRepository.deleteAllInBatch();
		}

		@DisplayName("올바른 요청을 입력하면 성공한다.")
		@Test
		void successWithValidRequest() {

			// Given
			Long memberId = member.getId();
			ScheduleServiceRequest request = createScheduleServiceRequest();

			// When
			scheduleService.createSchedule(memberId, request);

			// Then
			SchedulePattern schedulePattern = schedulePatternRepository.findById(1L).get();
			List<Schedule> schedules = scheduleRepository.findAll();

			// SchedulePattern assert
			assertThat(schedulePattern.getRepeatStartDate()).isEqualTo(
				request.getStartDateTime().toLocalDate());
			assertThat(schedulePattern.getRepeatEndDate()).isEqualTo(request.getRepeatEndTime());
			assertThat(schedulePattern.getRepeatRule()).isEqualTo(request.getRepeatRule());
			assertThat(schedulePattern.getMember().getId()).isEqualTo(memberId);

			// Schedule assert
			for (int i = 0; i < schedules.size(); i++) {
				Schedule schedule = schedules.get(i);
				assertThat(schedule.getContent()).isEqualTo(request.getContent());
				assertThat(schedule.getLocation()).isEqualTo(request.getLocation());
				assertThat(schedule.getTitle()).isEqualTo(request.getTitle());

				LocalDateTime nextStartDateTime = ScheduleDateUtil.getNextCycle(
					request.getStartDateTime(), request.getRepeatRule(), i);
				LocalDateTime nextEndDateTime = ScheduleDateUtil.getNextCycle(
					request.getEndDateTime(), request.getRepeatRule(), i);
				assertThat(schedule.getStartDateTime()).isEqualToIgnoringSeconds(nextStartDateTime);
				assertThat(schedule.getEndDateTime()).isEqualToIgnoringSeconds(nextEndDateTime);

				long diff = ChronoUnit.SECONDS.between(
					schedule.getStartDateTime(), schedule.getEndDateTime());
				assertThat(diff).isEqualTo(ChronoUnit.SECONDS.between(
					request.getStartDateTime(), request.getEndDateTime()));
			}
		}

		@DisplayName("로그인한 회원 외의 회원에 대한 요청을 하면 실패한다")
		@Test
		void FailWithNoPermissionRequest() {

			// Given
			Long memberId = member.getId();
			Long otherMemberId = memberId + 1;

			// When
			ScheduleServiceRequest request = createScheduleServiceRequest();

			// Then
			assertThatThrownBy(() -> scheduleService.createSchedule(otherMemberId, request))
				.isInstanceOf(NoPermissionException.class)
				.hasMessage(String.format(DetailMessage.NO_PERMISSION, Resource.MEMBER.getName(),
					Operation.CREATE.getName()));
		}
	}

	private Member createMember(String nickname) {

		return Member.builder()
			.name("홍길동")
			.nickname(nickname)
			.phone("01012341234")
			.password("password")
			.gender(Gender.MALE)
			.birthDay(LocalDate.of(1999, 10, 10))
			.build();
	}

	private ScheduleServiceRequest createScheduleServiceRequest() {
		return ScheduleServiceRequest.builder()
			.title("title")
			.startDateTime(LocalDateTime.now())
			.endDateTime(LocalDateTime.now().plusDays(5))
			.repeatRule(RepeatRule.M)
			.build();
	}
}
