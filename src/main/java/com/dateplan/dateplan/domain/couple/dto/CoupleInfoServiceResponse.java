package com.dateplan.dateplan.domain.couple.dto;

import java.time.LocalDate;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CoupleInfoServiceResponse {

	private Long coupleId;
	private Long partnerId;
	private LocalDate firstDate;

	public CoupleInfoResponse toCoupleInfoResponse() {
		return CoupleInfoResponse.builder()
			.coupleId(coupleId)
			.partnerId(partnerId)
			.firstDate(firstDate)
			.build();
	}
}