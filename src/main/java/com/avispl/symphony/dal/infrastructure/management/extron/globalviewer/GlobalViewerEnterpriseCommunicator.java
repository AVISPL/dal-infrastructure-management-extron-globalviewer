/** Copyright (c) 2026 AVI-SPL, Inc. All Rights Reserved. */
package com.avispl.symphony.dal.infrastructure.management.extron.globalviewer;

import com.avispl.symphony.api.dal.control.Controller;
import com.avispl.symphony.api.dal.dto.control.AdvancedControllableProperty;
import com.avispl.symphony.api.dal.dto.control.ControllableProperty;
import com.avispl.symphony.api.dal.dto.monitor.ExtendedStatistics;
import com.avispl.symphony.api.dal.dto.monitor.Statistics;
import com.avispl.symphony.api.dal.dto.monitor.aggregator.AggregatedDevice;
import com.avispl.symphony.api.dal.error.ResourceNotReachableException;
import com.avispl.symphony.api.dal.monitor.Monitorable;
import com.avispl.symphony.api.dal.monitor.aggregator.Aggregator;
import com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.base.BaseCommunicator;
import com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.base.FieldProperty;
import com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.common.Constant;
import com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.common.utils.MonitoringUtil;
import com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.common.utils.Util;
import com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.types.aggregated.AggregatedGeneralProperty;
import com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.types.aggregator.General;
import com.avispl.symphony.dal.util.StringUtils;
import com.avispl.symphony.dal.util.ControllablePropertyFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections.CollectionUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
	 * Cached data
	 */
	private final Map<String, Map<String, String>> cachedMonitoringDevice = Collections.synchronizedMap(new HashMap<>());

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
					if (logger.isDebugEnabled()) {
						logger.debug("Fetching other than aggregated device list");
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
							logger.debug("Fetching devices list");
						}
						populateListDevice();
					} catch (Exception e) {
						logger.error("Error occurred during device list retrieval: " + e.getMessage(), e);
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
		aggregatedDeviceList.clear();
		this.localExtendedStatistics.getStatistics().clear();
		super.internalDestroy();
	}

	@Override
	public List<Statistics> getMultipleStatistics() throws Exception {
		this.reentrantLock.lock();
		try {
			this.authenticate();
			versionProperties.setProperty(General.MONITORED_DEVICES_TOTAL.getProperty(), String.valueOf(cachedMonitoringDevice.size()));
			versionProperties.setProperty(General.LAST_MONITORING_CYCLE_DURATION.getProperty(), String.valueOf(lastMonitoringCycleDuration));

			var statistics = new HashMap<>(MonitoringUtil.generateProperties(
					General.values(), null, property -> MonitoringUtil.mapToGeneral(this.versionProperties, property)
			));

			this.localExtendedStatistics.setStatistics(statistics);
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
		if (cachedMonitoringDevice.isEmpty()) {
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
			versionProperties.setProperty(General.MONITORED_DEVICES_TOTAL.getProperty(), Constant.NOT_AVAILABLE);
			versionProperties.setProperty(General.LAST_MONITORING_CYCLE_DURATION.getProperty(), String.valueOf(lastMonitoringCycleDuration));
			versionProperties.setProperty(General.MONITORING_CYCLE_INTERVAL.getProperty(), String.valueOf(this.getMonitoringRate()));
		} catch (IOException e) {
			this.logger.error(Constant.READ_PROPERTIES_FILE_FAILED, e);
		}
	}

	/**
	 * Populates aggregated device list by making a GET request to retrieve information.
	 * All of the "Aggregated Device > General" and "Aggregated Device > Live Status" properties
	 * (see {@link AggregatedGeneralProperty}) are sourced from this single list call - the {@code LiveStatus}
	 * sub-object has been confirmed to be identical between this endpoint and the per-device
	 * {@code /devices/{deviceId}} endpoint, so no separate per-device fetch is needed for this data.
	 * Any error during the process is logged.
	 */
	private void populateListDevice() {
		try{
			String jsonResult = this.doGet(Constant.DEVICES_ENDPOINT);
			if (logger.isDebugEnabled()) {
				logger.debug(String.format("Device list response received. length=%s", jsonResult == null ? -1 : jsonResult.length()));
			}
			JsonNode listResponse = objectMapper.readTree(jsonResult);
			if (listResponse == null || !listResponse.has(Constant.DEVICES) || listResponse.get(Constant.DEVICES).isEmpty()) {
				return;
			}
			JsonNode data = listResponse.path(Constant.DEVICES);
			if (data == null || !data.isArray() || data.isEmpty()) {
				return;
			}
			Map<String, Map<String, String>> nextDeviceCache = new HashMap<>();
			for (JsonNode node : data) {
				String deviceId = extractValue(node, AggregatedGeneralProperty.DEVICE_ID);
				if (Constant.NOT_AVAILABLE.equals(deviceId)) {
					continue;
				}
				Map<String, String> mappingValue = new HashMap<>();
				for (AggregatedGeneralProperty info : AggregatedGeneralProperty.values()) {
					String value = extractValue(node, info);
					// Conditional properties (e.g. secondary/tertiary/quaternary lamp trackers, which only
					// exist for devices with that many physical lamps) are dropped entirely when their field
					// doesn't resolve, rather than being cached as N/A - see AggregatedGeneralProperty.
					if (value == null) {
						continue;
					}
					mappingValue.put(info.getName(), value);
				}
				nextDeviceCache.put(deviceId, mappingValue);
			}
			synchronized (cachedMonitoringDevice) {
				cachedMonitoringDevice.clear();
				cachedMonitoringDevice.putAll(nextDeviceCache);
			}
		} catch (Exception e){
			 throw new RuntimeException("Unable to retrieve names from response.", e);
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
	 * Clones and populates a new list of aggregated devices with mapped monitoring properties.
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
		synchronized (aggregatedDeviceList) {
			aggregatedDeviceList.clear();
			aggregatedDeviceList.addAll(devices);
			return new ArrayList<>(aggregatedDeviceList);
		}
	}

	/**
	 * Builds an {@link AggregatedDevice} from cached monitoring data, setting basic identity fields
	 * and the per-device monitoring properties map (see {@link AggregatedGeneralProperty}).
	 * <p>
	 * The raw GVE {@code DeviceType} value (the same one exposed as the flat {@code Type} property) is
	 * also set on the dedicated {@link AggregatedDevice#setCategory(String)} field.
	 *
	 * @param deviceId the device identifier (cache key)
	 * @param cachedData the cached property name/value pairs for the device
	 * @return a populated {@link AggregatedDevice}
	 */
	private AggregatedDevice buildAggregatedDevice(String deviceId, Map<String, String> cachedData) {
		AggregatedDevice aggregatedDevice = new AggregatedDevice();
		aggregatedDevice.setDeviceId(deviceId);
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
					continue;
				case POWER_STATUS:
					boolean isOn = Constant.ON.equalsIgnoreCase(cachedData.get(info.getName()));
					Util.addAdvancedControlProperties(controls, stats, ControllablePropertyFactory.createSwitch(info.getName(), isOn ? 1 : 0), isOn ? "1" : "0" );
					break;
				default:
					putGroupedProperty(stats, cachedData, info);
					break;
			}
		}
		aggregatedDevice.setProperties(stats);
		aggregatedDevice.setControllableProperties(controls);
		aggregatedDevice.setTimestamp(System.currentTimeMillis());
		return aggregatedDevice;
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
