# Extron GlobalViewer Enterprise (GVE) Integration - Capabilities & Configuration
This document covers the Extron GlobalViewer Enterprise (GVE) Adapter Capabilities and Configuration.

Symphony integrates with Extron GlobalViewer Enterprise to provide centralized monitoring of AV devices and controllers that are managed by a GVE server, including room/location context, live device status, utilization data, and alerts.
Main features are: aggregated device and controller monitoring, room and location mapping, power and connectivity status, lamp/filter utilization tracking, and alert visibility and clearing.

## Main use cases for Extron GlobalViewer Enterprise
- **Monitor** aggregated AV devices and controllers reported by GVE, including online/offline status, power status, and network host information
- **Monitor** GVE system, location, room, and service-level information
- **Track** lamp utilization, filter utilization, and operation time for supported devices
- **View** active and historical alerts raised by GVE for devices and controllers
- **Control** power (where supported) and clear controller/device alerts

## Prerequisites for setting up connection to Extron GlobalViewer Enterprise adapter
The Extron GlobalViewer Enterprise adapter communicates with the GVE server over HTTP/HTTPS using the GVE API. It requires a valid GVE user account.

Before integrating GVE with Symphony, the following prerequisites must be completed:
- An Extron GlobalViewer Enterprise user account
- The username and password for that account
- GVE devices, controllers, rooms, and locations already set up and visible within the GVE server

The Symphony instance or Cloud Connector must be able to reach:
- Management address: the GVE server host
- Protocol / Port: HTTPs, port 80 (default; confirm against the customer's GVE server setup)

Firewall or proxy rules must allow outbound connectivity between the Symphony Cloud Connector and the GVE server on the configured port.

The adapter supports all device models and types exposed by the GVE API, with monitorable properties and functionality determined by each device's reported capabilities.

## Extron GlobalViewer Enterprise Device Connection Setup and Provisioning

Note: The connection configuration below describes a successful GVE integration setup. These should not be confused with the adapter configuration parameters. They are not to be inferred as troubleshooting checks and should not be used when diagnosing specific errors unless a troubleshooting entry (provided in the Troubleshooting section) explicitly references them.

Once the GVE server is network-accessible, use the following settings to configure the Aggregator device in Symphony (available on the Configuration tab of the Aggregator configuration):

| Field | Description |
|---|---|
| Device Type | Infrastructure |
| Category | Management |
| Management | Extron |
| Model | GlobalViewer (Monitoring Proxy) |
| Monitoring Service | Advanced Monitoring |
| Monitoring Source | Direct |
| Management Address | GVE Server Host |
| Protocol | HTTPs |
| Username | <Username> |
| Password | <Password> |
| Port Number | 80 |

When the Aggregator is configured, saved, and set active, Symphony retrieves location, room, and device/controller metadata from the GVE server. Devices and controllers are then displayed in the same room where the Aggregator is located, until provisioned.

To provision a discovered device, open the Aggregator's Monitoring page, press the blue plus icon on the device tile, confirm the information on the device provisioning page, mark the device to provision, press Import, and confirm the import prompt. Once provisioned, the device is displayed with its live status and Extended Properties.

Adapter behavior and performance can be tuned via the following Adapter Configuration Parameters:

| Property | Description |
| --- | --- |
| alertEventsTotal | Maximum number of alerts shown per device/controller, most recent first. Defaults to 10. |
| alertMonitoredCategoryFilter | Comma-separated alert categories to limit which alerts are displayed. Leave empty to show all categories. |
| alertTypeFilter | Comma-separated alert types to limit which alerts are displayed. Leave empty to show all types. |
| configManagement | Enables or disables the device/controller power controls and the alert-clearing buttons. Defaults to enabled. |
| displayPropertyGroups | Comma-separated list of optional property groups to display. Defaults to General (only the ungrouped properties). Possible values: General (default), All, GVERoom/GVELocation/GVESystem (adapter-level properties for rooms, locations, and system information), GVEService (represents the Monitoring, Scheduling, and UDPListener service subgroups), LiveStatus (device properties, including lamp and connection statistics), Network/System (controller properties), Alerts (represents all alert-related property groups). |
| locationFilter | Comma-separated Location IDs to limit monitored devices to. Leave empty to monitor devices in all locations. |
| roomFilter | Comma-separated Room IDs to limit monitored devices to. Leave empty to monitor devices in all rooms. |

Note: Invalid values for numeric properties will cause the adapter to automatically revert to the default value.

## Extron GlobalViewer Enterprise - Filtering Device(s)

GVE devices can only be filtered using these two properties: `locationFilter` and `roomFilter`. No other filter properties are supported.

| Property | Description | Default |
|---|---|---|
| locationFilter | Comma-separated Location IDs to limit monitored devices to | Blank, csv string |
| roomFilter | Comma-separated Room IDs to limit monitored devices to | Blank, csv string |

## Available Monitored Data for Extron GlobalViewer Enterprise adapter
The GVE adapter exposes properties across the following groups. Controllable properties are marked with an asterisk (*).

**Aggregator-level properties:**

| Property Group | Description |
|---|---|
| Aggregator (General) | Active Property Groups, Adapter Build Date, Adapter Uptime, Adapter Uptime (min), Adapter Version, Last Monitoring Cycle Duration (graphable), Monitored Devices Total (graphable), Monitoring Cycle Interval (min) |
| Alert Actions | Controller Alerts* (clears all controller alerts), Device Alerts* (clears all device alerts) |
| GVE Location (GVELocation_[LocationID]) | ID, Name, Status |
| GVE Room (GVERoom_[RoomID]) | Category, ID, Location ID, Name, Status |
| GVE Services (GVEService_[ServiceName]) | Name, Status |
| GVE System | Is Supported, Mobile Enabled, Version |

**Monitored (Aggregated) Devices:**

| Property Group | Description |
|---|---|
| General | Controller Command GUID, Controller ID, Controller Port Number, Controller Port Type, Device ID, Device Model, Device Name, Device Online, Host, Lamp Cost ($), Power*, Power Off Power Consumption (W), Power On Power Consumption (W), Power Status, Room ID, Status, Type |
| Active Alerts | Monitored Categories, Total Count, Types |
| Alerts | Controller ID, Event Time (UTC), GC Config Name, IP Address, Monitor History Log ID, Monitored Category, Type |
| Live Status | Connection, Filter Utilization (hr), Lamp Utilization (hr) / Average Lamp Utilization (hr) (shown per lamp for devices with more than one lamp), Maximum Filter Utilization (hr), Maximum Lamp Utilization (hr), Operation Time (hr) |

**Controllers:**

| Property Group | Description |
|---|---|
| General | Device ID, Device Make, Device Model, Device Online, Power* (not available for IP Link Pro controllers), Room ID, Status, Type |
| Alerts / Active Alerts | Same properties as for devices |
| Network | DHCP Enabled, Gateway IP Address, IP Address, Subnet |
| System | Firmware Version, MAC Address, Part Number |

**Notes:**
- The Total Count under Active Alerts always reflects the true, complete number of active alerts for that device or controller, regardless of the alertEventsTotal limit or any alert filters configured.
- Only the most recent alerts (up to the configured alertEventsTotal, default 10) are listed under the Alerts property group.

## Troubleshooting checks for Extron GlobalViewer Enterprise

**Troubleshooting guidance**
- If an error occurs, focus only on troubleshooting steps that are provided in the section below.
- Do not include prerequisite/setup information.
- Do not add unrelated configuration details from other sections.
- If the document does not provide a direct error troubleshooting step, state that the document does not contain enough guidance for that specific issue.

**Login Error**
- Verify the Username and Password correspond to a valid Extron GlobalViewer Enterprise user account
- Verify the account has not been locked, disabled, or expired on the GVE server

**API Error/ Link Error / Connection Error / Ping Timeout**
Note: Connection errors are distinct from login/authentication errors. If credentials are incorrect, refer to the Login Error section instead.
- Verify the Management Address (GVE Server Host) is correct and reachable from the Symphony Cloud Connector
- Confirm the configured Protocol and Port Number match the GVE server's setup (HTTPs, port 80 by default)
- Verify outbound connectivity and firewall/proxy configuration between the Symphony Cloud Connector and the GVE server

**Unable to Control Device/Controller Power or Clear Alerts**
- Confirm the configManagement adapter parameter is enabled (it is enabled by default)

**Alert Count Appears Incomplete**
- The Alerts list under a device or controller only shows the most recent alerts up to the configured alertEventsTotal (default 10); refer to the Active Alerts > Total Count property for the true, complete alert count
- If alertMonitoredCategoryFilter or alertTypeFilter are configured, alerts outside those categories/types will not be displayed

If none of the recommended steps help, please enter an SOS ticket at {https://avi-spl.atlassian.net/servicedesk/customer/portals}

## What AI Assistant can do with Extron GlobalViewer Enterprise integration:
- Find Extron GlobalViewer Enterprise devices and controllers in Symphony (Infrastructure | Management | Extron)
- Verify the GVE adapter configuration and connectivity status

## What AI Assistant cannot do with Extron GlobalViewer Enterprise integration:
- Provision devices or controllers into Symphony from GVE
- Modify device-side settings, rooms, locations, or alert rules - these must be configured directly in the GlobalViewer Enterprise server console

## Extron GlobalViewer Enterprise - Additional Resources
For more questions and details about the adapter and its configuration, please refer to:
- Extron GlobalViewer Enterprise Release Notes: https://symphony.knowledgeowl.com/help/extron-globalviewer-enterprise-technical-breakdown

Please make sure to mention this is communication between the Symphony Cloud Connector and the customer's GVE server (Management Address), not a connection directly to individual Extron devices.
