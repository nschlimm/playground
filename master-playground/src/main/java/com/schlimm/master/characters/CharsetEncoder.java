package com.schlimm.master.characters;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;

public class CharsetEncoder {
	
	public static void main(String[] args) {
		Charset utf8 = Charset.forName("UTF8");
		Charset utf16 = Charset.forName("UTF_16BE");
		CharBuffer cb = CharBuffer.allocate(12);
		cb.append("Hello World!");
		cb.flip();
		ByteBuffer bb8 = utf8.encode(cb);
		System.out.println("bb8 = " + bb8);
		cb.rewind(); // no need to reset limit ... therefore rewind() instead of flip()
		ByteBuffer bb16 = utf16.encode(cb);
		System.out.println("bb16 = " + bb16);
	}

}
