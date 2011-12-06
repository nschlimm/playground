package com.schlimm.java7.coin;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DiamondJava6Example {

	public static void main(String[] args) {
		List<Map<Date, String>> listOfMaps_Java6Notation = new ArrayList<Map<Date, String>>(); // type information twice!
		HashMap<Date, String> aMap = new HashMap<Date, String>(); // type information twice
		aMap.put(new Date(), "Hello");
		listOfMaps_Java6Notation.add(aMap);
		System.out.println(listOfMaps_Java6Notation);
	}
}
