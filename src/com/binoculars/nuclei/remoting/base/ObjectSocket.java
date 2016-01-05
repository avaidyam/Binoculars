package com.binoculars.nuclei.remoting.base;

import org.nustaq.serialization.FSTConfiguration;

import java.io.IOException;

/**
 *
 */
public interface ObjectSocket {

	void writeObject(Object toWrite) throws Exception;

	void flush() throws Exception;

	void setLastError(Throwable ex);

	Throwable getLastError();

	/**
	 * set by outer machinery
	 * @param conf
	 */
	void setConf( FSTConfiguration conf );

	FSTConfiguration getConf();

	void close() throws IOException;

	default boolean canWrite() {
		return true;
	}

	boolean isClosed();
}
