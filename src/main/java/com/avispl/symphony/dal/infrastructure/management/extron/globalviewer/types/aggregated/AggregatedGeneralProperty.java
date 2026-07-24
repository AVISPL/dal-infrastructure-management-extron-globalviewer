/*
 * Copyright (c) 2025 AVI-SPL, Inc. All Rights Reserved.
 */
package com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.types.aggregated;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.base.FieldProperty;
import com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.common.Constant;

/**
 * Represents the aggregated device properties documented under the "Aggregated Device > General" and
 * "Aggregated Device > Live Status" sections of the GVE Adapter Property Reference. All of these are
 * sourced from a single {@code /devices} list call - the {@code LiveStatus} sub-object (and its lamp/
 * filter counters) has been confirmed to be identical between the {@code /devices} list response and
 * the {@code /devices/{deviceId}} per-device response, so no separate per-device fetch is needed for
 * this data.
 * <p>
 * Each constant maps a monitored property {@code name} (matching the reference doc's display name,
 * including unit suffixes like {@code (hr)}/{@code (W)}/{@code ($)}) to its location in the device JSON
 * via a Jackson {@code field} pointer, with an optional {@code group} prefix ({@code LiveStatus} for
 * everything under the {@code Live Status} section, empty/flat for {@code General}).
 * </p>
 *
 * @author Harry / Symphony Dev Team
 * @since 1.0.0
 */
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Getter
@AllArgsConstructor
public enum AggregatedGeneralProperty implements FieldProperty {
	// General
	DEVICE_ID("DeviceID", "/DeviceId", "", false),
	DEVICE_NAME("DeviceName", "/DeviceName", "", false),
	DEVICE_TYPE("Type", "/DeviceType", "", false),
	ROOM_ID("RoomID", "/RoomId", "", false),
	MODEL_ID("ModelId", "/ModelId", "", false),
	CONTROLLER_ID("ControllerID", "/ControllerId", "", false),
	CONTROLLER_COMMAND_GUID("ControllerCommandGUID", "/ControllerCommandGuid", "", false),
	CONTROLLER_PORT_NUMBER("ControllerPortNumber", "/ControllerPortNumber", "", false),
	CONTROLLER_PORT_TYPE("ControllerPortType", "/ControllerPortType", "", false),
	HOST("Host", "/Host", "", false),
	STATUS("Status", "/Status", "", false),
	LAMP_COST("LampCost($)", "/LampCost", "", false),
	POWER_ON_POWER_CONSUMPTION("PowerOnPowerConsumption(W)", "/PowerOnPowerConsumption", "", false),
	POWER_OFF_POWER_CONSUMPTION("PowerOffPowerConsumption(W)", "/PowerOffPowerConsumption", "", false),
	POWER_STATUS("PowerStatus", "/LiveStatus/DeviceStatus", "", false),

	// Live Status - confirmed present (even if defaulted to 0) regardless of lamp count
	CONNECTION("Connection", "/LiveStatus/Connection", Constant.LIVE_STATUS_GROUP, false),
	MAX_LAMP_HOURS("MaximumLampUtilization(hr)", "/LiveStatus/MaxLampHours", Constant.LIVE_STATUS_GROUP, false),
	OPERATION_HOURS("OperationTime(hr)", "/LiveStatus/OperationHours", Constant.LIVE_STATUS_GROUP, false),
	FILTER_HOURS("FilterUtilization(hr)", "/LiveStatus/FilterHours", Constant.LIVE_STATUS_GROUP, false),
	MAX_FILTER_HOURS("MaximumFilterUtilization(hr)", "/LiveStatus/MaxFilterHours", Constant.LIVE_STATUS_GROUP, false),
	;

	/** Monitored property name exposed to Symphony (matches the reference doc's display name). */
	String name;
	/** Jackson pointer to the value within a single device JSON node. */
	String field;
	/** Optional group prefix; empty means the property is flat (no {@code group#} prefix). */
	String group;
	/** When {@code true}, this property is omitted entirely (rather than shown as {@code N/A}) when {@link #field} doesn't resolve. */
	boolean conditional;
}
