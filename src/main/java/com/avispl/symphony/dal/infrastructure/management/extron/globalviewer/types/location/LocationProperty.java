/** Copyright (c) 2026 AVI-SPL, Inc. All Rights Reserved. */
package com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.types.location;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.base.FieldProperty;
import com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.common.Constant;

/**
 * Represents the "Aggregator &gt; GVE Location" section of the GVE Adapter Property Reference, fetched
 * once per cycle from {@code /locations} (list response shape
 * {@code { "Locations": [...], "ResponseStatus": {} } }).
 * <p>
 * Unlike {@link com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.types.aggregated.AggregatedGeneralProperty},
 * these are adapter/aggregator-level statistics (part of the single {@code ExtendedStatistics} returned by
 * {@code getMultipleStatistics()}), not per-device properties. Since there can be many locations, each
 * cached location is exposed as its own dynamic group keyed by its {@link #ID}, e.g.
 * {@code GVELocation_5#Name}, {@code GVELocation_5#Status} - see {@link Constant#GVE_LOCATION_GROUP}.
 * <p>
 * Field pointers are resolved relative to a single element of the {@code Locations} array. Only fields
 * confirmed present in a real response are mapped here - the GVE Adapter Property Reference also lists
 * {@code ParentID} for locations, but it didn't appear in the sample response used to build this enum
 * (plausibly because that sample was the root location, which wouldn't have a parent), and there's no
 * confirmed sibling field to infer its JSON key name from, so it's intentionally left unmapped.
 * </p>
 *
 * @author Kevin / Symphony Dev Team
 * @since 1.0.0
 */
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Getter
@AllArgsConstructor
public enum LocationProperty implements FieldProperty {
	/** Also used as the cache lookup key/per-instance group suffix (see {@link Constant#GVE_LOCATION_GROUP}). */
	ID("ID", "/LocationId", Constant.GVE_LOCATION_GROUP, false),
	NAME("Name", "/LocationName", Constant.GVE_LOCATION_GROUP, false),
	STATUS("Status", "/Status", Constant.GVE_LOCATION_GROUP, false),
	;

	/** Property name exposed to Symphony (as a {@code GVELocation_<ID>#} grouped adapter statistic). */
	String name;
	/** Jackson pointer to the value within a single location JSON node. */
	String field;
	/** Base group prefix; combined with the location's own ID at runtime (see {@link Constant#INDEXED_GROUP_FORMAT}). */
	String group;
	/** When {@code true}, this property is omitted entirely (rather than shown as {@code N/A}) when {@link #field} doesn't resolve. */
	boolean conditional;
}
