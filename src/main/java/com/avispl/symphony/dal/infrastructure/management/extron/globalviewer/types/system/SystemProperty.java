/** Copyright (c) 2026 AVI-SPL, Inc. All Rights Reserved. */
package com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.types.system;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.base.FieldProperty;
import com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.common.Constant;

/**
 * The "Aggregator &gt; GVE System" adapter-level statistics, sourced from {@code /system}. The response
 * is a single flat object (no wrapper key, no ID) - {@code ResponseStatus} is an API call envelope, not
 * system info, so it's intentionally not mapped here.
 *
 * @author Ritik Madaan / Symphony Dev Team
 * @since 1.0.0
 */
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Getter
@AllArgsConstructor
public enum SystemProperty implements FieldProperty {
	VERSION("Version", "/Version", Constant.GVE_SYSTEM_GROUP, false),
	MOBILE_ENABLED("MobileEnabled", "/MobileEnabled", Constant.GVE_SYSTEM_GROUP, false),
	IS_SUPPORTED("IsSupported", "/IsSupported", Constant.GVE_SYSTEM_GROUP, false),
	;

	/** Property name exposed to Symphony. */
	String name;
	/** Jackson pointer to the value within the {@code /system} response. */
	String field;
	/** Base group prefix. */
	String group;
	/** When {@code true}, this property is omitted (rather than shown as {@code N/A}) when {@link #field} doesn't resolve. */
	boolean conditional;
}
