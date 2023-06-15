package com.dateplan.dateplan.domain.anniversary.service;

import com.dateplan.dateplan.domain.anniversary.entity.Anniversary;
import com.dateplan.dateplan.domain.anniversary.entity.AnniversaryPattern;
import com.dateplan.dateplan.domain.anniversary.entity.AnniversaryRepeatRule;
import com.dateplan.dateplan.domain.anniversary.repository.AnniversaryJDBCRepository;
import com.dateplan.dateplan.domain.anniversary.repository.AnniversaryPatternRepository;
import com.dateplan.dateplan.domain.anniversary.repository.AnniversaryRepository;
import com.dateplan.dateplan.domain.couple.entity.Couple;
import com.dateplan.dateplan.domain.couple.service.CoupleReadService;
import com.dateplan.dateplan.domain.member.entity.Member;
import com.dateplan.dateplan.global.constant.DateConstants;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
@Transactional
public class AnniversaryService {

	private final CoupleReadService coupleReadService;
	private final AnniversaryPatternRepository anniversaryPatternRepository;
	private final AnniversaryRepository anniversaryRepository;
	private final AnniversaryJDBCRepository anniversaryJDBCRepository;

	public void createAnniversariesForBirthDay(Member member) {

		Couple couple = coupleReadService.findCoupleByMemberOrElseThrow(member);

		LocalDate birthDay = member.getBirthDay();

		AnniversaryPattern anniversaryPattern = AnniversaryPattern.ofBirthDay(couple, birthDay);
		anniversaryPatternRepository.save(anniversaryPattern);

		List<Anniversary> anniversaries = createRepeatedAnniversariesForBirthDay(member, birthDay,
			anniversaryPattern);

		anniversaryJDBCRepository.saveAll(anniversaries);
	}

	private List<Anniversary> createRepeatedAnniversariesForBirthDay(Member member,
		LocalDate birthDay,
		AnniversaryPattern anniversaryPattern) {

		return IntStream.iterate(
			0,
				years -> birthDay.plusYears(years)
					.isBefore(DateConstants.CALENDER_END_DATE.minusDays(1)),
				years -> years + 1)
			.mapToObj(years -> Anniversary.ofBirthDay(
				member.getName() + " 님의 생일",
				birthDay.plusYears(years),
				anniversaryPattern
			))
			.toList();
	}

	public void createAnniversariesForFirstDate(Couple couple) {

		LocalDate firstDate = couple.getFirstDate();

		AnniversaryPattern anniversaryPattern = AnniversaryPattern.ofFirstDate(couple, firstDate,
			AnniversaryRepeatRule.NONE);

		anniversaryPatternRepository.save(anniversaryPattern);

		Anniversary anniversary = Anniversary.ofFirstDate("처음 만난 날", firstDate, anniversaryPattern);
		anniversaryRepository.save(anniversary);

		createAndSaveAnniversariesForFirstDate(couple, AnniversaryRepeatRule.HUNDRED_DAYS);
		createAndSaveAnniversariesForFirstDate(couple, AnniversaryRepeatRule.YEAR);
	}

	private void createAndSaveAnniversariesForFirstDate(Couple couple,
		AnniversaryRepeatRule repeatRule) {

		LocalDate firstDate = couple.getFirstDate();

		if (!Objects.equals(repeatRule, AnniversaryRepeatRule.NONE)) {
			AnniversaryPattern anniversaryPattern = AnniversaryPattern.ofFirstDate(couple,
				firstDate,
				repeatRule);

			anniversaryPatternRepository.save(anniversaryPattern);

			List<Anniversary> anniversaries = createRepeatedAnniversariesForFirstDate(
				anniversaryPattern, firstDate);

			anniversaryJDBCRepository.saveAll(anniversaries);
		}
	}

	private List<Anniversary> createRepeatedAnniversariesForFirstDate(
		AnniversaryPattern anniversaryPattern, LocalDate firstDate) {

		return switch (anniversaryPattern.getRepeatRule()) {

			case HUNDRED_DAYS -> {
				LocalDate anniversaryDate = firstDate.minusDays(1);

				yield IntStream.iterate(
					100,
						days -> anniversaryDate.plusDays(days)
							.isBefore(DateConstants.NEXT_DAY_FROM_CALENDER_END_DATE),
						days -> days + 100)
					.mapToObj(days -> Anniversary.ofFirstDate(
						"만난지 " + days + "일",
						anniversaryDate.plusDays(days),
						anniversaryPattern
					))
					.toList();
			}

			case YEAR -> IntStream.iterate(
					1,
					years -> firstDate.plusYears(years)
						.isBefore(DateConstants.NEXT_DAY_FROM_CALENDER_END_DATE),
					years -> years + 1)
				.mapToObj(years -> Anniversary.ofFirstDate(
					"만난지 " + years + "주년",
					firstDate.plusYears(years),
					anniversaryPattern
				))
				.toList();

			case NONE -> List.of();
		};
	}
}
