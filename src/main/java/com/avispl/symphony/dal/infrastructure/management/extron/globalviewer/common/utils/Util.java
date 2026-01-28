/** Copyright (c) 2026 AVI-SPL, Inc. All Rights Reserved. */
package com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.common.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import com.avispl.symphony.dal.util.StringUtils;

/**
 * Utility class providing helper methods.
 *
 * @author Kevin / Symphony Dev Team
 * @since 1.0.0
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Util {
	public static boolean isBoolean(String value) {
		return value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false");
	}

	public static boolean isInt(String value) {
		if (StringUtils.isNullOrEmpty(value, true)) {
			return false;
		}
		try {
			Integer.parseInt(value);
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}
}
