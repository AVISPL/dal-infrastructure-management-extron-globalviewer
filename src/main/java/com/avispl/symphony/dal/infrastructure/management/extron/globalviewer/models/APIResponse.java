/**
 * Copyright (c) 2026 AVI-SPL, Inc. All Rights Reserved.
 */
package com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/**
 * Represents a generic API response from the Extron GlobalViewer Enterprise system.
 * <p>
 * This model wraps the top-level {@code ResponseStatus} object returned by the API,
 * which carries an error code and a human-readable message describing the outcome
 * of the request.
 * </p>
 *
 * @author Kevin / Symphony Dev Team
 * @since 1.0.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
public class APIResponse {
    @JsonProperty("ResponseStatus")
    ResponseStatus responseStatus;

    /**
     * Represents the {@code ResponseStatus} block contained in an API response.
     * <p>
     * Contains an {@code ErrorCode} that indicates success or the type of failure,
     * and a {@code Message} with additional detail about the result.
     * </p>
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @NoArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    @Getter
    @Setter
    public static class ResponseStatus {
        @JsonProperty("ErrorCode")
        String errorCode;

        @JsonProperty("Message")
        String message;
    }
}
