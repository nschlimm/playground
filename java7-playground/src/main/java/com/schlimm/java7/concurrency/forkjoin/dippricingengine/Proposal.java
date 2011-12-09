package com.schlimm.java7.concurrency.forkjoin.dippricingengine;

@SuppressWarnings("unused")
public class Proposal {

	private String vorname;
	private String nachname;
	private String hsn;
	private String tsn;
	private boolean comprehensive;
	private boolean partInsuranceCover;
	private boolean automotiveLiability;

	public Proposal(String vorname, String nachname, String hsn, String tsn, boolean comprehensive,
			boolean partInsuranceCover, boolean automotiveLiability) {
		super();
		this.vorname = vorname;
		this.nachname = nachname;
		this.hsn = hsn;
		this.tsn = tsn;
		this.comprehensive = comprehensive;
		this.partInsuranceCover = partInsuranceCover;
		this.automotiveLiability = automotiveLiability;
	}

	public boolean multipleCovers() {
		return ((isAutomotiveLiability() ? 1 : 0) + (isComprehensive() ? 1 : 0) + (isPartInsuranceCover() ? 1 : 0)) > 1;
	}

	public boolean isComprehensive() {
		return comprehensive;
	}

	public boolean isPartInsuranceCover() {
		return partInsuranceCover;
	}

	public boolean isAutomotiveLiability() {
		return automotiveLiability;
	}

	public String getHsn() {
		return hsn;
	}

	public String getTsn() {
		return tsn;
	}
}
