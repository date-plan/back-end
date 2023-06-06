package com.dateplan.dateplan.service.couple;

import static com.dateplan.dateplan.global.exception.ErrorCode.DetailMessage.ALREADY_CONNECTED;
import static com.dateplan.dateplan.global.exception.ErrorCode.DetailMessage.INVALID_CONNECTION_CODE;
import static com.dateplan.dateplan.global.exception.ErrorCode.DetailMessage.SELF_CONNECTION_NOT_ALLOWED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.dateplan.dateplan.domain.couple.entity.Couple;
import com.dateplan.dateplan.domain.couple.repository.CoupleRepository;
import com.dateplan.dateplan.domain.couple.service.CoupleService;
import com.dateplan.dateplan.domain.member.dto.ConnectionServiceRequest;
import com.dateplan.dateplan.domain.member.dto.ConnectionServiceResponse;
import com.dateplan.dateplan.domain.member.entity.Member;
import com.dateplan.dateplan.domain.member.repository.MemberRepository;
import com.dateplan.dateplan.domain.member.service.MemberReadService;
import com.dateplan.dateplan.global.auth.MemberThreadLocal;
import com.dateplan.dateplan.global.constant.Gender;
import com.dateplan.dateplan.global.exception.member.AlreadyConnectedException;
import com.dateplan.dateplan.global.exception.member.InvalidConnectionCodeException;
import com.dateplan.dateplan.global.exception.member.SelfConnectionNotAllowedException;
import com.dateplan.dateplan.global.util.RandomCodeGenerator;
import com.dateplan.dateplan.service.ServiceTestSupport;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

public class CoupleServiceTest extends ServiceTestSupport {

	@Autowired
	private CoupleService coupleService;

	@SpyBean
	private StringRedisTemplate redisTemplate;

	@SpyBean
	private CoupleRepository coupleRepository;

	@Autowired
	private MemberRepository memberRepository;

	@MockBean
	private MemberReadService memberReadService;

	@DisplayName("연결 코드 조회 시")
	@Nested
	class GetConnectionCode {

		Member member;

		@BeforeEach
		void setUp() {
			member = memberRepository.save(createMember("01012345678"));
			MemberThreadLocal.set(member);
		}

		@AfterEach
		void tearDown() {
			redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
			coupleRepository.deleteAllInBatch();
			memberRepository.deleteAllInBatch();
			MemberThreadLocal.remove();
		}

		@DisplayName("24시간 내에 생성된 코드가 없다면 새로 생성한 코드를 반환하고, redis에 저장된다")
		@Test
		void returnNewConnectionCode() {

			// Given
			String connectionCode = "ABC123";
			ConnectionServiceResponse response;

			try (MockedStatic<RandomCodeGenerator> generator = mockStatic(
				RandomCodeGenerator.class)) {

				// Stub
				given(RandomCodeGenerator.generateConnectionCode(6)).willReturn(connectionCode);

				// When
				response = coupleService.getConnectionCode();

				// Verify
				generator.verify(() -> RandomCodeGenerator.generateConnectionCode(anyInt()),
					times(1));
			}

			ValueOperations<String, String> opsForValue = redisTemplate.opsForValue();

			String savedCode = opsForValue.get(getConnectionKey(member.getId()));
			String savedId = opsForValue.get(connectionCode);

			// Then
			assertThat(savedCode).isEqualTo(connectionCode);
			assertThat(savedId).isEqualTo(String.valueOf(member.getId()));
			assertThat(response.getConnectionCode()).isEqualTo(connectionCode);
		}

		@DisplayName("24시간 내에 생성된 코드가 있다면, 이미 생성된 코드를 반환한다")
		@Test
		void returnPreCreatedConnectionCode() {

			// Given
			String connectionCode = "ABC123";
			ValueOperations<String, String> valueOperations = redisTemplate.opsForValue();
			String key = getConnectionKey(member.getId());
			valueOperations.set(key, connectionCode);
			ConnectionServiceResponse savedConnectionCode;

			try (MockedStatic<RandomCodeGenerator> generator = mockStatic(
				RandomCodeGenerator.class)) {

				// Stubbing
				generator.when(
						() -> RandomCodeGenerator.generateConnectionCode(anyInt()))
					.thenAnswer(invocation -> null);

				// When
				savedConnectionCode = coupleService.getConnectionCode();

				// Verify
				generator.verify(
					() -> RandomCodeGenerator.generateConnectionCode(anyInt()), never());
			}

			// Then
			assertThat(savedConnectionCode.getConnectionCode()).isEqualTo(connectionCode);
		}

		@DisplayName("이미 코드가 존재한다면, 반복된 코드가 나오지 않을때까지 생성한다.")
		@Test
		void returnNotDuplicatedConnectionCode() {

			// Given
			ValueOperations<String, String> valueOperations = redisTemplate.opsForValue();

			String savedConnectionCode = "ABC123";
			String newConnectionCode = "123ABC";
			valueOperations.set(savedConnectionCode, "someId");

			ConnectionServiceResponse response;

			try (MockedStatic<RandomCodeGenerator> generator = mockStatic(
				RandomCodeGenerator.class)) {

				// Stubbing
				given(RandomCodeGenerator.generateConnectionCode(6))
					.willReturn(savedConnectionCode)
					.willReturn(savedConnectionCode)
					.willReturn(newConnectionCode);

				// When
				response = coupleService.getConnectionCode();

				// Verify
				generator.verify(
					() -> RandomCodeGenerator.generateConnectionCode(anyInt()), times(3));
			}

			// Then
			assertThat(response.getConnectionCode()).isEqualTo(newConnectionCode);
		}
	}

	@Nested
	@DisplayName("회원 연결 시")
	class ConnectCouple {

		Member member;
		Member oppositeMember;

		@BeforeEach
		void setUp() {
			member = memberRepository.save(createMember("01012345678"));
			oppositeMember = memberRepository.save(createMember("01012345679"));
			MemberThreadLocal.set(member);
		}

		@AfterEach
		void tearDown() {
			redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
			coupleRepository.deleteAllInBatch();
			memberRepository.deleteAllInBatch();
			MemberThreadLocal.remove();
		}

		@DisplayName("올바른 요청 코드를 입력하면 상대방과 연결된다")
		@Test
		void connectCoupleWithValidRequest() {

			// Given
			String connectionCode = "ABC123";

			ValueOperations<String, String> opsForValue = redisTemplate.opsForValue();
			opsForValue.set(getConnectionKey(oppositeMember.getId()), connectionCode);
			opsForValue.set(connectionCode, String.valueOf(oppositeMember.getId()));

			ConnectionServiceRequest request = createConnectionServiceRequest(connectionCode);

			// Stubbing
			given(memberReadService.findMemberByIdOrElseThrow(anyLong())).willReturn(
				oppositeMember);
			given(coupleRepository.findById(anyLong())).willReturn(
				Optional.ofNullable(createCouple(member, oppositeMember)));

			// When
			coupleService.connectCouple(request);

			// Then
			Couple couple = coupleRepository.findById(1L).orElse(null);
			assertThat(couple).isNotNull();
			assertThat(couple.getFirstDate()).isEqualTo(request.getFirstDate());
			assertThat(couple.getMember1().getId()).isIn(member.getId(), oppositeMember.getId());
			assertThat(couple.getMember2().getId()).isIn(member.getId(), oppositeMember.getId());
		}

		@DisplayName("존재하지 않는 코드를 입력하면 실패한다")
		@Test
		void failWithInvalidRequest() {

			// Given
			String connectionCode = "ABC123";
			ConnectionServiceRequest request = createConnectionServiceRequest(connectionCode);

			// Stubbing
			given(memberReadService.findMemberByIdOrElseThrow(anyLong()))
				.willReturn(oppositeMember);

			// When & Then
			assertThatThrownBy(() -> coupleService.connectCouple(request))
				.isInstanceOf(InvalidConnectionCodeException.class)
				.hasMessage(INVALID_CONNECTION_CODE);
		}

		@DisplayName("상대방이 이미 연결된 경우라면 실패한다")
		@Test
		void failWithAlreadyConnected() {

			// Given
			coupleRepository.save(createCouple(member, oppositeMember));
			String connectionCode = "ABC123";
			ValueOperations<String, String> opsForValue = redisTemplate.opsForValue();
			opsForValue.set(connectionCode, String.valueOf(oppositeMember.getId()));
			ConnectionServiceRequest request = createConnectionServiceRequest(connectionCode);

			// Stubbing
			given(memberReadService.findMemberByIdOrElseThrow(anyLong()))
				.willReturn(oppositeMember);

			// When & Then
			assertThatThrownBy(() -> coupleService.connectCouple(request))
				.isInstanceOf(AlreadyConnectedException.class)
				.hasMessage(ALREADY_CONNECTED);
		}

		@DisplayName("자기 자신과 연결하려 하면 실패한다")
		@Test
		void failWithSelfConnection() {

			// Given
			String connectionCode = "ABC123";

			ValueOperations<String, String> opsForValue = redisTemplate.opsForValue();
			opsForValue.set(getConnectionKey(member.getId()), connectionCode);
			opsForValue.set(connectionCode, String.valueOf(member.getId()));

			ConnectionServiceRequest request = createConnectionServiceRequest(connectionCode);

			// Stubbing
			given(memberReadService.findMemberByIdOrElseThrow(anyLong()))
				.willReturn(oppositeMember);

			// When & Then
			assertThatThrownBy(() -> coupleService.connectCouple(request))
				.isInstanceOf(SelfConnectionNotAllowedException.class)
				.hasMessage(SELF_CONNECTION_NOT_ALLOWED);
		}


	}

	private Couple createCouple(Member member, Member oppositeMember) {
		return Couple.builder()
			.member1(member)
			.member2(oppositeMember)
			.firstDate(LocalDate.now().minusDays(1L))
			.build();
	}

	public Member createMember(String phone) {
		return Member.builder()
			.phone(phone)
			.password("password")
			.name("name")
			.profileImageUrl("url")
			.birth(LocalDate.now().minusDays(1L))
			.gender(Gender.MALE)
			.nickname("nickname")
			.build();
	}

	public String getConnectionKey(Long id) {
		return "[CONNECTION]" + id;
	}

	public ConnectionServiceRequest createConnectionServiceRequest(String connectionCode) {
		return ConnectionServiceRequest.builder()
			.connectionCode(connectionCode)
			.firstDate(LocalDate.now().minusDays(1L))
			.build();
	}
}