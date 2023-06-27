package com.dateplan.dateplan.service.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dateplan.dateplan.domain.member.entity.Member;
import com.dateplan.dateplan.domain.member.repository.MemberRepository;
import com.dateplan.dateplan.domain.schedule.dto.ScheduleServiceRequest;
import com.dateplan.dateplan.domain.schedule.dto.ScheduleUpdateServiceRequest;
import com.dateplan.dateplan.domain.schedule.entity.Schedule;
import com.dateplan.dateplan.domain.schedule.entity.SchedulePattern;
import com.dateplan.dateplan.domain.schedule.repository.SchedulePatternRepository;
import com.dateplan.dateplan.domain.schedule.repository.ScheduleRepository;
import com.dateplan.dateplan.domain.schedule.service.ScheduleService;
import com.dateplan.dateplan.global.auth.MemberThreadLocal;
import com.dateplan.dateplan.global.constant.DateConstants;
import com.dateplan.dateplan.global.constant.Gender;
import com.dateplan.dateplan.global.constant.Operation;
import com.dateplan.dateplan.global.constant.RepeatRule;
import com.dateplan.dateplan.global.constant.Resource;
import com.dateplan.dateplan.global.exception.ErrorCode.DetailMessage;
import com.dateplan.dateplan.global.exception.auth.NoPermissionException;
import com.dateplan.dateplan.global.exception.schedule.ScheduleNotFoundException;
import com.dateplan.dateplan.global.util.ScheduleDateUtil;
import com.dateplan.dateplan.service.ServiceTestSupport;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
		@ParameterizedTest
		@EnumSource(value = RepeatRule.class, names = {"N", "D", "W", "M", "Y"})
		void successWithValidRequest(RepeatRule repeatRule) {

			// Given
			Long memberId = member.getId();
			ScheduleServiceRequest request = createScheduleServiceRequest(repeatRule);

			// When
			scheduleService.createSchedule(memberId, request);

			// Then
			SchedulePattern schedulePattern = schedulePatternRepository.findAll().get(0);
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

		@DisplayName("현재 로그인한 회원의 id와 요청의 member_id가 다르면 실패한다.")
		@ParameterizedTest
		@EnumSource(value = RepeatRule.class, names = {"N", "D", "W", "M", "Y"})
		void FailWithNoPermissionRequest(RepeatRule repeatRule) {

			// Given
			Long memberId = member.getId();
			Long otherMemberId = memberId + 1;

			// When
			ScheduleServiceRequest request = createScheduleServiceRequest(repeatRule);

			// Then
			NoPermissionException exception = new NoPermissionException(Resource.MEMBER,
				Operation.CREATE);
			assertThatThrownBy(() -> scheduleService.createSchedule(otherMemberId, request))
				.isInstanceOf(exception.getClass())
				.hasMessage(exception.getMessage());
		}
	}

	@DisplayName("일정 수정 시")
	@Nested
	class UpdateSchedule {

		Member member;
		Schedule schedule;

		@BeforeEach
		void setUp() {
			member = memberRepository.save(createMember("nickname"));
			MemberThreadLocal.set(member);

			SchedulePattern schedulePattern = schedulePatternRepository.save(
				SchedulePattern.builder()
					.member(member)
					.repeatRule(RepeatRule.N)
					.repeatStartDate(LocalDate.now())
					.repeatEndDate(DateConstants.CALENDER_END_DATE)
					.build()
			);
			schedule = scheduleRepository.save(createSchedule(schedulePattern));
		}

		@AfterEach
		void tearDown() {
			MemberThreadLocal.remove();
			scheduleRepository.deleteAllInBatch();
			schedulePatternRepository.deleteAllInBatch();
			memberRepository.deleteAllInBatch();
		}

		@DisplayName("요청 시, 요청의 내용으로 일정이 수정된다.")
		@Test
		void successWithValidRequest() {
			// Given
			ScheduleUpdateServiceRequest request = createScheduleUpdateServiceRequest();

			// When
			scheduleService.updateSchedule(member.getId(), schedule.getId(), request, member);

			// Then
			Schedule updatedSchedule = scheduleRepository.findById(schedule.getId()).get();

			assertThat(updatedSchedule.getTitle()).isEqualTo(request.getTitle());
			assertThat(updatedSchedule.getContent()).isEqualTo(request.getContent());
			assertThat(updatedSchedule.getLocation()).isEqualTo(request.getLocation());
			assertThat(updatedSchedule.getStartDateTime()).isEqualTo(request.getStartDateTime());
			assertThat(updatedSchedule.getEndDateTime()).isEqualTo(request.getEndDateTime());
		}

		@DisplayName("현재 로그인한 회원의 id와 요청의 member_id가 다르면 실패한다.")
		@Test
		void failWithNoPermission() {

			// Given
			ScheduleUpdateServiceRequest request = createScheduleUpdateServiceRequest();

			// When & Then
			NoPermissionException exception = new NoPermissionException(Resource.MEMBER,
				Operation.UPDATE);
			assertThatThrownBy(() ->
				scheduleService.updateSchedule(member.getId() + 100, schedule.getId(), request,
					member))
				.isInstanceOf(exception.getClass())
				.hasMessage(exception.getMessage());

		}

		@DisplayName("요청에 해당하는 일정을 찾을 수 없으면 실패한다")
		@Test
		void failWIthScheduleNotFound() {
			// Given
			ScheduleUpdateServiceRequest request = createScheduleUpdateServiceRequest();

			// When & Then
			ScheduleNotFoundException exception = new ScheduleNotFoundException();
			assertThatThrownBy(() ->
				scheduleService.updateSchedule(
					member.getId(), schedule.getId() + 100, request, member))
				.isInstanceOf(exception.getClass())
				.hasMessage(exception.getMessage());

		}
	}

	private Schedule createSchedule(SchedulePattern schedulePattern) {
		return Schedule.builder()
			.schedulePattern(schedulePattern)
			.title("title")
			.content("content")
			.location("location")
			.startDateTime(LocalDateTime.now())
			.endDateTime(LocalDateTime.now().plusDays(5))
			.build();
	}

	private ScheduleUpdateServiceRequest createScheduleUpdateServiceRequest() {
		return ScheduleUpdateServiceRequest.builder()
			.title("new Title")
			.content("new Content")
			.location("new Location")
			.startDateTime(LocalDateTime.now().plusDays(10).truncatedTo(ChronoUnit.SECONDS))
			.endDateTime(LocalDateTime.now().plusDays(20).truncatedTo(ChronoUnit.SECONDS))
			.build();
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

	private ScheduleServiceRequest createScheduleServiceRequest(RepeatRule repeatRule) {
		return ScheduleServiceRequest.builder()
			.title("title")
			.startDateTime(LocalDateTime.now().with(LocalTime.MIN))
			.endDateTime(LocalDateTime.now().with(LocalTime.MAX))
			.repeatRule(repeatRule)
			.build();
	}
}
