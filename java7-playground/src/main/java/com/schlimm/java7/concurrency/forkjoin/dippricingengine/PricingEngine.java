package com.schlimm.java7.concurrency.forkjoin.dippricingengine;

import java.util.HashMap;
import java.util.Map;

public class PricingEngine {

	private double comprehensiveCoverBasePrice = 132.01;
	private double partInsuranceCoverBasePrice = 70.01;
	private double automotiveLiabilityBasePrice = 167.01;

	private Map<String, Double> hsnTsnFactors = new HashMap<>();
	{
		hsnTsnFactors.put("7909AAL", 2.31);
		hsnTsnFactors.put("0005432", 1.91);
		hsnTsnFactors.put("0583442", 3.31);
		hsnTsnFactors.put("4026AAA", 2.43);
	}

	public Map<String, Double> calculatePrices(Proposal proposal) {
		Map<String, Double> result = new HashMap<>();
		if (proposal.isComprehensive()) {
			complexRateCalculation();
			result.put("Comprehensive Cover",
					comprehensiveCoverBasePrice * hsnTsnFactors.get(proposal.getHsn().concat(proposal.getTsn())));
		}
		if (proposal.isPartInsuranceCover()) {
			complexRateCalculation();
			result.put("Part Insurance Cover",
					partInsuranceCoverBasePrice * hsnTsnFactors.get(proposal.getHsn().concat(proposal.getTsn())));
		}
		if (proposal.isAutomotiveLiability()) {
			complexRateCalculation();
			result.put("Automotive Liability",
					automotiveLiabilityBasePrice * hsnTsnFactors.get(proposal.getHsn().concat(proposal.getTsn())));
		}
		return result;
	}

	private void complexRateCalculation() {
		int a = 0, b = 1;
		for (int i = 0; i < 1000000000; i++) {
			a = a + b;
			b = a - b;
		}
	}

}
