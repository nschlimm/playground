package com.schlimm.webappbenchmarker.protocol;

public interface ApplicationLayerProtocol {
	
	byte[] toByteArray(Object object);
	Object fromByteArray(byte[] data);

}
