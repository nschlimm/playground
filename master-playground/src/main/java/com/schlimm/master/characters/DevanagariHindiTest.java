package com.schlimm.master.characters;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;

public class DevanagariHindiTest {
	
	public static void main(String[] args) throws FileNotFoundException {
		StringBuffer buf = new StringBuffer();
		for (int i = 0x0901; i < 0x093A; i++) {
			buf.append("0x0");
			buf.append(Integer.toHexString(i)).append(" : ");
			buf.append((char)i);
			buf.append("<br/>");
		}
		// Unicode streams with InputStreamReader and OutputStreamWriter
		// If you need a different encoding use PrintWriter and point to OutputStreamWriter
		PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream("utf16.html"), Charset.forName("UTF_16BE")));
		out.println(buf);
		out.close();
	}

}
