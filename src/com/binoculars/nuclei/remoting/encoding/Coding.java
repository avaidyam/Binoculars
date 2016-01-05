package com.binoculars.nuclei.remoting.encoding;

import org.nustaq.serialization.FSTConfiguration;

/**
 * Used by the {@link com.binoculars.nuclei.remoting.base.RemoteRegistry} to wrap the task of configuring
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
