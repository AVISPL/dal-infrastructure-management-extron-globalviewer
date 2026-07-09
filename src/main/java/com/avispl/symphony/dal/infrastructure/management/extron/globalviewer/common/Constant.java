/** Copyright (c) 2026 AVI-SPL, Inc. All Rights Reserved. */
package com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.common;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Utility class that defines constant values used across the application.
 *
 * @author Kevin / Symphony Dev Team
 * @since 1.0.0
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Constant {
	//	Formats
	public static final String PROPERTY_FORMAT = "%s#%s";

	//	Values
	public static final String NOT_AVAILABLE = "N/A";
	public static final String NONE = "None";
	public static final String EMPTY = "";
	public static final String ONLINE = "Online";
	public static final String ON = "On";

	//	Endpoint
	public static final String AUTH_ENDPOINT = "/auth";
	public static final String DEVICES_ENDPOINT = "/devices";

	//	Groups
	public static final String GENERAL_GROUP = "General";
	public static final String LIVE_STATUS_GROUP = "LiveStatus";

	//	Response payload keys
	public static final String DEVICES = "Devices";

	//	Warning messages
	public static final String INVALID_VALUE_WARNING = "The value is invalid(%s), returning null.";
	public static final String FETCHED_DATA_NULL_WARNING = "Fetched data is null. Endpoint: %s, ResponseClass: %s";

	//	Fail messages
	public static final String READ_PROPERTIES_FILE_FAILED = "Failed to load version properties file.";
	public static final String FETCH_DATA_FAILED = "Device monitoring cannot proceed, the required data could not be fetched from the %s endpoint.";
}
