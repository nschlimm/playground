package com.schlimm.master.characters;

import java.nio.charset.Charset;

public class AvailableCharsets {

	public static void main(String[] args) {
		for (String name : Charset.availableCharsets().keySet()) {
			System.out.println(name);
		}
	}
}
