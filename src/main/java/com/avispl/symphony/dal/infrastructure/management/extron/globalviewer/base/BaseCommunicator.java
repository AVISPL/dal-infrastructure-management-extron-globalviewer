/** Copyright (c) 2026 AVI-SPL, Inc. All Rights Reserved. */
package com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.base;

import com.avispl.symphony.api.dal.error.CommandFailureException;
import com.avispl.symphony.api.dal.error.ResourceNotReachableException;
import com.avispl.symphony.dal.communicator.RestCommunicator;
import com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.common.Constant;
import com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.models.APIResponse;
import com.avispl.symphony.dal.util.StringUtils;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.util.UriComponentsBuilder;

import javax.security.auth.login.FailedLoginException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Configures the communicator and provides helper methods for managing adapter properties.
 * <p>This class centralizes all communicator-related configuration and exposes utility methods to access adapter properties.
 *
 * @author Kevin / Symphony Dev Team
 * @since 1.0.0
 */
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class BaseCommunicator extends RestCommunicator {
	/** Lock for thread-safe operations. */
	protected final ReentrantLock reentrantLock = new ReentrantLock();
	/** Stores the session ID extracted from the response cookie upon successful authentication. */
	private String sessionID = "";

	@Override
	protected void internalInit() throws Exception {
		this.setAuthenticationScheme(AuthenticationScheme.None);
		this.setTrustAllCertificates(true);
		this.setBaseUri("/GVE/api");
		super.internalInit();
	}

	@Override
	protected void authenticate() throws Exception {
		if (StringUtils.isNullOrEmpty(this.getLogin(), true) || StringUtils.isNullOrEmpty(this.getPassword(), true)) {
			throw new FailedLoginException("Failed to authenticate, the username or password has not provided");
		}
		// 	Already authenticated - reuse existing session ID
		if (StringUtils.isNotNullOrEmpty(this.sessionID)) {
			return;
		}
		var loginUrl = UriComponentsBuilder.newInstance()
				.scheme(this.getProtocol()).host(this.host).port(this.getPort())
				.path(this.getBaseUri()).path(Constant.AUTH_ENDPOINT)
				.toUriString();
		try {
			//	Send login API to get session ID from cookie header
			var request = new HttpEntity<>(Map.of("UserName", this.getLogin(), "Password", this.getPassword()));
			var response = this.obtainRestTemplate().exchange(loginUrl, HttpMethod.POST, request, APIResponse.class);
			//	Validate the credentials
			var responseBody = Objects.requireNonNullElseGet(response.getBody(), APIResponse::new);
			var status = responseBody.getResponseStatus();
			if (status != null && "Login Failed".equals(status.getMessage())) {
				throw new FailedLoginException("Invalid authentication credentials for " + loginUrl);
			}
			//	Validate the session ID
			this.sessionID = Optional.of(response.getHeaders().get(HttpHeaders.SET_COOKIE))
					.filter(cookies -> !cookies.isEmpty()).map(cookies -> cookies.get(0))
					.orElseThrow(() -> new FailedLoginException("Failed to authenticate, session ID missing from response header"));
			if (StringUtils.isNullOrEmpty(this.sessionID, true)) {
				throw new FailedLoginException("Failed to authenticate, the session ID is null or empty");
			}
		} catch (ResourceAccessException e) {
			throw new ResourceNotReachableException("Cannot reach resource at " + loginUrl, e);
		}
	}

	@Override
	protected HttpHeaders putExtraRequestHeaders(HttpMethod httpMethod, String uri, HttpHeaders headers) throws Exception {
		if (!"/login".equals(uri)) {
			headers.set(HttpHeaders.COOKIE, this.sessionID);
		}
		return super.putExtraRequestHeaders(httpMethod, uri, headers);
	}

	/**
	 * A single REST call (e.g. {@code () -> this.doGet(endpoint)}), to be retried by
	 * {@link #withSessionRecovery(RestOperation)} after a session recovery.
	 */
	@FunctionalInterface
	protected interface RestOperation {
		String execute() throws Exception;
	}

	/**
	 * Runs {@code operation}, transparently recovering from a server-invalidated session (a stale
	 * session cookie surfaces as HTTP 401 or 405) by clearing the cached session, re-authenticating,
	 * and retrying {@code operation} once. Any other failure (bad credentials, network issue,
	 * unrelated 4xx/5xx, or a retry that still fails) is propagated as-is.
	 *
	 * @param operation the REST call to run, and retry once if it fails due to an invalid session
	 * @return {@code operation}'s result
	 * @throws Exception if {@code operation} fails for a reason other than a recoverable session,
	 * or if the retry itself fails
	 */
	protected String withSessionRecovery(RestOperation operation) throws Exception {
		try {
			return operation.execute();
		} catch (FailedLoginException | CommandFailureException e) {
			if (!isSessionInvalid(e)) {
				throw e;
			}
			this.sessionID = "";
			this.authenticate();
			return operation.execute();
		}
	}

	/**
	 * Determines whether {@code e} signals a server-invalidated session: either a {@link FailedLoginException}
	 * (thrown by the framework itself for HTTP 401), or a {@link CommandFailureException} with status
	 * 401 or 405.
	 *
	 * @param e the exception thrown by a REST call
	 * @return {@code true} if {@code e} indicates the cached session is no longer valid
	 */
	private boolean isSessionInvalid(Exception e) {
		if (e instanceof FailedLoginException) {
			return true;
		}
		int statusCode = ((CommandFailureException) e).getStatusCode();
		return statusCode == 401 || statusCode == 405;
	}
}
