package com.schlimm.master.characters;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;

public class CharactersTest {

	public static void main(String[] args) throws FileNotFoundException {
		StringBuffer buf = new StringBuffer();
		buf.appendCodePoint(0x1D11E);
		buf.appendCodePoint(0x1D160);
		buf.appendCodePoint(0x1D160);
		buf.appendCodePoint(0x1D160);
		PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream("music.html"), Charset.forName("UTF_32BE")));
		out.println(buf);
		out.close();
	}
}
