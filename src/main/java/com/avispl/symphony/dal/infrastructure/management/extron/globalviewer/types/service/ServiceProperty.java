/** Copyright (c) 2026 AVI-SPL, Inc. All Rights Reserved. */
package com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.types.service;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.base.FieldProperty;
import com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.common.Constant;

/**
 * The shared "Windows service" shape used by every {@code /services/*} endpoint ({@code /services/monitoring},
 * {@code /services/scheduling}, {@code /services/udplistener}) - each response wraps a single flat
 * {@value Constant#WINDOWS_SERVICE} object with the same {@code Service}/{@code Status} fields, differing
 * only in which service it describes. {@code group} is intentionally left blank here since that differs per
 * endpoint (e.g. {@link Constant#MONITORING_SERVICE_GROUP} vs {@link Constant#SCHEDULING_SERVICE_GROUP} vs
 * {@link Constant#UDP_LISTENER_SERVICE_GROUP}) - callers supply the group explicitly when building stats.
 * {@code ResponseStatus} is an API call envelope, not service info, so it's intentionally not mapped here.
 *
 * @author Ritik Madaan / Symphony Dev Team
 * @since 1.0.0
 */
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Getter
@AllArgsConstructor
public enum ServiceProperty implements FieldProperty {
	NAME("Name", "/Service", Constant.EMPTY, false),
	STATUS("Status", "/Status", Constant.EMPTY, false),
	;

	/** Property name exposed to Symphony. */
	String name;
	/** Jackson pointer to the value within a {@code /services/*} response. */
	String field;
	/** Left blank - the group prefix differs per endpoint and is supplied by the caller instead. */
	String group;
	/** When {@code true}, this property is omitted (rather than shown as {@code N/A}) when {@link #field} doesn't resolve. */
	boolean conditional;
}
