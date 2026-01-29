/** Copyright (c) 2026 AVI-SPL, Inc. All Rights Reserved. */
package com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.types.aggregator;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.base.BaseProperty;

/**
 * Represents general properties.
 *
 * @author Kevin / Symphony Dev Team
 * @since 1.0.0
 */
@FieldDefaults(makeFinal = true)
@Getter
@AllArgsConstructor
public enum General implements BaseProperty {
	ACTIVE_PROPERTY_GROUPS("ActivePropertyGroups", "adapter.active.property.groups"),
	ADAPTER_BUILD_DATE("AdapterBuildDate", "adapter.build.date"),
	ADAPTER_UPTIME("AdapterUptime", "adapter.uptime"),
	ADAPTER_UPTIME_MIN("AdapterUptime(min)", "adapter.uptime"),
	ADAPTER_VERSION("AdapterVersion", "adapter.version"),
	MONITORED_DEVICES_TOTAL("MonitoredDevicesTotal", "adapter.devices.total"),
	LAST_MONITORING_CYCLE_DURATION("LastMonitoringCycleDuration(s)", "adapter.last.cycle.duration"),
	SYSTEM_MONITORING_CYCLE_INTERVAL("SystemMonitoringCycleInterval(s)", "adapter.system.cycle.interval")
	;

	String name;
	String property;
}
