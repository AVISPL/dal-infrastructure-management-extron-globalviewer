/** Copyright (c) 2026 AVI-SPL, Inc. All Rights Reserved. */
package com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.types.location;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.base.FieldProperty;
import com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.common.Constant;

/**
 * The "Aggregator &gt; GVE Location" adapter-level statistics, sourced from {@code /locations}.
 *
 * @author Ritik Madaan / Symphony Dev Team
 * @since 1.0.0
 */
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Getter
@AllArgsConstructor
public enum LocationProperty implements FieldProperty {
	ID("ID", "/LocationId", Constant.GVE_LOCATION_GROUP, false),
	NAME("Name", "/LocationName", Constant.GVE_LOCATION_GROUP, false),
	STATUS("Status", "/Status", Constant.GVE_LOCATION_GROUP, false),
	;

	/** Property name exposed to Symphony. */
	String name;
	/** Jackson pointer to the value within a single location JSON node. */
	String field;
	/** Base group prefix; combined with the location's ID at runtime. */
	String group;
	/** When {@code true}, this property is omitted (rather than shown as {@code N/A}) when {@link #field} doesn't resolve. */
	boolean conditional;
}
