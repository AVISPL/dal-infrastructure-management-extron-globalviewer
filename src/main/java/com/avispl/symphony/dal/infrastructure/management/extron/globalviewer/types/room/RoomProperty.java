/** Copyright (c) 2026 AVI-SPL, Inc. All Rights Reserved. */
package com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.types.room;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.base.FieldProperty;
import com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.common.Constant;

/**
 * Represents the "Aggregator &gt; GVE Room" section of the GVE Adapter Property Reference, fetched once
 * per cycle from {@code /rooms} (list response shape {@code { "Rooms": [...], "ResponseStatus": {} } }).
 * <p>
 * Unlike {@link com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.types.aggregated.AggregatedGeneralProperty},
 * these are adapter/aggregator-level statistics (part of the single {@code ExtendedStatistics} returned by
 * {@code getMultipleStatistics()}), not per-device properties. Since there can be many rooms, each cached
 * room is exposed as its own dynamic group keyed by its {@link #ID}, e.g. {@code GVERoom_101#Name},
 * {@code GVERoom_101#Category} - see {@link Constant#GVE_ROOM_GROUP}.
 * <p>
 * Field pointers are resolved relative to a single element of the {@code Rooms} array. Only fields
 * confirmed present in a real response are mapped here - the GVE Adapter Property Reference also lists
 * {@code ContactEmail}, {@code ContactName}, {@code ContactPhone}, {@code Notes} and {@code WebCamURL}
 * for rooms, but none of those appeared in the sample response used to build this enum, and there's no
 * confirmed sibling field to infer their JSON key names from, so they're intentionally left unmapped.
 * </p>
 *
 * @author Kevin / Symphony Dev Team
 * @since 1.0.0
 */
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Getter
@AllArgsConstructor
public enum RoomProperty implements FieldProperty {
	/** Also used as the cache lookup key/per-instance group suffix (see {@link Constant#GVE_ROOM_GROUP}). */
	ID("ID", "/RoomId", Constant.GVE_ROOM_GROUP, false),
	NAME("Name", "/RoomName", Constant.GVE_ROOM_GROUP, false),
	LOCATION_ID("LocationID", "/LocationId", Constant.GVE_ROOM_GROUP, false),
	CATEGORY("Category", "/Category", Constant.GVE_ROOM_GROUP, false),
	STATUS("Status", "/Status", Constant.GVE_ROOM_GROUP, false),
	;

	/** Property name exposed to Symphony (as a {@code GVERoom_<ID>#} grouped adapter statistic). */
	String name;
	/** Jackson pointer to the value within a single room JSON node. */
	String field;
	/** Base group prefix; combined with the room's own ID at runtime (see {@link Constant#INDEXED_GROUP_FORMAT}). */
	String group;
	/** When {@code true}, this property is omitted entirely (rather than shown as {@code N/A}) when {@link #field} doesn't resolve. */
	boolean conditional;
}
