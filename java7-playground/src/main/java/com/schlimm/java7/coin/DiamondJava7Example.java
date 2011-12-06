package com.schlimm.java7.coin;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DiamondJava7Example {

	public static void main(String[] args) {
		List<Map<Date, String>> listOfMaps = new ArrayList<>(); // type information once!
		HashMap<Date, String> aMap = new HashMap<>(); // type information once!
		aMap.put(new Date(), "Hello");
		listOfMaps.add(aMap);
		System.out.println(listOfMaps);
	}
}
