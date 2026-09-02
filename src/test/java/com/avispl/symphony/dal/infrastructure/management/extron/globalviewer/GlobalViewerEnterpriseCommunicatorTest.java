/** Copyright (c) 2026 AVI-SPL, Inc. All Rights Reserved. */
package com.avispl.symphony.dal.infrastructure.management.extron.globalviewer;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.avispl.symphony.api.dal.dto.control.AdvancedControllableProperty;
import com.avispl.symphony.api.dal.dto.control.ControllableProperty;
import com.avispl.symphony.api.dal.dto.monitor.ExtendedStatistics;
import com.avispl.symphony.api.dal.dto.monitor.aggregator.AggregatedDevice;
import com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.common.Constant;
import com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.types.aggregated.AggregatedGeneralProperty;
import com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.types.controller.ControllerProperty;

import javax.security.auth.login.FailedLoginException;

/**
 * Integration tests that exercise the real adapter pipeline (fetch -&gt; cache -&gt; aggregated device)
 * against a live GVE server - no crafted/mocked responses. Fill in {@link #setUp} with a real host/login/
 * password before running.
 *
 * @author Kevin / Symphony Dev Team
 * @since 1.0.0
 */
class GlobalViewerEnterpriseCommunicatorTest {
	/** How long to wait for a full monitoring cycle to complete before reading the results back. */
	private static final long DATA_COLLECTION_WAIT_MS = 10000;

	private ExtendedStatistics extendedStatistics;
	private GlobalViewerEnterpriseCommunicator communicator;

	@BeforeEach
	void setUp() throws Exception {
		this.communicator = new GlobalViewerEnterpriseCommunicator();
		this.communicator.setHost("");
		this.communicator.setPort(443);
		this.communicator.setLogin("");
		this.communicator.setPassword("");
		this.communicator.init();
	}

	@AfterEach
	void destroy() throws Exception {
		this.communicator.disconnect();
		this.communicator.destroy();
	}

	@Test
	void testAuthentication_WithInvalidCredential() {
		Assertions.assertThrows(FailedLoginException.class, () -> this.communicator.getMultipleStatistics());
	}

	@Test
	void testGetMultipleStatistics() throws Exception {
		this.extendedStatistics = (ExtendedStatistics) this.communicator.getMultipleStatistics().get(0);
		Map<String, String> statistics = this.extendedStatistics.getStatistics();

		this.verifyStatistics(statistics);
	}

	private void verifyStatistics(Map<String, String> statistics) {
		Map<String, Map<String, String>> groups = new LinkedHashMap<>();
		groups.put(Constant.GENERAL_GROUP, this.filterGroupStatistics(statistics, null));

		for (Map<String, String> initGroup : groups.values()) {
			for (Map.Entry<String, String> initStatistics : initGroup.entrySet()) {
				Assertions.assertNotNull(initStatistics.getValue(), "Value is null with property: " + initStatistics.getKey());
			}
		}
	}

	private Map<String, String> filterGroupStatistics(Map<String, String> statistics, String groupName) {
		return statistics.entrySet().stream()
				.filter(e -> (groupName == null) ? !e.getKey().contains("#") : e.getKey().startsWith(groupName))
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
	}

	@Test
	void testGetAggregatedData() throws Exception {
		List<AggregatedDevice> aggregatedDeviceList = collectAggregatedDevices();

		Assertions.assertFalse(aggregatedDeviceList.isEmpty(), "Expected at least one aggregated device or controller");
		for (AggregatedDevice aggregatedDevice : aggregatedDeviceList) {
			Assertions.assertNotNull(aggregatedDevice.getDeviceId(), "Aggregated device is missing an ID");
		}
		System.out.println("Aggregated devices fetched: " + aggregatedDeviceList.size());
	}

	/**
	 * Verifies every controller device's properties have non-null values. Controllers are surfaced as their
	 * own {@link AggregatedDevice} entries (category {@value Constant#CONTROLLER}), not as adapter-level
	 * stats.
	 */
	@Test
	void testGetControllerData() throws Exception {
		List<AggregatedDevice> aggregatedDeviceList = collectAggregatedDevices();
		List<AggregatedDevice> controllers = filterByCategory(aggregatedDeviceList, Constant.CONTROLLER, true);

		for (AggregatedDevice controller : controllers) {
			System.out.println("Controller: " + controller.getDeviceId() + " (" + controller.getDeviceName() + "), online=" + controller.getDeviceOnline());
			for (Map.Entry<String, String> entry : controller.getProperties().entrySet()) {
				Assertions.assertNotNull(entry.getValue(), "Value is null with property: " + entry.getKey());
			}
		}
		System.out.println("Controllers fetched: " + controllers.size() + " / " + aggregatedDeviceList.size() + " aggregated devices");
	}

	/**
	 * Verifies a device's {@link Constant#POWER_PROPERTY} ("Power") switch's initial value matches
	 * the {@link AggregatedGeneralProperty#POWER_STATUS} ("PowerStatus") readback (only {@code "On"} results
	 * in the switch being on - {@code "Off"}/{@code "Unknown"}/anything else results in off), then sends the
	 * opposite value through the real adapter pipeline ({@link GlobalViewerEnterpriseCommunicator#controlProperty}
	 * -&gt; {@code POST /gvecommands/device}) and confirms it completes without throwing.
	 */
	@Test
	void testDevicePowerControlProperty() throws Exception {
		List<AggregatedDevice> aggregatedDeviceList = collectAggregatedDevices();
		List<AggregatedDevice> devices = filterByCategory(aggregatedDeviceList, Constant.CONTROLLER, false);
		Assertions.assertFalse(devices.isEmpty(), "Expected at least one non-controller device");

		AggregatedDevice device = devices.get(0);
		AdvancedControllableProperty powerControl = findControl(device, Constant.POWER_PROPERTY);
		Assertions.assertNotNull(powerControl, "Expected a '" + Constant.POWER_PROPERTY + "' control on device " + device.getDeviceId());

		String readback = device.getProperties().get(AggregatedGeneralProperty.POWER_STATUS.getName());
		int expectedInitialValue = Constant.ON.equalsIgnoreCase(readback) ? 1 : 0;
		System.out.println("Device: " + device.getDeviceId() + ", PowerStatus readback=" + readback + ", control value=" + powerControl.getValue());
		Assertions.assertEquals(String.valueOf(expectedInitialValue), String.valueOf(powerControl.getValue()),
				"Initial switch value should match the LiveStatus/Power readback");

		int newValue = expectedInitialValue == 1 ? 0 : 1;
		communicator.controlProperty(new ControllableProperty(Constant.POWER_PROPERTY, newValue, device.getDeviceId()));
		System.out.println("Sent " + Constant.POWER_PROPERTY + "=" + newValue + " to device " + device.getDeviceId());
	}

	/**
	 * For every controller: verifies IP Link Pro controllers ({@link ControllerProperty#TYPE} =
	 * {@value Constant#IPL_PRO_CONTROLLER_TYPE}) have no controllable properties at all, while other
	 * controllers have a {@link Constant#POWER_PROPERTY} switch whose initial value matches the
	 * {@link ControllerProperty#STATUS} readback (only {@code "Active"} results in on). Sends the opposite
	 * value through the real adapter pipeline ({@link GlobalViewerEnterpriseCommunicator#controlProperty}
	 * -&gt; {@code POST /gvecommands/controller}) for each non-Pro controller and confirms it completes
	 * without throwing.
	 */
	@Test
	void testControllerPowerControlProperty() throws Exception {
		List<AggregatedDevice> aggregatedDeviceList = collectAggregatedDevices();
		List<AggregatedDevice> controllers = filterByCategory(aggregatedDeviceList, Constant.CONTROLLER, true);
		Assertions.assertFalse(controllers.isEmpty(), "Expected at least one controller");

		for (AggregatedDevice controller : controllers) {
			String type = controller.getProperties().get(ControllerProperty.TYPE.getName());
			boolean isPro = Constant.IPL_PRO_CONTROLLER_TYPE.equalsIgnoreCase(type);
			List<AdvancedControllableProperty> controls = controller.getControllableProperties();

			System.out.println("Controller: " + controller.getDeviceId() + ", type=" + type + ", isPro=" + isPro + ", controls=" + controls.size());
			if (isPro) {
				Assertions.assertTrue(controls.isEmpty(), "IP Link Pro controller " + controller.getDeviceId() + " should not have any controllable properties");
				continue;
			}

			AdvancedControllableProperty powerControl = findControl(controller, Constant.POWER_PROPERTY);
			Assertions.assertNotNull(powerControl, "Expected a '" + Constant.POWER_PROPERTY + "' control on controller " + controller.getDeviceId());

			String status = controller.getProperties().get(ControllerProperty.STATUS.getName());
			int expectedInitialValue = Constant.ACTIVE.equalsIgnoreCase(status) ? 1 : 0;
			System.out.println("  Status=" + status + ", control value=" + powerControl.getValue());
			Assertions.assertEquals(String.valueOf(expectedInitialValue), String.valueOf(powerControl.getValue()),
					"Initial switch value should match the Status readback");

			int newValue = expectedInitialValue == 1 ? 0 : 1;
			communicator.controlProperty(new ControllableProperty(Constant.POWER_PROPERTY, newValue, controller.getDeviceId()));
			System.out.println("  Sent " + Constant.POWER_PROPERTY + "=" + newValue + " to controller " + controller.getDeviceId());
		}
	}

	/**
	 * Triggers a full monitoring cycle against the live GVE server and waits {@link #DATA_COLLECTION_WAIT_MS}
	 * for it to complete before reading the resulting aggregated devices/controllers back.
	 *
	 * @return the resulting list of aggregated devices/controllers
	 */
	private List<AggregatedDevice> collectAggregatedDevices() throws Exception {
		communicator.getMultipleStatistics();
		communicator.retrieveMultipleStatistics();
		Thread.sleep(DATA_COLLECTION_WAIT_MS);
		return communicator.retrieveMultipleStatistics();
	}

	/**
	 * Filters aggregated devices by category.
	 *
	 * @param devices the aggregated devices to filter
	 * @param category the category to match, e.g. {@link Constant#CONTROLLER}
	 * @param include when {@code true}, keeps devices matching {@code category}; when {@code false}, keeps everything else
	 * @return the filtered list
	 */
	private List<AggregatedDevice> filterByCategory(List<AggregatedDevice> devices, String category, boolean include) {
		return devices.stream()
				.filter(device -> include == category.equals(device.getCategory()))
				.collect(Collectors.toList());
	}

	/**
	 * Finds a controllable property by name on an aggregated device.
	 *
	 * @param device the aggregated device to search
	 * @param propertyName the controllable property's own name
	 * @return the matching control, or {@code null} if not present
	 */
	private AdvancedControllableProperty findControl(AggregatedDevice device, String propertyName) {
		return device.getControllableProperties().stream()
				.filter(control -> propertyName.equals(control.getName()))
				.findFirst()
				.orElse(null);
	}
}
