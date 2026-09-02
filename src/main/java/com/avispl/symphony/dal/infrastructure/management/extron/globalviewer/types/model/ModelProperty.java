/** Copyright (c) 2026 AVI-SPL, Inc. All Rights Reserved. */
package com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.types.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.base.FieldProperty;

/**
 * The device model properties, sourced from {@code /devices/model/{modelId}}.
 *
 * @author Ritik Madaan / Symphony Dev Team
 * @since 1.0.0
 */
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Getter
@AllArgsConstructor
public enum ModelProperty implements FieldProperty {
	ID("ModelId", "/ModelId", "", false),
	NAME("ModelName", "/ModelName", "", false),
	MANUFACTURER_ID("ManufacturerId", "/ManufacturerId", "", false),
	;

	/** Property name exposed internally. */
	String name;
	/** Jackson pointer to the value within the model JSON node. */
	String field;
	/** Group prefix; unused, always empty. */
	String group;
	/** When {@code true}, this property is omitted (rather than shown as {@code N/A}) when {@link #field} doesn't resolve. */
	boolean conditional;
}
