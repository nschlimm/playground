package com.schlimm.webappbenchmarker.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class StandardJavaSerialization implements ApplicationLayerProtocol {

	@Override
	public byte[] toByteArray(Object object) {
	    ByteArrayOutputStream bos = new ByteArrayOutputStream() ;
	    try {
			ObjectOutputStream out = new ObjectOutputStream(bos) ;
			out.writeObject(object);
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	    return bos.toByteArray();
	}

	@Override
	public Object fromByteArray(byte[] data) {
		Object object = null;
		try {
			object = new ObjectInputStream(new ByteArrayInputStream(data)).readObject();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		return object;
	}

}
