/** Copyright (c) 2026 AVI-SPL, Inc. All Rights Reserved. */
package com.avispl.symphony.dal.infrastructure.management.extron.globalviewer;

import com.avispl.symphony.api.dal.control.Controller;
import com.avispl.symphony.api.dal.dto.control.AdvancedControllableProperty;
import com.avispl.symphony.api.dal.dto.control.ControllableProperty;
import com.avispl.symphony.api.dal.dto.monitor.ExtendedStatistics;
import com.avispl.symphony.api.dal.dto.monitor.Statistics;
import com.avispl.symphony.api.dal.dto.monitor.aggregator.AggregatedDevice;
import com.avispl.symphony.api.dal.monitor.Monitorable;
import com.avispl.symphony.api.dal.monitor.aggregator.Aggregator;
import com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.base.BaseCommunicator;
import com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.base.FieldProperty;
import com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.common.Constant;
import com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.common.utils.MonitoringUtil;
import com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.common.utils.Util;
import com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.models.APIResponse;
import com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.types.aggregated.AggregatedGeneralProperty;
import com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.types.aggregated.DeviceTypeCategory;
import com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.types.aggregator.General;
import com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.types.alert.AlertProperty;
import com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.types.controller.ControllerProperty;
import com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.types.location.LocationProperty;
import com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.types.manufacturer.ManufacturerProperty;
import com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.types.model.ModelProperty;
import com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.types.room.RoomProperty;
import com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.types.service.ServiceProperty;
import com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.types.system.SystemProperty;
import com.avispl.symphony.dal.util.StringUtils;
import com.avispl.symphony.dal.util.ControllablePropertyFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.apache.commons.collections.CollectionUtils;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * GlobalViewerEnterpriseCommunicator class
 *
 * @author Kevin / Symphony Dev Team
 * @since 1.0.0
 */
public class GlobalViewerEnterpriseCommunicator extends BaseCommunicator implements Aggregator, Monitorable, Controller {
	/** Application configuration loaded from {@code version.properties}. */
	private final Properties versionProperties;
	/** Device adapter instantiation timestamp. */
	private final long adapterInitializationTimestamp;
	/** Stores extended statistics to be sent to the adapter. */
	private final ExtendedStatistics localExtendedStatistics;
	/**
	 * Executor that runs all the async operations, that is posting and
	 */
	private ExecutorService executorService;

	/**
	 * Indicates whether a device is considered as paused.
	 * True by default so if the system is rebooted and the actual value is lost -> the device won't start stats
	 * collection unless the {@link GlobalViewerEnterpriseCommunicator#retrieveMultipleStatistics()} method is called which will change it
	 * to a correct value
	 */
	private volatile boolean devicePaused = true;

	/**
	 * A private field that represents an instance of the GlobalViewerEnterprise class, which is responsible for loading device data for GlobalViewerEnterprise
	 */
	private GlobalViewerEnterpriseDataLoader deviceDataLoader;

	/**
	 * We don't want the statistics to be collected constantly, because if there's not a big list of devices -
	 * new devices' statistics loop will be launched before the next monitoring iteration. To avoid that -
	 * this variable stores a timestamp which validates it, so when the devices' statistics is done collecting, variable
	 * is set to currentTime + 30s, at the same time, calling {@link #retrieveMultipleStatistics()} and updating the
	 */
	private long nextDevicesCollectionIterationTimestamp;

	/**
	 * How much time last monitoring cycle took to finish
	 */
	private long lastMonitoringCycleDuration;

	/**
	 * This parameter holds timestamp of when we need to stop performing API calls
	 * It used when device stop retrieving statistic. Updated each time of called #retrieveMultipleStatistics
	 */
	private volatile long validRetrieveStatisticsTimestamp;

	/**
	 * Room IDs to filter monitored devices by.
	 */
	private Set<String> roomFilter = new HashSet<>();

	/**
	 * Location IDs to filter monitored devices by.
	 */
	private Set<String> locationFilter = new HashSet<>();

	/**
	 * Retrieves {@link #roomFilter}.
	 *
	 * @return value of {@link #roomFilter}
	 */
	public String getRoomFilter() {
		return String.join(",", roomFilter);
	}

	/**
	 * Sets {@link #roomFilter} value.
	 *
	 * @param roomFilter new value of {@link #roomFilter}
	 */
	public void setRoomFilter(String roomFilter) {
		this.roomFilter = Arrays.stream(roomFilter.split(",")).map(String::trim).filter(StringUtils::isNotNullOrEmpty).collect(Collectors.toSet());
	}

	/**
	 * Retrieves {@link #locationFilter}.
	 *
	 * @return value of {@link #locationFilter}
	 */
	public String getLocationFilter() {
		return String.join(",", locationFilter);
	}

	/**
	 * Sets {@link #locationFilter} value.
	 *
	 * @param locationFilter new value of {@link #locationFilter}
	 */
	public void setLocationFilter(String locationFilter) {
		this.locationFilter = Arrays.stream(locationFilter.split(",")).map(String::trim).filter(StringUtils::isNotNullOrEmpty).collect(Collectors.toSet());
	}

	/**
	 * Property groups to display, matched case-sensitively. Defaults to {@link Constant#GENERAL_GROUP}
	 * (only flat/ungrouped properties, no optional groups at all). {@link Constant#ALL_GROUPS}
	 * enables every optional group; otherwise, only the exact group names present are enabled - one or more of
	 * {@link Constant#GVE_ROOM_GROUP}, {@link Constant#GVE_LOCATION_GROUP}, {@link Constant#GVE_SYSTEM_GROUP},
	 * {@link Constant#SERVICES_DISPLAY_GROUP}, {@link Constant#ALERTS_DISPLAY_GROUP}, {@link Constant#LIVE_STATUS_GROUP},
	 * {@link Constant#CONTROLLER_NETWORK_GROUP}, {@link Constant#CONTROLLER_SYSTEM_GROUP}. Gates both the extra API
	 * calls those groups need and whether their properties are added to the adapter/device/controller statistics.
	 */
	private Set<String> displayPropertyGroups = new HashSet<>(Collections.singletonList(Constant.GENERAL_GROUP));

	/**
	 * Retrieves {@link #displayPropertyGroups}.
	 *
	 * @return value of {@link #displayPropertyGroups}
	 */
	public String getDisplayPropertyGroups() {
		return String.join(",", displayPropertyGroups);
	}

	/**
	 * Sets {@link #displayPropertyGroups} value; falls back to {@link Constant#GENERAL_GROUP}
	 * when blank/unparsable.
	 *
	 * @param displayPropertyGroups new value of {@link #displayPropertyGroups}, comma-separated
	 */
	public void setDisplayPropertyGroups(String displayPropertyGroups) {
		Set<String> parsed = StringUtils.isNullOrEmpty(displayPropertyGroups, true) ? Collections.emptySet()
				: Arrays.stream(displayPropertyGroups.split(",")).map(String::trim).filter(StringUtils::isNotNullOrEmpty).collect(Collectors.toSet());
		this.displayPropertyGroups = parsed.isEmpty() ? new HashSet<>(Collections.singletonList(Constant.GENERAL_GROUP)) : parsed;
	}

	/**
	 * Checks whether {@code group} should be displayed, per {@link #displayPropertyGroups}.
	 *
	 * @param group the group name to check, e.g. {@link Constant#GVE_ROOM_GROUP}
	 * @return {@code true} if {@code group} should be displayed
	 */
	private boolean isGroupDisplayed(String group) {
		return displayPropertyGroups.contains(Constant.ALL_GROUPS) || displayPropertyGroups.contains(group);
	}

	/**
	 * {@link AlertProperty#TYPE} values to filter displayed alerts by, matched case-insensitively; combined
	 * with {@link #alertMonitoredCategoryFilter} using AND when both are configured (an empty filter isn't
	 * applied). Only affects which alerts get an {@code Alert_XX} display group - the {@code ActiveAlerts}
	 * summary is unaffected and always reflects every alert.
	 */
	private Set<String> alertTypeFilter = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

	/**
	 * {@link AlertProperty#MONITOR_NAME} values to filter displayed alerts by, matched case-insensitively;
	 * combined with {@link #alertTypeFilter} using AND when both are configured (an empty filter isn't
	 * applied). Only affects which alerts get an {@code Alert_XX} display group - the {@code ActiveAlerts}
	 * summary is unaffected and always reflects every alert.
	 */
	private Set<String> alertMonitoredCategoryFilter = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

	/**
	 * Retrieves {@link #alertTypeFilter}.
	 *
	 * @return value of {@link #alertTypeFilter}
	 */
	public String getAlertTypeFilter() {
		return String.join(",", alertTypeFilter);
	}

	/**
	 * Sets {@link #alertTypeFilter} value.
	 *
	 * @param alertTypeFilter new value of {@link #alertTypeFilter}, comma-separated
	 */
	public void setAlertTypeFilter(String alertTypeFilter) {
		this.alertTypeFilter = Arrays.stream(alertTypeFilter.split(","))
				.map(String::trim).filter(StringUtils::isNotNullOrEmpty)
				.collect(Collectors.toCollection(() -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER)));
	}

	/**
	 * Retrieves {@link #alertMonitoredCategoryFilter}.
	 *
	 * @return value of {@link #alertMonitoredCategoryFilter}
	 */
	public String getAlertMonitoredCategoryFilter() {
		return String.join(",", alertMonitoredCategoryFilter);
	}

	/**
	 * Sets {@link #alertMonitoredCategoryFilter} value.
	 *
	 * @param alertMonitoredCategoryFilter new value of {@link #alertMonitoredCategoryFilter}, comma-separated
	 */
	public void setAlertMonitoredCategoryFilter(String alertMonitoredCategoryFilter) {
		this.alertMonitoredCategoryFilter = Arrays.stream(alertMonitoredCategoryFilter.split(","))
				.map(String::trim).filter(StringUtils::isNotNullOrEmpty)
				.collect(Collectors.toCollection(() -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER)));
	}

	/**
	 * Maximum number of alerts displayed per device; alerts are sorted latest-{@link AlertProperty#EVENT_TIME}-first
	 * and anything beyond this many (per device) is dropped.
	 */
	private volatile int alertEventsTotal = 10;

	/**
	 * Retrieves {@link #alertEventsTotal}.
	 *
	 * @return value of {@link #alertEventsTotal}
	 */
	public String getAlertEventsTotal() {
		return String.valueOf(alertEventsTotal);
	}

	/**
	 * Sets {@link #alertEventsTotal} value; falls back to the default of 10 when invalid or non-positive.
	 *
	 * @param alertEventsTotal new value of {@link #alertEventsTotal}
	 */
	public void setAlertEventsTotal(String alertEventsTotal) {
		try {
			int parsed = Integer.parseInt(alertEventsTotal.trim());
			this.alertEventsTotal = parsed > 0 ? parsed : 10;
		} catch (Exception e) {
			this.alertEventsTotal = 10;
		}
	}

	/**
	 * Cached data
	 */
	private final Map<String, Map<String, String>> cachedMonitoringDevice = Collections.synchronizedMap(new HashMap<>());

	/**
	 * Cached GVE Room data, keyed by {@link RoomProperty#ID}.
	 */
	private final Map<String, Map<String, String>> cachedRooms = Collections.synchronizedMap(new HashMap<>());

	/**
	 * Cached GVE Location data, keyed by {@link LocationProperty#ID}.
	 */
	private final Map<String, Map<String, String>> cachedLocations = Collections.synchronizedMap(new HashMap<>());

	/**
	 * Cached GVE Controller data, keyed by {@link ControllerProperty#ID}.
	 */
	private final Map<String, Map<String, String>> cachedControllers = Collections.synchronizedMap(new HashMap<>());

	/**
	 * Cached GVE Alert data, keyed by {@link AlertProperty#DEVICE_ID}, each device holding its own list
	 * of alerts sorted latest-{@link AlertProperty#EVENT_TIME}-first and capped at {@link #alertEventsTotal}.
	 */
	private final Map<String, List<Map<String, String>>> cachedAlertsByDevice = Collections.synchronizedMap(new HashMap<>());

	/**
	 * Cached GVE Alert data, keyed by {@link AlertProperty#CONTROLLER_ID} - mirrors {@link #cachedAlertsByDevice}
	 * but for alerts routed to the controller they belong to (i.e. alerts with no resolvable
	 * {@link AlertProperty#DEVICE_ID}) instead of a device. Kept separate from {@link #cachedAlertsByDevice}
	 * since device IDs and controller IDs are independent numbering spaces and could otherwise collide.
	 */
	private final Map<String, List<Map<String, String>>> cachedAlertsByController = Collections.synchronizedMap(new HashMap<>());

	/**
	 * GVE command action IDs from {@link Constant#GVE_COMMANDS_ENDPOINT}, keyed by {@code ControllerType:Name}
	 * (e.g. {@code Device:Power}, {@code Controller:Front Panel Lockout}) so the right {@code ActionId} can be
	 * resolved for a given control without hardcoding IDs that could differ between GVE installations.
	 */
	private final Map<String, Integer> cachedActionIds = Collections.synchronizedMap(new HashMap<>());

	/**
	 * Cached "Aggregator &gt; GVE System" info from {@link Constant#SYSTEM_ENDPOINT}.
	 */
	private final Map<String, String> cachedSystemInfo = Collections.synchronizedMap(new HashMap<>());

	/**
	 * Cached "Aggregator &gt; GVE Monitoring Service" info from {@link Constant#MONITORING_SERVICE_ENDPOINT}.
	 */
	private final Map<String, String> cachedMonitoringServiceInfo = Collections.synchronizedMap(new HashMap<>());

	/**
	 * Cached "Aggregator &gt; GVE Scheduling Service" info from {@link Constant#SCHEDULING_SERVICE_ENDPOINT}.
	 */
	private final Map<String, String> cachedSchedulingServiceInfo = Collections.synchronizedMap(new HashMap<>());

	/**
	 * Cached "Aggregator &gt; GVE UDP Listener Service" info from {@link Constant#UDP_LISTENER_SERVICE_ENDPOINT}.
	 */
	private final Map<String, String> cachedUdpListenerServiceInfo = Collections.synchronizedMap(new HashMap<>());

	/**
	 * Cached GVE Model data, keyed by {@link ModelProperty#ID}.
	 */
	private final Map<String, Map<String, String>> cachedModels = Collections.synchronizedMap(new HashMap<>());

	/**
	 * Cached GVE Manufacturer data, keyed by {@link ManufacturerProperty#ID}.
	 */
	private final Map<String, Map<String, String>> cachedManufacturers = Collections.synchronizedMap(new HashMap<>());

	/**
	 * Timestamp of the last full {@link #cachedModels}/{@link #cachedManufacturers} refresh.
	 */
	private volatile long lastModelCacheRefreshTimestamp;

	/**
	 * Minimum allowed value for {@link #modelInfoRetrievalIntervalMillis} (1 hour); values entered below
	 * this are clamped up to it rather than rejected.
	 */
	private static final long MIN_MODEL_INFO_RETRIEVAL_INTERVAL_MILLIS = 3_600_000L;

	/**
	 * Milliseconds between full refreshes of {@link #cachedModels}/{@link #cachedManufacturers}.
	 */
	private volatile long modelInfoRetrievalIntervalMillis = 86_400_000L;

	/**
	 * Retrieves {@link #modelInfoRetrievalIntervalMillis}.
	 *
	 * @return value of {@link #modelInfoRetrievalIntervalMillis}
	 */
	public String getModelInfoRetrievalInterval() {
		return String.valueOf(modelInfoRetrievalIntervalMillis);
	}

	/**
	 * Sets {@link #modelInfoRetrievalIntervalMillis}.
	 *
	 * @param modelInfoRetrievalInterval new value, in milliseconds; falls back to 86,400,000 (24 hours) when
	 * invalid or non-positive, and is clamped up to {@link #MIN_MODEL_INFO_RETRIEVAL_INTERVAL_MILLIS} (1 hour)
	 * when positive but below it
	 */
	public void setModelInfoRetrievalInterval(String modelInfoRetrievalInterval) {
		try {
			long parsed = Long.parseLong(modelInfoRetrievalInterval.trim());
			this.modelInfoRetrievalIntervalMillis = parsed > 0 ? Math.max(parsed, MIN_MODEL_INFO_RETRIEVAL_INTERVAL_MILLIS) : 86_400_000L;
		} catch (Exception e) {
			this.modelInfoRetrievalIntervalMillis = 86_400_000L;
		}
	}

	/**
	 * Per-device alert summary (true, uncapped total count and the distinct {@link AlertProperty#TYPE}/
	 * {@link AlertProperty#MONITOR_NAME} values seen), keyed by {@link AlertProperty#DEVICE_ID}.
	 */
	private final Map<String, AlertSummary> cachedAlertSummaryByDevice = Collections.synchronizedMap(new HashMap<>());

	/**
	 * Per-controller alert summary, mirroring {@link #cachedAlertSummaryByDevice} but keyed by
	 * {@link AlertProperty#CONTROLLER_ID} for alerts routed to a controller instead of a device.
	 */
	private final Map<String, AlertSummary> cachedAlertSummaryByController = Collections.synchronizedMap(new HashMap<>());

	/**
	 * A device's true (uncapped) alert count and the distinct alert types/monitored categories seen across all of
	 * its alerts - backs the {@link Constant#ACTIVE_ALERTS_GROUP} group, shown whenever a device has
	 * more than one alert. {@code types}/{@code monitors} are kept in a case-insensitive {@link TreeSet} so
	 * they're always alphabetical, without needing a separate sort step wherever they're displayed.
	 */
	static final class AlertSummary {
		int totalCount;
		final Set<String> types = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		final Set<String> monitors = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
	}

	/**
	 * List of aggregated devices populated from {@link #cachedMonitoringDevice}.
	 */
	private final List<AggregatedDevice> aggregatedDeviceList = Collections.synchronizedList(new ArrayList<>());

	/**
	 * A mapper for reading and writing JSON using Jackson library.
	 * ObjectMapper provides functionality for converting between Java objects and JSON.
	 * It can be used to serialize objects to JSON format, and deserialize JSON data to objects.
	 */
	private final ObjectMapper objectMapper = new ObjectMapper();

	/**
	 * Raw Extron {@code DeviceType} -&gt; Symphony type/category, loaded once from
	 * {@code device-type-category-mapping.yml}. A {@code DeviceType} not present here (see
	 * {@code buildAggregatedDevice}) falls back to using the raw value itself as {@code category}, and leaves
	 * {@code type} unset (its own default) - this is also what happens for every {@code DeviceType} if the
	 * mapping file fails to load.
	 */
	private Map<String, DeviceTypeCategory> deviceTypeCategoryMapping = Collections.emptyMap();

	/**
	 * Update the status of the device.
	 * The device is considered as paused if did not receive any retrieveMultipleStatistics()
	 * calls during {@link GlobalViewerEnterpriseCommunicator}
	 */
	private synchronized void updateAggregatorStatus() {
		devicePaused = validRetrieveStatisticsTimestamp < System.currentTimeMillis();
	}

	/**
	 * Uptime time stamp to valid one, based on the current polling cycle interval
	 */
	private synchronized void updateValidRetrieveStatisticsTimestamp() {
		validRetrieveStatisticsTimestamp = System.currentTimeMillis() + getMonitoringRate() * 60 * 1000L;
		updateAggregatorStatus();
	}

	class GlobalViewerEnterpriseDataLoader implements Runnable {
		private volatile boolean inProgress;

		public GlobalViewerEnterpriseDataLoader() {
			inProgress = true;
		}

		@Override
		public void run() {
			loop:
			while (inProgress) {
				try {
					try {
						TimeUnit.MILLISECONDS.sleep(500);
					} catch (InterruptedException e) {
						logger.info(String.format("Sleep for 0.5 second was interrupted with error message: %s", e.getMessage()));
					}

					if (!inProgress) {
						break loop;
					}

					updateAggregatorStatus();
					if (devicePaused) {
						continue loop;
					}
					while (nextDevicesCollectionIterationTimestamp > System.currentTimeMillis()) {
						try {
							TimeUnit.MILLISECONDS.sleep(1000);
						} catch (InterruptedException e) {
							logger.info(String.format("Sleep for 1 second was interrupted with error message: %s", e.getMessage()));
						}
					}
					long startCycle = System.currentTimeMillis();
					try {
						if (isGroupDisplayed(Constant.GVE_ROOM_GROUP)) {
							if (logger.isDebugEnabled()) {
								logger.debug("Fetching rooms list");
							}
							populateRoomList();
						}
					} catch (Exception e) {
						logger.error("Error occurred during room list retrieval", e);
					}
					try {
						if (isGroupDisplayed(Constant.GVE_LOCATION_GROUP)) {
							if (logger.isDebugEnabled()) {
								logger.debug("Fetching locations list");
							}
							populateLocationList();
						}
					} catch (Exception e) {
						logger.error("Error occurred during location list retrieval", e);
					}
					try {
						if (logger.isDebugEnabled()) {
							logger.debug("Fetching GVE command actions");
						}
						populateActionList();
					} catch (Exception e) {
						logger.error("Error occurred during GVE command action retrieval", e);
					}
					try {
						if (isGroupDisplayed(Constant.GVE_SYSTEM_GROUP)) {
							if (logger.isDebugEnabled()) {
								logger.debug("Fetching system info");
							}
							populateSystemInfo();
						}
					} catch (Exception e) {
						logger.error("Error occurred during system info retrieval", e);
					}

					if (isGroupDisplayed(Constant.SERVICES_DISPLAY_GROUP)) {
						try {
							if (logger.isDebugEnabled()) {
								logger.debug("Fetching monitoring service info");
							}
							populateMonitoringServiceInfo();
						} catch (Exception e) {
							logger.error("Error occurred during monitoring service info retrieval", e);
						}
						try {
							if (logger.isDebugEnabled()) {
								logger.debug("Fetching scheduling service info");
							}
							populateSchedulingServiceInfo();
						} catch (Exception e) {
							logger.error("Error occurred during scheduling service info retrieval", e);
						}
						try {
							if (logger.isDebugEnabled()) {
								logger.debug("Fetching UDP listener service info");
							}
							populateUdpListenerServiceInfo();
						} catch (Exception e) {
							logger.error("Error occurred during UDP listener service info retrieval", e);
						}
					}

					try {
						if (logger.isDebugEnabled()) {
							logger.debug("Fetching controllers list");
						}
						populateControllerList();
					} catch (Exception e) {
						logger.error("Error occurred during controller list retrieval", e);
					}
					try {
						if (logger.isDebugEnabled()) {
							logger.debug("Fetching devices list");
						}
						populateListDevice();
					} catch (Exception e) {
						logger.error("Error occurred during device list retrieval", e);
					}
					try {
						if (isGroupDisplayed(Constant.ALERTS_DISPLAY_GROUP)) {
							if (logger.isDebugEnabled()) {
								logger.debug("Fetching alerts list");
							}
							populateAlertList();
						}
					} catch (Exception e) {
						logger.error("Error occurred during alert list retrieval", e);
					}
					try {
						if (logger.isDebugEnabled()) {
							logger.debug("Fetching model and manufacturer data");
						}
						populateModelAndManufacturerData();
					} catch (Exception e) {
						logger.error("Error occurred during model/manufacturer retrieval", e);
					}
					nextDevicesCollectionIterationTimestamp = System.currentTimeMillis() + (getMonitoringRate() * 60000L);
					lastMonitoringCycleDuration = Math.max((System.currentTimeMillis() - startCycle) / 1000, 1L);
					logger.debug("Finished collecting devices statistics cycle at " + new Date() + ", total duration: " + lastMonitoringCycleDuration);

					if (logger.isDebugEnabled()) {
						logger.debug("Finished collecting devices statistics cycle at " + new Date());
					}
				} catch (Exception e) {
					logger.error("Unexpected error occurred during main device collection cycle", e);
				}
			}
			logger.debug("Main device collection loop is completed, in progress marker: " + inProgress);
			// Finished collecting
		}

		/**
		 * Triggers main loop to stop
		 */
		public void stop() {
			inProgress = false;
		}
	}


	public GlobalViewerEnterpriseCommunicator() {
		super();
		this.versionProperties = new Properties();
		this.adapterInitializationTimestamp = System.currentTimeMillis();
		this.localExtendedStatistics = new ExtendedStatistics();
		this.localExtendedStatistics.setStatistics(new HashMap<>());
	}

	@Override
	protected void internalInit() throws Exception {
		super.internalInit();
		if (logger.isDebugEnabled()) {
			logger.debug("Internal init is called.");
		}
		executorService = Executors.newFixedThreadPool(1);
		executorService.submit(deviceDataLoader = new GlobalViewerEnterpriseDataLoader());
		this.loadVersionProperties(this.versionProperties);
		this.deviceTypeCategoryMapping = this.loadDeviceTypeCategoryMapping();
	}

	@Override
	protected void internalDestroy() {
		if (deviceDataLoader != null) {
			deviceDataLoader.stop();
			deviceDataLoader = null;
		}
		if (executorService != null) {
			executorService.shutdownNow();
			executorService = null;
		}
		this.versionProperties.clear();
		this.deviceTypeCategoryMapping = Collections.emptyMap();
		cachedMonitoringDevice.clear();
		cachedRooms.clear();
		cachedLocations.clear();
		cachedActionIds.clear();
		cachedSystemInfo.clear();
		cachedMonitoringServiceInfo.clear();
		cachedSchedulingServiceInfo.clear();
		cachedUdpListenerServiceInfo.clear();
		cachedControllers.clear();
		cachedAlertsByDevice.clear();
		cachedAlertsByController.clear();
		cachedModels.clear();
		cachedManufacturers.clear();
		cachedAlertSummaryByDevice.clear();
		cachedAlertSummaryByController.clear();
		aggregatedDeviceList.clear();
		this.localExtendedStatistics.getStatistics().clear();
		super.internalDestroy();
	}

	@Override
	public List<Statistics> getMultipleStatistics() throws Exception {
		this.reentrantLock.lock();
		try {
			this.authenticate();
			updateActivePropertyGroups();
			var statistics = new HashMap<>(MonitoringUtil.generateProperties(
					General.values(), null, property -> MonitoringUtil.mapToGeneral(this.versionProperties, property)
			));
			if (isGroupDisplayed(Constant.GVE_ROOM_GROUP)) {
				putIndexedGroupedProperties(statistics, cachedRooms, RoomProperty.values());
			}
			if (isGroupDisplayed(Constant.GVE_LOCATION_GROUP)) {
				putIndexedGroupedProperties(statistics, cachedLocations, LocationProperty.values());
			}
			if (isGroupDisplayed(Constant.GVE_SYSTEM_GROUP)) {
				for (SystemProperty property : SystemProperty.values()) {
					putGroupedProperty(statistics, cachedSystemInfo, property);
				}
			}
			if (isGroupDisplayed(Constant.SERVICES_DISPLAY_GROUP)) {
				for (ServiceProperty property : ServiceProperty.values()) {
					putGroupedProperty(statistics, cachedMonitoringServiceInfo, property, Constant.MONITORING_SERVICE_GROUP);
				}
				for (ServiceProperty property : ServiceProperty.values()) {
					putGroupedProperty(statistics, cachedSchedulingServiceInfo, property, Constant.SCHEDULING_SERVICE_GROUP);
				}
				for (ServiceProperty property : ServiceProperty.values()) {
					putGroupedProperty(statistics, cachedUdpListenerServiceInfo, property, Constant.UDP_LISTENER_SERVICE_GROUP);
				}
			}

			List<AdvancedControllableProperty> controls = new ArrayList<>();
			if (isGroupDisplayed(Constant.ALERTS_DISPLAY_GROUP)) {
				putAlertActions(statistics, controls);
			}

			Map<String, String> dynamicStatistics = new HashMap<>();
			dynamicStatistics.put(Constant.MONITORED_DEVICES_TOTAL, String.valueOf(cachedMonitoringDevice.size()));
			dynamicStatistics.put(Constant.LAST_MONITORING_CYCLE_DURATION, String.valueOf(lastMonitoringCycleDuration));

			this.localExtendedStatistics.setStatistics(statistics);
			this.localExtendedStatistics.setDynamicStatistics(dynamicStatistics);
			this.localExtendedStatistics.setControllableProperties(controls);
		} finally {
			this.reentrantLock.unlock();
		}
		return Collections.singletonList(this.localExtendedStatistics);
	}

	/**
	 * Adds {@link Constant#ALERT_ACTIONS_GROUP}'s delete buttons, one per entity type that currently has
	 * at least one alert (per {@link #cachedAlertSummaryByDevice}/{@link #cachedAlertSummaryByController}).
	 */
	private void putAlertActions(Map<String, String> statistics, List<AdvancedControllableProperty> controls) {
		if (!cachedAlertSummaryByDevice.isEmpty()) {
			String name = String.format(Constant.PROPERTY_FORMAT, Constant.ALERT_ACTIONS_GROUP, Constant.DELETE_DEVICE_ALERTS_PROPERTY);
			Util.addAdvancedControlProperties(controls, statistics,
					ControllablePropertyFactory.createButton(name, Constant.DELETE_BUTTON_LABEL, Constant.DELETE_BUTTON_LABEL, 0L), Constant.NONE);
		}
		if (!cachedAlertSummaryByController.isEmpty()) {
			String name = String.format(Constant.PROPERTY_FORMAT, Constant.ALERT_ACTIONS_GROUP, Constant.DELETE_CONTROLLER_ALERTS_PROPERTY);
			Util.addAdvancedControlProperties(controls, statistics,
					ControllablePropertyFactory.createButton(name, Constant.DELETE_BUTTON_LABEL, Constant.DELETE_BUTTON_LABEL, 0L), Constant.NONE);
		}
	}

	@Override
	public List<AggregatedDevice> retrieveMultipleStatistics() throws Exception {
		if (executorService == null || executorService.isTerminated() || executorService.isShutdown()) {
			if (logger.isDebugEnabled()) {
				logger.debug("Restarting executor service and initializing with the new data loader");
			}
			executorService = Executors.newFixedThreadPool(1);
			executorService.submit(deviceDataLoader = new GlobalViewerEnterpriseDataLoader());
		}
		nextDevicesCollectionIterationTimestamp = System.currentTimeMillis();
		updateValidRetrieveStatisticsTimestamp();
		if (cachedMonitoringDevice.isEmpty() && cachedControllers.isEmpty()) {
			return Collections.emptyList();
		}
		return cloneAndPopulateAggregatedDeviceList();
	}

	@Override
	public List<AggregatedDevice> retrieveMultipleStatistics(List<String> deviceIds) throws Exception {
		return retrieveMultipleStatistics()
				.stream()
				.filter(aggregatedDevice -> deviceIds.contains(aggregatedDevice.getDeviceId()))
				.collect(Collectors.toList());
	}

	@Override
	public void controlProperty(ControllableProperty controllableProperty) throws Exception {
		String property = controllableProperty.getProperty();
		if (StringUtils.isNullOrEmpty(property, true)) {
			return;
		}

		// Adapter-level controls have no device ID, so handle them before the routing below.
		if (String.format(Constant.PROPERTY_FORMAT, Constant.ALERT_ACTIONS_GROUP, Constant.DELETE_DEVICE_ALERTS_PROPERTY).equals(property)) {
			deleteDeviceAlerts();
			return;
		}
		if (String.format(Constant.PROPERTY_FORMAT, Constant.ALERT_ACTIONS_GROUP, Constant.DELETE_CONTROLLER_ALERTS_PROPERTY).equals(property)) {
			deleteControllerAlerts();
			return;
		}

		String aggregatedDeviceId = controllableProperty.getDeviceId();
		if (StringUtils.isNullOrEmpty(aggregatedDeviceId, true)) {
			return;
		}
		int value = parseControlValue(controllableProperty.getValue());

		if (aggregatedDeviceId.startsWith(Constant.DEVICE_ID_PREFIX)) {
			String rawDeviceId = aggregatedDeviceId.substring(Constant.DEVICE_ID_PREFIX.length());
			if (Constant.POWER_PROPERTY.equals(property)) {
				sendDeviceCommand(rawDeviceId, Constant.ACTION_NAME_POWER, value);
				updateCachedReadback(cachedMonitoringDevice, rawDeviceId, AggregatedGeneralProperty.POWER_STATUS.getName(), value == 1 ? Constant.ON : Constant.OFF);
			}
			return;
		}
		if (!aggregatedDeviceId.startsWith(Constant.CONTROLLER_ID_PREFIX)) {
			return;
		}

		String rawControllerId = aggregatedDeviceId.substring(Constant.CONTROLLER_ID_PREFIX.length());
		if (Constant.POWER_PROPERTY.equals(property)) {
			sendControllerCommand(rawControllerId, Constant.ACTION_NAME_POWER, value);
			updateCachedReadback(cachedControllers, rawControllerId, ControllerProperty.STATUS.getName(), value == 1 ? Constant.ACTIVE : Constant.INACTIVE);
		}
	}

	/**
	 * Optimistically updates a cached readback field right after a GVE command succeeds, so the next poll
	 * reflects it immediately. No-ops if the entity isn't cached.
	 *
	 * @param cache the outer cache map ({@link #cachedMonitoringDevice} or {@link #cachedControllers})
	 * @param rawId the entity's raw (unprefixed) ID - the cache key
	 * @param fieldName the readback field to update
	 * @param value the new value to reflect
	 */
	private static void updateCachedReadback(Map<String, Map<String, String>> cache, String rawId, String fieldName, String value) {
		synchronized (cache) {
			Map<String, String> entry = cache.get(rawId);
			if (entry != null) {
				entry.put(fieldName, value);
			}
		}
	}

	/**
	 * Parses a raw controllable property value (typically {@code "0"}/{@code "1"} for a switch, but
	 * tolerates e.g. {@code "1.0"}) into an {@code int}, defaulting to {@code 0} when missing/unparsable.
	 *
	 * @param rawValue the value from {@link ControllableProperty#getValue()}
	 * @return the parsed value, or {@code 0} if it can't be parsed
	 */
	private static int parseControlValue(Object rawValue) {
		if (rawValue == null) {
			return 0;
		}
		try {
			return (int) Double.parseDouble(String.valueOf(rawValue).trim());
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	/**
	 * Sends a {@link Constant#DEVICE_COMMAND_ENDPOINT} command for the given device, resolving its
	 * {@code ActionId} via {@link #cachedActionIds} (keyed by {@link Constant#ACTION_CONTROLLER_TYPE_DEVICE}).
	 *
	 * @param deviceId the device's raw (unprefixed) ID
	 * @param actionName the action's {@code Name} (e.g. {@link Constant#ACTION_NAME_POWER})
	 * @param value the command value ({@code 0}/{@code 1} for on-off/lock-unlock actions)
	 * @throws Exception if the action can't be resolved or the request itself fails
	 */
	private void sendDeviceCommand(String deviceId, String actionName, int value) throws Exception {
		Integer actionId = cachedActionIds.get(actionKey(Constant.ACTION_CONTROLLER_TYPE_DEVICE, actionName));
		if (actionId == null) {
			throw new IllegalStateException("No GVE action found for Device action '" + actionName + "'");
		}
		Map<String, Object> body = new HashMap<>();
		body.put("ActionId", actionId);
		body.put("DeviceId", Integer.parseInt(deviceId));
		body.put("Value", value);
		String requestBody = objectMapper.writeValueAsString(body);
		String response = this.withSessionRecovery(() -> this.doPost(Constant.DEVICE_COMMAND_ENDPOINT, requestBody));
		validateCommandResponse(response);
	}

	/**
	 * Sends a {@link Constant#CONTROLLER_COMMAND_ENDPOINT} command for the given controller, resolving its
	 * {@code ActionId} via {@link #cachedActionIds} (keyed by {@link Constant#CONTROLLER}).
	 *
	 * @param controllerId the controller's raw (unprefixed) ID
	 * @param actionName the action's {@code Name} (e.g. {@link Constant#ACTION_NAME_POWER})
	 * @param value the command value ({@code 0}/{@code 1} for on-off/lock-unlock actions)
	 * @throws Exception if the action can't be resolved or the request itself fails
	 */
	private void sendControllerCommand(String controllerId, String actionName, int value) throws Exception {
		Integer actionId = cachedActionIds.get(actionKey(Constant.CONTROLLER, actionName));
		if (actionId == null) {
			throw new IllegalStateException("No GVE action found for Controller action '" + actionName + "'");
		}
		Map<String, Object> body = new HashMap<>();
		body.put("ActionId", actionId);
		body.put("ControllerId", Integer.parseInt(controllerId));
		body.put("Value", value);
		String requestBody = objectMapper.writeValueAsString(body);
		String response = this.withSessionRecovery(() -> this.doPost(Constant.CONTROLLER_COMMAND_ENDPOINT, requestBody));
		validateCommandResponse(response);
	}

	/** Deletes every device alert on the GVE server (not scoped to any filter). */
	private void deleteDeviceAlerts() throws Exception {
		String response = this.withSessionRecovery(() -> this.doPut(Constant.ALERTS_DELETE_DEVICES_ENDPOINT, null, String.class));
		validateCommandResponse(response);
	}

	/** Deletes every controller alert on the GVE server (not scoped to any filter). */
	private void deleteControllerAlerts() throws Exception {
		String response = this.withSessionRecovery(() -> this.doPut(Constant.ALERTS_DELETE_CONTROLLERS_ENDPOINT, null, String.class));
		validateCommandResponse(response);
	}

	/**
	 * Checks a GVE command response's {@code ResponseStatus} and throws if it carries a non-blank
	 * {@code ErrorCode}.
	 */
	private void validateCommandResponse(String response) throws Exception {
		APIResponse apiResponse = objectMapper.readValue(response, APIResponse.class);
		APIResponse.ResponseStatus status = apiResponse.getResponseStatus();
		if (status != null && StringUtils.isNotNullOrEmpty(status.getErrorCode())) {
			throw new RuntimeException("GVE command failed: " + status.getMessage());
		}
	}

	@Override
	public void controlProperties(List<ControllableProperty> controllableProperties) throws Exception {
		if (CollectionUtils.isEmpty(controllableProperties)) {
			return;
		}
		for (ControllableProperty controllableProperty : controllableProperties) {
			this.controlProperty(controllableProperty);
		}
	}

	/**
	 * Loads version properties and sets initial values used to create Adapter metadata group.
	 *
	 * @param versionProperties the properties to load and set default values
	 */
	private void loadVersionProperties(Properties versionProperties) {
		try {
			versionProperties.load(this.getClass().getResourceAsStream("/version.properties"));
			versionProperties.setProperty(General.ADAPTER_UPTIME.getProperty(), String.valueOf(this.adapterInitializationTimestamp));
			versionProperties.setProperty(General.MONITORING_CYCLE_INTERVAL.getProperty(), String.valueOf(this.getMonitoringRate()));
		} catch (IOException e) {
			this.logger.error(Constant.READ_PROPERTIES_FILE_FAILED, e);
		}
	}

	/** Refreshes {@link General#ACTIVE_PROPERTY_GROUPS} from the current {@link #displayPropertyGroups}. */
	private void updateActivePropertyGroups() {
		String activeGroups = displayPropertyGroups.stream().sorted().collect(Collectors.joining(","));
		versionProperties.setProperty(General.ACTIVE_PROPERTY_GROUPS.getProperty(),
				StringUtils.isNotNullOrEmpty(activeGroups) ? activeGroups : Constant.NOT_AVAILABLE);
	}

	/**
	 * Loads {@link #deviceTypeCategoryMapping} from {@code device-type-category-mapping.yml}. On failure, logs
	 * the error and returns an empty map, so every {@code DeviceType} falls back to using its raw value as the
	 * category, and {@code type} is left unset, instead of the adapter failing to start.
	 *
	 * @return the parsed mapping, or an empty map if the resource couldn't be read/parsed
	 */
	private Map<String, DeviceTypeCategory> loadDeviceTypeCategoryMapping() {
		try {
			YAMLMapper yamlMapper = new YAMLMapper();
			return yamlMapper.readValue(this.getClass().getResourceAsStream("/catalogue/device-type-category-mapping.yml"),
					new TypeReference<Map<String, DeviceTypeCategory>>() {});
		} catch (IOException e) {
			this.logger.error("Failed to load device-type-category-mapping.yml, DeviceType will be used as category as-is.", e);
			return Collections.emptyMap();
		}
	}

	/**
	 * Populates {@link #cachedMonitoringDevice} by making a GET request to {@link Constant#DEVICES_ENDPOINT}.
	 */
	private void populateListDevice() {
		try {
			Map<String, Map<String, String>> nextDeviceCache = fetchEntityList(Constant.DEVICES_ENDPOINT, Constant.DEVICES,
					AggregatedGeneralProperty.DEVICE_ID, AggregatedGeneralProperty.values());
			nextDeviceCache.entrySet().removeIf(entry -> !matchesDeviceFilters(entry.getValue()));
			synchronized (cachedMonitoringDevice) {
				cachedMonitoringDevice.clear();
				cachedMonitoringDevice.putAll(nextDeviceCache);
			}
		} catch (Exception e) {
			throw new RuntimeException("Unable to retrieve devices from response.", e);
		}
	}

	/**
	 * Checks whether a device matches {@link #roomFilter} and {@link #locationFilter}.
	 *
	 * @param cachedData the cached property name/value pairs for the device
	 * @return {@code true} if the device should be monitored
	 */
	private boolean matchesDeviceFilters(Map<String, String> cachedData) {
		return matchesRoomAndLocationFilters(cachedData.get(AggregatedGeneralProperty.ROOM_ID.getName()));
	}

	/**
	 * Checks whether a controller matches {@link #roomFilter} and {@link #locationFilter}.
	 *
	 * @param cachedData the cached property name/value pairs for the controller
	 * @return {@code true} if the controller should be monitored
	 */
	private boolean matchesControllerFilters(Map<String, String> cachedData) {
		return matchesRoomAndLocationFilters(cachedData.get(ControllerProperty.ROOM_ID.getName()));
	}

	/**
	 * Checks whether {@code roomId} (and, transitively, the location it belongs to) matches
	 * {@link #roomFilter} and {@link #locationFilter} (both must match when configured; an empty
	 * filter is not applied).
	 *
	 * @param roomId the entity's resolved room ID, or {@code null} if unresolved
	 * @return {@code true} if the entity should be monitored
	 */
	private boolean matchesRoomAndLocationFilters(String roomId) {
		if (!roomFilter.isEmpty() && !roomFilter.contains(roomId)) {
			return false;
		}
		if (!locationFilter.isEmpty()) {
			Map<String, String> room = roomId == null ? Collections.emptyMap() : cachedRooms.getOrDefault(roomId, Collections.emptyMap());
			String locationId = room.get(RoomProperty.LOCATION_ID.getName());
			if (!locationFilter.contains(locationId)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Checks whether an alert matches {@link #alertTypeFilter} and {@link #alertMonitoredCategoryFilter}
	 * (both must match when configured, matched case-insensitively; an empty filter is not applied).
	 * Only gates whether the alert is added to {@link #cachedAlertsByDevice}/{@link #cachedAlertsByController}
	 * (the displayed {@code Alert_XX} groups) - the entity's {@link AlertSummary} always reflects every alert
	 * regardless of this filter.
	 *
	 * @param alert the alert's property name/value pairs, as built by {@link #parseAlerts}
	 * @return {@code true} if the alert should be displayed
	 */
	private boolean matchesAlertFilters(Map<String, String> alert) {
		if (!alertTypeFilter.isEmpty() && !alertTypeFilter.contains(alert.get(AlertProperty.TYPE.getName()))) {
			return false;
		}
		if (!alertMonitoredCategoryFilter.isEmpty() && !alertMonitoredCategoryFilter.contains(alert.get(AlertProperty.MONITOR_NAME.getName()))) {
			return false;
		}
		return true;
	}

	/**
	 * Populates {@link #cachedRooms} by making a GET request to {@link Constant#ROOMS_ENDPOINT}.
	 */
	private void populateRoomList() {
		try {
			Map<String, Map<String, String>> nextRoomCache = fetchEntityList(Constant.ROOMS_ENDPOINT, Constant.ROOMS,
					RoomProperty.ID, RoomProperty.values());
			synchronized (cachedRooms) {
				cachedRooms.clear();
				cachedRooms.putAll(nextRoomCache);
			}
		} catch (Exception e) {
			throw new RuntimeException("Unable to retrieve rooms from response.", e);
		}
	}

	/**
	 * Populates {@link #cachedLocations} by making a GET request to {@link Constant#LOCATIONS_ENDPOINT}.
	 */
	private void populateLocationList() {
		try {
			Map<String, Map<String, String>> nextLocationCache = fetchEntityList(Constant.LOCATIONS_ENDPOINT, Constant.LOCATIONS,
					LocationProperty.ID, LocationProperty.values());
			synchronized (cachedLocations) {
				cachedLocations.clear();
				cachedLocations.putAll(nextLocationCache);
			}
		} catch (Exception e) {
			throw new RuntimeException("Unable to retrieve locations from response.", e);
		}
	}

	/**
	 * Populates {@link #cachedActionIds} by making a GET request to {@link Constant#GVE_COMMANDS_ENDPOINT},
	 * resolving each action's {@code ActionId} by its own {@code ControllerType}/{@code Name} rather than
	 * hardcoding IDs, since these could differ between GVE installations.
	 */
	private void populateActionList() {
		try {
			String jsonResult = this.withSessionRecovery(() -> this.doGet(Constant.GVE_COMMANDS_ENDPOINT));
			JsonNode listResponse = objectMapper.readTree(jsonResult);
			Map<String, Integer> nextActionIds = new HashMap<>();
			if (listResponse != null && listResponse.has(Constant.ACTIONS) && listResponse.get(Constant.ACTIONS).isArray()) {
				for (JsonNode node : listResponse.path(Constant.ACTIONS)) {
					String name = node.path("Name").asText(null);
					String controllerType = node.path("ControllerType").asText(null);
					int actionId = node.path("ActionId").asInt(-1);
					if (StringUtils.isNullOrEmpty(name, true) || StringUtils.isNullOrEmpty(controllerType, true) || actionId < 0) {
						continue;
					}
					nextActionIds.put(actionKey(controllerType, name), actionId);
				}
			}
			synchronized (cachedActionIds) {
				cachedActionIds.clear();
				cachedActionIds.putAll(nextActionIds);
			}
		} catch (Exception e) {
			throw new RuntimeException("Unable to retrieve GVE command actions from response.", e);
		}
	}

	/**
	 * Builds the {@link #cachedActionIds} lookup key for a given action.
	 *
	 * @param controllerType the action's {@code ControllerType} (e.g. {@link Constant#ACTION_CONTROLLER_TYPE_DEVICE}, {@link Constant#CONTROLLER})
	 * @param actionName the action's {@code Name} (e.g. {@link Constant#ACTION_NAME_POWER})
	 * @return the composite lookup key
	 */
	private static String actionKey(String controllerType, String actionName) {
		return controllerType + ":" + actionName;
	}

	/**
	 * Populates {@link #cachedSystemInfo} by making a GET request to {@link Constant#SYSTEM_ENDPOINT}. Unlike
	 * most other endpoints, the response is a flat object with no wrapper key, so it's parsed directly rather
	 * than through {@link #fetchSingleEntity}.
	 */
	private void populateSystemInfo() {
		try {
			String jsonResult = this.withSessionRecovery(() -> this.doGet(Constant.SYSTEM_ENDPOINT));
			JsonNode response = objectMapper.readTree(jsonResult);
			Map<String, String> nextSystemInfo = new HashMap<>();
			if (response != null) {
				for (SystemProperty property : SystemProperty.values()) {
					String value = extractValue(response, property);
					if (value == null) {
						continue;
					}
					nextSystemInfo.put(property.getName(), value);
				}
			}
			synchronized (cachedSystemInfo) {
				cachedSystemInfo.clear();
				cachedSystemInfo.putAll(nextSystemInfo);
			}
		} catch (Exception e) {
			throw new RuntimeException("Unable to retrieve system info from response.", e);
		}
	}

	/**
	 * Populates {@link #cachedMonitoringServiceInfo} by making a GET request to
	 * {@link Constant#MONITORING_SERVICE_ENDPOINT}.
	 */
	private void populateMonitoringServiceInfo() {
		try {
			Map<String, String> nextServiceInfo = fetchSingleEntity(Constant.MONITORING_SERVICE_ENDPOINT, Constant.WINDOWS_SERVICE, ServiceProperty.values());
			synchronized (cachedMonitoringServiceInfo) {
				cachedMonitoringServiceInfo.clear();
				if (nextServiceInfo != null) {
					cachedMonitoringServiceInfo.putAll(nextServiceInfo);
				}
			}
		} catch (Exception e) {
			throw new RuntimeException("Unable to retrieve monitoring service info from response.", e);
		}
	}

	/**
	 * Populates {@link #cachedSchedulingServiceInfo} by making a GET request to
	 * {@link Constant#SCHEDULING_SERVICE_ENDPOINT}.
	 */
	private void populateSchedulingServiceInfo() {
		try {
			Map<String, String> nextServiceInfo = fetchSingleEntity(Constant.SCHEDULING_SERVICE_ENDPOINT, Constant.WINDOWS_SERVICE, ServiceProperty.values());
			synchronized (cachedSchedulingServiceInfo) {
				cachedSchedulingServiceInfo.clear();
				if (nextServiceInfo != null) {
					cachedSchedulingServiceInfo.putAll(nextServiceInfo);
				}
			}
		} catch (Exception e) {
			throw new RuntimeException("Unable to retrieve scheduling service info from response.", e);
		}
	}

	/**
	 * Populates {@link #cachedUdpListenerServiceInfo} by making a GET request to
	 * {@link Constant#UDP_LISTENER_SERVICE_ENDPOINT}.
	 */
	private void populateUdpListenerServiceInfo() {
		try {
			Map<String, String> nextServiceInfo = fetchSingleEntity(Constant.UDP_LISTENER_SERVICE_ENDPOINT, Constant.WINDOWS_SERVICE, ServiceProperty.values());
			synchronized (cachedUdpListenerServiceInfo) {
				cachedUdpListenerServiceInfo.clear();
				if (nextServiceInfo != null) {
					cachedUdpListenerServiceInfo.putAll(nextServiceInfo);
				}
			}
		} catch (Exception e) {
			throw new RuntimeException("Unable to retrieve UDP listener service info from response.", e);
		}
	}

	/**
	 * Populates {@link #cachedControllers} by making a GET request to {@link Constant#CONTROLLERS_ENDPOINT},
	 * filtered by {@link #roomFilter}/{@link #locationFilter} the same way monitored devices are.
	 */
	private void populateControllerList() {
		try {
			Map<String, Map<String, String>> nextControllerCache = fetchEntityList(Constant.CONTROLLERS_ENDPOINT, Constant.CONTROLLERS,
					ControllerProperty.ID, ControllerProperty.values());
			nextControllerCache.entrySet().removeIf(entry -> !matchesControllerFilters(entry.getValue()));
			synchronized (cachedControllers) {
				cachedControllers.clear();
				cachedControllers.putAll(nextControllerCache);
			}
		} catch (Exception e) {
			throw new RuntimeException("Unable to retrieve controllers from response.", e);
		}
	}

	/**
	 * Populates {@link #cachedAlertsByDevice}/{@link #cachedAlertsByController} by making a GET request to
	 * {@link Constant#ALERTS_ENDPOINT}, routing each alert to whichever it belongs to (see {@link #parseAlerts}).
	 */
	private void populateAlertList() {
		try {
			String jsonResult = this.withSessionRecovery(() -> this.doGet(Constant.ALERTS_ENDPOINT));
			Map<String, List<Map<String, String>>> nextDeviceAlertCache = new HashMap<>();
			Map<String, AlertSummary> nextDeviceAlertSummaryCache = new HashMap<>();
			Map<String, List<Map<String, String>>> nextControllerAlertCache = new HashMap<>();
			Map<String, AlertSummary> nextControllerAlertSummaryCache = new HashMap<>();
			parseAlerts(jsonResult, nextDeviceAlertCache, nextDeviceAlertSummaryCache, nextControllerAlertCache, nextControllerAlertSummaryCache);
			synchronized (cachedAlertsByDevice) {
				cachedAlertsByDevice.clear();
				cachedAlertsByDevice.putAll(nextDeviceAlertCache);
			}
			synchronized (cachedAlertSummaryByDevice) {
				cachedAlertSummaryByDevice.clear();
				cachedAlertSummaryByDevice.putAll(nextDeviceAlertSummaryCache);
			}
			synchronized (cachedAlertsByController) {
				cachedAlertsByController.clear();
				cachedAlertsByController.putAll(nextControllerAlertCache);
			}
			synchronized (cachedAlertSummaryByController) {
				cachedAlertSummaryByController.clear();
				cachedAlertSummaryByController.putAll(nextControllerAlertSummaryCache);
			}
		} catch (Exception e) {
			throw new RuntimeException("Unable to retrieve alerts from response.", e);
		}
	}

	/**
	 * Parses a raw {@link Constant#ALERTS_ENDPOINT} response, routing each alert by which ID resolves:
	 * a resolvable {@link AlertProperty#DEVICE_ID} makes it a device alert (grouped under
	 * {@code deviceAlertCache}/{@code deviceAlertSummaryCache}), otherwise a resolvable
	 * {@link AlertProperty#CONTROLLER_ID} makes it a controller alert (grouped under
	 * {@code controllerAlertCache}/{@code controllerAlertSummaryCache}); alerts with neither are dropped
	 * entirely. Device alerts also carry their own {@link AlertProperty#CONTROLLER_ID} (the controller they're
	 * connected through) - that's just informational for them, not used for routing. {@link #matchesAlertFilters}
	 * only controls which alerts make it into the {@code *AlertCache} maps (the displayed {@code Alert_XX}
	 * groups) - the {@code *AlertSummaryCache} maps always reflect every alert, filtered or not.
	 *
	 * @param jsonResult the raw JSON response body
	 * @param deviceAlertCache destination for device alerts, filtered by {@link #matchesAlertFilters}, sorted
	 * latest-{@link AlertProperty#EVENT_TIME}-first and capped at {@link #alertEventsTotal} per device
	 * @param deviceAlertSummaryCache destination for each device's true (unfiltered, uncapped) {@link AlertSummary}
	 * @param controllerAlertCache same as {@code deviceAlertCache}, but for controller alerts
	 * @param controllerAlertSummaryCache same as {@code deviceAlertSummaryCache}, but for controller alerts
	 * @throws Exception if the response cannot be parsed
	 */
	void parseAlerts(String jsonResult, Map<String, List<Map<String, String>>> deviceAlertCache, Map<String, AlertSummary> deviceAlertSummaryCache,
			Map<String, List<Map<String, String>>> controllerAlertCache, Map<String, AlertSummary> controllerAlertSummaryCache) throws Exception {
		JsonNode listResponse = objectMapper.readTree(jsonResult);
		if (listResponse != null && listResponse.has(Constant.ALERTS) && !listResponse.get(Constant.ALERTS).isEmpty()) {
			for (JsonNode node : listResponse.path(Constant.ALERTS)) {
				String deviceId = extractValue(node, AlertProperty.DEVICE_ID);
				String controllerId = extractValue(node, AlertProperty.CONTROLLER_ID);
				boolean isDeviceAlert = !Constant.NOT_AVAILABLE.equals(deviceId);
				boolean isControllerAlert = !isDeviceAlert && !Constant.NOT_AVAILABLE.equals(controllerId);
				if (!isDeviceAlert && !isControllerAlert) {
					continue;
				}
				String ownerId = isDeviceAlert ? deviceId : controllerId;
				Map<String, List<Map<String, String>>> alertCache = isDeviceAlert ? deviceAlertCache : controllerAlertCache;
				Map<String, AlertSummary> alertSummaryCache = isDeviceAlert ? deviceAlertSummaryCache : controllerAlertSummaryCache;

				Map<String, String> alert = new HashMap<>();
				for (AlertProperty property : AlertProperty.values()) {
					String value = extractValue(node, property);
					if (value == null) {
						continue;
					}
					alert.put(property.getName(), value);
				}

				// The summary always reflects every alert regardless of alertTypeFilter/alertMonitoredCategoryFilter -
				// those filters only decide what's added to alertCache below (the displayed Alert_XX groups).
				AlertSummary summary = alertSummaryCache.computeIfAbsent(ownerId, id -> new AlertSummary());
				summary.totalCount++;
				String type = alert.get(AlertProperty.TYPE.getName());
				if (StringUtils.isNotNullOrEmpty(type) && !Constant.NOT_AVAILABLE.equals(type)) {
					summary.types.add(type);
				}
				String monitor = alert.get(AlertProperty.MONITOR_NAME.getName());
				if (StringUtils.isNotNullOrEmpty(monitor) && !Constant.NOT_AVAILABLE.equals(monitor)) {
					summary.monitors.add(monitor);
				}

				if (matchesAlertFilters(alert)) {
					alertCache.computeIfAbsent(ownerId, id -> new ArrayList<>()).add(alert);
				}
			}
		}
		sortAndCapAlerts(deviceAlertCache);
		sortAndCapAlerts(controllerAlertCache);
	}

	/**
	 * Sorts each entry's alerts latest-{@link AlertProperty#EVENT_TIME}-first, then drops everything beyond
	 * {@link #alertEventsTotal}. Sorting happens before capping so the alerts that survive the cap are
	 * genuinely the most recent ones, rather than an arbitrary prefix in whatever order the API returned them
	 * in. The true count/type/monitor values tracked separately via the matching {@code AlertSummary} cache
	 * are unaffected by this cap.
	 *
	 * @param alertCache the alert cache to sort/cap in place
	 */
	private void sortAndCapAlerts(Map<String, List<Map<String, String>>> alertCache) {
		for (List<Map<String, String>> alerts : alertCache.values()) {
			alerts.sort(Comparator.comparing(GlobalViewerEnterpriseCommunicator::parseEventTime).reversed());
			if (alerts.size() > alertEventsTotal) {
				alerts.subList(alertEventsTotal, alerts.size()).clear();
			}
		}
	}

	/**
	 * Resolves an alert's {@link AlertProperty#EVENT_TIME} for sorting purposes, tolerating missing or
	 * malformed values by sorting them last (oldest) rather than throwing or sorting them first.
	 *
	 * @param alert the alert's property name/value pairs, as built by {@link #parseAlerts}
	 * @return the parsed {@link Instant}, or {@link Instant#MIN} if the value is missing/unparsable
	 */
	private static Instant parseEventTime(Map<String, String> alert) {
		String value = alert.get(AlertProperty.EVENT_TIME.getName());
		if (StringUtils.isNullOrEmpty(value, true) || Constant.NOT_AVAILABLE.equals(value)) {
			return Instant.MIN;
		}
		try {
			return Instant.parse(value);
		} catch (DateTimeParseException e) {
			return Instant.MIN;
		}
	}

	/**
	 * Resolves {@link #cachedModels}/{@link #cachedManufacturers} for every distinct model/manufacturer ID
	 * referenced by {@link #cachedMonitoringDevice}, fetching only IDs not already cached. Once every
	 * {@link #modelInfoRetrievalIntervalMillis}, both caches are cleared first so renamed/removed entries are
	 * picked up again.
	 */
	private void populateModelAndManufacturerData() {
		if (System.currentTimeMillis() - lastModelCacheRefreshTimestamp >= modelInfoRetrievalIntervalMillis) {
			cachedModels.clear();
			cachedManufacturers.clear();
			lastModelCacheRefreshTimestamp = System.currentTimeMillis();
		}

		Set<String> modelIds;
		synchronized (cachedMonitoringDevice) {
			modelIds = cachedMonitoringDevice.values().stream()
					.map(device -> device.get(AggregatedGeneralProperty.MODEL_ID.getName()))
					.filter(StringUtils::isNotNullOrEmpty)
					.collect(Collectors.toSet());
		}
		for (String modelId : modelIds) {
			if (cachedModels.containsKey(modelId)) {
				continue;
			}
			try {
				Map<String, String> model = fetchSingleEntity(String.format(Constant.MODEL_ENDPOINT, modelId), Constant.MODEL, ModelProperty.values());
				if (model != null) {
					cachedModels.put(modelId, model);
				}
			} catch (Exception e) {
				logger.error("Unable to retrieve model " + modelId, e);
			}
		}

		Set<String> manufacturerIds = cachedModels.values().stream()
				.map(model -> model.get(ModelProperty.MANUFACTURER_ID.getName()))
				.filter(StringUtils::isNotNullOrEmpty)
				.collect(Collectors.toSet());
		for (String manufacturerId : manufacturerIds) {
			if (cachedManufacturers.containsKey(manufacturerId)) {
				continue;
			}
			try {
				Map<String, String> manufacturer = fetchSingleEntity(String.format(Constant.MANUFACTURER_ENDPOINT, manufacturerId), Constant.MANUFACTURER, ManufacturerProperty.values());
				if (manufacturer != null) {
					cachedManufacturers.put(manufacturerId, manufacturer);
				}
			} catch (Exception e) {
				logger.error("Unable to retrieve manufacturer " + manufacturerId, e);
			}
		}
	}

	/**
	 * Fetches a single entity from the given endpoint, extracting its properties from the object at
	 * {@code wrapperKey}.
	 *
	 * @param endpoint the endpoint to GET
	 * @param wrapperKey the JSON key wrapping the entity object (e.g. {@link Constant#MODEL})
	 * @param properties all properties to extract from the entity
	 * @param <T> the enum type implementing {@link FieldProperty}
	 * @return the extracted property name/value pairs, or {@code null} if the response is empty/malformed
	 * @throws Exception if the request itself fails
	 */
	private <T extends Enum<T> & FieldProperty> Map<String, String> fetchSingleEntity(String endpoint, String wrapperKey, T[] properties) throws Exception {
		String jsonResult = this.doGet(endpoint);
		JsonNode response = objectMapper.readTree(jsonResult);
		if (response == null || !response.has(wrapperKey) || response.get(wrapperKey).isNull() || response.get(wrapperKey).isMissingNode()) {
			return null;
		}
		JsonNode node = response.path(wrapperKey);
		Map<String, String> result = new HashMap<>();
		for (T info : properties) {
			String value = extractValue(node, info);
			if (value == null) {
				continue;
			}
			result.put(info.getName(), value);
		}
		return result;
	}

	/**
	 * Fetches a list of entities from the given endpoint, extracting each entity's properties keyed by
	 * {@code idProperty}'s resolved value.
	 *
	 * @param endpoint the endpoint to GET
	 * @param wrapperKey the JSON key wrapping the array of entities (e.g. {@link Constant#DEVICES})
	 * @param idProperty the property used as the resulting map's key
	 * @param properties all properties to extract for each entity
	 * @param <T> the enum type implementing {@link FieldProperty}
	 * @return a map of entity ID to its extracted property name/value pairs; empty if the response is empty/malformed
	 * @throws Exception if the request itself fails
	 */
	private <T extends Enum<T> & FieldProperty> Map<String, Map<String, String>> fetchEntityList(String endpoint, String wrapperKey, T idProperty, T[] properties) throws Exception {
		String jsonResult = this.withSessionRecovery(() -> this.doGet(endpoint));
		if (logger.isDebugEnabled()) {
			logger.debug(String.format("Response received from %s. length=%s", endpoint, jsonResult == null ? -1 : jsonResult.length()));
		}
		JsonNode listResponse = objectMapper.readTree(jsonResult);
		if (listResponse == null || !listResponse.has(wrapperKey) || listResponse.get(wrapperKey).isEmpty()) {
			return Collections.emptyMap();
		}
		JsonNode data = listResponse.path(wrapperKey);
		if (!data.isArray() || data.isEmpty()) {
			return Collections.emptyMap();
		}
		Map<String, Map<String, String>> result = new HashMap<>();
		for (JsonNode node : data) {
			String id = extractValue(node, idProperty);
			if (Constant.NOT_AVAILABLE.equals(id)) {
				continue;
			}
			Map<String, String> mappingValue = new HashMap<>();
			for (T info : properties) {
				String value = extractValue(node, info);
				if (value == null) {
					continue;
				}
				mappingValue.put(info.getName(), value);
			}
			if (Constant.DEVICES.equals(wrapperKey)) {
				putDynamicLampUtilization(node, mappingValue);
			}
			result.put(id, mappingValue);
		}
		return result;
	}

	/** Matches lamp-hours field names (unsuffixed or numbered) under a device's LiveStatus node. */
	private static final Pattern LAMP_HOURS_PATTERN = Pattern.compile("LampHours(\\d*)");
	/** Matches average-lamp-hours field names (unsuffixed or numbered) under a device's LiveStatus node. */
	private static final Pattern AVERAGE_LAMP_HOURS_PATTERN = Pattern.compile("AverageLampHours(\\d*)");

	/**
	 * Scans {@code node}'s {@code LiveStatus} fields for however many lamp/average-lamp readings are
	 * present, and puts them into {@code mappingValue} under fully-qualified keys (e.g.
	 * {@code LiveStatus#Lamp3Utilization(hr)}), named unindexed if there's only one lamp or indexed
	 * ({@code Lamp1Utilization(hr)}, {@code Lamp2Utilization(hr)}, ...) otherwise.
	 *
	 * @param node the device JSON node
	 * @param mappingValue the destination cached property map for this device
	 */
	private void putDynamicLampUtilization(JsonNode node, Map<String, String> mappingValue) {
		JsonNode liveStatus = node.path("LiveStatus");
		Map<Integer, String> lampValues = new HashMap<>();
		Map<Integer, String> averageLampValues = new HashMap<>();

		Iterator<String> fieldNames = liveStatus.fieldNames();
		while (fieldNames.hasNext()) {
			String fieldName = fieldNames.next();
			putMatchedIndex(liveStatus, fieldName, LAMP_HOURS_PATTERN, lampValues);
			putMatchedIndex(liveStatus, fieldName, AVERAGE_LAMP_HOURS_PATTERN, averageLampValues);
		}

		boolean isMultiLamp = lampValues.size() > 1;
		putIndexedLampEntries(mappingValue, lampValues, isMultiLamp, "Lamp");
		putIndexedLampEntries(mappingValue, averageLampValues, isMultiLamp, "AverageLamp");
	}

	/**
	 * If {@code fieldName} matches {@code pattern}, puts its resolved value into {@code destination}
	 * keyed by lamp index (unsuffixed = index {@code 1}, numbered = that number); no-op otherwise.
	 *
	 * @param parent the JSON node {@code fieldName} belongs to
	 * @param fieldName the field name to test against {@code pattern}
	 * @param pattern the field-name pattern to match
	 * @param destination the map to add the resolved value to, keyed by lamp index
	 */
	private void putMatchedIndex(JsonNode parent, String fieldName, Pattern pattern, Map<Integer, String> destination) {
		Matcher matcher = pattern.matcher(fieldName);
		if (!matcher.matches()) {
			return;
		}
		String indexGroup = matcher.group(1);
		int index = StringUtils.isNullOrEmpty(indexGroup, true) ? 1 : Integer.parseInt(indexGroup);
		String value = parent.path(fieldName).asText();
		if (StringUtils.isNotNullOrEmpty(value)) {
			destination.put(index, value);
		}
	}

	/**
	 * Puts each resolved lamp index's value into {@code mappingValue}, named {@code baseName + "Utilization(hr)"}
	 * (unindexed) or {@code baseName + index + "Utilization(hr)"} depending on {@code isMultiLamp}.
	 *
	 * @param mappingValue the destination cached property map for this device
	 * @param values resolved values keyed by lamp index
	 * @param isMultiLamp whether the device has more than one lamp
	 * @param baseName {@code "Lamp"} or {@code "AverageLamp"}
	 */
	private void putIndexedLampEntries(Map<String, String> mappingValue, Map<Integer, String> values, boolean isMultiLamp, String baseName) {
		for (Map.Entry<Integer, String> entry : values.entrySet()) {
			String indexPart = isMultiLamp ? String.valueOf(entry.getKey()) : Constant.EMPTY;
			String propertyName = baseName + indexPart + "Utilization(hr)";
			String key = String.format(Constant.PROPERTY_FORMAT, Constant.LIVE_STATUS_GROUP, propertyName);
			mappingValue.put(key, entry.getValue());
		}
	}

	/**
	 * Extracts the value pointed to by the given property from a device JSON node.
	 * <p>
	 * When the field is missing/null/blank: {@link FieldProperty#isConditional() conditional} properties
	 * resolve to {@code null} (signaling the caller to omit the property entirely, since it only applies
	 * to some devices/hardware configurations), while regular properties fall back to
	 * {@link Constant#NOT_AVAILABLE}.
	 *
	 * @param node the device JSON node
	 * @param property the property whose {@code field} pointer is resolved
	 * @return the trimmed text value, {@code null} when missing and {@code conditional}, or
	 * {@link Constant#NOT_AVAILABLE} when missing and not conditional
	 */
	private String extractValue(JsonNode node, FieldProperty property) {
		JsonNode valueNode = node.at(property.getField());
		boolean isBlank = valueNode.isMissingNode() || valueNode.isNull() || StringUtils.isNullOrEmpty(valueNode.asText(), true);
		if (isBlank) {
			return property.isConditional() ? null : Constant.NOT_AVAILABLE;
		}
		return valueNode.asText();
	}

	/**
	 * Clones and populates a new list of aggregated devices with mapped monitoring properties, combining
	 * monitored devices ({@link #cachedMonitoringDevice}) and controllers ({@link #cachedControllers}) -
	 * both are surfaced as their own {@link AggregatedDevice} entries.
	 *
	 * @return A new list of {@link AggregatedDevice} objects with mapped monitoring properties.
	 */
	private List<AggregatedDevice> cloneAndPopulateAggregatedDeviceList() {
		List<AggregatedDevice> devices = new ArrayList<>();
		synchronized (cachedMonitoringDevice) {
			for (Map.Entry<String, Map<String, String>> entry : cachedMonitoringDevice.entrySet()) {
				devices.add(buildAggregatedDevice(entry.getKey(), entry.getValue()));
			}
		}
		synchronized (cachedControllers) {
			for (Map.Entry<String, Map<String, String>> entry : cachedControllers.entrySet()) {
				devices.add(buildAggregatedController(entry.getKey(), entry.getValue()));
			}
		}
		synchronized (aggregatedDeviceList) {
			aggregatedDeviceList.clear();
			aggregatedDeviceList.addAll(devices);
			return new ArrayList<>(aggregatedDeviceList);
		}
	}

	/**
	 * Builds an {@link AggregatedDevice} from cached monitoring data. {@code deviceId} is prefixed with
	 * {@value Constant#DEVICE_ID_PREFIX} on the resulting {@link AggregatedDevice#getDeviceId()}, since
	 * devices and controllers can otherwise share the same raw ID.
	 *
	 * @param deviceId the device identifier (cache key)
	 * @param cachedData the cached property name/value pairs for the device
	 * @return a populated {@link AggregatedDevice}
	 */
	private AggregatedDevice buildAggregatedDevice(String deviceId, Map<String, String> cachedData) {
		AggregatedDevice aggregatedDevice = new AggregatedDevice();
		aggregatedDevice.setDeviceId(Constant.DEVICE_ID_PREFIX + deviceId);
		aggregatedDevice.setDeviceName(cachedData.get(AggregatedGeneralProperty.DEVICE_NAME.getName()));
		String rawDeviceType = cachedData.get(AggregatedGeneralProperty.DEVICE_TYPE.getName());
		DeviceTypeCategory typeCategory = deviceTypeCategoryMapping.get(rawDeviceType);
		aggregatedDevice.setCategory(typeCategory == null ? rawDeviceType : typeCategory.getCategory());
		if (typeCategory != null) {
			aggregatedDevice.setType(typeCategory.getType());
		}
		String connection = cachedData.get(AggregatedGeneralProperty.CONNECTION.getName());
		aggregatedDevice.setDeviceOnline(Constant.ONLINE.equalsIgnoreCase(connection));

		Map<String, String> stats = new HashMap<>();
		List<AdvancedControllableProperty> controls = new ArrayList<>();
		for (AggregatedGeneralProperty info : AggregatedGeneralProperty.values()) {
			switch (info) {
				case DEVICE_ID:
				case DEVICE_NAME:
				case MODEL_ID:
					continue;
				case POWER_STATUS:
					putGroupedPropertyIfDisplayed(stats, cachedData, info);
					boolean isOn = Constant.ON.equalsIgnoreCase(cachedData.get(info.getName()));
					Util.addAdvancedControlProperties(controls, stats, ControllablePropertyFactory.createSwitch(Constant.POWER_PROPERTY, isOn ? 1 : 0), isOn ? "1" : "0" );
					break;
				default:
					putGroupedPropertyIfDisplayed(stats, cachedData, info);
					break;
			}
		}
		// Lamp/average-lamp utilization entries are already fully-qualified LiveStatus#... stats keys (see
		// #putDynamicLampUtilization) - no other cached property name contains "#", so this picks up exactly
		// those and nothing else; gated the same as the rest of the LiveStatus group.
		if (isGroupDisplayed(Constant.LIVE_STATUS_GROUP)) {
			for (Map.Entry<String, String> entry : cachedData.entrySet()) {
				if (entry.getKey().contains("#")) {
					stats.put(entry.getKey(), entry.getValue());
				}
			}
		}
		if (isGroupDisplayed(Constant.ALERTS_DISPLAY_GROUP)) {
			putAlerts(stats, cachedAlertsByDevice.get(deviceId), cachedAlertSummaryByDevice.get(deviceId), AlertProperty.DEVICE_ID);
		}
		aggregatedDevice.setProperties(stats);
		aggregatedDevice.setControllableProperties(controls);
		aggregatedDevice.setTimestamp(System.currentTimeMillis());
		resolveModelAndManufacturer(aggregatedDevice, cachedData);
		return aggregatedDevice;
	}

	/**
	 * Builds an {@link AggregatedDevice} from cached controller data, surfaced as its own aggregated device
	 * (category {@value Constant#CONTROLLER}). {@link ControllerProperty#MODEL_NAME} maps to {@code deviceModel}
	 * instead of being exposed as a stat; {@link ControllerProperty#MAC_ADDRESS} maps to {@code macAddresses}
	 * in addition to still being exposed as a stat. {@code deviceMake} is hardcoded to
	 * {@value Constant#CONTROLLER_MANUFACTURER} since the {@code /controllers} response has no manufacturer.
	 * {@code controllerId} is prefixed with {@value Constant#CONTROLLER_ID_PREFIX} to avoid ID collisions with devices.
	 *
	 * @param controllerId the controller identifier (cache key)
	 * @param cachedData the cached property name/value pairs for the controller
	 * @return a populated {@link AggregatedDevice}
	 */
	private AggregatedDevice buildAggregatedController(String controllerId, Map<String, String> cachedData) {
		AggregatedDevice aggregatedDevice = new AggregatedDevice();
		aggregatedDevice.setDeviceId(Constant.CONTROLLER_ID_PREFIX + controllerId);
		aggregatedDevice.setDeviceName(cachedData.get(ControllerProperty.NAME.getName()));
		aggregatedDevice.setCategory(Constant.CONTROLLER);
		boolean isOnline = Boolean.parseBoolean(cachedData.get(ControllerProperty.ONLINE.getName()));
		aggregatedDevice.setDeviceOnline(isOnline);
		aggregatedDevice.setDeviceModel(cachedData.getOrDefault(ControllerProperty.MODEL_NAME.getName(), Constant.NOT_AVAILABLE));
		aggregatedDevice.setDeviceMake(Constant.CONTROLLER_MANUFACTURER);
		String macAddress = cachedData.get(ControllerProperty.MAC_ADDRESS.getName());
		if (StringUtils.isNotNullOrEmpty(macAddress) && !Constant.NOT_AVAILABLE.equals(macAddress)) {
			aggregatedDevice.setMacAddresses(Collections.singletonList(macAddress));
		}

		Map<String, String> stats = new HashMap<>();
		for (ControllerProperty property : ControllerProperty.values()) {
			if (property == ControllerProperty.ID || property == ControllerProperty.NAME
					|| property == ControllerProperty.MODEL_NAME || property == ControllerProperty.ONLINE) {
				continue;
			}
			putGroupedPropertyIfDisplayed(stats, cachedData, property);
		}

		List<AdvancedControllableProperty> controls = new ArrayList<>();
		if (!isProController(cachedData.get(ControllerProperty.TYPE.getName()))) {
			boolean isActive = Constant.ACTIVE.equalsIgnoreCase(cachedData.get(ControllerProperty.STATUS.getName()));
			Util.addAdvancedControlProperties(controls, stats, ControllablePropertyFactory.createSwitch(Constant.POWER_PROPERTY, isActive ? 1 : 0), isActive ? "1" : "0");
		}

		if (isGroupDisplayed(Constant.ALERTS_DISPLAY_GROUP)) {
			putAlerts(stats, cachedAlertsByController.get(controllerId), cachedAlertSummaryByController.get(controllerId), AlertProperty.CONTROLLER_ID);
		}

		aggregatedDevice.setProperties(stats);
		aggregatedDevice.setControllableProperties(controls);
		aggregatedDevice.setTimestamp(System.currentTimeMillis());
		return aggregatedDevice;
	}

	/**
	 * Checks whether a controller's {@link ControllerProperty#TYPE} is {@value Constant#IPL_PRO_CONTROLLER_TYPE} -
	 * per the {@link Constant#GVE_COMMANDS_ENDPOINT} documentation, GVE commands are only valid for IP
	 * Link controllers, not IP Link Pro.
	 *
	 * @param controllerType the controller's raw {@link ControllerProperty#TYPE} value
	 * @return {@code true} if the type is an IP Link Pro controller
	 */
	private static boolean isProController(String controllerType) {
		return Constant.IPL_PRO_CONTROLLER_TYPE.equalsIgnoreCase(controllerType);
	}

	/**
	 * Puts the given alerts into {@code stats} as indexed {@code Alert_XX} sub-groups (skipped when
	 * {@code alerts} is {@code null}), plus an always-present {@link Constant#ACTIVE_ALERTS_GROUP} group
	 * ({@code TotalCount}, {@code Types}, {@code MonitoredCategories} - {@link Constant#NOT_AVAILABLE} when
	 * there are none). Used for both devices and controllers - {@code ownIdProperty} is whichever of
	 * {@link AlertProperty#DEVICE_ID}/{@link AlertProperty#CONTROLLER_ID} identifies the entity {@code stats}
	 * belongs to, and is omitted from each {@code Alert_XX} sub-group since it's redundant there (it's always
	 * that same entity's own ID). The other ID property is kept only when it actually resolves for that alert
	 * - e.g. a device's alerts still show which controller they're connected through, but a controller alert's
	 * unresolved {@link AlertProperty#DEVICE_ID} (always {@link Constant#NOT_AVAILABLE} for a controller alert)
	 * is omitted entirely rather than showing up as {@code DeviceID=N/A}.
	 *
	 * @param stats the destination device/controller statistics map
	 * @param alerts the entity's alerts, sorted latest-first, or {@code null} if none
	 * @param summary the entity's true (uncapped) alert summary, or {@code null} if none
	 * @param ownIdProperty {@link AlertProperty#DEVICE_ID} when {@code stats} is a device's, or
	 * {@link AlertProperty#CONTROLLER_ID} when it's a controller's
	 */
	void putAlerts(Map<String, String> stats, List<Map<String, String>> alerts, AlertSummary summary, AlertProperty ownIdProperty) {
		if (alerts != null) {
			int index = 1;
			for (Map<String, String> alert : alerts) {
				String groupName = String.format(Constant.INDEXED_GROUP_FORMAT, Constant.ALERT_GROUP, String.format("%02d", index));
				for (AlertProperty property : AlertProperty.values()) {
					if (property == ownIdProperty) {
						continue;
					}
					String value = alert.getOrDefault(property.getName(), Constant.NOT_AVAILABLE);
					boolean isUnresolvedIdProperty = (property == AlertProperty.DEVICE_ID || property == AlertProperty.CONTROLLER_ID)
							&& Constant.NOT_AVAILABLE.equals(value);
					if (isUnresolvedIdProperty) {
						continue;
					}
					String key = String.format(Constant.PROPERTY_FORMAT, groupName, property.getName());
					stats.put(key, value);
				}
				index++;
			}
		}

		int totalCount = summary == null ? 0 : summary.totalCount;
		stats.put(String.format(Constant.PROPERTY_FORMAT, Constant.ACTIVE_ALERTS_GROUP, "TotalCount"), String.valueOf(totalCount));
		stats.put(String.format(Constant.PROPERTY_FORMAT, Constant.ACTIVE_ALERTS_GROUP, "Types"),
				totalCount == 0 ? Constant.NOT_AVAILABLE : String.join(", ", summary.types));
		stats.put(String.format(Constant.PROPERTY_FORMAT, Constant.ACTIVE_ALERTS_GROUP, "MonitoredCategories"),
				totalCount == 0 ? Constant.NOT_AVAILABLE : String.join(", ", summary.monitors));
	}

	/**
	 * Resolves the device's model/manufacturer names, chaining the device's model ID through
	 * {@link #cachedModels} and then {@link #cachedManufacturers}, and sets them on
	 * {@link AggregatedDevice#setDeviceModel(String)}/{@link AggregatedDevice#setDeviceMake(String)}.
	 *
	 * @param aggregatedDevice the device to set the resolved model/make on
	 * @param cachedData the cached property name/value pairs for the device
	 */
	private void resolveModelAndManufacturer(AggregatedDevice aggregatedDevice, Map<String, String> cachedData) {
		String modelId = cachedData.get(AggregatedGeneralProperty.MODEL_ID.getName());
		Map<String, String> model = modelId == null ? null : cachedModels.get(modelId);
		aggregatedDevice.setDeviceModel(model == null ? Constant.NOT_AVAILABLE : model.getOrDefault(ModelProperty.NAME.getName(), Constant.NOT_AVAILABLE));

		String manufacturerId = model == null ? null : model.get(ModelProperty.MANUFACTURER_ID.getName());
		Map<String, String> manufacturer = manufacturerId == null ? null : cachedManufacturers.get(manufacturerId);
		aggregatedDevice.setDeviceMake(manufacturer == null ? Constant.NOT_AVAILABLE : manufacturer.getOrDefault(ManufacturerProperty.NAME.getName(), Constant.NOT_AVAILABLE));
	}

	/**
	 * Puts every cached entity's properties into {@code stats}, each as its own group keyed by the
	 * entity's own ID.
	 *
	 * @param stats the destination adapter statistics map
	 * @param cachedEntities cached entities keyed by their own ID, each holding its property name/value pairs
	 * @param properties all properties to place for each cached entity
	 * @param <T> the enum type implementing {@link FieldProperty}
	 */
	private <T extends Enum<T> & FieldProperty> void putIndexedGroupedProperties(Map<String, String> stats, Map<String, Map<String, String>> cachedEntities, T[] properties) {
		synchronized (cachedEntities) {
			for (Map.Entry<String, Map<String, String>> entry : cachedEntities.entrySet()) {
				for (T property : properties) {
					putIndexedGroupedProperty(stats, entry.getValue(), property, entry.getKey());
				}
			}
		}
	}

	/**
	 * Puts the cached value for the given property into the stats map, grouped under the property's
	 * group combined with {@code instanceId} (e.g. {@code GVERoom_101#Name}).
	 *
	 * @param stats the destination adapter statistics map
	 * @param cachedData the cached property name/value pairs for the entity instance
	 * @param property the property to resolve and place into {@code stats}
	 * @param instanceId the entity instance's own ID
	 */
	private void putIndexedGroupedProperty(Map<String, String> stats, Map<String, String> cachedData, FieldProperty property, String instanceId) {
		if (property.isConditional() && !cachedData.containsKey(property.getName())) {
			return;
		}
		String groupName = String.format(Constant.INDEXED_GROUP_FORMAT, property.getGroup(), instanceId);
		String key = String.format(Constant.PROPERTY_FORMAT, groupName, property.getName());
		stats.put(key, cachedData.getOrDefault(property.getName(), Constant.NOT_AVAILABLE));
	}

	/**
	 * Puts the cached value for the given property into the stats map, prefixing the key with the
	 * property's group (if any) using {@link Constant#PROPERTY_FORMAT}.
	 *
	 * @param stats the destination monitoring properties map
	 * @param cachedData the cached property name/value pairs for the device
	 * @param property the property to resolve and place into {@code stats}
	 */
	private void putGroupedProperty(Map<String, String> stats, Map<String, String> cachedData, FieldProperty property) {
		putGroupedProperty(stats, cachedData, property, property.getGroup());
	}

	/**
	 * Same as {@link #putGroupedProperty(Map, Map, FieldProperty)}, but with an explicit group prefix rather
	 * than {@code property}'s own - for shared property definitions (like {@link ServiceProperty}) whose
	 * group varies by caller rather than by property.
	 */
	private void putGroupedProperty(Map<String, String> stats, Map<String, String> cachedData, FieldProperty property, String groupName) {
		if (property.isConditional() && !cachedData.containsKey(property.getName())) {
			return;
		}
		String key = StringUtils.isNullOrEmpty(groupName, true)
				? property.getName()
				: String.format(Constant.PROPERTY_FORMAT, groupName, property.getName());
		stats.put(key, cachedData.getOrDefault(property.getName(), Constant.NOT_AVAILABLE));
	}

	/**
	 * Same as {@link #putGroupedProperty(Map, Map, FieldProperty)}, but skips the property entirely (rather
	 * than adding it) when it belongs to a non-flat group that {@link #isGroupDisplayed} says shouldn't be
	 * shown - for enums like {@link AggregatedGeneralProperty}/{@link ControllerProperty} that mix flat/
	 * always-shown properties with optional-group ones in the same {@code values()} loop.
	 *
	 * @param stats the destination monitoring properties map
	 * @param cachedData the cached property name/value pairs for the device/controller
	 * @param property the property to resolve and place into {@code stats}
	 */
	private void putGroupedPropertyIfDisplayed(Map<String, String> stats, Map<String, String> cachedData, FieldProperty property) {
		String group = property.getGroup();
		if (StringUtils.isNotNullOrEmpty(group) && !isGroupDisplayed(group)) {
			return;
		}
		putGroupedProperty(stats, cachedData, property);
	}
}
