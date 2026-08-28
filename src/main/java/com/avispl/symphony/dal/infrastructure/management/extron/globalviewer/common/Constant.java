/** Copyright (c) 2026 AVI-SPL, Inc. All Rights Reserved. */
package com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.common;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Utility class that defines constant values used across the application.
 *
 * @author Kevin / Symphony Dev Team
 * @since 1.0.0
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Constant {
	//	Formats
	public static final String PROPERTY_FORMAT = "%s#%s";
	public static final String INDEXED_GROUP_FORMAT = "%s_%s";

	//	Values
	public static final String NOT_AVAILABLE = "N/A";
	public static final String NONE = "None";
	public static final String EMPTY = "";
	public static final String ONLINE = "Online";
	public static final String OFFLINE = "Offline";
	public static final String ON = "On";
	public static final String OFF = "Off";
	public static final String ACTIVE = "Active";
	public static final String INACTIVE = "Inactive";
	public static final String CONTROLLER_MANUFACTURER = "Extron";

	public static final String DEVICE_ID_PREFIX = "device_";
	public static final String CONTROLLER_ID_PREFIX = "controller_";
	//	Endpoint
	public static final String AUTH_ENDPOINT = "/auth";
	public static final String DEVICES_ENDPOINT = "/devices";
	public static final String ROOMS_ENDPOINT = "/rooms";
	public static final String LOCATIONS_ENDPOINT = "/locations";
	public static final String ALERTS_ENDPOINT = "/alerts";
	public static final String MODEL_ENDPOINT = "/devices/model/%s";
	public static final String MANUFACTURER_ENDPOINT = "/devices/manufacturer/%s";
	public static final String CONTROLLERS_ENDPOINT = "/controllers";
	public static final String GVE_COMMANDS_ENDPOINT = "/gvecommands";
	public static final String DEVICE_COMMAND_ENDPOINT = "/gvecommands/device";
	public static final String CONTROLLER_COMMAND_ENDPOINT = "/gvecommands/controller";

	public static final String SYSTEM_ENDPOINT = "/system";
	public static final String MONITORING_SERVICE_ENDPOINT = "/services/monitoring";
	public static final String SCHEDULING_SERVICE_ENDPOINT = "/services/scheduling";
	public static final String UDP_LISTENER_SERVICE_ENDPOINT = "/services/udplistener";

	//	Groups
	public static final String GENERAL_GROUP = "General";
	public static final String LIVE_STATUS_GROUP = "LiveStatus";
	public static final String GVE_ROOM_GROUP = "GVERoom";
	public static final String GVE_LOCATION_GROUP = "GVELocation";
	public static final String GVE_SYSTEM_GROUP = "GVESystem";
	public static final String MONITORING_SERVICE_GROUP = "GVEService_Monitoring";
	public static final String SCHEDULING_SERVICE_GROUP = "GVEService_Scheduling";
	public static final String UDP_LISTENER_SERVICE_GROUP = "GVEService_UDPListener";
	public static final String CONTROLLER_NETWORK_GROUP = "Network";
	public static final String CONTROLLER_SYSTEM_GROUP = "System";
	public static final String ALERT_GROUP = "Alert";
	public static final String ACTIVE_ALERTS_GROUP = "ActiveAlerts";

	//	Response payload keys
	public static final String DEVICES = "Devices";
	public static final String ROOMS = "Rooms";
	public static final String LOCATIONS = "Locations";
	public static final String ALERTS = "Alerts";
	public static final String MODEL = "Model";
	public static final String MANUFACTURER = "Manufacturer";
	public static final String CONTROLLERS = "Controllers";
	public static final String CONTROLLER = "Controller";
	public static final String ACTIONS = "Actions";

	//	GVE command actions
	public static final String ACTION_CONTROLLER_TYPE_DEVICE = "Device";
	public static final String ACTION_NAME_POWER = "Power";
	public static final String POWER_PROPERTY = "Power";

	public static final String IPL_PRO_CONTROLLER_TYPE = "IPL Pro";
	
	public static final String WINDOWS_SERVICE = "WindowsService";

	//	Dynamic statistics
	public static final String MONITORED_DEVICES_TOTAL = "MonitoredDevicesTotal";
	public static final String LAST_MONITORING_CYCLE_DURATION = "LastMonitoringCycleDuration(sec)";

	//	Warning messages
	public static final String INVALID_VALUE_WARNING = "The value is invalid(%s), returning null.";
	public static final String FETCHED_DATA_NULL_WARNING = "Fetched data is null. Endpoint: %s, ResponseClass: %s";

	//	Fail messages
	public static final String READ_PROPERTIES_FILE_FAILED = "Failed to load version properties file.";
	public static final String FETCH_DATA_FAILED = "Device monitoring cannot proceed, the required data could not be fetched from the %s endpoint.";
}
