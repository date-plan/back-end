package com.dateplan.dateplan.controller.anniversary;

import static com.dateplan.dateplan.global.exception.ErrorCode.DetailMessage.INVALID_ANNIVERSARY_CONTENT;
import static com.dateplan.dateplan.global.exception.ErrorCode.DetailMessage.INVALID_ANNIVERSARY_REPEAT_RULE;
import static com.dateplan.dateplan.global.exception.ErrorCode.DetailMessage.INVALID_ANNIVERSARY_TITLE;
import static com.dateplan.dateplan.global.exception.ErrorCode.DetailMessage.INVALID_CALENDER_TIME_RANGE;
import static com.dateplan.dateplan.global.exception.ErrorCode.DetailMessage.INVALID_DATE_PATTERN;
import static com.dateplan.dateplan.global.exception.ErrorCode.INVALID_INPUT_VALUE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dateplan.dateplan.controller.ControllerTestSupport;
import com.dateplan.dateplan.domain.anniversary.dto.AnniversaryCreateRequest;
import com.dateplan.dateplan.domain.anniversary.dto.AnniversaryCreateServiceRequest;
import com.dateplan.dateplan.domain.anniversary.entity.AnniversaryRepeatRule;
import com.dateplan.dateplan.domain.member.entity.Member;
import com.dateplan.dateplan.global.auth.MemberThreadLocal;
import com.dateplan.dateplan.global.constant.Gender;
import com.dateplan.dateplan.global.constant.Operation;
import com.dateplan.dateplan.global.constant.Resource;
import com.dateplan.dateplan.global.exception.auth.NoPermissionException;
import com.dateplan.dateplan.global.exception.couple.MemberNotConnectedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.springframework.http.MediaType;

public class AnniversaryControllerTest extends ControllerTestSupport {

	@BeforeEach
	void setUp() {
		given(
			authInterceptor.preHandle(any(HttpServletRequest.class), any(HttpServletResponse.class),
				any(Object.class)))
			.willReturn(true);
	}

	@Nested
	@DisplayName("기념일 생성시")
	class GetFirstDate {

		private static final String REQUEST_URL = "/api/couples/{couple_id}/anniversary";

		@BeforeEach
		void setUp() {
			Member member = createMember();
			MemberThreadLocal.set(member);
		}

		@AfterEach
		void tearDown() {
			MemberThreadLocal.remove();
		}

		@DisplayName("현재 로그인한 회원이 요청한 couple id 의 couple 내에 존재한다면 성공한다.")
		@Test
		void withLoginMemberWithExistsInCouple() throws Exception {

			// Given
			Long coupleId = 1L;
			AnniversaryCreateRequest request = AnniversaryCreateRequest.builder()
				.title("title")
				.content("content")
				.date(LocalDate.of(2020, 10, 10))
				.repeatRule(AnniversaryRepeatRule.YEAR)
				.build();

			// Stub
			willDoNothing()
				.given(anniversaryService)
				.createAnniversaries(any(Member.class), anyLong(),
					any(AnniversaryCreateServiceRequest.class));

			// When & Then
			mockMvc.perform(post(REQUEST_URL, coupleId)
					.content(om.writeValueAsString(request))
					.contentType(MediaType.APPLICATION_JSON)
					.characterEncoding(StandardCharsets.UTF_8))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value("true"));
		}

		@DisplayName("제목을 입력하지 않거나 2-15자가 아니라면 에러 코드, 메시지를 응답한다.")
		@NullSource
		@CsvSource({"a", "aaaaaaaaaaaaaaaa"})
		@ParameterizedTest
		void withInvalidTitle(String title) throws Exception {

			// Given
			Long coupleId = 1L;
			AnniversaryCreateRequest request = AnniversaryCreateRequest.builder()
				.title(title)
				.content("content")
				.date(LocalDate.of(2020, 10, 10))
				.repeatRule(AnniversaryRepeatRule.YEAR)
				.build();

			// Stub
			willDoNothing()
				.given(anniversaryService)
				.createAnniversaries(any(Member.class), anyLong(),
					any(AnniversaryCreateServiceRequest.class));

			// When & Then
			mockMvc.perform(post(REQUEST_URL, coupleId)
					.content(om.writeValueAsString(request))
					.contentType(MediaType.APPLICATION_JSON)
					.characterEncoding(StandardCharsets.UTF_8))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value("false"))
				.andExpect(jsonPath("$.code").value(INVALID_INPUT_VALUE.getCode()))
				.andExpect(jsonPath("$.message").value(INVALID_ANNIVERSARY_TITLE));
		}

		@DisplayName("내용이 100자 이상이라면 에러 코드, 메시지를 응답한다.")
		@Test
		void withInvalidContentLength() throws Exception {

			// Given
			Long coupleId = 1L;
			AnniversaryCreateRequest request = AnniversaryCreateRequest.builder()
				.title("title")
				.content("t".repeat(101))
				.date(LocalDate.of(2020, 10, 10))
				.repeatRule(AnniversaryRepeatRule.YEAR)
				.build();

			// Stub
			willDoNothing()
				.given(anniversaryService)
				.createAnniversaries(any(Member.class), anyLong(),
					any(AnniversaryCreateServiceRequest.class));

			// When & Then
			mockMvc.perform(post(REQUEST_URL, coupleId)
					.content(om.writeValueAsString(request))
					.contentType(MediaType.APPLICATION_JSON)
					.characterEncoding(StandardCharsets.UTF_8))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value("false"))
				.andExpect(jsonPath("$.code").value(INVALID_INPUT_VALUE.getCode()))
				.andExpect(jsonPath("$.message").value(INVALID_ANNIVERSARY_CONTENT));
		}

		@DisplayName("반복 패턴이 NONE, YEAR 가 아니라면 에러 코드, 메시지를 응답한다.")
		@NullAndEmptySource
		@CsvSource({"ABC"})
		@ParameterizedTest
		void withInvalidRepeatRule(String repeatRule) throws Exception {

			// Given
			Long coupleId = 1L;
			Map<String, String> request = createAnniversaryCreateRequestMap(repeatRule,
				"2020-10-10");

			// Stub
			willDoNothing()
				.given(anniversaryService)
				.createAnniversaries(any(Member.class), anyLong(),
					any(AnniversaryCreateServiceRequest.class));

			// When & Then
			mockMvc.perform(post(REQUEST_URL, coupleId)
					.content(om.writeValueAsString(request))
					.contentType(MediaType.APPLICATION_JSON)
					.characterEncoding(StandardCharsets.UTF_8))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value("false"))
				.andExpect(jsonPath("$.code").value(INVALID_INPUT_VALUE.getCode()))
				.andExpect(jsonPath("$.message").value(INVALID_ANNIVERSARY_REPEAT_RULE));
		}

		@DisplayName("기념일 날짜가 yyyy-MM-dd 패턴이 아니라면 에러 코드, 메시지를 응답한다.")
		@NullAndEmptySource
		@CsvSource({"20201010", "2020/10/10", "2020.10.10"})
		@ParameterizedTest
		void withInvalidDatePattern(String date) throws Exception {

			// Given
			Long coupleId = 1L;
			Map<String, String> request = createAnniversaryCreateRequestMap("NONE", date);

			// Stub
			willDoNothing()
				.given(anniversaryService)
				.createAnniversaries(any(Member.class), anyLong(),
					any(AnniversaryCreateServiceRequest.class));

			// When & Then
			mockMvc.perform(post(REQUEST_URL, coupleId)
					.content(om.writeValueAsString(request))
					.contentType(MediaType.APPLICATION_JSON)
					.characterEncoding(StandardCharsets.UTF_8))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value("false"))
				.andExpect(jsonPath("$.code").value(INVALID_INPUT_VALUE.getCode()))
				.andExpect(jsonPath("$.message").value(INVALID_DATE_PATTERN));
		}

		@DisplayName("기념일 날짜가 2049-12-31 이후라면 에러 코드, 메시지를 응답한다.")
		@Test
		void withInvalidDateRange() throws Exception {

			// Given
			Long coupleId = 1L;
			Map<String, String> request = createAnniversaryCreateRequestMap("NONE", "2050-01-01");

			// Stub
			willDoNothing()
				.given(anniversaryService)
				.createAnniversaries(any(Member.class), anyLong(),
					any(AnniversaryCreateServiceRequest.class));

			// When & Then
			mockMvc.perform(post(REQUEST_URL, coupleId)
					.content(om.writeValueAsString(request))
					.contentType(MediaType.APPLICATION_JSON)
					.characterEncoding(StandardCharsets.UTF_8))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value("false"))
				.andExpect(jsonPath("$.code").value(INVALID_INPUT_VALUE.getCode()))
				.andExpect(jsonPath("$.message").value(INVALID_CALENDER_TIME_RANGE));
		}

		@DisplayName("현재 로그인한 회원이 요청한 couple id 의 couple 내에 존재하지 않는다면 에러 코드, 메시지를 응답한다.")
		@Test
		void withMemberNotInCouple() throws Exception {

			// Given
			Long coupleId = 1L;
			AnniversaryCreateRequest request = AnniversaryCreateRequest.builder()
				.title("title")
				.content("content")
				.date(LocalDate.of(2020, 10, 10))
				.repeatRule(AnniversaryRepeatRule.YEAR)
				.build();

			// Stub
			NoPermissionException expectedException = new NoPermissionException(Resource.COUPLE,
				Operation.CREATE);
			willThrow(expectedException)
				.given(anniversaryService)
				.createAnniversaries(any(Member.class), anyLong(),
					any(AnniversaryCreateServiceRequest.class));

			// When & Then
			mockMvc.perform(post(REQUEST_URL, coupleId)
					.content(om.writeValueAsString(request))
					.contentType(MediaType.APPLICATION_JSON)
					.characterEncoding(StandardCharsets.UTF_8))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.success").value("false"))
				.andExpect(jsonPath("$.code").value(expectedException.getErrorCode().getCode()))
				.andExpect(jsonPath("$.message").value(expectedException.getMessage()));
		}

		@DisplayName("연결되지 않은 회원의 요청이라면 에러 코드, 메시지를 응답한다.")
		@Test
		void withNotConnectedMember() throws Exception {

			// Given
			Long coupleId = 1L;
			AnniversaryCreateRequest request = AnniversaryCreateRequest.builder()
				.title("title")
				.content("content")
				.date(LocalDate.of(2020, 10, 10))
				.repeatRule(AnniversaryRepeatRule.YEAR)
				.build();

			// Stub
			MemberNotConnectedException expectedException = new MemberNotConnectedException();
			willThrow(expectedException)
				.given(anniversaryService)
				.createAnniversaries(any(Member.class), anyLong(),
					any(AnniversaryCreateServiceRequest.class));

			// When & Then
			mockMvc.perform(post(REQUEST_URL, coupleId)
					.content(om.writeValueAsString(request))
					.contentType(MediaType.APPLICATION_JSON)
					.characterEncoding(StandardCharsets.UTF_8))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value("false"))
				.andExpect(jsonPath("$.code").value(expectedException.getErrorCode().getCode()))
				.andExpect(jsonPath("$.message").value(expectedException.getMessage()));
		}
	}

	private Member createMember() {

		return Member.builder()
			.name("홍길동")
			.nickname("nickname")
			.phone("01012341234")
			.password("password")
			.gender(Gender.MALE)
			.birthDay(LocalDate.of(1999, 10, 10))
			.build();
	}

	private Map<String, String> createAnniversaryCreateRequestMap(String repeatRule, String date) {

		HashMap<String, String> requestMap = new HashMap<>();

		requestMap.put("title", "title");
		requestMap.put("content", "content");
		requestMap.put("repeatRule", repeatRule);
		requestMap.put("date", date);

		return requestMap;
	}
}