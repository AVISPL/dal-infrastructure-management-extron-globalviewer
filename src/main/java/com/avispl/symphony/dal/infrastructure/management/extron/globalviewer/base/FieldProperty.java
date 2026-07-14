/** Copyright (c) 2026 AVI-SPL, Inc. All Rights Reserved. */
package com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.base;

/**
 * Extends {@link BaseProperty} for property enums that are resolved from a JSON payload via a
 * Jackson pointer, optionally grouped under a prefix when exposed as a monitored property.
 *
 * @author Ritik Madaan / Symphony Dev Team
 * @since 1.0.0
 */
public interface FieldProperty extends BaseProperty {
	/**
	 * Returns the Jackson pointer to the value within the JSON node this property is resolved from.
	 *
	 * @return the field pointer
	 */
	String getField();

	/**
	 * Returns the optional group prefix; empty means the property is flat (no {@code group#} prefix).
	 *
	 * @return the group prefix
	 */
	String getGroup();

	/**
	 * Indicates whether this property should only be exposed when its {@link #getField()} pointer
	 * actually resolves against the device JSON. Conditional properties are omitted entirely (not shown
	 * as {@code N/A}) when absent, for data that only exists on some devices/hardware configurations
	 * (e.g. a secondary lamp tracker that only multi-lamp projectors report). Non-conditional properties
	 * always show up, falling back to {@code N/A} when missing.
	 *
	 * @return {@code true} if the property should be omitted (rather than shown as {@code N/A}) when absent
	 */
	boolean isConditional();
}
