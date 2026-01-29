/** Copyright (c) 2026 AVI-SPL, Inc. All Rights Reserved. */
package com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.common.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * @author Kevin / Symphony Dev Team
 * @since 1.0.0
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Constant {
	//	Formats
	public static final String PROPERTY_FORMAT = "%s#%s";

	//	Values
	public static final String NOT_AVAILABLE = "N/A";

	//	Groups
	public static final String GENERAL_GROUP = "General";

	//	Warning messages
	public static final String INVALID_VALUE_WARNING = "The value is invalid(%s), returning null.";

	//	Fail messages
	public static final String READ_PROPERTIES_FILE_FAILED = "Failed to load version properties file.";
}
