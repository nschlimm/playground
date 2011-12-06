package com.schlimm.java7.coin;

public class StringInSwitch {

	public void printMonth(String month) {
		switch (month) {
		case "April":
		case "June":
		case "September":
		case "November":
		case "January":
		case "March":
		case "May":
		case "July":
		case "August":
		case "December":
		default:
			System.out.println("done!");
		}
	}

}
