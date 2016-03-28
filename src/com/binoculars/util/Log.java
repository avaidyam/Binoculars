package com.binoculars.util;

import com.binoculars.nuclei.Domain;
import com.binoculars.nuclei.Nucleus;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.function.Supplier;

/**
 * A wrapper for structured logging + metrics.
 */
public class Log extends Nucleus<Log> {

	/**
	 * Logger Severity levels.
	 */
	public enum Severity {
		VERBOSE, // --> Log.v()
		DEBUG, // --> Log.d()
		INFO, // --> Log.i()
		WARN, // --> Log.w()
		ERROR, // --> Log.e()
		ASSERT; // --> Log.wtf()

		/**
		 * Returns a short string representation of the Severity.
		 *
		 * @return a short string representation of the Severity
		 */
		@Override
		public String toString() {
			switch (this) {
				case VERBOSE: return "V";
				case DEBUG: return "D";
				case INFO: return "I";
				case WARN: return "W";
				case ERROR: return "E";
				case ASSERT: return "WTF";
				default: return "???";
			}
		}
	}

	/**
	 * Logger interface to plug into another logging system.
	 */
	@FunctionalInterface
	public interface Logger {

		/**
		 * Log the details provided to an output.
		 *
		 * @param thread the logging thread
		 * @param severity the logging severity
		 * @param tag the logging tag
		 * @param msg the message to log
		 * @param exception an optional exception to log
		 */
		void log(Thread thread, Severity severity, String tag, String msg, Throwable exception);
	}

	/**
	 * The date format used by the default logger.
	 */
	private static final DateFormat formatter = new SimpleDateFormat("HH:mm:ss:SSS");

	/**
	 * A facility to supply the default logger. The default logger
	 * prints all messages (including exceptions and stack traces)
	 * to the console out in the following format:
	 *
	 * [04:32:22:984] I/MyTag <dispatch thread 1>: this is a test message
	 *
	 */
	private static final Supplier<Logger> defaultLogger = () -> (t, sev, tag, msg, ex) -> {
		System.out.println("[" + formatter.format(new Date()) + "] " +
				sev + "/" + (tag == null ? "-" : tag) +
				" <" + (t == null ? "-" : t.getName()) + ">: " + msg);

		if (ex == null)
			return;
		if (sev.ordinal() < Severity.ERROR.ordinal())
			System.out.println(ex.toString());
		else ex.printStackTrace(System.out);
	};

	/** SINGLETON */

	private enum LogHolder {
		$;
		Log L = Nucleus.of(Log.class, 100000);
	}

	public static Log get() {
		return LogHolder.$.L;
	}

	/** EXTERNAL LOGGING FACILITY */

	public static void v(String tag, String msg) {
		LogHolder.$.L.verbose(tag, msg, null);
	}

	public static void v(String tag, String msg, Throwable ex) {
		LogHolder.$.L.verbose(tag, msg, ex);
	}

	public static void d(String tag, String msg) {
		LogHolder.$.L.debug(tag, msg, null);
	}

	public static void d(String tag, String msg, Throwable ex) {
		LogHolder.$.L.debug(tag, msg, ex);
	}

    public static void i(String tag, String msg) {
        LogHolder.$.L.info(tag, msg, null);
    }

	public static void i(String tag, String msg, Throwable ex) {
		LogHolder.$.L.info(tag, msg, ex);
	}

	public static void w(String tag, String msg) {
		LogHolder.$.L.warn(tag, msg, null);
	}

    public static void w(String tag, String msg, Throwable ex) {
        LogHolder.$.L.warn(tag, msg, ex);
    }

	public static void e(String tag, String msg) {
		LogHolder.$.L.error(tag, msg, null);
	}

	public static void e(String tag, String msg, Throwable ex) {
		LogHolder.$.L.error(tag, msg, ex);
	}

	public static void wtf(String tag, String msg) {
		LogHolder.$.L.$assert(tag, msg, null);
	}

	public static void wtf(String tag, String msg, Throwable ex) {
		LogHolder.$.L.$assert(tag, msg, ex);
	}

	/** INTERNAL LOGGING DELEGATION */

	@Domain.CallerSide
	public void verbose(String tag, String msg, Throwable ex) {
		self().println(Thread.currentThread(), Severity.VERBOSE, tag, msg, ex);
	}

	@Domain.CallerSide
	public void debug(String tag, String msg, Throwable ex) {
		self().println(Thread.currentThread(), Severity.DEBUG, tag, msg, ex);
	}

	@Domain.CallerSide
    public void info(String tag, String msg, Throwable ex) {
        self().println(Thread.currentThread(), Severity.INFO, tag, msg, ex);
    }

    @Domain.CallerSide
    public void warn(String tag, String msg, Throwable ex) {
        self().println(Thread.currentThread(), Severity.WARN, tag, msg, ex);
    }

    @Domain.CallerSide
    public void error(String tag, String msg, Throwable ex) {
        self().println(Thread.currentThread(), Severity.ERROR, tag, msg, ex);
    }

	@Domain.CallerSide
	public void $assert(String tag, String msg, Throwable ex) {
		self().println(Thread.currentThread(), Severity.ASSERT, tag, msg, ex);

		// Forcibly flush buffers and halt the Java Runtime.
		System.out.flush();
		Runtime.getRuntime().halt(-1);
	}

	/** CONFIGURATION */

	private volatile Severity severity = Severity.INFO;
	private Logger logger = defaultLogger.get();

	/**
	 * Sets the current logging severity level.
	 *
	 * @param severity the severity to assume
	 */
	public void setSeverity(Severity severity) {
		getNucleus().severity = severity;
	}

	/**
	 * Returns the current logging severity level.
	 *
	 * @return the current severity
	 */
	@Domain.CallerSide
	public Severity getSeverity() {
		return getNucleus().severity;
	}

	/**
	 * Sets the current logger to the provided implementation.
	 *
	 * @param logger a delegate implementation of Logger
	 */
	public void setLogger(Logger logger) {
		this.logger = logger;
	}

	/**
	 * Resets the current Logger to the default Logger implementation.
	 */
	@Domain.CallerSide
	public void resetLogger() {
		this.logger = defaultLogger.get();
	}

	/**
	 * Delegate async function that invokes the current Logger.
	 * The Logger will not be invoked if the severity is within threshold.
	 *
	 * @param thread the logging thread
	 * @param severity the logging severity
	 * @param tag the logging tag
	 * @param msg the message to log
	 * @param ex an optional exception to log
	 */
    public void println(Thread thread, Severity severity, String tag, String msg, Throwable ex) {
	    if (this.severity.ordinal() <= severity.ordinal())
		    logger.log(thread, severity, tag, msg, ex);
    }
}
