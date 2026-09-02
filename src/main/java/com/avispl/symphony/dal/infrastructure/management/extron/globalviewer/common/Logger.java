/** Copyright (c) 2026 AVI-SPL, Inc. All Rights Reserved. */
package com.avispl.symphony.dal.infrastructure.management.extron.globalviewer.common;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Wrapper class around {@link Log} that provides convenience factory and helper methods for application logging.
 *
 * @author Kevin / Symphony Dev Team
 * @since 1.0.0
 */
public final class Logger {
	private final Log log;

	public Logger(Log logger) {
		this.log = logger;
	}

	public static <T> Logger ofClass(Class<T> clazz) {
		return new Logger(LogFactory.getLog(clazz));
	}

	public void info(Object o) {
		if (this.log.isInfoEnabled()) {
			this.log.info(o);
		}
	}

	public void warn(Object o) {
		if (this.log.isWarnEnabled()) {
			this.log.warn(o);
		}
	}

	public void error(Object o, Throwable throwable) {
		if (this.log.isErrorEnabled()) {
			this.log.error(o, throwable);
		}
	}
}
