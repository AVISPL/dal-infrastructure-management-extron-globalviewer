/*
 * Copyright (c) 2025 AVI-SPL, Inc. All Rights Reserved.
 */
package com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.types.aggregated;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.base.BaseProperty;

/**
 * Represents general properties of an aggregated device retrieved from the {@code /devices} endpoint.
 * <p>
 * Each constant maps a monitored property {@code name} to its location in the device JSON via a
 * Jackson {@code field} pointer (so both top-level and nested {@code LiveStatus} fields are supported),
 * with an optional {@code group} prefix.
 * </p>
 *
 * @author Harry / Symphony Dev Team
 * @since 1.0.0
 */
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Getter
@AllArgsConstructor
public enum AggregatedGeneralProperty implements BaseProperty {
	DEVICE_ID("DeviceId", "/DeviceId", ""),
	DEVICE_NAME("DeviceName", "/DeviceName", ""),
	DEVICE_TYPE("Type", "/DeviceType", ""),
	ROOM_ID("RoomID", "/RoomId", ""),
	CONTROLLER_ID("ControllerID", "/ControllerId", ""),
	CONTROLLER_COMMAND_GUID("ControllerCommandGUID", "/ControllerCommandGuid", ""),
	CONTROLLER_PORT_NUMBER("ControllerPortNumber", "/ControllerPortNumber", ""),
	CONTROLLER_PORT_TYPE("ControllerPortType", "/ControllerPortType", ""),
	HOST("Host", "/Host", ""),
	STATUS("Status", "/Status", ""),
	LAMP_COST("LampCost($)", "/LampCost", ""),
	POWER_ON_POWER_CONSUMPTION("PowerOnPowerConsumption(W)", "/PowerOnPowerConsumption", ""),
	POWER_OFF_POWER_CONSUMPTION("PowerOffPowerConsumption(W)", "/PowerOffPowerConsumption", ""),
	CONNECTION("Connection", "/LiveStatus/Connection", ""),
	POWER_STATUS("PowerStatus", "/LiveStatus/DeviceStatus", ""),
	;

	/** Monitored property name exposed to Symphony. */
	String name;
	/** Jackson pointer to the value within a single device JSON node. */
	String field;
	/** Optional group prefix; empty means the property is flat (no {@code group#} prefix). */
	String group;
}
