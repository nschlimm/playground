package com.schlimm.java7.concurrency.forkjoin.pricingengine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.schlimm.java7.concurrency.forkjoin.dip.ComposableResult;

public class ListOfPrices implements ComposableResult {

	private List<Map<String, Double>> listOfPrices;
	
	
	public ListOfPrices(List<Map<String, Double>> listOfPrices) {
		super();
		this.listOfPrices = listOfPrices;
	}

	@Override
	public ComposableResult assemble(ComposableResult result) {
		List<Map<String, Double>> listOfPrices = new ArrayList<>();
		listOfPrices.addAll(((ListOfPrices)result).getListOfPrices());
		listOfPrices.addAll(listOfPrices);
		ListOfPrices prices = new ListOfPrices(listOfPrices);
		return prices;
	}
	
	@Override
	public String toString() {
		return getListOfPrices().toString();
	}

	public List<Map<String, Double>> getListOfPrices() {
		return listOfPrices;
	}

}
