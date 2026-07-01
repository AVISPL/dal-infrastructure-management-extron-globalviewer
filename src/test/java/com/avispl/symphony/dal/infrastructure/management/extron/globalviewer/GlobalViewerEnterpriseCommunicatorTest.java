/** Copyright (c) 2026 AVI-SPL, Inc. All Rights Reserved. */
package com.avispl.symphony.dal.infrastructure.management.extron.globalviewer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.avispl.symphony.api.dal.dto.monitor.ExtendedStatistics;
import com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.common.Constant;

import javax.security.auth.login.FailedLoginException;

/**
 * @author Kevin / Symphony Dev Team
 * @since 1.0.0
 */
class GlobalViewerEnterpriseCommunicatorTest {
	private ExtendedStatistics extendedStatistics;
	private GlobalViewerEnterpriseCommunicator communicator;

	@BeforeEach
	void setUp() throws Exception {
		this.communicator = new GlobalViewerEnterpriseCommunicator();
		this.communicator.setHost("");
		this.communicator.setPort(80);
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
}
