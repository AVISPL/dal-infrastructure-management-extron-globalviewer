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
import com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.types.aggregator.General;
import com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.types.alert.AlertProperty;
import com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.types.controller.ControllerProperty;
import com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.types.location.LocationProperty;
import com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.types.manufacturer.ManufacturerProperty;
import com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.types.model.ModelProperty;
import com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.types.room.RoomProperty;
import com.avispl.symphony.dal.util.StringUtils;
import com.avispl.symphony.dal.util.ControllablePropertyFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
	 * GVE command action IDs from {@link Constant#GVE_COMMANDS_ENDPOINT}, keyed by {@code ControllerType:Name}
	 * (e.g. {@code Device:Power}, {@code Controller:Front Panel Lockout}) so the right {@code ActionId} can be
	 * resolved for a given control without hardcoding IDs that could differ between GVE installations.
	 */
	private final Map<String, Integer> cachedActionIds = Collections.synchronizedMap(new HashMap<>());

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
						if (logger.isDebugEnabled()) {
							logger.debug("Fetching rooms list");
						}
						populateRoomList();
					} catch (Exception e) {
						logger.error("Error occurred during room list retrieval", e);
					}
					try {
						if (logger.isDebugEnabled()) {
							logger.debug("Fetching locations list");
						}
						populateLocationList();
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
						if (logger.isDebugEnabled()) {
							logger.debug("Fetching alerts list");
						}
						populateAlertList();
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
		cachedMonitoringDevice.clear();
		cachedRooms.clear();
		cachedLocations.clear();
		cachedActionIds.clear();
		cachedControllers.clear();
		cachedAlertsByDevice.clear();
		cachedModels.clear();
		cachedManufacturers.clear();
		cachedAlertSummaryByDevice.clear();
		aggregatedDeviceList.clear();
		this.localExtendedStatistics.getStatistics().clear();
		super.internalDestroy();
	}

	@Override
	public List<Statistics> getMultipleStatistics() throws Exception {
		this.reentrantLock.lock();
		try {
			this.authenticate();
			var statistics = new HashMap<>(MonitoringUtil.generateProperties(
					General.values(), null, property -> MonitoringUtil.mapToGeneral(this.versionProperties, property)
			));
			putIndexedGroupedProperties(statistics, cachedRooms, RoomProperty.values());
			putIndexedGroupedProperties(statistics, cachedLocations, LocationProperty.values());

			Map<String, String> dynamicStatistics = new HashMap<>();
			dynamicStatistics.put(Constant.MONITORED_DEVICES_TOTAL, String.valueOf(cachedMonitoringDevice.size()));
			dynamicStatistics.put(Constant.LAST_MONITORING_CYCLE_DURATION, String.valueOf(lastMonitoringCycleDuration));

			this.localExtendedStatistics.setStatistics(statistics);
			this.localExtendedStatistics.setDynamicStatistics(dynamicStatistics);
		} finally {
			this.reentrantLock.unlock();
		}
		return Collections.singletonList(this.localExtendedStatistics);
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
		String aggregatedDeviceId = controllableProperty.getDeviceId();
		String property = controllableProperty.getProperty();
		if (StringUtils.isNullOrEmpty(aggregatedDeviceId, true) || StringUtils.isNullOrEmpty(property, true)) {
			return;
		}
		int value = parseControlValue(controllableProperty.getValue());

		if (aggregatedDeviceId.startsWith(Constant.DEVICE_ID_PREFIX)) {
			String rawDeviceId = aggregatedDeviceId.substring(Constant.DEVICE_ID_PREFIX.length());
			if (Constant.POWER_PROPERTY.equals(property)) {
				sendDeviceCommand(rawDeviceId, Constant.ACTION_NAME_POWER, value);
			}
			return;
		}
		if (!aggregatedDeviceId.startsWith(Constant.CONTROLLER_ID_PREFIX)) {
			return;
		}

		String rawControllerId = aggregatedDeviceId.substring(Constant.CONTROLLER_ID_PREFIX.length());
		if (Constant.POWER_PROPERTY.equals(property)) {
			sendControllerCommand(rawControllerId, Constant.ACTION_NAME_POWER, value);
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

	/**
	 * Checks a GVE command response's {@code ResponseStatus} and throws if it carries a non-blank
	 * {@code ErrorCode}.
	 *
	 * @param response the raw response body from a {@link Constant#DEVICE_COMMAND_ENDPOINT}/{@link Constant#CONTROLLER_COMMAND_ENDPOINT} call
	 * @throws Exception if the response can't be parsed, or if it reports a failure
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
			versionProperties.setProperty(General.ACTIVE_PROPERTY_GROUPS.getProperty(), Constant.NOT_AVAILABLE);
			versionProperties.setProperty(General.ADAPTER_UPTIME.getProperty(), String.valueOf(this.adapterInitializationTimestamp));
			versionProperties.setProperty(General.MONITORING_CYCLE_INTERVAL.getProperty(), String.valueOf(this.getMonitoringRate()));
		} catch (IOException e) {
			this.logger.error(Constant.READ_PROPERTIES_FILE_FAILED, e);
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
	 * Only gates whether the alert is added to {@link #cachedAlertsByDevice} (the displayed {@code Alert_XX}
	 * groups) - the device's {@link AlertSummary} always reflects every alert regardless of this filter.
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
	 * Populates {@link #cachedAlertsByDevice} by making a GET request to {@link Constant#ALERTS_ENDPOINT},
	 * grouping alerts under the device ID ({@link AlertProperty#DEVICE_ID}) each one belongs to. Alerts
	 * with no resolvable device ID are dropped.
	 */
	private void populateAlertList() {
		try {
			String jsonResult = this.withSessionRecovery(() -> this.doGet(Constant.ALERTS_ENDPOINT));
			Map<String, List<Map<String, String>>> nextAlertCache = new HashMap<>();
			Map<String, AlertSummary> nextAlertSummaryCache = new HashMap<>();
			parseAlerts(jsonResult, nextAlertCache, nextAlertSummaryCache);
			synchronized (cachedAlertsByDevice) {
				cachedAlertsByDevice.clear();
				cachedAlertsByDevice.putAll(nextAlertCache);
			}
			synchronized (cachedAlertSummaryByDevice) {
				cachedAlertSummaryByDevice.clear();
				cachedAlertSummaryByDevice.putAll(nextAlertSummaryCache);
			}
		} catch (Exception e) {
			throw new RuntimeException("Unable to retrieve alerts from response.", e);
		}
	}

	/**
	 * Parses a raw {@link Constant#ALERTS_ENDPOINT} response, grouping alerts under the device ID
	 * ({@link AlertProperty#DEVICE_ID}) each one belongs to. Alerts with no resolvable device ID are
	 * dropped entirely. {@link #matchesAlertFilters} only controls which alerts make it into
	 * {@code alertCache} (the displayed {@code Alert_XX} groups) - {@code alertSummaryCache} always
	 * reflects every alert, filtered or not.
	 *
	 * @param jsonResult the raw JSON response body
	 * @param alertCache destination for alerts grouped by device ID, filtered by {@link #matchesAlertFilters},
	 * sorted latest-{@link AlertProperty#EVENT_TIME}-first and capped at {@link #alertEventsTotal} per device
	 * @param alertSummaryCache destination for each device's true (unfiltered, uncapped) {@link AlertSummary}
	 * @throws Exception if the response cannot be parsed
	 */
	void parseAlerts(String jsonResult, Map<String, List<Map<String, String>>> alertCache, Map<String, AlertSummary> alertSummaryCache) throws Exception {
		JsonNode listResponse = objectMapper.readTree(jsonResult);
		if (listResponse != null && listResponse.has(Constant.ALERTS) && !listResponse.get(Constant.ALERTS).isEmpty()) {
			for (JsonNode node : listResponse.path(Constant.ALERTS)) {
				String deviceId = extractValue(node, AlertProperty.DEVICE_ID);
				if (Constant.NOT_AVAILABLE.equals(deviceId)) {
					continue;
				}
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
				AlertSummary summary = alertSummaryCache.computeIfAbsent(deviceId, id -> new AlertSummary());
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
					alertCache.computeIfAbsent(deviceId, id -> new ArrayList<>()).add(alert);
				}
			}
		}
		// Sort each device's alerts latest-EventTime-first, then drop everything beyond alertEventsTotal.
		// Sorting happens before capping so the alerts that survive the cap are genuinely the most recent
		// ones, rather than an arbitrary prefix in whatever order the API returned them in. The true
		// count/type/monitor values tracked above via alertSummaryCache are unaffected by this cap.
		for (List<Map<String, String>> alertsForDevice : alertCache.values()) {
			alertsForDevice.sort(Comparator.comparing(GlobalViewerEnterpriseCommunicator::parseEventTime).reversed());
			if (alertsForDevice.size() > alertEventsTotal) {
				alertsForDevice.subList(alertEventsTotal, alertsForDevice.size()).clear();
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
		aggregatedDevice.setCategory(cachedData.get(AggregatedGeneralProperty.DEVICE_TYPE.getName()));
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
					putGroupedProperty(stats, cachedData, info);
					boolean isOn = Constant.ON.equalsIgnoreCase(cachedData.get(info.getName()));
					Util.addAdvancedControlProperties(controls, stats, ControllablePropertyFactory.createSwitch(Constant.POWER_PROPERTY, isOn ? 1 : 0), isOn ? "1" : "0" );
					break;
				default:
					putGroupedProperty(stats, cachedData, info);
					break;
			}
		}
		// Lamp/average-lamp utilization entries are already fully-qualified stats keys (see
		// #putDynamicLampUtilization) - no other cached property name contains "#", so this picks up
		// exactly those and nothing else.
		for (Map.Entry<String, String> entry : cachedData.entrySet()) {
			if (entry.getKey().contains("#")) {
				stats.put(entry.getKey(), entry.getValue());
			}
		}
		putDeviceAlerts(stats, cachedAlertsByDevice.get(deviceId), cachedAlertSummaryByDevice.get(deviceId));
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
			putGroupedProperty(stats, cachedData, property);
		}

		List<AdvancedControllableProperty> controls = new ArrayList<>();
		if (!isProController(cachedData.get(ControllerProperty.TYPE.getName()))) {
			boolean isActive = Constant.ACTIVE.equalsIgnoreCase(cachedData.get(ControllerProperty.STATUS.getName()));
			Util.addAdvancedControlProperties(controls, stats, ControllablePropertyFactory.createSwitch(Constant.POWER_PROPERTY, isActive ? 1 : 0), isActive ? "1" : "0");
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
	 * there are none).
	 *
	 * @param stats the destination device statistics map
	 * @param alerts the device's alerts, sorted latest-first, or {@code null} if none
	 * @param summary the device's true (uncapped) alert summary, or {@code null} if none
	 */
	void putDeviceAlerts(Map<String, String> stats, List<Map<String, String>> alerts, AlertSummary summary) {
		if (alerts != null) {
			int index = 1;
			for (Map<String, String> alert : alerts) {
				String groupName = String.format(Constant.INDEXED_GROUP_FORMAT, Constant.ALERT_GROUP, String.format("%02d", index));
				for (AlertProperty property : AlertProperty.values()) {
					if (property == AlertProperty.DEVICE_ID) {
						continue;
					}
					String key = String.format(Constant.PROPERTY_FORMAT, groupName, property.getName());
					stats.put(key, alert.getOrDefault(property.getName(), Constant.NOT_AVAILABLE));
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
		if (property.isConditional() && !cachedData.containsKey(property.getName())) {
			return;
		}
		String groupName = property.getGroup();
		String key = StringUtils.isNullOrEmpty(groupName, true)
				? property.getName()
				: String.format(Constant.PROPERTY_FORMAT, groupName, property.getName());
		stats.put(key, cachedData.getOrDefault(property.getName(), Constant.NOT_AVAILABLE));
	}
}
