/** Copyright (c) 2026 AVI-SPL, Inc. All Rights Reserved. */
package com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.types.service;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.base.FieldProperty;
import com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.common.Constant;

/**
 * The "Aggregator &gt; GVE Scheduling Service" adapter-level statistics, sourced from
 * {@code /services/scheduling}. The response wraps a single flat {@value Constant#WINDOWS_SERVICE} object -
 * {@code ResponseStatus} is an API call envelope, not service info, so it's intentionally not mapped here.
 *
 * @author Ritik Madaan / Symphony Dev Team
 * @since 1.0.0
 */
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Getter
@AllArgsConstructor
public enum SchedulingServiceProperty implements FieldProperty {
	NAME("Name", "/Service", Constant.SCHEDULING_SERVICE_GROUP, false),
	STATUS("Status", "/Status", Constant.SCHEDULING_SERVICE_GROUP, false),
	;

	/** Property name exposed to Symphony. */
	String name;
	/** Jackson pointer to the value within the {@code /services/scheduling} response. */
	String field;
	/** Base group prefix. */
	String group;
	/** When {@code true}, this property is omitted (rather than shown as {@code N/A}) when {@link #field} doesn't resolve. */
	boolean conditional;
}
