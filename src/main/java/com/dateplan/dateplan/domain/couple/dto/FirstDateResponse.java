package com.dateplan.dateplan.domain.couple.dto;

import java.time.LocalDate;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class FirstDateResponse {

	private LocalDate firstDate;

}
