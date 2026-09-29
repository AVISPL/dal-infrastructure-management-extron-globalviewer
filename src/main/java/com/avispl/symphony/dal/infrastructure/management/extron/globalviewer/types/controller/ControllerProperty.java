/** Copyright (c) 2026 AVI-SPL, Inc. All Rights Reserved. */
package com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.types.controller;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.base.FieldProperty;
import com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.common.Constant;

/**
 * The controller properties of an {@link com.avispl.symphony.api.dal.dto.monitor.aggregator.AggregatedDevice},
 * sourced from {@code /controllers}. {@link #MODEL_NAME} is not exposed as a stat directly - it's mapped
 * onto the aggregated device's {@code deviceModel} instead (see {@code buildAggregatedController}).
 *
 * @author Ritik Madaan / Symphony Dev Team
 * @since 1.0.0
 */
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Getter
@AllArgsConstructor
public enum ControllerProperty implements FieldProperty {
	ID("ID", "/ControllerId", Constant.EMPTY, false),
	NAME("Name", "/ControllerName", Constant.EMPTY, false),
	STATUS("Status", "/Status", Constant.EMPTY, false),
	ROOM_ID("RoomID", "/RoomId", Constant.EMPTY, false),
	ONLINE("Online", "/IsOnline", Constant.EMPTY, false),
	TYPE("Type", "/ControllerType", Constant.EMPTY, false),
	MODEL_NAME("ModelName", "/ModelName", Constant.EMPTY, false),
	PART_NUMBER("PartNumber", "/PartNumber", Constant.CONTROLLER_SYSTEM_GROUP, false),
	FIRMWARE_VERSION("FirmwareVersion", "/FirmwareVersion", Constant.CONTROLLER_SYSTEM_GROUP, false),
	MAC_ADDRESS("MACAddress", "/MacAddress", Constant.CONTROLLER_SYSTEM_GROUP, false),
	IP_ADDRESS("IPAddress", "/NetworkSettings/IPAddress", Constant.CONTROLLER_NETWORK_GROUP, false),
	GATEWAY_IP_ADDRESS("GatewayIPAddress", "/NetworkSettings/GatewayIPAddress", Constant.CONTROLLER_NETWORK_GROUP, false),
	SUBNET_MASK("SubnetMask", "/NetworkSettings/SubnetMask", Constant.CONTROLLER_NETWORK_GROUP, false),
	DHCP_ENABLED("DHCPEnabled", "/NetworkSettings/IsDhcpEnabled", Constant.CONTROLLER_NETWORK_GROUP, false),
	;

	/** Property name exposed to Symphony. */
	String name;
	/** Jackson pointer to the value within a single controller JSON node. */
	String field;
	/** Group prefix; empty for flat properties, {@value Constant#CONTROLLER_SYSTEM_GROUP} for hardware/system identity fields, or {@value Constant#CONTROLLER_NETWORK_GROUP} for the {@code NetworkSettings} fields. */
	String group;
	/** When {@code true}, this property is omitted (rather than shown as {@code N/A}) when {@link #field} doesn't resolve. */
	boolean conditional;
}
