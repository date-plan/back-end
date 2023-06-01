package com.dateplan.dateplan.global.constant;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class InputPattern {

	public static final String PHONE_PATTERN = "^010\\d{4}\\d{4}$";
	public static final String MEMBER_NAME_PATTERN = "^[가-힣]{2,10}$";
	public static final String NICKNAME_PATTERN = "^[A-Za-z0-9가-힣]{2,10}$";
	public static final String PASSWORD_PATTERN = "^[A-Za-z0-9]{5,20}$";
}