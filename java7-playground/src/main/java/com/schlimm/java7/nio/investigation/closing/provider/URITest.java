package com.schlimm.java7.nio.investigation.closing.provider;

import java.net.URI;
import java.net.URISyntaxException;

public class URITest {
	
	private static final String FILE_URI = "file:/e:/temp/afile.out";

	public static void main(String[] args) throws URISyntaxException {
		System.out.println(new URI(FILE_URI).getAuthority());
		System.out.println(new URI(FILE_URI).getFragment());
		System.out.println(new URI(FILE_URI).getHost());
		System.out.println(new URI(FILE_URI).getPath());
		System.out.println(new URI(FILE_URI).getPort());
		System.out.println(new URI(FILE_URI).getScheme());
		System.out.println(new URI(FILE_URI).getSchemeSpecificPart());
		System.out.println(new URI(FILE_URI).getUserInfo());
	}

}
