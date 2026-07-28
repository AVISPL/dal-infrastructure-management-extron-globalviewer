/** Copyright (c) 2026 AVI-SPL, Inc. All Rights Reserved. */
package com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.types.alert;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.base.FieldProperty;

/**
 * The device alert properties, sourced from {@code /alerts}.
 *
 * @author Ritik Madaan / Symphony Dev Team
 * @since 1.0.0
 */
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Getter
@AllArgsConstructor
public enum AlertProperty implements FieldProperty {
	DEVICE_ID("DeviceID", "/DeviceId", "", false),
	HISTORY_LOG_ID("MonitorHistoryLogID", "/MonitorHistoryLogId", "", false),
	MONITOR_NAME("MonitoredCategory", "/MonitorName", "", false),
	IP_ADDRESS("IPAddress", "/IPAddress", "", false),
	EVENT_TIME("EventTime(UTC)", "/EventTime", "", false),
	TYPE("Type", "/AlertType", "", false),
	GC_CONFIG_NAME("GCConfigName", "/GCConfigName", "", false),
	;

	/** Property name exposed to Symphony. */
	String name;
	/** Jackson pointer to the value within a single alert JSON node. */
	String field;
	/** Group prefix; unused, always empty - the per-device sub-group is applied by the caller. */
	String group;
	/** When {@code true}, this property is omitted (rather than shown as {@code N/A}) when {@link #field} doesn't resolve. */
	boolean conditional;
}
