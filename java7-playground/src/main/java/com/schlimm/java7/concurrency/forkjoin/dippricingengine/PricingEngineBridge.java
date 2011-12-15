package com.schlimm.java7.concurrency.forkjoin.dippricingengine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.schlimm.forkjoindip.ComposableResult;
import com.schlimm.forkjoindip.ComputationActivityBridge;
import com.schlimm.forkjoindip.DecomposableInput;

public class PricingEngineBridge extends ComputationActivityBridge<List<Proposal>, List<Map<String, Double>>> {

	private PricingEngine engine = new PricingEngine();
	
	@Override
	public ComposableResult<List<Map<String, Double>>> compute(DecomposableInput<List<Proposal>> input) {
		Map<String, Double> result = engine.calculatePrices(input.getRawInput().get(0));
		List<Map<String, Double>> priceList = new ArrayList<>();
		priceList.add(result);
		return new ListOfPrices(priceList);
	}


}
