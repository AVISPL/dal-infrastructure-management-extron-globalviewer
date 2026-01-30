/** Copyright (c) 2026 AVI-SPL, Inc. All Rights Reserved. */
package com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.base;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.security.auth.login.FailedLoginException;

import com.avispl.symphony.dal.communicator.RestCommunicator;
import com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.common.constants.Constant;
import com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.types.ResponseType;

/**
 * Configures the communicator and provides helper methods for managing adapter properties.
 * <p>This class centralizes all communicator-related configuration and exposes utility methods to access adapter properties.
 *
 * @author Kevin / Symphony Dev Team
 * @since 1.0.0
 */
public abstract class BaseCommunicator extends RestCommunicator {
	/** Lock for thread-safe operations. */
	protected final ReentrantLock reentrantLock;
	/** Object mapper used to convert JSON responses into Java objects. */
	private final ObjectMapper objectMapper;

	protected BaseCommunicator() {
		this.reentrantLock = new ReentrantLock();
		this.objectMapper = new ObjectMapper();
	}

	@Override
	protected void internalInit() throws Exception {
		this.setAuthenticationScheme(AuthenticationScheme.None);
		this.setTrustAllCertificates(true);
		super.internalInit();
	}

	/**
	 * Fetches data from a given endpoint and maps the response to the specified type defined in {@link ResponseType}.
	 *
	 * @param endpoint the target endpoint to fetch data from
	 * @param responseType defines how to extract and map the response into a specific class
	 * @param <T> the generic type representing the expected response object
	 * @return the mapped response object, or {@code null} if the response is empty or mapping fails
	 * @throws FailedLoginException if authentication fails while accessing the endpoint
	 * @throws IllegalStateException if an unexpected error occurs while fetching or processing the response
	 */
	public <T> T fetchData(String endpoint, ResponseType responseType) throws FailedLoginException {
		String previewedResponse = null;
		try {
			String response = Optional.ofNullable(super.doGet(endpoint)).map(String::trim).orElse(null);
			if (response == null || response.isBlank()) {
				this.logger.warn("Empty response from endpoint '%s'".formatted(endpoint));
				return null;
			}
			previewedResponse = response.substring(0, Math.min(150, response.length()));
			JsonNode responseNode = responseType.extractNode(this.objectMapper.readTree(response));
			@SuppressWarnings("unchecked")
			T mappedResponse = responseType.isCollection()
					? (T) this.objectMapper.convertValue(responseNode, responseType.getTypeRef(this.objectMapper))
					: (T) this.objectMapper.treeToValue(responseNode, responseType.getClazz());
			if (Objects.isNull(mappedResponse)) {
				this.logger.warn(String.format(Constant.FETCHED_DATA_NULL_WARNING, endpoint, responseType.getClazz().getSimpleName()));
			}

			return mappedResponse;
		} catch (FailedLoginException e) {
			throw e;
		} catch (JacksonException e) {
			this.logger.error("Failed to parse JSON from endpoint %s, preview: %s".formatted(endpoint, previewedResponse), e);
			return null;
		} catch (Exception e) {
			throw new IllegalStateException(Constant.FETCH_DATA_FAILED.formatted(endpoint), e);
		}
	}
}
