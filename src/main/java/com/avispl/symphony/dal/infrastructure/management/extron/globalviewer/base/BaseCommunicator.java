/** Copyright (c) 2026 AVI-SPL, Inc. All Rights Reserved. */
package com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.base;

import java.util.concurrent.locks.ReentrantLock;

import com.avispl.symphony.dal.communicator.RestCommunicator;

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

	protected BaseCommunicator() {
		this.reentrantLock = new ReentrantLock();
	}

	@Override
	protected void internalInit() throws Exception {
		this.setAuthenticationScheme(AuthenticationScheme.None);
		this.setTrustAllCertificates(true);
		super.internalInit();
	}
}
