/*
 * Copyright (c) 2016 Aditya Vaidyam
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.avaidyam.binoculars.remoting.encoding;

import com.avaidyam.binoculars.remoting.base.RemoteRegistry;
import org.nustaq.serialization.FSTConfiguration;

/**
 * Used by the {@link RemoteRegistry} to wrap the task of configuring
 * and coding different remote references automatically.
 */
public class Coding {
	SerializerType coding;
	Class crossPlatformShortClazzNames[];

	public Coding(SerializerType coding) {
		this.coding = coding;
	}

	public Coding(SerializerType coding, Class[] crossPlatformShortClazzNames) {
		this.coding = coding;
		this.crossPlatformShortClazzNames = crossPlatformShortClazzNames;
	}

	public Class[] getCrossPlatformShortClazzNames() {
		return crossPlatformShortClazzNames;
	}

	public SerializerType getCoding() {
		return coding;
	}

	public FSTConfiguration createConf() {
		FSTConfiguration conf;
		switch (coding) {
			case MinBin:
				conf = FSTConfiguration.createMinBinConfiguration();
				break;
			case Json:
				conf = FSTConfiguration.createJsonConfiguration();
				break;
			case JsonNoRef:
				conf = FSTConfiguration.createJsonConfiguration(false, false);
				break;
			case JsonNoRefPretty:
				conf = FSTConfiguration.createJsonConfiguration(true, false);
				break;
			case UnsafeBinary:
				conf = FSTConfiguration.createFastBinaryConfiguration();
				break;
			case FSTSer:
				conf = FSTConfiguration.createDefaultConfiguration();
				break;
			default:
				throw new RuntimeException("unknown ser configuration type");
		}
		return conf;
	}

	@Override
	public String toString() {
		return "Coding{" +
				"coding=" + coding +
				'}';
	}
}
