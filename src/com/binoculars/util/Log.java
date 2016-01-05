package com.binoculars.util;

import com.binoculars.nuclei.Nucleus;
import com.binoculars.nuclei.annotation.CallerSideMethod;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.function.Supplier;

/**
 * A wrapper for logging + metrics.
 */
public class Log extends Nucleus<Log> {

	/** LOGGER SEVERITY CONSTANTS */

	public static final int VERBOSE = 0; // --> Log.v()
	public static final int DEBUG = 1; // --> Log.d()
    public static final int INFO = 2; // --> Log.i()
    public static final int WARN = 3; // --> Log.w()
    public static final int ERROR = 4; // --> Log.e()
    public static final int ASSERT = 5; // --> Log.wtf()

	/**
	 * Convert a severity level to a string identifier.
	 *
	 * @param severity the severity level to convert
	 * @return the severity as a string
	 */
	public static String severityToString(int severity) {
		switch (severity) {
			case VERBOSE:
				return "V";
			case DEBUG:
				return "D";
			case INFO:
				return "I";
			case WARN:
				return "W";
			case ERROR:
				return "E";
			case ASSERT:
				return "WTF";
			default:
				return " ";
		}
	}

	/** DEFAULT LOGGER IMPLEMENTATION */

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
		void log(Thread thread, int severity, String tag, String msg, Throwable exception);
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
				severityToString(sev) + "/" + (tag == null ? "-" : tag) +
				" <" + (t == null ? "-" : t.getName()) + ">: " + msg);

		if (ex == null)
			return;
		if (sev < ERROR)
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

	@CallerSideMethod
	public void verbose(String tag, String msg, Throwable ex) {
		self().println(Thread.currentThread(), VERBOSE, tag, msg, ex);
	}

	@CallerSideMethod
	public void debug(String tag, String msg, Throwable ex) {
		self().println(Thread.currentThread(), DEBUG, tag, msg, ex);
	}

	@CallerSideMethod
    public void info(String tag, String msg, Throwable ex) {
        self().println(Thread.currentThread(), INFO, tag, msg, ex);
    }

    @CallerSideMethod
    public void warn(String tag, String msg, Throwable ex) {
        self().println(Thread.currentThread(), WARN, tag, msg, ex);
    }

    @CallerSideMethod
    public void error(String tag, String msg, Throwable ex) {
        self().println(Thread.currentThread(), ERROR, tag, msg, ex);
    }

	@CallerSideMethod
	public void $assert(String tag, String msg, Throwable ex) {
		self().println(Thread.currentThread(), ASSERT, tag, msg, ex);

		// Forcibly flush buffers and halt the Java Runtime.
		System.out.flush();
		Runtime.getRuntime().halt(-1);
	}

	/** CONFIGURATION */

	private volatile int severity = 0;
	private Logger logger = defaultLogger.get();

	/**
	 * Sets the current logging severity level.
	 *
	 * @param severity the severity to assume
	 */
	public void setSeverity(int severity) {
		getNucleus().severity = severity;
	}

	/**
	 * Returns the current logging severity level.
	 *
	 * @return the current severity
	 */
	@CallerSideMethod
	public int getSeverity() {
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
	@CallerSideMethod
	public void resetLogger() {
		this.logger = defaultLogger.get();
	}

	/** ASYNC DELEGATE FACILITY */

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
    public void println(Thread thread, int severity, String tag, String msg, Throwable ex) {
	    if (this.severity <= severity)
		    logger.log(thread, severity, tag, msg, ex);
    }
}
