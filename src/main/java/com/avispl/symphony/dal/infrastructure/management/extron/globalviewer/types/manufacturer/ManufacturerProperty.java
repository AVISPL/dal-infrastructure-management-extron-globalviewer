/** Copyright (c) 2026 AVI-SPL, Inc. All Rights Reserved. */
package com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.types.manufacturer;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.base.FieldProperty;

/**
 * The device manufacturer properties, sourced from {@code /devices/manufacturer/{manufacturerId}}.
 *
 * @author Ritik Madaan / Symphony Dev Team
 * @since 1.0.0
 */
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Getter
@AllArgsConstructor
public enum ManufacturerProperty implements FieldProperty {
	ID("ManufacturerId", "/ManufacturerId", "", false),
	NAME("ManufacturerName", "/ManufacturerName", "", false),
	;

	/** Property name exposed internally. */
	String name;
	/** Jackson pointer to the value within the manufacturer JSON node. */
	String field;
	/** Group prefix; unused, always empty. */
	String group;
	/** When {@code true}, this property is omitted (rather than shown as {@code N/A}) when {@link #field} doesn't resolve. */
	boolean conditional;
}
