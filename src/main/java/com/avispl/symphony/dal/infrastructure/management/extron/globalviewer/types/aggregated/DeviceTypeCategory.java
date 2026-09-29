/** Copyright (c) 2026 AVI-SPL, Inc. All Rights Reserved. */
package com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.types.aggregated;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single entry of {@code device-type-category-mapping.yml} - the Symphony {@code type}/{@code category}
 * that a raw Extron {@code DeviceType} value should be mapped onto.
 *
 * @author Ritik Madaan / Symphony Dev Team
 * @since 1.0.0
 */
@Getter
@Setter
@NoArgsConstructor
public class DeviceTypeCategory {
	/** The Symphony type this Extron {@code DeviceType} maps to (e.g. {@code "AV Devices"}). */
	private String type;
	/** The Symphony category this Extron {@code DeviceType} maps to (e.g. {@code "Receivers"}). */
	private String category;
}
